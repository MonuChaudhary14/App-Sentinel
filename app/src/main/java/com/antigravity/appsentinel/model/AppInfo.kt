package com.antigravity.appsentinel.model

enum class SafetyLevel(val label: String) {
    SAFE_DEBLOAT("Safe to Remove"),
    CAUTION("Caution / Optional"),
    USER_APP("User App"),
    SYSTEM_CORE("System Core (Protected)")
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val isEnabled: Boolean = true,
    val isUninstalledForUser: Boolean = false,
    val ramUsageMb: Float = 0f,
    val cpuUsagePct: Float = 0f,
    val safetyLevel: SafetyLevel = SafetyLevel.USER_APP,
    val description: String = "",
    val isRunning: Boolean = false,
    val isForeground: Boolean = false,
    val hasActiveActivities: Boolean = false
)

data class SystemHealth(
    val totalRamMb: Float = 7600f,
    val usedRamMb: Float = 0f,
    val freeRamMb: Float = 0f,
    val foregroundPackage: String = "",
    val foregroundAppName: String = "None",
    val runningProcessesCount: Int = 0
)

