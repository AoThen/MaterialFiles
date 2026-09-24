/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.crashlog.CrashLogReader
import me.zhanghai.android.files.crashlog.ErrorLogTee
import me.zhanghai.android.files.databinding.CrashLogListFragmentBinding
import me.zhanghai.android.files.util.createSendTextIntent
import me.zhanghai.android.files.util.fadeToVisibilityUnsafe
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser
import java.text.DateFormat
import java.util.Date

class CrashLogListFragment : Fragment() {
    private lateinit var binding: CrashLogListFragmentBinding

    private lateinit var adapter: CrashLogListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        CrashLogListFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        adapter = CrashLogListAdapter { item -> onLogItemClick(item) }
        binding.recyclerView.adapter = adapter

        loadLogs()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.crash_log_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_clear -> {
                confirmClear()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun loadLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { buildItems() }
            if (!isAdded) {
                return@launch
            }
            binding.emptyView.fadeToVisibilityUnsafe(items.isEmpty())
            adapter.replace(items)
        }
    }

    private fun buildItems(): List<CrashLogListItem> {
        // Use application context so getString cannot fail if the fragment detaches mid-load.
        val items = mutableListOf<CrashLogListItem>()
        val runtimeFiles = ErrorLogTee.listRuntimeLogs()
        if (runtimeFiles.isNotEmpty()) {
            val latestModified = runtimeFiles.maxOf { it.lastModified() }
            val totalSize = runtimeFiles.sumOf { it.length() }
            items.add(
                CrashLogListItem(
                    title = application.getString(R.string.settings_runtime_log_title),
                    subtitle = formatInfo(application, totalSize, latestModified),
                    isRuntimeLog = true,
                    file = null
                )
            )
        }
        for (file in CrashLogReader.listCrashLogs()) {
            items.add(
                CrashLogListItem(
                    title = file.name,
                    subtitle = formatInfo(application, file.length(), file.lastModified()),
                    isRuntimeLog = false,
                    file = file
                )
            )
        }
        return items
    }

    private fun formatInfo(context: Context, size: Long, lastModified: Long): String {
        val sizeText = Formatter.formatFileSize(context, size)
        val timeText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(lastModified))
        return context.getString(R.string.settings_crash_logs_item_info_format, timeText, sizeText)
    }

    private fun onLogItemClick(item: CrashLogListItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                if (item.isRuntimeLog) {
                    ErrorLogTee.readTail()
                } else {
                    item.file?.let { CrashLogReader.readCrashLog(it) }
                }
            }
            if (!isAdded) {
                return@launch
            }
            if (content.isNullOrEmpty()) {
                showToast(R.string.settings_crash_logs_read_failed)
                return@launch
            }
            showContentDialog(item, content)
        }
    }

    private fun showContentDialog(item: CrashLogListItem, content: String) {
        val padding = resources.getDimensionPixelSize(R.dimen.screen_edge_margin)
        val textView = TextView(requireContext()).apply {
            text = content
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        val scrollView = ScrollView(requireContext()).apply { addView(textView) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title)
            .setView(scrollView)
            .setPositiveButton(R.string.share) { _, _ ->
                startActivitySafe(content.createSendTextIntent().withChooser())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_crash_logs_clear_title)
            .setMessage(R.string.settings_crash_logs_clear_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> clearLogs() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                CrashLogReader.clearCrashLogs()
                ErrorLogTee.clear()
            }
            if (isAdded) {
                loadLogs()
            }
        }
    }
}
