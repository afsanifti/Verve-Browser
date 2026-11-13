package com.verve.browser.presentation.browser.viewmodel

import com.verve.browser.presentation.browser.BrowserUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class TabSessionController(
    private val uiState: MutableStateFlow<BrowserUiState>
) {

    fun openTabSwitcher() {
        uiState.update {
            it.copy(
                isTabSwitcherOpen = true,
                isSearchFocused = false
            )
        }
    }

    fun closeTabSwitcher() {
        uiState.update {
            it.copy(isTabSwitcherOpen = false)
        }
    }

    fun navigateHome() {
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) {
                    tab.copy(
                        url = "",
                        title = "",
                        siteSeedColor = null,
                        topRegionColor = null,
                        bottomRegionColor = null,
                        isLoading = false,
                        loadingProgress = 0,
                        canGoBack = false,
                        canGoForward = false
                    )
                } else {
                    tab
                }
            }
            state.copy(
                tabs = updatedTabs,
                currentUrl = "",
                searchInput = "",
                isSearchFocused = false,
                isTabSwitcherOpen = false,
                siteSeedColor = null,
                topRegionColor = null,
                bottomRegionColor = null,
                bottomCurtainProgress = 0f,
                hasFixedBottomBar = false,
                isPageReadyToReveal = false,
                isToolbarCollapsed = false,
                isToolbarManuallyCollapsed = false,
                isLoading = false,
                loadingProgress = 0,
                canGoBack = false,
                canGoForward = false
            )
        }
    }

    fun onUrlSubmitted(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        val destinationUrl = UrlDestinationResolver.resolve(trimmed)

        uiState.update { state ->
            val updatedRecents = (listOf(trimmed) + state.recentSearches)
                .distinctBy { it.lowercase() }
                .take(8)

            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) {
                    tab.copy(
                        url = destinationUrl,
                        title = "",
                        siteSeedColor = null,
                        topRegionColor = null,
                        bottomRegionColor = null,
                        isLoading = true,
                        loadingProgress = 0,
                        lastAccessedAt = System.currentTimeMillis()
                    )
                } else {
                    tab
                }
            }

            state.copy(
                tabs = updatedTabs,
                currentUrl = destinationUrl,
                searchInput = destinationUrl,
                isSearchFocused = false,
                isTabSwitcherOpen = false,
                recentSearches = updatedRecents,
                searchSuggestions = updatedRecents.take(4),
                siteSeedColor = null,
                topRegionColor = null,
                bottomRegionColor = null,
                bottomCurtainProgress = 0f,
                hasFixedBottomBar = false,
                isPageReadyToReveal = false,
                isToolbarCollapsed = false,
                isToolbarManuallyCollapsed = false
            )
        }
    }
}