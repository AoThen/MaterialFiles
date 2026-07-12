/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import me.zhanghai.android.files.compat.reversedCompat

data class AppSortOptions(
    val by: By,
    val order: Order
) {
    fun createComparator(): Comparator<AppsListItem> {
        var comparator: Comparator<AppsListItem> = compareBy { it.label.lowercase() }
        when (by) {
            By.NAME -> {}
            By.PACKAGE_NAME ->
                comparator = compareBy { it.applicationInfo.packageName }.then(comparator)
            By.INSTALL_TIME ->
                comparator = compareBy { it.firstInstallTime }.then(comparator)
        }
        if (order == Order.DESCENDING) {
            comparator = comparator.reversedCompat()
        }
        return comparator
    }

    enum class By {
        NAME,
        PACKAGE_NAME,
        INSTALL_TIME
    }

    enum class Order {
        ASCENDING,
        DESCENDING
    }
}