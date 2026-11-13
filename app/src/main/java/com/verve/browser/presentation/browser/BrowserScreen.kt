package com.verve.browser.presentation.browser

import android.app.Activity
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.verve.browser.presentation.browser.components.ExpressiveBottomToolbar
import com.verve.browser.presentation.browser.components.PageRevealCurtain
import com.verve.browser.presentation.browser.components.SearchSuggestionsList
import com.verve.browser.presentation.browser.components.VerveWebView
import kotlinx.coroutines.delay
import kotlin.math.max

private val BottomCurtainToolbarHeight = 54.dp

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val uiState: BrowserUiState by viewModel.uiState.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val density = LocalDensity.current

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
        uiState.bottomRegionColor ?: defaultRegionColor
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
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "adaptiveBottomRegionColor"
    )

    // Conditional bottom curtain: transparent (0.dp) during normal scrolling;
    // rises smoothly to cover the compact toolbar + navigation bar inset
    // when the actual document bottom is reached.
    val shouldShowBottomCurtain = isOnWebPage && uiState.isAtPageBottom && !uiState.isSearchFocused
    val targetBottomCurtainHeightDp = if (shouldShowBottomCurtain) {
        BottomCurtainToolbarHeight + bottomNavInsetHeightDp
    } else {
        0.dp
    }

    val animatedBottomCurtainHeightDp by animateDpAsState(
        targetValue = targetBottomCurtainHeightDp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "bottomCurtainHeight"
    )

    val animatedBottomCurtainAlpha by animateFloatAsState(
        targetValue = if (shouldShowBottomCurtain) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "bottomCurtainAlpha"
    )

    val animatedBottomFloorHeightPx = with(density) { animatedBottomCurtainHeightDp.roundToPx() }

    if (!view.isInEditMode) {
        val isTopLight = targetTopColor.luminance() > 0.5f
        val isBottomLight = targetBottomColor.luminance() > 0.5f
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = isTopLight
            controller.isAppearanceLightNavigationBars = isBottomLight
        }
    }

    LaunchedEffect(uiState.currentUrl) {
        if (uiState.currentUrl.isNotEmpty()) {
            delay(1500L)
            viewModel.onRevealTimeout()
        }
    }

    BackHandler(enabled = uiState.isSearchFocused || isOnWebPage || canGoBackInWeb) {
        if (uiState.isSearchFocused) {
            focusManager.clearFocus()
            viewModel.onSearchFocusChanged(false)
            return@BackHandler
        }

        val webView = webViewRef
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            webViewRef = null
            viewModel.navigateHome()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(defaultRegionColor)
    ) {
        // LAYER 1: Webpage Viewport
        // - Top inset (status bar + display cutout) applied exactly once so content
        //   begins immediately below the status bar and display cutout safe area.
        // - Bottom extends full-bleed behind the floating pill by default, and rises
        //   smoothly by animatedBottomCurtainHeightDp only when the document bottom is reached.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topSafeAreaHeightDp)
                .consumeWindowInsets(topSafeAreaInsets.union(bottomNavInsets))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isOnWebPage) {
                    VerveWebView(
                        url = uiState.currentUrl,
                        viewModel = viewModel,
                        floorHeightPx = animatedBottomFloorHeightPx,
                        onWebViewCreated = { webViewRef = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    PageRevealCurtain(
                        visible = !uiState.isPageReadyToReveal,
                        progress = uiState.loadingProgress
                    )
                }
            }

            if (animatedBottomCurtainHeightDp > 0.dp) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(animatedBottomCurtainHeightDp)
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

        // LAYER 2B: Conditional Adaptive Bottom Curtain (transparent until document bottom is reached)
        if (animatedBottomCurtainHeightDp > 0.dp && animatedBottomCurtainAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(animatedBottomCurtainHeightDp)
                    .graphicsLayer { alpha = animatedBottomCurtainAlpha }
                    .background(animatedBottomColor)
            )
        }

        // Focused Search Overlay Backdrop (surfaceContainerLow)
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
                onTabSwitcherClick = { /* open tab switcher */ },
                currentUrl = uiState.currentUrl,
                isWebMode = isOnWebPage,
                isCollapsed = uiState.isToolbarCollapsed,
                isSearchFocused = uiState.isSearchFocused,
                bottomWindowInsets = WindowInsets(0, 0, 0, 0),
                onSearchFocusChanged = viewModel::onSearchFocusChanged,
                onCollapsedChange = viewModel::setToolbarCollapsed,
                onLeftActionClick = { /* open page menu sheet */ },
                onSiteSettingsClick = { /* open site settings / adblock info */ },
                onRefreshClick = { webViewRef?.reload() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}