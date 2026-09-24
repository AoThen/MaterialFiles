/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.databinding.CrashLogListItemBinding
import me.zhanghai.android.files.ui.SimpleAdapter
import me.zhanghai.android.files.util.layoutInflater

class CrashLogListAdapter(
    private val onItemClick: (CrashLogListItem) -> Unit
) : SimpleAdapter<CrashLogListItem, CrashLogListAdapter.ViewHolder>() {
    override val hasStableIds: Boolean
        get() = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CrashLogListItemBinding.inflate(parent.context.layoutInflater, parent, false)
        return ViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding
        binding.nameText.text = item.title
        binding.pathText.text = item.subtitle
    }

    class ViewHolder(val binding: CrashLogListItemBinding) : RecyclerView.ViewHolder(binding.root)
}
