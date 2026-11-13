package com.verve.browser.presentation.browser.viewmodel

import androidx.compose.ui.graphics.Color
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.util.WebEdgeColorExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class PageAppearanceAndProgressController(
    private val uiState: MutableStateFlow<BrowserUiState>
) {

    fun onPageUrlUpdated(tabId: String, url: String) {
        if (url.isBlank() || url == "about:blank") return
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(url = url) else tab
            }
            if (tabId == state.activeTabId) {
                state.copy(
                    tabs = updatedTabs,
                    currentUrl = url,
                    searchInput = if (state.isSearchFocused) state.searchInput else url
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun onPageTitleUpdated(tabId: String, title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(title = cleanTitle) else tab
            }
            state.copy(tabs = updatedTabs)
        }
    }

    fun onSnapshotColorReady(tabId: String, color: Color?) {
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(siteSeedColor = color ?: tab.siteSeedColor)
                } else {
                    tab
                }
            }
            if (tabId == state.activeTabId) {
                state.copy(
                    tabs = updatedTabs,
                    siteSeedColor = color ?: state.siteSeedColor,
                    isPageReadyToReveal = true
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun onRevealTimeout() {
        uiState.update { it.copy(isPageReadyToReveal = true) }
    }

    fun updateSiteSeedColor(tabId: String, color: Color?) {
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(siteSeedColor = color) else tab
            }
            if (tabId == state.activeTabId) {
                state.copy(tabs = updatedTabs, siteSeedColor = color)
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun updateTopRegionColor(tabId: String, color: Color?) {
        if (color == null) return
        uiState.update { state ->
            val targetTab = state.tabs.firstOrNull { it.id == tabId }
            if (targetTab != null && WebEdgeColorExtractor.isPerceptuallySimilar(targetTab.topRegionColor, color)) {
                return@update state
            }
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(topRegionColor = color) else tab
            }
            if (tabId == state.activeTabId) {
                state.copy(tabs = updatedTabs, topRegionColor = color)
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun updateBottomRegionColor(tabId: String, color: Color?) {
        if (color == null) return
        uiState.update { state ->
            val targetTab = state.tabs.firstOrNull { it.id == tabId }
            if (targetTab != null && WebEdgeColorExtractor.isPerceptuallySimilar(targetTab.bottomRegionColor, color)) {
                return@update state
            }
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(bottomRegionColor = color) else tab
            }
            if (tabId == state.activeTabId) {
                state.copy(tabs = updatedTabs, bottomRegionColor = color)
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun onProgressChanged(tabId: String, progress: Int) {
        uiState.update { state ->
            val isLoading = progress in 1..99
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(isLoading = isLoading, loadingProgress = progress)
                } else {
                    tab
                }
            }
            if (tabId == state.activeTabId) {
                state.copy(
                    tabs = updatedTabs,
                    loadingProgress = progress,
                    isLoading = isLoading
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun updateNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(canGoBack = canGoBack, canGoForward = canGoForward)
                } else {
                    tab
                }
            }
            if (tabId == state.activeTabId) {
                state.copy(
                    tabs = updatedTabs,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }
}