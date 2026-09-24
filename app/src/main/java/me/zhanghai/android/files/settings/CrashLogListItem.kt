/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import java.io.File

class CrashLogListItem(
    val title: String,
    val subtitle: String,
    val isRuntimeLog: Boolean,
    val file: File?
)
