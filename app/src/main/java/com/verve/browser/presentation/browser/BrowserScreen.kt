package com.verve.browser.presentation.browser

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.verve.browser.presentation.browser.components.QuickTabsScreen
import com.verve.browser.presentation.browser.screen.BrowserBackHandler
import com.verve.browser.presentation.browser.screen.BrowserBottomProgressiveCurtain
import com.verve.browser.presentation.browser.screen.BrowserSearchOverlay
import com.verve.browser.presentation.browser.screen.BrowserSystemBarSync
import com.verve.browser.presentation.browser.screen.BrowserTopSafeAreaCurtain
import com.verve.browser.presentation.browser.screen.BrowserWebViewport
import com.verve.browser.presentation.browser.screen.rememberBrowserCurtainMetrics
import com.verve.browser.presentation.browser.screen.rememberBrowserInsetsState
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val uiState: BrowserUiState by viewModel.uiState.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val tabWebViewPool = remember { TabWebViewPool() }
    val thumbnailCache = remember { TabThumbnailCache() }

    DisposableEffect(tabWebViewPool, thumbnailCache) {
        onDispose {
            tabWebViewPool.destroyAll()
            thumbnailCache.clear()
        }
    }

    val isOnWebPage = uiState.currentUrl.isNotEmpty()
    val defaultRegionColor = MaterialTheme.colorScheme.surfaceContainerLow
    val insetsState = rememberBrowserInsetsState()
    val curtainMetrics = rememberBrowserCurtainMetrics(
        uiState = uiState,
        isOnWebPage = isOnWebPage,
        bottomNavInsetHeightDp = insetsState.bottomNavInsetHeightDp,
        defaultRegionColor = defaultRegionColor
    )

    LaunchedEffect(isOnWebPage) {
        if (!isOnWebPage) {
            webViewRef = null
        }
    }

    LaunchedEffect(uiState.activeTabId, uiState.currentUrl) {
        if (uiState.currentUrl.isNotEmpty()) {
            delay(1500L.milliseconds)
            viewModel.onRevealTimeout()
        }
    }

    BrowserSystemBarSync(
        isTabSwitcherOpen = uiState.isTabSwitcherOpen,
        targetTopColor = curtainMetrics.targetTopColor,
        targetBottomColor = curtainMetrics.targetBottomColor
    )

    BrowserBackHandler(
        uiState = uiState,
        isOnWebPage = isOnWebPage,
        webViewRef = webViewRef,
        onClearWebViewRef = { webViewRef = null },
        tabWebViewPool = tabWebViewPool,
        thumbnailCache = thumbnailCache,
        viewModel = viewModel
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(defaultRegionColor)
    ) {
        // LAYER 1: Webpage Viewport
        BrowserWebViewport(
            uiState = uiState,
            isOnWebPage = isOnWebPage,
            insetsState = insetsState,
            curtainMetrics = curtainMetrics,
            viewModel = viewModel,
            tabWebViewPool = tabWebViewPool,
            thumbnailCache = thumbnailCache,
            onWebViewCreated = { webViewRef = it }
        )

        // LAYER 2A: Independent Adaptive Top Region (status bar + display cutout)
        BrowserTopSafeAreaCurtain(
            topSafeAreaHeightDp = insetsState.topSafeAreaHeightDp,
            defaultRegionColor = defaultRegionColor,
            animatedTopColor = curtainMetrics.animatedTopColor,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // LAYER 2B: Decoupled Progressive Bottom Curtain
        BrowserBottomProgressiveCurtain(
            curtainMetrics = curtainMetrics,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // LAYER 3: Search Backdrop, Suggestions & Floating Bottom Toolbar
        BrowserSearchOverlay(
            uiState = uiState,
            isOnWebPage = isOnWebPage,
            bottomNavInsets = insetsState.bottomNavInsets,
            webViewRef = webViewRef,
            tabWebViewPool = tabWebViewPool,
            thumbnailCache = thumbnailCache,
            viewModel = viewModel
        )

        // LAYER 4: Dedicated Card-Based Quick Tabs Switcher
        if (uiState.isTabSwitcherOpen) {
            QuickTabsScreen(
                tabs = uiState.tabs,
                activeTabId = uiState.activeTabId,
                thumbnailProvider = thumbnailCache::get,
                onBackClick = viewModel::closeTabSwitcher,
                onNewTabClick = viewModel::closeTabSwitcher,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}