package com.verve.browser.presentation.browser

import androidx.compose.ui.graphics.Color

data class BrowserUiState(
    val currentUrl: String = "",
    val searchInput: String = "",
    val isSearchFocused: Boolean = false,
    val searchSuggestions: List<String> = DEFAULT_SEARCH_SUGGESTIONS,
    val recentSearches: List<String> = DEFAULT_SEARCH_SUGGESTIONS,
    val siteSeedColor: Color? = null,
    val isPageReadyToReveal: Boolean = false,
    val isToolbarCollapsed: Boolean = false,
    val isLoading: Boolean = false,
    val loadingProgress: Int = 0,
    val blockedAdsCount: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
) {
    companion object {
        val DEFAULT_SEARCH_SUGGESTIONS = listOf(
            "wikipedia.org",
            "github.com",
            "android developers",
            "material 3 expressive"
        )
    }
}