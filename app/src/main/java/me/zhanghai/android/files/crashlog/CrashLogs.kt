/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.crashlog

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.defaultSharedPreferences
import me.zhanghai.android.files.settings.CrashLogListActivity
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.startActivitySafe

/**
 * Cross-launch crash flag and next-launch notification, following the legadopro pattern
 * (`LocalConfig.appCrash` + `MainActivity.notifyAppCrash`).
 */
object CrashLogs {
    private const val KEY_APP_CRASH = "appCrash"

    fun markCrashed() {
        // commit() so the flag survives when the process is killed right after a crash.
        defaultSharedPreferences.edit().putBoolean(KEY_APP_CRASH, true).commit()
    }

    private fun consumeCrashed(): Boolean {
        if (!defaultSharedPreferences.getBoolean(KEY_APP_CRASH, false)) {
            return false
        }
        defaultSharedPreferences.edit().putBoolean(KEY_APP_CRASH, false).commit()
        return true
    }

    fun notifyAppCrash(activity: Activity) {
        if (BuildConfig.DEBUG) {
            // Still clear the flag so debug builds don't accumulate it.
            consumeCrashed()
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        if (!defaultSharedPreferences.getBoolean(KEY_APP_CRASH, false)) {
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.settings_crash_notify_title)
            .setMessage(R.string.settings_crash_notify_message)
            .setPositiveButton(R.string.settings_crash_notify_yes) { _, _ ->
                consumeCrashed()
                activity.startActivitySafe(CrashLogListActivity::class.createIntent())
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                consumeCrashed()
            }
            .setOnCancelListener {
                consumeCrashed()
            }
            .show()
    }
}
