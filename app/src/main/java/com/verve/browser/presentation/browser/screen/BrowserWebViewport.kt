package com.verve.browser.presentation.browser.screen

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.components.VerveWebView
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool

@Composable
internal fun BrowserWebViewport(
    uiState: BrowserUiState,
    isOnWebPage: Boolean,
    insetsState: BrowserInsetsState,
    curtainMetrics: BrowserCurtainMetrics,
    viewModel: BrowserViewModel,
    tabWebViewPool: TabWebViewPool,
    thumbnailCache: TabThumbnailCache,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = insetsState.topSafeAreaHeightDp.coerceAtLeast(0.dp),
                bottom = curtainMetrics.fixedBottomPaddingDp.coerceAtLeast(0.dp)
            )
            .graphicsLayer { clip = true }
            .consumeWindowInsets(insetsState.topSafeAreaInsets.union(insetsState.bottomNavInsets))
    ) {
        if (isOnWebPage) {
            VerveWebView(
                tabId = uiState.activeTabId,
                url = uiState.currentUrl,
                viewModel = viewModel,
                tabWebViewPool = tabWebViewPool,
                thumbnailCache = thumbnailCache,
                onWebViewCreated = onWebViewCreated,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = -curtainMetrics.scrollBottomShiftPx.toFloat()
                    }
            )
        }
    }
}