package com.antigravity.appsentinel

import com.antigravity.appsentinel.data.CuratedKnowledgeBase
import com.antigravity.appsentinel.model.SafetyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class SystemMonitorParsingTest {

    @Test
    fun testForegroundPackageRegexMatching() {
        val dumpsysWindowSample = """
            mCurrentFocus=Window{81c63d5 u0 com.antigravity.appsentinel/com.antigravity.appsentinel.MainActivity}
            mFocusedApp=ActivityRecord{d1f5e88 u0 com.antigravity.appsentinel/.MainActivity t12}
        """.trimIndent()

        val m = Pattern.compile("(?:u\\d+\\s+)?([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+)/")
            .matcher(dumpsysWindowSample)

        assertTrue(m.find())
        assertEquals("com.antigravity.appsentinel", m.group(1))
    }

    @Test
    fun testMeminfoSinglePassProcessSectionIsolation() {
        // Sample dumpsys meminfo containing both "Total PSS by process" and "Total PSS by OOM adjustment"
        val sampleMeminfo = """
            Applications Memory Usage (in Kilobytes):
            Uptime: 100000 Realtime: 100000

            Total PSS by process:
                150,000K: com.android.systemui (pid 1000 / activities)
                 80,000K: com.google.android.gms (pid 2000)
                 50,000K: com.whatsapp (pid 3000)

            Total PSS by OOM adjustment:
                150,000K: Persistent
                     150,000K: com.android.systemui (pid 1000)
                      80,000K: com.google.android.gms (pid 2000)
                 50,000K: Foreground
                      50,000K: com.whatsapp (pid 3000)

            Total RAM: 7,600,000K (status normal)
             Free RAM: 2,500,000K (cached pss)
             Used RAM: 5,100,000K (used pss)
        """.trimIndent()

        val pssSection = if (sampleMeminfo.contains("Total PSS by process:")) {
            sampleMeminfo
                .substringAfter("Total PSS by process:")
                .substringBefore("Total PSS by OOM adjustment:")
                .substringBefore("Total PSS by category:")
        } else {
            sampleMeminfo
        }

        val pssPattern = Pattern.compile("^\\s*([\\d,]+)K:\\s+([^\\s]+)\\s+\\(pid[^)]*\\)", Pattern.MULTILINE)
        val matcher = pssPattern.matcher(pssSection)

        val processRamMap = mutableMapOf<String, Float>()
        var procCount = 0

        while (matcher.find()) {
            procCount++
            val kb = matcher.group(1)?.replace(",", "")?.toFloatOrNull() ?: 0f
            val fullProc = matcher.group(2) ?: continue
            val pkgName = fullProc.substringBefore(":")
            processRamMap[pkgName] = (processRamMap[pkgName] ?: 0f) + (kb / 1024f)
        }

        // Exactly 3 processes in the process section, not 6
        assertEquals(3, procCount)
        assertEquals(150000f / 1024f, processRamMap["com.android.systemui"] ?: 0f, 0.01f)
        assertEquals(80000f / 1024f, processRamMap["com.google.android.gms"] ?: 0f, 0.01f)
        assertEquals(50000f / 1024f, processRamMap["com.whatsapp"] ?: 0f, 0.01f)
    }

    @Test
    fun testPackageNameValidation() {
        val regex = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")

        assertTrue(regex.matches("com.antigravity.appsentinel"))
        assertTrue(regex.matches("com.android.systemui"))
        assertTrue(regex.matches("a.b.c.d_1"))

        // Malicious or invalid injection attempts
        assertFalse(regex.matches("com.example.app; rm -rf /"))
        assertFalse(regex.matches("com.example.app & reboot"))
        assertFalse(regex.matches("`reboot`"))
        assertFalse(regex.matches("\$(reboot)"))
        assertFalse(regex.matches("plainstring"))
        assertFalse(regex.matches(""))
    }

    @Test
    fun testCuratedKnowledgeBaseLookups() {
        val systemCoreMeta = CuratedKnowledgeBase.getMetadata("com.android.systemui", true)
        assertEquals(SafetyLevel.SYSTEM_CORE, systemCoreMeta.safetyLevel)

        val safeDebloatMeta = CuratedKnowledgeBase.getMetadata("com.vivo.soundrecorder", true)
        assertEquals(SafetyLevel.SAFE_DEBLOAT, safeDebloatMeta.safetyLevel)

        val unknownUserMeta = CuratedKnowledgeBase.getMetadata("com.sample.randomgame", false)
        assertEquals(SafetyLevel.USER_APP, unknownUserMeta.safetyLevel)
        assertEquals("Randomgame", unknownUserMeta.friendlyName)
    }
}

