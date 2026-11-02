package com.antigravity.appsentinel.data

import com.antigravity.appsentinel.model.SafetyLevel

object CuratedKnowledgeBase {

    data class PackageMetadata(
        val friendlyName: String,
        val description: String,
        val safetyLevel: SafetyLevel
    )

    private val database = mapOf(
        // Vivo OEM Protected by Firmware (Cannot be disabled/uninstalled without root)
        "com.vivo.appstore" to PackageMetadata("V-Appstore", "Vivo App Store (Firmware locked by Vivo, cannot be disabled without root)", SafetyLevel.SYSTEM_CORE),
        "com.vivo.globalsearch" to PackageMetadata("Vivo Global Search", "Swipe-down search (Firmware locked by Vivo, cannot be disabled without root)", SafetyLevel.SYSTEM_CORE),
        "com.vivo.gamecube" to PackageMetadata("Vivo Game Space / GameCube", "Ultra Game Mode background listener (Firmware locked by Vivo)", SafetyLevel.SYSTEM_CORE),
        "com.vivo.weather.provider" to PackageMetadata("Vivo Weather Provider", "Background weather provider (Firmware locked by Vivo)", SafetyLevel.SYSTEM_CORE),

        // Vivo Bloatware & Services (Can be frozen/uninstalled)
        "com.vivo.smartoffice" to PackageMetadata("Vivo Office Suite", "Pre-installed document viewer", SafetyLevel.SAFE_DEBLOAT),
        "com.yozo.vivo.office" to PackageMetadata("Yozo Office for Vivo", "Pre-installed office app", SafetyLevel.SAFE_DEBLOAT),
        "com.vivo.easyshare" to PackageMetadata("EasyShare", "Vivo file sharing utility", SafetyLevel.SAFE_DEBLOAT),
        "com.vivo.stepcount" to PackageMetadata("Vivo Step Counter", "Background pedometer and fitness tracking", SafetyLevel.SAFE_DEBLOAT),
        "com.vivo.compass" to PackageMetadata("Vivo Compass", "Built-in compass tool", SafetyLevel.SAFE_DEBLOAT),
        "com.vivo.notes" to PackageMetadata("Vivo Notes", "Stock note-taking app", SafetyLevel.SAFE_DEBLOAT),
        "com.vivo.calculator" to PackageMetadata("Vivo Calculator", "Stock calculator app", SafetyLevel.SAFE_DEBLOAT),
        "com.vivo.feedback" to PackageMetadata("Vivo User Feedback", "OEM feedback and telemetry collector", SafetyLevel.SAFE_DEBLOAT),
        "com.vivo.soundrecorder" to PackageMetadata("Vivo Voice Recorder", "Stock voice recording app", SafetyLevel.SAFE_DEBLOAT),
        "com.vivo.hybrid" to PackageMetadata("Vivo Quick Apps", "Instant web app runner", SafetyLevel.SAFE_DEBLOAT),
        "com.vivo.tws.vivotws" to PackageMetadata("Vivo Earphones", "Vivo TWS earphone companion service", SafetyLevel.SAFE_DEBLOAT),
        "com.vivo.networkimprove" to PackageMetadata("Vivo Network Accelerate", "Dual Wi-Fi boost helper", SafetyLevel.SAFE_DEBLOAT),

        // Google Services
        "com.google.android.googlequicksearchbox" to PackageMetadata("Google Search & Assistant", "Google App, Discover feed, and Voice Assistant", SafetyLevel.SAFE_DEBLOAT),
        "com.google.android.apps.restore" to PackageMetadata("Google Cloud Restore", "Initial device setup and restore wizard", SafetyLevel.SAFE_DEBLOAT),
        "com.google.android.as" to PackageMetadata("Android System Intelligence", "Live caption and context engine", SafetyLevel.CAUTION),
        "com.google.android.gms" to PackageMetadata("Google Play Services", "Core Google APIs, push notifications, and Play Integrity", SafetyLevel.SYSTEM_CORE),
        "com.google.android.inputmethod.latin" to PackageMetadata("Gboard", "Default Google keyboard", SafetyLevel.CAUTION),
        "com.google.android.apps.messaging" to PackageMetadata("Google Messages", "Default SMS and RCS messaging app", SafetyLevel.CAUTION),
        "com.android.vending" to PackageMetadata("Google Play Store", "Official Android app store", SafetyLevel.CAUTION),

        // Android System Core (Locked)
        "com.android.systemui" to PackageMetadata("System UI", "Android status bar, notification shade, and navigation", SafetyLevel.SYSTEM_CORE),
        "com.android.launcher3" to PackageMetadata("System Home Screen", "Home screen and desktop app grid", SafetyLevel.SYSTEM_CORE),
        "com.android.phone" to PackageMetadata("Phone / Telephony", "Cellular call stack and SIM service", SafetyLevel.SYSTEM_CORE),
        "com.android.bluetooth" to PackageMetadata("Bluetooth Service", "Bluetooth radio manager", SafetyLevel.SYSTEM_CORE),
        "system" to PackageMetadata("Android OS Core (system_server)", "Master system process", SafetyLevel.SYSTEM_CORE),
        "surfaceflinger" to PackageMetadata("SurfaceFlinger", "Display and graphics compositor", SafetyLevel.SYSTEM_CORE)
    )

    fun getMetadata(packageName: String, isSystem: Boolean): PackageMetadata {
        val cleanPkg = packageName.substringBefore(":")
        val meta = database[packageName] ?: database[cleanPkg]
        if (meta != null) return meta

        return if (!isSystem) {
            PackageMetadata(
                friendlyName = cleanPkg.substringAfterLast(".").replaceFirstChar { it.uppercase() },
                description = "User installed application",
                safetyLevel = SafetyLevel.USER_APP
            )
        } else {
            PackageMetadata(
                friendlyName = cleanPkg.substringAfterLast(".").replaceFirstChar { it.uppercase() },
                description = "OEM / System service",
                safetyLevel = SafetyLevel.CAUTION
            )
        }
    }
}
