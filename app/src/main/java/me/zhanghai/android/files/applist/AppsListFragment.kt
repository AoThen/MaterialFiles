/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import me.zhanghai.android.files.databinding.AppsListFragmentBinding
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.viewModels

class AppsListFragment : Fragment() {
    private lateinit var binding: AppsListFragmentBinding

    private lateinit var adapter: AppsListAdapter

    private val viewModel by viewModels { { AppsListViewModel() } }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        AppsListFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        adapter = AppsListAdapter { item ->
            val intent = AppSignatureInfoActivity::class.createIntent()
                .putExtra(AppSignatureInfoActivity.EXTRA_PACKAGE_NAME, item.applicationInfo.packageName)
            startActivitySafe(intent)
        }
        binding.recyclerView.adapter = adapter

        viewModel.appsListLiveData.observe(viewLifecycleOwner) { onAppsListChanged(it) }
    }

    private fun onAppsListChanged(apps: List<AppsListItem>) {
        adapter.replace(apps)
    }

    companion object {
        fun newInstance(): AppsListFragment = AppsListFragment()
    }
}

class AppsListViewModel : ViewModel() {
    private val _appsListLiveData = AppsListLiveData()
    val appsListLiveData: LiveData<List<AppsListItem>>
        get() = _appsListLiveData
}