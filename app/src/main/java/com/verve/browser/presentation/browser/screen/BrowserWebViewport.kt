package com.verve.browser.presentation.browser.screen

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.verve.browser.R
import com.verve.browser.data.download.VerveDownloadManager
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.components.InlinePdfViewerOverlay
import com.verve.browser.presentation.browser.components.VerveWebView
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool
import com.verve.browser.presentation.browser.util.WebViewThemeSync
import com.verve.browser.presentation.theme.rememberThemeConfig
import kotlin.math.abs

private val TabCarouselGutterDp = 12.dp
private val TabCarouselCardShape = RoundedCornerShape(16.dp)
private val TabCarouselBackdropColor = Color(0xFF0C0D10)

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
    modifier: Modifier = Modifier,
    carouselOffsetFraction: Float = 0f,
    onShowCustomView: (View?, WebChromeClient.CustomViewCallback?) -> Unit = { _, _ -> },
    onHideCustomView: () -> Unit = {}
) {
    val density = LocalDensity.current
    val isCarouselActive = abs(carouselOffsetFraction) > 0.001f

    val themeConfig = rememberThemeConfig()
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = WebViewThemeSync.resolveIsDarkTheme(themeConfig.darkMode, isSystemDark)

    LaunchedEffect(tabWebViewPool, isDarkTheme) {
        tabWebViewPool.updateDarkTheme(isDarkTheme)
    }

    val activeTabIndex = remember(uiState.tabs, uiState.activeTabId) {
        uiState.tabs.indexOfFirst { it.id == uiState.activeTabId }
    }
    val activeTab = if (activeTabIndex != -1) uiState.tabs[activeTabIndex] else null
    val leftTab = if (activeTabIndex > 0) uiState.tabs[activeTabIndex - 1] else null
    val rightTab = if (activeTabIndex != -1 && activeTabIndex < uiState.tabs.lastIndex) {
        uiState.tabs[activeTabIndex + 1]
    } else {
        null
    }

    val activePdfUriString = activeTab?.activePdfUriString
    val activePdfUri = remember(activePdfUriString) {
        activePdfUriString?.let { runCatching { it.toUri() }.getOrNull() }
    }
    val activePdfTitle = activeTab?.activePdfTitle?.ifBlank { "document.pdf" } ?: "document.pdf"

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = insetsState.topSafeAreaHeightDp.coerceAtLeast(0.dp),
                bottom = curtainMetrics.fixedBottomPaddingDp.coerceAtLeast(0.dp)
            )
            .graphicsLayer { clip = true }
            .consumeWindowInsets(insetsState.topSafeAreaInsets.union(insetsState.bottomNavInsets))
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val gutterPx = with(density) { TabCarouselGutterDp.toPx() }
        val pageStridePx = viewportWidthPx + gutterPx

        if (isOnWebPage && activePdfUri != null && activePdfUriString != null) {
            InlinePdfViewerOverlay(
                pdfUri = activePdfUri,
                pdfUriString = activePdfUriString,
                pdfTitle = activePdfTitle,
                onClose = { viewModel.closeInlinePdf(uiState.activeTabId) },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (isCarouselActive) 0f else 1f }
            )
        } else if (isOnWebPage) {
            val currentTabId = uiState.activeTabId
            VerveWebView(
                tabId = currentTabId,
                url = uiState.currentUrl,
                viewModel = viewModel,
                tabWebViewPool = tabWebViewPool,
                thumbnailCache = thumbnailCache,
                onWebViewCreated = { createdWebView ->
                    WebViewThemeSync.applyToWebView(createdWebView, isDarkTheme)
                    VerveDownloadManager.registerInlinePdfHandler(createdWebView) { pdfUri, title ->
                        viewModel.openInlinePdf(currentTabId, pdfUri, title)
                    }
                    onWebViewCreated(createdWebView)
                },
                onShowCustomView = onShowCustomView,
                onHideCustomView = onHideCustomView,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isCarouselActive) 0f else 1f
                        translationY = -curtainMetrics.scrollBottomShiftPx.toFloat()
                    }
            )
        }

        // Chrome-style 1:1 interactive tab carousel overlay during horizontal bottom-bar swipe
        if (isCarouselActive && activeTab != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TabCarouselBackdropColor)
            ) {
                if (carouselOffsetFraction > 0f && leftTab != null) {
                    TabCarouselPageCard(
                        tabId = leftTab.id,
                        tabUrl = leftTab.url,
                        tabTitle = leftTab.title,
                        isIncognito = leftTab.isIncognito,
                        topRegionColor = leftTab.topRegionColor,
                        siteSeedColor = leftTab.siteSeedColor,
                        thumbnailCache = thumbnailCache,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = (carouselOffsetFraction - 1f) * pageStridePx
                            }
                    )
                }

                TabCarouselPageCard(
                    tabId = activeTab.id,
                    tabUrl = activeTab.url,
                    tabTitle = activeTab.title,
                    isIncognito = activeTab.isIncognito,
                    topRegionColor = activeTab.topRegionColor,
                    siteSeedColor = activeTab.siteSeedColor,
                    thumbnailCache = thumbnailCache,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = carouselOffsetFraction * pageStridePx
                        }
                )

                if (carouselOffsetFraction < 0f && rightTab != null) {
                    TabCarouselPageCard(
                        tabId = rightTab.id,
                        tabUrl = rightTab.url,
                        tabTitle = rightTab.title,
                        isIncognito = rightTab.isIncognito,
                        topRegionColor = rightTab.topRegionColor,
                        siteSeedColor = rightTab.siteSeedColor,
                        thumbnailCache = thumbnailCache,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = (carouselOffsetFraction + 1f) * pageStridePx
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabCarouselPageCard(
    tabId: String,
    tabUrl: String,
    tabTitle: String,
    isIncognito: Boolean,
    topRegionColor: Color?,
    siteSeedColor: Color?,
    thumbnailCache: TabThumbnailCache,
    modifier: Modifier = Modifier
) {
    val snapshot = thumbnailCache.get(tabId)
    val defaultSurface = MaterialTheme.colorScheme.surfaceContainerLow
    val cardBackground = when {
        tabUrl.isEmpty() -> defaultSurface
        topRegionColor != null -> topRegionColor
        siteSeedColor != null -> siteSeedColor
        else -> defaultSurface
    }

    Box(
        modifier = modifier
            .clip(TabCarouselCardShape)
            .background(cardBackground),
        contentAlignment = Alignment.Center
    ) {
        if (tabUrl.isNotEmpty() && snapshot != null) {
            Image(
                bitmap = snapshot,
                contentDescription = tabTitle.ifEmpty { tabUrl },
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val hostLabel = remember(tabUrl, tabTitle, isIncognito) {
                if (tabUrl.isEmpty()) {
                    if (isIncognito) "Private Tab" else "New Tab"
                } else {
                    tabTitle.ifBlank {
                        runCatching { tabUrl.toUri().host?.removePrefix("www.") }
                            .getOrNull()
                            .orEmpty()
                            .ifBlank { tabUrl }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isIncognito) R.drawable.ic_eyes_closed else R.drawable.ic_search
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = hostLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}