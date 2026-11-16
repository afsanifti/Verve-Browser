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

    fun onPageLoadStarted(tabId: String) {
        uiState.update { state ->
            val targetTab = state.tabs.firstOrNull { it.id == tabId }
            val isIncognito = targetTab?.isIncognito == true
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        isMainFrameLoading = true,
                        isLoading = true,
                        loadingProgress = 0
                    )
                } else {
                    tab
                }
            }
            if (tabId == state.activeTabId) {
                state.copy(
                    tabs = updatedTabs,
                    isLoading = true,
                    loadingProgress = 0,
                    isThemeExtracting = !isIncognito,
                    pendingSiteSeedColor = null
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun onPageLoadFinished(tabId: String) {
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        isMainFrameLoading = false,
                        isLoading = false,
                        loadingProgress = 100
                    )
                } else {
                    tab
                }
            }
            if (tabId == state.activeTabId) {
                state.copy(
                    tabs = updatedTabs,
                    isLoading = false,
                    loadingProgress = 100,
                    isPageReadyToReveal = true
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun onThemeExtractionStarted(tabId: String) {
        uiState.update { state ->
            val targetTab = state.tabs.firstOrNull { it.id == tabId }
            if (targetTab?.isIncognito == true) return@update state
            if (tabId == state.activeTabId && targetTab?.isMainFrameLoading == true) {
                state.copy(
                    isThemeExtracting = true,
                    pendingSiteSeedColor = null
                )
            } else {
                state
            }
        }
    }

    fun onSnapshotColorReady(tabId: String, color: Color?) {
        uiState.update { state ->
            val targetTab = state.tabs.firstOrNull { it.id == tabId }
            val isIncognito = targetTab?.isIncognito == true
            val effectiveColor = if (isIncognito) null else color

            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        siteSeedColor = if (isIncognito) {
                            null
                        } else {
                            effectiveColor ?: if (state.isThemeExtracting) null else tab.siteSeedColor
                        }
                    )
                } else {
                    tab
                }
            }
            if (tabId == state.activeTabId) {
                val resolvedColor = if (isIncognito) {
                    null
                } else {
                    effectiveColor ?: if (state.isThemeExtracting) null else state.siteSeedColor
                }
                state.copy(
                    tabs = updatedTabs,
                    siteSeedColor = resolvedColor,
                    pendingSiteSeedColor = resolvedColor,
                    isThemeExtracting = false,
                    isPageReadyToReveal = true
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun onThemeGradientAnimationCompleted() {
        uiState.update { state ->
            state.copy(
                siteSeedColor = if (state.isIncognito) null else (state.pendingSiteSeedColor ?: state.siteSeedColor),
                isThemeExtracting = false
            )
        }
    }

    fun onRevealTimeout() {
        uiState.update { state ->
            state.copy(
                isPageReadyToReveal = true,
                isThemeExtracting = false
            )
        }
    }

    fun updateSiteSeedColor(tabId: String, color: Color?) {
        uiState.update { state ->
            val targetTab = state.tabs.firstOrNull { it.id == tabId }
            val resolvedColor = if (targetTab?.isIncognito == true) null else color
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(siteSeedColor = resolvedColor) else tab
            }
            if (tabId == state.activeTabId) {
                state.copy(
                    tabs = updatedTabs,
                    siteSeedColor = resolvedColor,
                    pendingSiteSeedColor = resolvedColor,
                    isThemeExtracting = false
                )
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
            val targetTab = state.tabs.firstOrNull { it.id == tabId } ?: return@update state
            // Ignore post-load subresource/iframe progress updates so the pill never re-enters loading
            if (!targetTab.isMainFrameLoading && progress < 100) {
                return@update state
            }

            val isLoading = targetTab.isMainFrameLoading && progress in 0..99
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