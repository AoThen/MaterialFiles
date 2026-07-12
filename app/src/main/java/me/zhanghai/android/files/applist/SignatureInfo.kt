/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import java.security.cert.X509Certificate

class SignatureInfo(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val sha256Digest: String,
    val sha1Digest: String,
    val certificates: List<CertificateInfo>
)

class CertificateInfo(
    val serialNumber: String,
    val issuerDN: String,
    val subjectDN: String,
    val notBefore: String,
    val notAfter: String,
    val sigAlgName: String,
    val keyType: String
)