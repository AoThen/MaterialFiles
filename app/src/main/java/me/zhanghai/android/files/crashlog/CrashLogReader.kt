/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.crashlog

import me.zhanghai.android.files.app.application
import java.io.File

/**
 * Lists, reads and clears crash log files under `noBackupFilesDir/crash_logs`.
 */
object CrashLogReader {
    private const val DIR_NAME = "crash_logs"
    private const val FILE_PREFIX = "crash-"

    fun listCrashLogs(): List<File> {
        val dir = File(application.noBackupFilesDir, DIR_NAME)
        return runCatching {
            dir.listFiles { file ->
                file.isFile && file.name.startsWith(FILE_PREFIX)
            }?.sortedByDescending { it.name } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun readCrashLog(file: File): String? =
        runCatching { file.readText() }.getOrNull()

    fun clearCrashLogs() {
        runCatching {
            val dir = File(application.noBackupFilesDir, DIR_NAME)
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith(FILE_PREFIX)) {
                    file.delete()
                }
            }
        }
    }
}
