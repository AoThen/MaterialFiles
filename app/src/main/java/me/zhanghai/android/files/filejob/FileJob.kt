/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.os.Handler
import android.os.Looper
import me.zhanghai.android.files.util.showToast
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Random

abstract class FileJob {
    val id = Random().nextInt()

    internal lateinit var service: FileJobService
        private set

    fun runOn(service: FileJobService) {
        this.service = service
        var completed = false
        try {
            run()
            completed = true
            // TODO: Toast
        } catch (e: InterruptedIOException) {
            // TODO
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
            service.showToast(e.toString())
        } finally {
            if (completed) {
                // Keep the final notification (usually at 100% progress) visible long enough for
                // it to be rendered before removing it.
                Handler(Looper.getMainLooper()).postDelayed({
                    service.notificationManager.cancel(id)
                }, COMPLETION_NOTIFICATION_DURATION_MILLIS)
            } else {
                service.notificationManager.cancel(id)
            }
        }
    }

    @Throws(IOException::class)
    protected abstract fun run()

    companion object {
        private const val COMPLETION_NOTIFICATION_DURATION_MILLIS = 1_000L
    }
}
