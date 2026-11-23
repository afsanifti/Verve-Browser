package com.verve.browser.presentation.browser

import androidx.compose.ui.graphics.Color

data class BrowserTab(
    val id: String,
    val url: String = "",
    val title: String = "",
    val isIncognito: Boolean = false,
    val stackId: String? = null,
    val siteSeedColor: Color? = null,
    val topRegionColor: Color? = null,
    val bottomRegionColor: Color? = null,
    val isMainFrameLoading: Boolean = false,
    val isLoading: Boolean = false,
    val loadingProgress: Int = 0,
    val blockedAdsCount: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val activePdfUriString: String? = null,
    val activePdfTitle: String? = null
)

data class TabStack(
    val id: String,
    val title: String,
    val seedColor: Color? = null,
    val isCollapsed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)