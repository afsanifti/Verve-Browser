package com.verve.browser.presentation.browser.screen.overlay

import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.verve.browser.domain.model.AdBlockMode
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.components.TopTranslateBanner
import com.verve.browser.presentation.browser.components.searchpill.PageActionsBottomSheet
import com.verve.browser.presentation.browser.components.searchpill.pageactions.SiteControlsBottomSheet
import com.verve.browser.presentation.browser.util.PageTranslationManager
import com.verve.browser.presentation.browser.util.ReaderModeManager
import com.verve.browser.presentation.browser.util.ReaderPalette
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.downloads.DownloadsRoute

private const val DesktopUserAgent =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@Composable
internal fun BrowserOverlayModals(
    uiState: BrowserUiState,
    overlayState: BrowserOverlayState,
    webViewRef: WebView?,
    thumbnailCache: TabThumbnailCache,
    viewModel: BrowserViewModel,
    onSiteControlsClick: () -> Unit,
    onFindOnPageClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onZoomClick: () -> Unit
) {
    val context = LocalContext.current
    val readerPalette = ReaderPalette.fromColorScheme(MaterialTheme.colorScheme)
    val adBlockMode by viewModel.adBlockMode.collectAsState()

    // Tracks the global mode chosen in Settings so temporary per-page icon taps don't overwrite it
    var settingsDefaultMode by remember { mutableStateOf(adBlockMode) }
    var isQuickCyclingMode by remember { mutableStateOf(false) }

    LaunchedEffect(adBlockMode) {
        if (isQuickCyclingMode) {
            isQuickCyclingMode = false
        } else {
            settingsDefaultMode = adBlockMode
        }
    }

    // Automatically restore the Settings adblock level when navigating to a new webpage
    LaunchedEffect(uiState.currentUrl) {
        if (uiState.currentUrl.isNotBlank() && adBlockMode != settingsDefaultMode) {
            isQuickCyclingMode = true
            viewModel.setAdBlockMode(settingsDefaultMode)
        }
    }

    val effectiveSourceLanguage = overlayState.detectedPageLanguage ?: "English"

    val runPageTranslation: (String, Boolean) -> Unit = { targetLanguage, showUndoBanner ->
        val activeWebView = webViewRef
        val previousLang = if (overlayState.isTranslated) overlayState.translateLanguage else null
        val isOriginalPageLanguage = targetLanguage.equals(effectiveSourceLanguage, ignoreCase = true)

        if (activeWebView == null || uiState.currentUrl.isBlank() || isOriginalPageLanguage) {
            activeWebView?.let { PageTranslationManager.restore(it) }
            overlayState.isTranslated = false
            overlayState.translatedBannerLanguage = null
        } else {
            overlayState.translateLanguage = targetLanguage
            overlayState.isTranslated = true
            PageTranslationManager.translatePage(
                webView = activeWebView,
                targetLanguageName = targetLanguage
            ) { success ->
                overlayState.isTranslated = success
                if (success) {
                    if (showUndoBanner) {
                        overlayState.showTranslatedBanner(
                            newLanguage = targetLanguage,
                            previousLanguage = previousLang
                        )
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Unable to translate this page",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Top language prompt & "Language changed • Undo" popup
    TopTranslateBanner(
        isPromptVisible = overlayState.isTranslatePromptVisible && overlayState.activeBannerItem == null,
        detectedLanguage = overlayState.detectedPageLanguage,
        defaultLanguage = overlayState.defaultLanguage,
        translatedLanguage = if (overlayState.activeBannerItem == null) {
            overlayState.translatedBannerLanguage
        } else {
            null
        },
        onTranslateToDefaultClick = {
            overlayState.dismissTranslatePrompt(uiState.currentUrl)
            runPageTranslation(overlayState.defaultLanguage, true)
        },
        onDismissPromptClick = {
            overlayState.dismissTranslatePrompt(uiState.currentUrl)
        },
        onUndoTranslationClick = {
            val prev = overlayState.previousLanguageBeforeTranslate
            overlayState.translatedBannerLanguage = null
            if (!prev.isNullOrBlank()) {
                runPageTranslation(prev, false)
            } else {
                webViewRef?.let { PageTranslationManager.restore(it) }
                overlayState.isTranslated = false
                overlayState.translateLanguage = overlayState.defaultLanguage
            }
        },
        onDismissTranslatedClick = {
            overlayState.translatedBannerLanguage = null
        }
    )

    // Binary On/Off toggle used by the Switch inside SiteControlsBottomSheet
    val toggleCurrentSiteShield: () -> Unit = {
        val url = uiState.currentUrl
        if (url.isNotBlank()) {
            val nowWhitelisted = viewModel.toggleSiteWhitelist(url)
            overlayState.isShieldEnabled = !nowWhitelisted
            webViewRef?.reload()
        } else {
            overlayState.isShieldEnabled = !overlayState.isShieldEnabled
        }
    }

    // 3-state cycle: Off (outline) -> Standard (filled) -> Aggressive (encrypt) -> Off (outline)
    val cycleCurrentSiteShield: () -> Unit = {
        val url = uiState.currentUrl
        when {
            !overlayState.isShieldEnabled -> {
                if (adBlockMode != AdBlockMode.STANDARD) {
                    isQuickCyclingMode = true
                    viewModel.setAdBlockMode(AdBlockMode.STANDARD)
                }
                if (url.isNotBlank()) {
                    val nowWhitelisted = viewModel.toggleSiteWhitelist(url)
                    overlayState.isShieldEnabled = !nowWhitelisted
                    webViewRef?.reload()
                } else {
                    overlayState.isShieldEnabled = true
                }
            }
            adBlockMode == AdBlockMode.STANDARD -> {
                isQuickCyclingMode = true
                viewModel.setAdBlockMode(AdBlockMode.AGGRESSIVE)
                if (url.isNotBlank()) {
                    webViewRef?.reload()
                }
            }
            else -> {
                if (url.isNotBlank()) {
                    val nowWhitelisted = viewModel.toggleSiteWhitelist(url)
                    overlayState.isShieldEnabled = !nowWhitelisted
                    webViewRef?.reload()
                } else {
                    overlayState.isShieldEnabled = false
                }
            }
        }
    }

    if (overlayState.isPageActionsSheetOpen) {
        PageActionsBottomSheet(
            currentUrl = uiState.currentUrl,
            isPageLoading = uiState.isLoading,
            canGoForward = uiState.activeTab?.canGoForward == true || (webViewRef?.canGoForward() == true),
            isTranslated = overlayState.isTranslated,
            translateLanguage = overlayState.translateLanguage,
            sourceLanguage = effectiveSourceLanguage,
            isShieldEnabled = overlayState.isShieldEnabled,
            adBlockMode = adBlockMode,
            isBookmarked = overlayState.isBookmarked,
            isDesktopSite = overlayState.isDesktopSite,
            isReaderMode = overlayState.isReaderMode,
            isQuicConnection = overlayState.isQuicEnabled && overlayState.isQuicConnection,
            onDismissRequest = { overlayState.isPageActionsSheetOpen = false },
            onRefreshClick = {
                overlayState.isReaderMode = false
                overlayState.isTranslated = false
                overlayState.translatedBannerLanguage = null
                webViewRef?.reload()
            },
            onStopLoadingClick = {
                webViewRef?.stopLoading()
                viewModel.onPageLoadFinished(uiState.activeTabId)
                viewModel.onRevealTimeout()
            },
            onForwardClick = {
                if (webViewRef?.canGoForward() == true) {
                    webViewRef.goForward()
                }
            },
            onTranslateToggle = { enabled ->
                if (enabled) {
                    overlayState.isPageActionsSheetOpen = false
                    runPageTranslation(overlayState.translateLanguage, true)
                } else {
                    webViewRef?.let { PageTranslationManager.restore(it) }
                    overlayState.isTranslated = false
                    overlayState.translatedBannerLanguage = null
                }
            },
            onTranslateLanguageSelected = { selectedLanguage ->
                overlayState.translateLanguage = selectedLanguage
                if (selectedLanguage.equals(effectiveSourceLanguage, ignoreCase = true)) {
                    webViewRef?.let { PageTranslationManager.restore(it) }
                    overlayState.isTranslated = false
                    overlayState.translatedBannerLanguage = null
                    overlayState.isPageActionsSheetOpen = false
                } else {
                    overlayState.isPageActionsSheetOpen = false
                    runPageTranslation(selectedLanguage, true)
                }
            },
            onSiteControlsClick = {
                overlayState.isSiteControlsSheetOpen = true
                onSiteControlsClick()
            },
            onFindOnPageClick = {
                overlayState.openFindInPage()
                onFindOnPageClick()
            },
            onDownloadsClick = {
                overlayState.openDownloads()
                onDownloadsClick()
            },
            onZoomClick = {
                overlayState.openZoomBar()
                onZoomClick()
            },
            onNewTabClick = {
                webViewRef?.let { activeView ->
                    thumbnailCache.captureFromWebView(
                        tabId = uiState.activeTabId,
                        webView = activeView,
                        isIncognito = uiState.isIncognito
                    )
                }
                viewModel.createNewTab(isIncognito = uiState.isIncognito)
            },
            onShieldToggle = cycleCurrentSiteShield,
            onBookmarkToggle = { bookmarked ->
                overlayState.isBookmarked = bookmarked
            },
            onDesktopSiteToggle = { desktop ->
                overlayState.isDesktopSite = desktop
                webViewRef?.apply {
                    settings.userAgentString = if (desktop) DesktopUserAgent else null
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    reload()
                }
            },
            onReaderModeToggle = { enabled ->
                val activeWebView = webViewRef
                if (activeWebView == null) {
                    overlayState.isReaderMode = false
                } else if (enabled) {
                    ReaderModeManager.enable(
                        webView = activeWebView,
                        palette = readerPalette
                    ) { activated ->
                        overlayState.isReaderMode = activated
                        if (activated) {
                            overlayState.isPageActionsSheetOpen = false
                        } else {
                            Toast.makeText(
                                context,
                                "No article content found on this page",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    ReaderModeManager.disable(activeWebView)
                    overlayState.isReaderMode = false
                    overlayState.isPageActionsSheetOpen = false
                }
            }
        )
    }

    if (overlayState.isSiteControlsSheetOpen) {
        SiteControlsBottomSheet(
            currentUrl = uiState.currentUrl,
            webView = webViewRef,
            isQuicConnection = overlayState.isQuicEnabled && overlayState.isQuicConnection,
            blockedAdsCount = uiState.blockedAdsCount,
            isShieldEnabled = overlayState.isShieldEnabled,
            adBlockMode = adBlockMode,
            onToggleShield = toggleCurrentSiteShield,
            onDismissRequest = { overlayState.isSiteControlsSheetOpen = false }
        )
    }

    // Full-screen Downloads Manager overlay
    AnimatedVisibility(
        visible = overlayState.isDownloadsScreenOpen,
        enter = slideInVertically(
            animationSpec = BottomBarSlideSpring,
            initialOffsetY = { fullHeight -> fullHeight }
        ),
        exit = slideOutVertically(
            animationSpec = BottomBarSlideSpring,
            targetOffsetY = { fullHeight -> fullHeight }
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 1f)
        ) {
            DownloadsRoute(
                onNavigateBack = { overlayState.isDownloadsScreenOpen = false },
                onOpenUrl = { url ->
                    overlayState.isDownloadsScreenOpen = false
                    viewModel.onUrlSubmitted(url)
                }
            )
        }
    }
}