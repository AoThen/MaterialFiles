/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.crashlog

import android.os.Process

/**
 * Global uncaught exception handler. Must be installed **after** Firebase/Crashlytics so that
 * the previously registered handler (Crashlytics or the system default) is captured and
 * delegated to after the local crash log is written.
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
