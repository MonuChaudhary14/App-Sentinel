package com.antigravity.appsentinel.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.antigravity.appsentinel.model.AppInfo
import com.antigravity.appsentinel.model.SafetyLevel
import com.antigravity.appsentinel.model.SystemHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class SystemMonitorRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun getSystemHealth(): SystemHealth = withContext(Dispatchers.IO) {
        val meminfoResult = ShizukuManager.executeCommand("dumpsys meminfo")
        val foregroundResult = ShizukuManager.executeCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")

        var totalRam = 7600f
        var freeRam = 2500f
        var usedRam = 5100f
        var runningProcs = 0

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

            // Count processes in meminfo
            val procMatcher = Pattern.compile("^\\s*[\\d,]+K:\\s+[^\\s]+\\s+\\(pid", Pattern.MULTILINE).matcher(meminfoResult.output)
            while (procMatcher.find()) {
                runningProcs++
            }
        }

        // Parse foreground package
        var fgPkg = ""
        if (foregroundResult.output.isNotEmpty()) {
            val m = Pattern.compile("u0\\s+([a-zA-Z0-9_.]+)/").matcher(foregroundResult.output)
            if (m.find()) {
                fgPkg = m.group(1) ?: ""
            }
        }

        var fgAppName = fgPkg
        if (fgPkg.isNotEmpty()) {
            try {
                val appInfo = packageManager.getApplicationInfo(fgPkg, 0)
                fgAppName = packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                fgAppName = CuratedKnowledgeBase.getMetadata(fgPkg, false).friendlyName
            }
        }

        SystemHealth(
            totalRamMb = totalRam,
            usedRamMb = usedRam,
            freeRamMb = freeRam,
            foregroundPackage = fgPkg,
            foregroundAppName = fgAppName.ifEmpty { "Home / Desktop" },
            runningProcessesCount = runningProcs
        )
    }

    suspend fun getApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val appList = mutableListOf<AppInfo>()

        // 1. Get foreground app package
        val foregroundResult = ShizukuManager.executeCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        var fgPkg = ""
        if (foregroundResult.output.isNotEmpty()) {
            val m = Pattern.compile("u0\\s+([a-zA-Z0-9_.]+)/").matcher(foregroundResult.output)
            if (m.find()) {
                fgPkg = m.group(1) ?: ""
            }
        }

        // 2. Get process memory metrics from dumpsys meminfo
        val meminfoResult = ShizukuManager.executeCommand("dumpsys meminfo")
        val processRamMap = mutableMapOf<String, Float>()
        val activeActivityPkgs = mutableSetOf<String>()
        if (meminfoResult.success) {
            val pssPattern = Pattern.compile("^\\s*([\\d,]+)K:\\s+([^\\s]+)\\s+\\(pid[^)]*\\)", Pattern.MULTILINE)
            val matcher = pssPattern.matcher(meminfoResult.output)
            while (matcher.find()) {
                val kb = matcher.group(1)?.replace(",", "")?.toFloatOrNull() ?: 0f
                val fullProc = matcher.group(2) ?: continue
                val matchedText = matcher.group(0) ?: ""
                val pkgName = fullProc.substringBefore(":")
                processRamMap[pkgName] = (processRamMap[pkgName] ?: 0f) + (kb / 1024f)
                if (matchedText.contains("/ activities")) {
                    activeActivityPkgs.add(pkgName)
                }
            }
        }

        // 3. Get uninstalled packages still existing in system partition: pm list packages -u
        val uninstalledResult = ShizukuManager.executeCommand("pm list packages -u")
        val allSystemPackages = mutableSetOf<String>()
        if (uninstalledResult.success) {
            uninstalledResult.output.lines().forEach { line ->
                if (line.startsWith("package:")) {
                    allSystemPackages.add(line.removePrefix("package:").trim())
                }
            }
        }

        // 4. Get currently installed packages via PackageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val installedPkgSet = mutableSetOf<String>()

        for (app in installedApps) {
            val pkg = app.packageName
            installedPkgSet.add(pkg)
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isEnabled = app.enabled
            val ram = processRamMap[pkg] ?: 0f
            val isRunning = ram > 0f
            val isFg = (pkg == fgPkg)
            val hasActivities = activeActivityPkgs.contains(pkg) || isFg

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

        // 5. Find packages that are in system partition but uninstalled for user 0
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
        appList.sortedWith(
            compareByDescending<AppInfo> { it.isForeground }
                .thenByDescending { it.hasActiveActivities }
                .thenByDescending { it.ramUsageMb }
                .thenBy { it.safetyLevel }
                .thenBy { it.appName }
        )
    }

    suspend fun freezeApp(packageName: String): CommandResult {
        return ShizukuManager.executeCommand("pm disable-user --user 0 $packageName")
    }

    suspend fun unfreezeApp(packageName: String): CommandResult {
        return ShizukuManager.executeCommand("pm enable --user 0 $packageName")
    }

    suspend fun uninstallApp(packageName: String): CommandResult {
        return ShizukuManager.executeCommand("pm uninstall -k --user 0 $packageName")
    }

    suspend fun restoreApp(packageName: String): CommandResult {
        return ShizukuManager.executeCommand("cmd package install-existing $packageName")
    }
}
