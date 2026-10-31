package org.fossify.phone.models

import org.fossify.commons.helpers.DAY_SECONDS

sealed class CallLogItem {
    data class Date(
        val timestamp: Long,
        val dayCode: String,
    ) : CallLogItem()

    data class Placeholder(val key: Int) : CallLogItem()

    fun getItemId(): Int {
        return when (this) {
            is Date -> -(timestamp / (DAY_SECONDS * 1000L)).toInt()
            is RecentCall -> id
            is Placeholder -> -1_000_000 - key
        }
    }
}
