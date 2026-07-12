/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.databinding.AppsListItemBinding
import me.zhanghai.android.files.ui.SimpleAdapter

class AppsListAdapter(
    private val onAppClick: (AppsListItem) -> Unit
) : SimpleAdapter<AppsListItem, AppsListAdapter.ViewHolder>() {

    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long =
        getItem(position)!!.applicationInfo.packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AppsListItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAppClick(getItem(position)!!)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)!!
        val binding = holder.binding
        binding.appIcon.setImageDrawable(
            item.applicationInfo.loadIcon(binding.appIcon.context)
        )
        binding.appNameText.text = item.label
        binding.appPackageText.text = item.applicationInfo.packageName
        binding.appSignatureText.text = item.signatureDigest
    }

    class ViewHolder(val binding: AppsListItemBinding) : RecyclerView.ViewHolder(binding.root)
}