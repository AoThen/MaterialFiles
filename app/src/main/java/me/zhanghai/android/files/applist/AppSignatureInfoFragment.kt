/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import android.os.Bundle
import me.zhanghai.android.files.R
import me.zhanghai.android.files.fileproperties.FilePropertiesTabFragment
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.viewModels

class AppSignatureInfoFragment : FilePropertiesTabFragment() {
    private val viewModel by viewModels {
        {
            AppSignatureInfoViewModel(
                requireActivity().intent.getStringExtra(AppSignatureInfoActivity.EXTRA_PACKAGE_NAME)!!
            )
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel.signatureInfoLiveData.observe(viewLifecycleOwner) { onSignatureInfoChanged(it) }
    }

    override fun refresh() {
        viewModel.reload()
    }

    private fun onSignatureInfoChanged(stateful: Stateful<SignatureInfo>) {
        bindView(stateful) { signatureInfo ->
            addItemView(R.string.app_signature_info_package_name, signatureInfo.packageName)
            if (signatureInfo.versionName != null) {
                addItemView(
                    R.string.app_signature_info_version_name,
                    getString(
                        R.string.app_signature_info_version_format,
                        signatureInfo.versionName, signatureInfo.versionCode
                    )
                )
            } else {
                addItemView(
                    R.string.app_signature_info_version_code,
                    signatureInfo.versionCode.toString()
                )
            }
            addItemView(R.string.app_signature_info_sha256, signatureInfo.sha256Digest)
            addItemView(R.string.app_signature_info_sha1, signatureInfo.sha1Digest)
            for ((index, certificate) in signatureInfo.certificates.withIndex()) {
                val prefix = if (signatureInfo.certificates.size > 1) {
                    getString(R.string.app_signature_info_certificate_index_format, index + 1)
                } else {
                    ""
                }
                addItemView(
                    R.string.app_signature_info_serial_number,
                    "$prefix${certificate.serialNumber}"
                )
                addItemView(R.string.app_signature_info_issuer, certificate.issuerDN)
                addItemView(R.string.app_signature_info_subject, certificate.subjectDN)
                addItemView(
                    R.string.app_signature_info_validity,
                    getString(
                        R.string.app_signature_info_validity_format,
                        certificate.notBefore, certificate.notAfter
                    )
                )
                addItemView(R.string.app_signature_info_signature_algorithm, certificate.sigAlgName)
                addItemView(R.string.app_signature_info_key_type, certificate.keyType)
            }
        }
    }
}