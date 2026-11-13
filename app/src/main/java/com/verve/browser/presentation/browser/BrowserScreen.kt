package com.verve.browser.presentation.browser

import android.app.Activity
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.verve.browser.presentation.browser.components.ExpressiveBottomToolbar
import com.verve.browser.presentation.browser.components.PageRevealCurtain
import com.verve.browser.presentation.browser.components.QuickTabsScreen
import com.verve.browser.presentation.browser.components.SearchSuggestionsList
import com.verve.browser.presentation.browser.components.VerveWebView
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

private val CollapsedToolbarTotalHeight = 35.dp
private val ExpandedToolbarTotalHeight = 92.dp

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val uiState: BrowserUiState by viewModel.uiState.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val tabWebViewPool = remember { TabWebViewPool() }
    val thumbnailCache = remember { TabThumbnailCache() }
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val density = LocalDensity.current

    DisposableEffect(tabWebViewPool, thumbnailCache) {
        onDispose {
            tabWebViewPool.destroyAll()
            thumbnailCache.clear()
        }
    }

    val isOnWebPage = uiState.currentUrl.isNotEmpty()
    val canGoBackInWeb = uiState.canGoBack || (webViewRef?.canGoBack() == true)

    val topSafeAreaInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
    val composeTopSafeAreaPx = topSafeAreaInsets.getTop(density)
    val rootTopSafeAreaPx = if (!view.isInEditMode) {
        ViewCompat.getRootWindowInsets(view)
            ?.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            ?.top ?: 0
    } else {
        0
    }
    val topSafeAreaHeightDp = with(density) { max(composeTopSafeAreaPx, rootTopSafeAreaPx).toDp() }

    val bottomNavInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
    val bottomNavInsetHeightDp = with(density) { bottomNavInsets.getBottom(density).toDp() }
    val defaultRegionColor = MaterialTheme.colorScheme.surfaceContainerLow

    val targetTopColor = if (isOnWebPage && !uiState.isSearchFocused) {
        uiState.topRegionColor ?: defaultRegionColor
    } else {
        defaultRegionColor
    }

    val targetBottomColor = if (isOnWebPage && !uiState.isSearchFocused) {
        uiState.bottomRegionColor ?: uiState.topRegionColor ?: defaultRegionColor
    } else {
        defaultRegionColor
    }

    val animatedTopColor by animateColorAsState(
        targetValue = targetTopColor,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "adaptiveTopRegionColor"
    )

    val animatedBottomColor by animateColorAsState(
        targetValue = targetBottomColor,
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "adaptiveBottomRegionColor"
    )

    val isToolbarEffectivelyCollapsed = isOnWebPage && uiState.isToolbarCollapsed && !uiState.isSearchFocused
    val activeToolbarHeightDp by animateDpAsState(
        targetValue = if (isToolbarEffectivelyCollapsed) {
            CollapsedToolbarTotalHeight
        } else {
            ExpandedToolbarTotalHeight
        },
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 500f
        ),
        label = "activeToolbarCurtainHeight"
    )

    val fixedBottomBarProgress by animateFloatAsState(
        targetValue = if (isOnWebPage && uiState.hasFixedBottomBar && !uiState.isSearchFocused) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 500f
        ),
        label = "fixedBottomBarCurtainProgress"
    )

    val effectiveCurtainProgress = if (isOnWebPage && !uiState.isSearchFocused) {
        max(uiState.bottomCurtainProgress, fixedBottomBarProgress).coerceIn(0f, 1f)
    } else {
        0f
    }
    val fullBottomCurtainHeightDp = activeToolbarHeightDp + bottomNavInsetHeightDp
    val progressiveBottomCurtainHeightDp = fullBottomCurtainHeightDp * effectiveCurtainProgress
    val progressiveBottomShiftPx = with(density) { progressiveBottomCurtainHeightDp.roundToPx() }

    if (!view.isInEditMode) {
        val isTopLight = if (uiState.isTabSwitcherOpen) false else targetTopColor.luminance() > 0.5f
        val isBottomLight = if (uiState.isTabSwitcherOpen) false else targetBottomColor.luminance() > 0.5f
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = isTopLight
            controller.isAppearanceLightNavigationBars = isBottomLight
        }
    }

    LaunchedEffect(uiState.activeTabId, uiState.currentUrl) {
        if (uiState.currentUrl.isNotEmpty()) {
            delay(1500L.milliseconds)
            viewModel.onRevealTimeout()
        }
    }

    BackHandler(enabled = uiState.isTabSwitcherOpen || uiState.isSearchFocused || isOnWebPage || canGoBackInWeb) {
        if (uiState.isTabSwitcherOpen) {
            viewModel.closeTabSwitcher()
            return@BackHandler
        }

        if (uiState.isSearchFocused) {
            focusManager.clearFocus()
            viewModel.onSearchFocusChanged(false)
            return@BackHandler
        }

        val webView = webViewRef
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            val activeId = uiState.activeTabId
            webViewRef = null
            tabWebViewPool.resetTab(activeId)
            thumbnailCache.remove(activeId)
            viewModel.navigateHome()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(defaultRegionColor)
    ) {
        // LAYER 1: Constant-Height Webpage Viewport
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topSafeAreaHeightDp)
                .clipToBounds()
                .consumeWindowInsets(topSafeAreaInsets.union(bottomNavInsets))
        ) {
            if (isOnWebPage) {
                VerveWebView(
                    tabId = uiState.activeTabId,
                    url = uiState.currentUrl,
                    viewModel = viewModel,
                    tabWebViewPool = tabWebViewPool,
                    thumbnailCache = thumbnailCache,
                    onWebViewCreated = { webViewRef = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, -progressiveBottomShiftPx) }
                )

                PageRevealCurtain(
                    visible = !uiState.isPageReadyToReveal,
                    progress = uiState.loadingProgress
                )
            }
        }

        // LAYER 2A: Independent Adaptive Top Region (status bar + display cutout)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topSafeAreaHeightDp)
                .background(animatedTopColor)
        )

        // LAYER 2B: Decoupled Progressive Bottom Curtain
        if (progressiveBottomCurtainHeightDp > 0.dp && effectiveCurtainProgress > 0.001f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(progressiveBottomCurtainHeightDp)
                    .graphicsLayer { alpha = effectiveCurtainProgress }
                    .background(animatedBottomColor)
            )
        }

        // Focused Search Overlay Backdrop
        AnimatedVisibility(
            visible = uiState.isSearchFocused,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        viewModel.onSearchFocusChanged(false)
                    }
            )
        }

        // LAYER 3: Native Floating Material You Search Bar & Suggestions Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(bottomNavInsets.union(WindowInsets.ime)),
            verticalArrangement = Arrangement.Bottom
        ) {
            AnimatedVisibility(
                visible = uiState.isSearchFocused && uiState.searchSuggestions.isNotEmpty(),
                enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 6 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(150)) { it / 6 }
            ) {
                SearchSuggestionsList(
                    suggestions = uiState.searchSuggestions,
                    onSuggestionClick = { suggestion ->
                        viewModel.onUrlSubmitted(suggestion)
                        focusManager.clearFocus()
                    },
                    onSuggestionFill = { suggestion ->
                        viewModel.onSearchInputChanged(suggestion)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            ExpressiveBottomToolbar(
                inputText = uiState.searchInput,
                onInputTextChange = viewModel::onSearchInputChanged,
                onUrlSubmitted = viewModel::onUrlSubmitted,
                onTabSwitcherClick = {
                    val activeId = uiState.activeTabId
                    val activeView = tabWebViewPool.peekWebView(activeId) ?: webViewRef
                    thumbnailCache.captureFromWebView(activeId, activeView)
                    viewModel.openTabSwitcher()
                },
                currentUrl = uiState.currentUrl,
                isWebMode = isOnWebPage,
                isCollapsed = uiState.isToolbarCollapsed,
                isSearchFocused = uiState.isSearchFocused,
                bottomWindowInsets = WindowInsets(0, 0, 0, 0),
                onSearchFocusChanged = viewModel::onSearchFocusChanged,
                onCollapsedChange = { collapsed ->
                    viewModel.setToolbarCollapsed(collapsed = collapsed, isManual = true)
                },
                onLeftActionClick = { /* open page menu sheet */ },
                onSiteSettingsClick = { /* open site settings / adblock info */ },
                onRefreshClick = { webViewRef?.reload() },
                modifier = Modifier.fillMaxWidth()
            )
        }

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