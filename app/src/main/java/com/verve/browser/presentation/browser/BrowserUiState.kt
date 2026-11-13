package com.verve.browser.presentation.browser

import androidx.compose.ui.graphics.Color

data class BrowserUiState(
    val currentUrl: String = "",
    val searchInput: String = "",
    val isSearchFocused: Boolean = false,
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
    val isAtPageBottom: Boolean
        get() = bottomCurtainProgress >= 0.99f || hasFixedBottomBar

    companion object {
        val DEFAULT_SEARCH_SUGGESTIONS = listOf(
            "wikipedia.org",
            "github.com",
            "android developers",
            "material 3 expressive"
        )
    }
}