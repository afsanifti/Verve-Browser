package com.verve.browser.presentation.browser

import androidx.compose.ui.graphics.Color

data class BrowserUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab(id = INITIAL_TAB_ID)),
    val activeTabId: String = INITIAL_TAB_ID,
    val currentUrl: String = "",
    val searchInput: String = "",
    val isSearchFocused: Boolean = false,
    val isTabSwitcherOpen: Boolean = false,
    val searchSuggestions: List<String> = DEFAULT_SEARCH_SUGGESTIONS,
    val recentSearches: List<String> = DEFAULT_SEARCH_SUGGESTIONS,
    val siteSeedColor: Color? = null,
    val topRegionColor: Color? = null,
    val bottomRegionColor: Color? = null,
    val bottomCurtainProgress: Float = 0f,
    val hasFixedBottomBar: Boolean = false,
    val isPageReadyToReveal: Boolean = false,
    val isToolbarCollapsed: Boolean = false,
    val isToolbarManuallyCollapsed: Boolean = false,
    val isLoading: Boolean = false,
    val loadingProgress: Int = 0,
    val blockedAdsCount: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
) {
    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()

    val isAtPageBottom: Boolean
        get() = bottomCurtainProgress >= 0.99f || hasFixedBottomBar

    companion object {
        const val INITIAL_TAB_ID = "tab_initial"

        val DEFAULT_SEARCH_SUGGESTIONS = listOf(
            "wikipedia.org",
            "github.com",
            "android developers",
            "material 3 expressive"
        )
    }
}