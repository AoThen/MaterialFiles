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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.compat.longVersionCodeCompat
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.sha1Digest
import me.zhanghai.android.files.util.sha256Digest
import me.zhanghai.android.files.util.toHexString

class SignatureInfoLiveData(private val packageName: String) : LiveData<Stateful<SignatureInfo>>() {
    init {
        loadValue()
    }

    fun loadValue() {
        value = Loading(value?.value)
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val result = try {
                var flags = PackageManager.GET_SIGNATURES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    flags = flags or PackageManager.GET_SIGNING_CERTIFICATES
                }
                val packageInfo = packageManager.getPackageInfo(packageName, flags)
                val signingCertificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                } ?: emptyArray()
                val rawBytes = signingCertificates.map { it.toByteArray() }
                val sha256Digest = if (rawBytes.isNotEmpty()) {
                    rawBytes.first().sha256Digest().toHexString()
                } else {
                    ""
                }
                val sha1Digest = if (rawBytes.isNotEmpty()) {
                    rawBytes.first().sha1Digest().toHexString()
                } else {
                    ""
                }
                val certificateFactory = CertificateFactory.getInstance("X.509")
                val certificates = signingCertificates.map { signature ->
                    certificateFactory.generateCertificate(
                        ByteArrayInputStream(signature.toByteArray())
                    ) as X509Certificate
                }
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val certificateInfos = certificates.map { cert ->
                    val publicKey = cert.publicKey
                    val keySize = when (publicKey) {
                        is java.security.interfaces.RSAKey -> publicKey.modulus.bitLength()
                        is java.security.interfaces.ECKey -> publicKey.params.order.bitLength()
                        is java.security.interfaces.DSAKey -> publicKey.params.bitLength()
                        else -> 0
                    }
                    val keyType = if (keySize > 0) {
                        "${publicKey.algorithm} $keySize"
                    } else {
                        publicKey.algorithm
                    }
                    CertificateInfo(
                        serialNumber = cert.serialNumber.toString(16).uppercase(),
                        issuerDN = cert.issuerX500Principal.name,
                        subjectDN = cert.subjectX500Principal.name,
                        notBefore = dateFormat.format(Date(cert.notBefore.time)),
                        notAfter = dateFormat.format(Date(cert.notAfter.time)),
                        sigAlgName = cert.sigAlgName,
                        keyType = keyType
                    )
                }
                val signatureInfo = SignatureInfo(
                    packageName = packageInfo.packageName,
                    versionName = packageInfo.versionName,
                    versionCode = packageInfo.longVersionCodeCompat,
                    sha256Digest = sha256Digest,
                    sha1Digest = sha1Digest,
                    certificates = certificateInfos
                )
                Success(signatureInfo)
            } catch (e: Exception) {
                Failure(value.value, e)
            }
            postValue(result)
        }
    }
}