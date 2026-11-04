package com.antigravity.appsentinel.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.appsentinel.data.ShizukuManager
import com.antigravity.appsentinel.data.SystemMonitorRepository
import com.antigravity.appsentinel.model.AppInfo
import com.antigravity.appsentinel.model.SafetyLevel
import com.antigravity.appsentinel.model.SystemHealth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isDarkTheme: Boolean = true,
    val shizukuConnected: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val systemHealth: SystemHealth = SystemHealth(),
    val apps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: Int = 0, // 0: Monitor, 1: Debloat, 2: Restorable
    val selectedSafetyFilter: SafetyLevel? = null,
    val selectedMonitorFilter: Int = 0, // 0: All Running, 1: Active UI, 2: Background Only
    val userMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SystemMonitorRepository(application)
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkShizukuStatus()
        refreshData()
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun checkShizukuStatus() {
        val running = ShizukuManager.isInstalledAndRunning
        val granted = ShizukuManager.hasPermission
        _uiState.update {
            it.copy(
                shizukuConnected = running,
                shizukuPermissionGranted = granted
            )
        }
    }

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission(1001)
    }

    fun openShizukuApp(context: android.content.Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                context.startActivity(intent)
            } else {
                _uiState.update { it.copy(userMessage = "Shizuku app is not installed.") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(userMessage = "Failed to launch Shizuku: ${e.message}") }
        }
    }

    fun refreshData() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            checkShizukuStatus()

            try {
                val snapshot = repository.getSystemMonitorSnapshot()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        systemHealth = snapshot.health,
                        apps = snapshot.apps
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        userMessage = "Failed to refresh data: ${e.message}"
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSelectedTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun setSelectedFilter(filter: SafetyLevel?) {
        _uiState.update { it.copy(selectedSafetyFilter = filter) }
    }

    fun setMonitorFilter(filterIndex: Int) {
        _uiState.update { it.copy(selectedMonitorFilter = filterIndex) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun cleanErrorMessage(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "no root permission" in lower || "securityexception" in lower ->
                "Locked by system firmware: cannot modify without root"
            "delete_failed_user_restricted" in lower ->
                "Locked by system firmware: cannot remove without root"
            else -> {
                val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("at ") }
                lines.lastOrNull()?.take(80) ?: "Action restricted by system"
            }
        }
    }

    fun freezeApp(app: AppInfo) {
        viewModelScope.launch {
            val result = repository.freezeApp(app.packageName)
            if (result.success) {
                _uiState.update { it.copy(userMessage = "Frozen: ${app.appName}") }
                refreshData()
            } else {
                val clean = cleanErrorMessage(result.error.ifEmpty { result.output })
                _uiState.update { it.copy(userMessage = "${app.appName}: $clean") }
            }
        }
    }

    fun unfreezeApp(app: AppInfo) {
        viewModelScope.launch {
            val result = repository.unfreezeApp(app.packageName)
            if (result.success) {
                _uiState.update { it.copy(userMessage = "Unfrozen: ${app.appName}") }
                refreshData()
            } else {
                val clean = cleanErrorMessage(result.error.ifEmpty { result.output })
                _uiState.update { it.copy(userMessage = "${app.appName}: $clean") }
            }
        }
    }

    fun uninstallApp(app: AppInfo) {
        viewModelScope.launch {
            val result = repository.uninstallApp(app.packageName)
            if (result.success) {
                _uiState.update { it.copy(userMessage = "Removed: ${app.appName}") }
                refreshData()
            } else {
                val clean = cleanErrorMessage(result.error.ifEmpty { result.output })
                _uiState.update { it.copy(userMessage = "${app.appName}: $clean") }
            }
        }
    }

    fun restoreApp(app: AppInfo) {
        viewModelScope.launch {
            val result = repository.restoreApp(app.packageName)
            if (result.success) {
                _uiState.update { it.copy(userMessage = "Restored: ${app.appName}") }
                refreshData()
            } else {
                val clean = cleanErrorMessage(result.error.ifEmpty { result.output })
                _uiState.update { it.copy(userMessage = "${app.appName}: $clean") }
            }
        }
    }
}
