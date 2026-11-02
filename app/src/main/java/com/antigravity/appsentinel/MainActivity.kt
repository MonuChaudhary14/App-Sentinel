package com.antigravity.appsentinel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.antigravity.appsentinel.ui.screens.MainScreen
import com.antigravity.appsentinel.ui.theme.AppSentinelTheme
import com.antigravity.appsentinel.viewmodel.MainViewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModel.checkShizukuStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        viewModel.checkShizukuStatus()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, _ ->
            viewModel.checkShizukuStatus()
            viewModel.refreshData()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            AppSentinelTheme(darkTheme = uiState.isDarkTheme) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
