package com.antigravity.appsentinel.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.antigravity.appsentinel.model.AppInfo
import com.antigravity.appsentinel.model.SafetyLevel
import com.antigravity.appsentinel.model.SystemHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class SystemMonitorSnapshot(
    val health: SystemHealth,
    val apps: List<AppInfo>
)

class SystemMonitorRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Single-pass fetch for both SystemHealth and AppInfo list.
     * Prevents running heavy privileged commands (dumpsys meminfo) multiple times.
     */
    suspend fun getSystemMonitorSnapshot(): SystemMonitorSnapshot = withContext(Dispatchers.IO) {
        // Fallback to accurate native Android memory info if dumpsys fails or Shizuku is offline
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val nativeTotalRam = if (memInfo.totalMem > 0) memInfo.totalMem / (1024f * 1024f) else 0f
        val nativeFreeRam = if (memInfo.availMem > 0) memInfo.availMem / (1024f * 1024f) else 0f
        val nativeUsedRam = (nativeTotalRam - nativeFreeRam).coerceAtLeast(0f)

        var totalRam = nativeTotalRam
        var freeRam = nativeFreeRam
        var usedRam = nativeUsedRam
        var runningProcs = 0

        // 1. Get foreground app package (with flexible pattern matching)
        val foregroundResult = ShizukuManager.executeCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        var fgPkg = ""
        if (foregroundResult.output.isNotEmpty()) {
            val m = Pattern.compile("(?:u\\d+\\s+)?([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+)/").matcher(foregroundResult.output)
            if (m.find()) {
                fgPkg = m.group(1) ?: ""
            }
        }

        // 2. Single-pass meminfo execution for RAM pool stats AND per-process memory metrics
        val meminfoResult = ShizukuManager.executeCommand("dumpsys meminfo")
        val processRamMap = mutableMapOf<String, Float>()
        val activeActivityPkgs = mutableSetOf<String>()

        if (meminfoResult.success && meminfoResult.output.isNotEmpty()) {
            val totalMatch = Pattern.compile("Total RAM:\\s+([\\d,]+)K").matcher(meminfoResult.output)
            if (totalMatch.find()) {
                val kb = totalMatch.group(1)?.replace(",", "")?.toFloatOrNull() ?: 0f
                totalRam = kb / 1024f
            }

            val freeMatch = Pattern.compile("Free RAM:\\s+([\\d,]+)K").matcher(meminfoResult.output)
            if (freeMatch.find()) {
                val kb = freeMatch.group(1)?.replace(",", "")?.toFloatOrNull() ?: 0f
                freeRam = kb / 1024f
            }

            val usedMatch = Pattern.compile("Used RAM:\\s+([\\d,]+)K").matcher(meminfoResult.output)
            if (usedMatch.find()) {
                val kb = usedMatch.group(1)?.replace(",", "")?.toFloatOrNull() ?: 0f
                usedRam = kb / 1024f
            }

            // Isolate "Total PSS by process:" to avoid double-counting from "Total PSS by OOM adjustment:"
            val pssSection = if (meminfoResult.output.contains("Total PSS by process:")) {
                meminfoResult.output
                    .substringAfter("Total PSS by process:")
                    .substringBefore("Total PSS by OOM adjustment:")
                    .substringBefore("Total PSS by category:")
            } else {
                meminfoResult.output
            }

            val pssPattern = Pattern.compile("^\\s*([\\d,]+)K:\\s+([^\\s]+)\\s+\\(pid[^)]*\\)", Pattern.MULTILINE)
            val matcher = pssPattern.matcher(pssSection)
            while (matcher.find()) {
                runningProcs++
                val kb = matcher.group(1)?.replace(",", "")?.toFloatOrNull() ?: 0f
                val fullProc = matcher.group(2) ?: continue
                val matchedText = matcher.group(0) ?: ""
                val procName = fullProc.substringBefore(":")
                processRamMap[procName] = (processRamMap[procName] ?: 0f) + (kb / 1024f)
                if (matchedText.contains("/ activities")) {
                    activeActivityPkgs.add(procName)
                }
            }
        }

        // Resolve foreground app label
        var fgAppName = fgPkg
        if (fgPkg.isNotEmpty()) {
            try {
                val appInfo = packageManager.getApplicationInfo(fgPkg, 0)
                fgAppName = packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                fgAppName = CuratedKnowledgeBase.getMetadata(fgPkg, false).friendlyName
            }
        }

        val health = SystemHealth(
            totalRamMb = totalRam,
            usedRamMb = usedRam,
            freeRamMb = freeRam,
            foregroundPackage = fgPkg,
            foregroundAppName = fgAppName.ifEmpty { "Home / Desktop" },
            runningProcessesCount = runningProcs
        )

        // 3. Get uninstalled packages in system partition: pm list packages -u
        val uninstalledResult = ShizukuManager.executeCommand("pm list packages -u")
        val allSystemPackages = mutableSetOf<String>()
        if (uninstalledResult.success) {
            uninstalledResult.output.lines().forEach { line ->
                if (line.startsWith("package:")) {
                    allSystemPackages.add(line.removePrefix("package:").trim())
                }
            }
        }

        // 4. Get currently installed packages via PackageManager (avoiding heavy GET_META_DATA)
        val installedApps = packageManager.getInstalledApplications(0)
        val installedPkgSet = mutableSetOf<String>()
        val appList = mutableListOf<AppInfo>()

        for (app in installedApps) {
            val pkg = app.packageName
            installedPkgSet.add(pkg)
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isEnabled = app.enabled

            // Aggregate memory across primary process and auxiliary sub-processes
            var ram = processRamMap[pkg] ?: 0f
            for ((proc, procRam) in processRamMap) {
                if (proc != pkg && (proc.startsWith("$pkg:") || proc.startsWith("$pkg."))) {
                    ram += procRam
                }
            }

            val isRunning = ram > 0f
            val isFg = (pkg == fgPkg)
            val hasActivities = activeActivityPkgs.any { it == pkg || it.startsWith("$pkg:") || it.startsWith("$pkg.") } || isFg

            val label = try {
                packageManager.getApplicationLabel(app).toString()
            } catch (e: Exception) {
                pkg
            }

            val meta = CuratedKnowledgeBase.getMetadata(pkg, isSystem)

            appList.add(
                AppInfo(
                    packageName = pkg,
                    appName = if (label != pkg) label else meta.friendlyName,
                    isSystem = isSystem,
                    isEnabled = isEnabled,
                    isUninstalledForUser = false,
                    ramUsageMb = ram,
                    cpuUsagePct = 0f,
                    safetyLevel = meta.safetyLevel,
                    description = meta.description,
                    isRunning = isRunning,
                    isForeground = isFg,
                    hasActiveActivities = hasActivities
                )
            )
        }

        // 5. Find packages in system partition uninstalled for current user
        for (pkg in allSystemPackages) {
            if (!installedPkgSet.contains(pkg)) {
                val meta = CuratedKnowledgeBase.getMetadata(pkg, true)
                appList.add(
                    AppInfo(
                        packageName = pkg,
                        appName = meta.friendlyName,
                        isSystem = true,
                        isEnabled = false,
                        isUninstalledForUser = true,
                        ramUsageMb = 0f,
                        cpuUsagePct = 0f,
                        safetyLevel = meta.safetyLevel,
                        description = meta.description,
                        isRunning = false,
                        isForeground = false
                    )
                )
            }
        }

        // Sort: foreground first, active activities next, then by RAM, then safety level
        val sortedApps = appList.sortedWith(
            compareByDescending<AppInfo> { it.isForeground }
                .thenByDescending { it.hasActiveActivities }
                .thenByDescending { it.ramUsageMb }
                .thenBy { it.safetyLevel }
                .thenBy { it.appName }
        )

        SystemMonitorSnapshot(health = health, apps = sortedApps)
    }

    private fun isValidPackageName(pkg: String): Boolean =
        PACKAGE_NAME_REGEX.matches(pkg)

    suspend fun freezeApp(packageName: String): CommandResult {
        if (!isValidPackageName(packageName)) {
            return CommandResult(false, "", "Invalid package name: $packageName", -1)
        }
        return ShizukuManager.executeCommand("pm disable-user --user 0 $packageName")
    }

    suspend fun unfreezeApp(packageName: String): CommandResult {
        if (!isValidPackageName(packageName)) {
            return CommandResult(false, "", "Invalid package name: $packageName", -1)
        }
        return ShizukuManager.executeCommand("pm enable --user 0 $packageName")
    }

    suspend fun uninstallApp(packageName: String): CommandResult {
        if (!isValidPackageName(packageName)) {
            return CommandResult(false, "", "Invalid package name: $packageName", -1)
        }
        return ShizukuManager.executeCommand("pm uninstall -k --user 0 $packageName")
    }

    suspend fun restoreApp(packageName: String): CommandResult {
        if (!isValidPackageName(packageName)) {
            return CommandResult(false, "", "Invalid package name: $packageName", -1)
        }
        return ShizukuManager.executeCommand("cmd package install-existing --user 0 $packageName")
    }

    companion object {
        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")
    }
}
