/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

enum class Protocol(val scheme: String, val implicit: Boolean) {
    FTP("ftp", false),
    FTPS("ftps", true),
    FTPES("ftpes", false)
}
