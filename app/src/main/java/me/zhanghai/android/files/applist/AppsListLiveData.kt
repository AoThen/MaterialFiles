/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import androidx.lifecycle.LiveData
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.util.sha1Digest
import me.zhanghai.android.files.util.toHexString

class AppsListLiveData : LiveData<List<AppsListItem>>() {
    init {
        loadValue()
    }

    fun loadValue() {
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val installedApplicationInfos = packageManager.getInstalledApplications(0)
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val items = installedApplicationInfos.map { applicationInfo ->
                val label = applicationInfo.loadLabel(packageManager).toString()
                var signatureDigest = ""
                var issuerName = ""
                var subjectName = ""
                var firstInstallTime = 0L
                try {
                    var flags = PackageManager.GET_SIGNATURES
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        flags = flags or PackageManager.GET_SIGNING_CERTIFICATES
                    }
                    val packageInfo = packageManager.getPackageInfo(applicationInfo.packageName, flags)
                    firstInstallTime = packageInfo.firstInstallTime
                    val signingCertificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.signingInfo?.apkContentsSigners
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.signatures
                    } ?: emptyArray()
                    if (signingCertificates.isNotEmpty()) {
                        signatureDigest = signingCertificates.first().toByteArray().sha1Digest().toHexString()
                        val cert = certificateFactory.generateCertificate(
                            ByteArrayInputStream(signingCertificates.first().toByteArray())
                        ) as X509Certificate
                        issuerName = cert.issuerX500Principal.name
                        subjectName = cert.subjectX500Principal.name
                    }
                } catch (_: Exception) {
                }
                AppsListItem(applicationInfo, label, signatureDigest, issuerName, subjectName, firstInstallTime)
            }
            postValue(items)
        }
    }
}