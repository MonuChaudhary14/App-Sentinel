package com.antigravity.appsentinel.data

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

object ShizukuManager {

    val isInstalledAndRunning: Boolean
        get() = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }

    val hasPermission: Boolean
        get() = try {
            if (isInstalledAndRunning) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        } catch (e: Throwable) {
            false
        }

    fun requestPermission(requestCode: Int) {
        try {
            if (isInstalledAndRunning && !hasPermission) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        if (!hasPermission) {
            return@withContext runStandardCommand(command)
        }

        try {
            val binder = Shizuku.getBinder()
            if (binder == null) {
                return@withContext runStandardCommand(command)
            }

            val service = IShizukuService.Stub.asInterface(binder)
            val remoteProcess = service.newProcess(arrayOf("sh", "-c", command), null, null)

            val inPfd = remoteProcess.inputStream
            val errPfd = remoteProcess.errorStream

            coroutineScope {
                val outDeferred = async(Dispatchers.IO) {
                    if (inPfd != null) {
                        ParcelFileDescriptor.AutoCloseInputStream(inPfd).bufferedReader().use { it.readText() }
                    } else ""
                }
                val errDeferred = async(Dispatchers.IO) {
                    if (errPfd != null) {
                        ParcelFileDescriptor.AutoCloseInputStream(errPfd).bufferedReader().use { it.readText() }
                    } else ""
                }

                val output = outDeferred.await()
                val error = errDeferred.await()
                val exitCode = remoteProcess.waitFor()
                CommandResult(exitCode == 0, output.trim(), error.trim(), exitCode)
            }
        } catch (e: Throwable) {
            // Fallback to standard process if Shizuku fails
            runStandardCommand(command)
        }
    }

    private suspend fun runStandardCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val outDeferred = async(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val errDeferred = async(Dispatchers.IO) {
                process.errorStream.bufferedReader().use { it.readText() }
            }
            val output = outDeferred.await()
            val error = errDeferred.await()
            val exitCode = process.waitFor()
            CommandResult(exitCode == 0, output.trim(), error.trim(), exitCode)
        } catch (e: Throwable) {
            CommandResult(false, "", e.message ?: "Execution failed", -1)
        }
    }
}

data class CommandResult(
    val success: Boolean,
    val output: String,
    val error: String,
    val exitCode: Int
)
