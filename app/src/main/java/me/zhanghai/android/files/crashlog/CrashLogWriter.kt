/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.crashlog

import android.annotation.SuppressLint
import android.os.Build
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.app.application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Writes uncaught exception logs to `noBackupFilesDir/crash_logs` as
 * `crash-yyyy-MM-dd-HH-mm-ss-{timestamp}.log`, with device/version params header and full stack
 * trace (including the cause chain via printStackTrace). Removes logs older than [KEEP_DAYS]
 * before writing. Never throws.
 */
object CrashLogWriter {
    private const val DIR_NAME = "crash_logs"
    private const val FILE_PREFIX = "crash-"
    private const val KEEP_DAYS = 7L

    @SuppressLint("SimpleDateFormat")
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)

    private val params: Map<String, String> by lazy {
        linkedMapOf(
            "MANUFACTURER" to Build.MANUFACTURER,
            "BRAND" to Build.BRAND,
            "MODEL" to Build.MODEL,
            "SDK_INT" to Build.VERSION.SDK_INT.toString(),
            "RELEASE" to Build.VERSION.RELEASE,
            "packageName" to application.packageName,
            "heapSize" to Runtime.getRuntime().maxMemory().toString(),
            "versionName" to BuildConfig.VERSION_NAME,
            "versionCode" to BuildConfig.VERSION_CODE.toString()
        )
    }

    fun save(throwable: Throwable) {
        runCatching {
            val dir = File(application.noBackupFilesDir, DIR_NAME).apply { mkdirs() }
            cleanupExpired(dir)
            val builder = StringBuilder()
            for ((key, value) in params) {
                builder.append(key).append('=').append(value).append('\n')
            }
            val writer = StringWriter()
            val printWriter = PrintWriter(writer)
            // printStackTrace already walks the full cause chain (with cycle protection).
            throwable.printStackTrace(printWriter)
            printWriter.close()
            builder.append(writer.toString())
            val timestamp = System.currentTimeMillis()
            val fileName = "$FILE_PREFIX${timeFormat.format(Date(timestamp))}-$timestamp.log"
            File(dir, fileName).writeText(builder.toString())
        }
    }

    private fun cleanupExpired(dir: File) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEEP_DAYS)
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}
