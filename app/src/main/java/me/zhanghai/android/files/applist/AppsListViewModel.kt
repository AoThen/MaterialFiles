/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.zhanghai.android.files.util.valueCompat

class AppsListViewModel : ViewModel() {
    private val _appsListLiveData = AppsListLiveData()

    val searchQueryLiveData = MutableLiveData("")
    val searchQuery: String
        get() = searchQueryLiveData.valueCompat

    private val _sortOptionsLiveData = MutableLiveData(
        AppSortOptions(AppSortOptions.By.NAME, AppSortOptions.Order.ASCENDING)
    )
    val sortOptionsLiveData: LiveData<AppSortOptions> = _sortOptionsLiveData
    val sortOptions: AppSortOptions
        get() = _sortOptionsLiveData.valueCompat

    val filteredAndSortedListLiveData: LiveData<List<AppsListItem>> =
        MediatorLiveData<List<AppsListItem>>().apply {
            var fullList: List<AppsListItem> = emptyList()
            var query: String = ""
            var sortOptions: AppSortOptions = AppSortOptions(
                AppSortOptions.By.NAME, AppSortOptions.Order.ASCENDING
            )

            fun update() {
                val filtered = if (query.isEmpty()) {
                    fullList
                } else {
                    val q = query.lowercase()
                    fullList.filter { item ->
                        q in item.label.lowercase()
                            || q in item.applicationInfo.packageName.lowercase()
                            || q in item.issuerName.lowercase()
                            || q in item.subjectName.lowercase()
                    }
                }
                val sorted = filtered.sortedWith(sortOptions.createComparator())
                value = sorted
            }

            addSource(_appsListLiveData) { list ->
                if (list != null) {
                    fullList = list
                    update()
                }
            }
            addSource(searchQueryLiveData) { q ->
                query = q
                update()
            }
            addSource(_sortOptionsLiveData) { options ->
                sortOptions = options
                update()
            }
        }

    fun setSearchQuery(query: String) {
        searchQueryLiveData.value = query
    }

    fun setSortBy(by: AppSortOptions.By) {
        _sortOptionsLiveData.value = sortOptions.copy(by = by)
    }

    fun setSortOrder(order: AppSortOptions.Order) {
        _sortOptionsLiveData.value = sortOptions.copy(order = order)
    }
}