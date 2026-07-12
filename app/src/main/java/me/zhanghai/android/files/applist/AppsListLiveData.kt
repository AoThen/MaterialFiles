/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import androidx.lifecycle.LiveData
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.util.sha1Digest
import me.zhanghai.android.files.util.toHexString

class AppsListLiveData : LiveData<List<AppsListItem>>() {
    init {
        loadValue()
    }

    fun loadValue() {
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val installedApplicationInfos = packageManager.getInstalledApplications(0).sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            val items = installedApplicationInfos.map { applicationInfo ->
                val label = applicationInfo.loadLabel(packageManager).toString()
                val signatureDigest = try {
                    var flags = PackageManager.GET_SIGNATURES
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        flags = flags or PackageManager.GET_SIGNING_CERTIFICATES
                    }
                    val packageInfo = packageManager.getPackageInfo(applicationInfo.packageName, flags)
                    val signingCertificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.signingInfo?.apkContentsSigners
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.signatures
                    } ?: emptyArray()
                    if (signingCertificates.isNotEmpty()) {
                        signingCertificates.first().toByteArray().sha1Digest().toHexString()
                    } else {
                        ""
                    }
                } catch (e: Exception) {
                    ""
                }
                AppsListItem(applicationInfo, label, signatureDigest)
            }
            postValue(items)
        }
    }
}