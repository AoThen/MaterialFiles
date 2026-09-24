/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.crashlog

import android.annotation.SuppressLint
import me.zhanghai.android.files.app.application
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Redirects [System.err] to a daily file under `noBackupFilesDir/logs` so that the many
 * `e.printStackTrace()` calls in the project (FTP(S), archive preview, etc.) are persisted.
 * Original [System.err] output (logcat) is preserved via a tee.
 */
object ErrorLogTee {
    private const val LOG_DIR_NAME = "logs"
    private const val FILE_PREFIX = "applog-"
    private const val MAX_FILE_SIZE = 1024L * 1024L
    private const val KEEP_DAYS = 7L
    private const val MAX_VIEW_BYTES = 256L * 1024L

    private val lock = Any()

    @SuppressLint("SimpleDateFormat")
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @SuppressLint("SimpleDateFormat")
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var installed = false
    private var currentDay = ""
    private var currentFile: File? = null
    private var rotatedFile: File? = null
    private var writesDisabled = false
    private val lineBytes = java.io.ByteArrayOutputStream()

    fun install() {
        synchronized(lock) {
            if (installed) {
                return
            }
            installed = true
            try {
                cleanupExpiredUnsafe()
                ensureOpenForTodayUnsafe()
                val original = System.err
                System.setErr(PrintStream(TeeOutputStream(original), true, Charsets.UTF_8.name()))
            } catch (e: Throwable) {
                writesDisabled = true
            }
        }
    }

    fun listRuntimeLogs(): List<File> {
        val dir = File(application.noBackupFilesDir, LOG_DIR_NAME)
        return runCatching {
            dir.listFiles { file ->
                file.isFile && file.name.startsWith(FILE_PREFIX)
            }?.sortedBy { it.name } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun readTail(maxBytes: Long = MAX_VIEW_BYTES): String {
        val files = listRuntimeLogs()
        if (files.isEmpty()) {
            return ""
        }
        // Read newest → oldest, keeping at most maxBytes of raw bytes, then join oldest-first.
        val segments = ArrayDeque<String>()
        var remaining = maxBytes
        var truncatedStart = false
        for (index in files.indices.reversed()) {
            if (remaining <= 0) {
                truncatedStart = true
                break
            }
            val file = files[index]
            val segment = runCatching { readFileTail(file, remaining) }.getOrNull() ?: continue
            val isPartial = segment.second
            segments.addFirst(segment.first)
            remaining -= segment.first.toByteArray(Charsets.UTF_8).size
            if (isPartial) {
                truncatedStart = true
                break
            }
        }
        if (segments.isEmpty()) {
            return ""
        }
        var text = segments.joinToString("")
        if (truncatedStart) {
            val newlineIndex = text.indexOf('\n')
            if (newlineIndex in 0..text.length - 2) {
                text = text.substring(newlineIndex + 1)
            }
        }
        return text
    }

    /** Returns (text, isPartial) where isPartial means the read started mid-file. */
    private fun readFileTail(file: File, maxBytes: Long): Pair<String, Boolean> {
        val length = file.length()
        if (length <= 0) {
            return "" to false
        }
        if (length <= maxBytes) {
            return file.readText() to false
        }
        return file.inputStream().use { input ->
            var toSkip = length - maxBytes
            while (toSkip > 0) {
                val skipped = input.skip(toSkip)
                if (skipped <= 0) {
                    break
                }
                toSkip -= skipped
            }
            val bytes = input.readBytes()
            String(bytes, Charsets.UTF_8) to true
        }
    }

    fun clear() {
        synchronized(lock) {
            flushLineUnsafe()
            try {
                val dir = File(application.noBackupFilesDir, LOG_DIR_NAME)
                dir.listFiles()?.forEach { file ->
                    if (file.name.startsWith(FILE_PREFIX)) {
                        file.delete()
                    }
                }
            } catch (e: Throwable) {
            }
            writesDisabled = false
            currentDay = ""
            currentFile = null
            rotatedFile = null
        }
    }

    private fun cleanupExpiredUnsafe() {
        val dir = File(application.noBackupFilesDir, LOG_DIR_NAME)
        if (!dir.isDirectory) {
            return
        }
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEEP_DAYS)
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith(FILE_PREFIX) && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun ensureOpenForTodayUnsafe() {
        val today = dayFormat.format(Date())
        if (today != currentDay) {
            currentDay = today
            val dir = File(application.noBackupFilesDir, LOG_DIR_NAME)
            dir.mkdirs()
            currentFile = File(dir, FILE_PREFIX + today + ".txt")
            rotatedFile = File(dir, FILE_PREFIX + today + "-1.txt")
            writesDisabled = false
        }
    }

    private fun appendByte(byte: Int) {
        synchronized(lock) {
            if (writesDisabled) {
                // Allow day rollover to re-enable writes after rotation exhaustion / IO error.
                val today = dayFormat.format(Date())
                if (today != currentDay) {
                    ensureOpenForTodayUnsafe()
                }
                if (writesDisabled) {
                    return
                }
            }
            if (byte == '\n'.code) {
                flushLineUnsafe()
            } else {
                lineBytes.write(byte)
                if (lineBytes.size() > 8192) {
                    flushLineUnsafe()
                }
            }
        }
    }

    private fun flushLineUnsafe() {
        if (writesDisabled) {
            lineBytes.reset()
            return
        }
        val line = lineBytes.toByteArray()
        lineBytes.reset()
        if (line.isEmpty()) {
            return
        }
        try {
            ensureOpenForTodayUnsafe()
            if (writesDisabled) {
                return
            }
            val file = currentFile ?: return
            val prefix = (timeFormat.format(Date()) + " ").toByteArray(Charsets.UTF_8)
            val data = prefix + line + '\n'.code.toByte()
            if (file.length() + data.size > MAX_FILE_SIZE) {
                val rotated = rotatedFile
                if (rotated != null && rotated.exists()) {
                    writesDisabled = true
                    return
                }
                if (rotated != null && file.exists() && !file.renameTo(rotated)) {
                    writesDisabled = true
                    return
                }
            }
            file.appendBytes(data)
        } catch (e: Throwable) {
            writesDisabled = true
        }
    }

    private inner class TeeOutputStream(private val original: OutputStream) : OutputStream() {
        override fun write(b: Int) {
            runCatching { original.write(b) }
            appendByte(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            runCatching { original.write(b, off, len) }
            for (index in 0 until len) {
                appendByte(b[off + index])
            }
        }
    }
}
