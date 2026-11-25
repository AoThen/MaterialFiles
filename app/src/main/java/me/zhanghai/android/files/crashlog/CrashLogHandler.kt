/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.crashlog

import android.os.Process

/**
 * Global uncaught exception handler. Captures the previously registered handler (the system
 * default) and delegates to it after the local crash log is written.
 */
object CrashLogHandler : Thread.UncaughtExceptionHandler {
    private var installed = false

    @Volatile
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install() {
        synchronized(this) {
            if (installed) {
                return
            }
            installed = true
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(this)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { CrashLogs.markCrashed() }
        runCatching { CrashLogWriter.save(throwable) }
        val handler = defaultHandler
        if (handler != null) {
            try {
                handler.uncaughtException(thread, throwable)
            } catch (e: Throwable) {
                // Fall through to kill the process below.
            }
        }
        Process.killProcess(Process.myPid())
        System.exit(10)
    }
}
