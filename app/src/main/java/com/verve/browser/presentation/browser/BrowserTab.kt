package com.verve.browser.presentation.browser

import androidx.compose.ui.graphics.Color

data class BrowserTab(
    val id: String,
    val url: String = "",
    val title: String = "",
    val siteSeedColor: Color? = null,
    val topRegionColor: Color? = null,
    val bottomRegionColor: Color? = null,
    val isLoading: Boolean = false,
    val loadingProgress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val lastAccessedAt: Long = System.currentTimeMillis()
)