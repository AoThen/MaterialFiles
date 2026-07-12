/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import me.zhanghai.android.files.util.Stateful

class AppSignatureInfoViewModel(packageName: String) : ViewModel() {
    private val _signatureInfoLiveData = SignatureInfoLiveData(packageName)
    val signatureInfoLiveData: LiveData<Stateful<SignatureInfo>>
        get() = _signatureInfoLiveData

    fun reload() {
        _signatureInfoLiveData.loadValue()
    }
}