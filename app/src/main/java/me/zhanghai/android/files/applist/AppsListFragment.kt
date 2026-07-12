/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.AppsListFragmentBinding
import me.zhanghai.android.files.ui.FixQueryChangeSearchView
import me.zhanghai.android.files.util.DebouncedRunnable
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.viewModels

class AppsListFragment : Fragment() {
    private lateinit var binding: AppsListFragmentBinding

    private lateinit var menuBinding: MenuBinding

    private lateinit var adapter: AppsListAdapter

    private val viewModel by viewModels { { AppsListViewModel() } }

    private val debouncedSearchRunnable = DebouncedRunnable(Handler(Looper.getMainLooper()), 1000) {
        if (!isResumed) {
            return@DebouncedRunnable
        }
        val query = viewModel.searchQuery
        if (query.isEmpty()) {
            return@DebouncedRunnable
        }
        viewModel.setSearchQuery(query)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

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

        viewModel.filteredAndSortedListLiveData.observe(viewLifecycleOwner) { onAppsListChanged(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        menuBinding = MenuBinding.inflate(menu, inflater)
        setUpSearchView()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        updateSortMenuItems()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort_by_name -> {
                viewModel.setSortBy(AppSortOptions.By.NAME)
                true
            }
            R.id.action_sort_by_package_name -> {
                viewModel.setSortBy(AppSortOptions.By.PACKAGE_NAME)
                true
            }
            R.id.action_sort_by_install_time -> {
                viewModel.setSortBy(AppSortOptions.By.INSTALL_TIME)
                true
            }
            R.id.action_sort_order_ascending -> {
                viewModel.setSortOrder(
                    if (!menuBinding.sortOrderAscendingItem.isChecked) {
                        AppSortOptions.Order.ASCENDING
                    } else {
                        AppSortOptions.Order.DESCENDING
                    }
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setUpSearchView() {
        val searchView = menuBinding.searchItem.actionView as FixQueryChangeSearchView
        searchView.setOnSearchClickListener {
            searchView.setQuery(viewModel.searchQuery, false)
            debouncedSearchRunnable()
        }
        menuBinding.searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.setSearchQuery("")
                return true
            }
        })
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                debouncedSearchRunnable.cancel()
                viewModel.setSearchQuery(query)
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                if (searchView.shouldIgnoreQueryChange) {
                    return false
                }
                debouncedSearchRunnable()
                return false
            }
        })
    }

    private fun updateSortMenuItems() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val sortOptions = viewModel.sortOptions
        val checkedSortByItem = when (sortOptions.by) {
            AppSortOptions.By.NAME -> menuBinding.sortByNameItem
            AppSortOptions.By.PACKAGE_NAME -> menuBinding.sortByPackageNameItem
            AppSortOptions.By.INSTALL_TIME -> menuBinding.sortByInstallTimeItem
        }
        checkedSortByItem.isChecked = true
        menuBinding.sortOrderAscendingItem.isChecked = sortOptions.order == AppSortOptions.Order.ASCENDING
    }

    private fun onAppsListChanged(apps: List<AppsListItem>) {
        adapter.replace(apps)
    }

    companion object {
        fun newInstance(): AppsListFragment = AppsListFragment()
    }

    private class MenuBinding private constructor(
        val menu: Menu,
        val searchItem: MenuItem,
        val sortByPackageNameItem: MenuItem,
        val sortByNameItem: MenuItem,
        val sortByInstallTimeItem: MenuItem,
        val sortOrderAscendingItem: MenuItem
    ) {
        companion object {
            fun inflate(menu: Menu, inflater: MenuInflater): MenuBinding {
                inflater.inflate(R.menu.apps_list, menu)
                return MenuBinding(
                    menu, menu.findItem(R.id.action_search),
                    menu.findItem(R.id.action_sort_by_package_name),
                    menu.findItem(R.id.action_sort_by_name),
                    menu.findItem(R.id.action_sort_by_install_time),
                    menu.findItem(R.id.action_sort_order_ascending)
                )
            }
        }
    }
}