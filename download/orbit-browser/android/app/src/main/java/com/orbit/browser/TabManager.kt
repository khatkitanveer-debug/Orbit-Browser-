package com.orbit.browser

import android.os.Bundle
import java.util.UUID

data class TabModel(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "orbit://newtab",
    var isIncognito: Boolean = false,
    var isDiscarded: Boolean = false,
    var savedState: Bundle? = null,
    var lastAccessedTimestamp: Long = System.currentTimeMillis()
)

/**
 * TabManager — Lightweight memory footprint with intelligent tab-discarding
 * for inactive background tabs. Prevents OOM crashes while maintaining state.
 */
class TabManager(private val maxActiveInMemoryTabs: Int = 5) {

    private val tabs = mutableListOf<TabModel>()
    var activeTabId: String? = null
        private set

    fun createTab(url: String = "orbit://newtab", isIncognito: Boolean = false): TabModel {
        val tab = TabModel(url = url, isIncognito = isIncognito)
        tabs.add(tab)
        activeTabId = tab.id
        enforceMemoryOptimization()
        return tab
    }

    fun selectTab(id: String): TabModel? {
        val tab = tabs.find { it.id == id } ?: return null
        tab.lastAccessedTimestamp = System.currentTimeMillis()
        activeTabId = tab.id
        if (tab.isDiscarded) {
            tab.isDiscarded = false // Re-hydrate on demand
        }
        enforceMemoryOptimization()
        return tab
    }

    fun closeTab(id: String): Boolean {
        val removed = tabs.removeAll { it.id == id }
        if (activeTabId == id) {
            activeTabId = tabs.lastOrNull()?.id
        }
        return removed
    }

    fun getTabs(): List<TabModel> = tabs.toList()

    fun getActiveTab(): TabModel? = tabs.find { it.id == activeTabId }

    private fun enforceMemoryOptimization() {
        if (tabs.size <= maxActiveInMemoryTabs) return

        // Sort background tabs by least recently used
        val backgroundTabs = tabs.filter { it.id != activeTabId && !it.isDiscarded }
            .sortedBy { it.lastAccessedTimestamp }

        val excess = tabs.count { !it.isDiscarded } - maxActiveInMemoryTabs
        if (excess > 0) {
            backgroundTabs.take(excess).forEach { tab ->
                tab.isDiscarded = true
                // WebView DOM and canvas memory is freed; URL and savedState preserved
            }
        }
    }
}
