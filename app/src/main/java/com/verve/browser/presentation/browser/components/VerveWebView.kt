package com.verve.browser.presentation.browser.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.verve.browser.data.download.VerveDownloadManager
import com.verve.browser.data.network.QuicNetworkManager
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.components.webview.createTabWebView
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool
import com.verve.browser.presentation.browser.util.WebViewThemeSync
import com.verve.browser.presentation.downloads.DownloadItem
import com.verve.browser.presentation.theme.rememberThemeConfig

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerveWebView(
    tabId: String,
    url: String,
    viewModel: BrowserViewModel,
    tabWebViewPool: TabWebViewPool,
    thumbnailCache: TabThumbnailCache,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier,
    onDownloadStarted: (DownloadItem) -> Unit = {}
) {
    var attachedTabId by remember { mutableStateOf("") }
    val latestOnDownloadStarted by rememberUpdatedState(onDownloadStarted)

    val themeConfig = rememberThemeConfig()
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = WebViewThemeSync.resolveIsDarkTheme(themeConfig.darkMode, isSystemDark)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { hostContainer ->
            val targetWebView = tabWebViewPool.getOrCreateWebView(
                tabId = tabId,
                context = hostContainer.context
            ) { ctx ->
                QuicNetworkManager.initialize(ctx)
                createTabWebView(
                    context = ctx,
                    tabId = tabId,
                    viewModel = viewModel,
                    tabWebViewPool = tabWebViewPool,
                    thumbnailCache = thumbnailCache
                ).also { createdWebView ->
                    VerveDownloadManager.attachToWebView(
                        context = ctx,
                        webView = createdWebView,
                        onDownloadStarted = { item -> latestOnDownloadStarted(item) }
                    )
                }
            }

            WebViewThemeSync.applyToWebView(targetWebView, isDarkTheme)

            if (attachedTabId != tabId || targetWebView.parent !== hostContainer) {
                if (attachedTabId.isNotEmpty() && attachedTabId != tabId) {
                    tabWebViewPool.peekWebView(attachedTabId)?.let { previous ->
                        thumbnailCache.captureFromWebView(attachedTabId, previous)
                        previous.onPause()
                    }
                }
                (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                hostContainer.removeAllViews()
                hostContainer.addView(
                    targetWebView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                targetWebView.onResume()
                attachedTabId = tabId
                onWebViewCreated(targetWebView)
            }

            val lastRequestedUrl = tabWebViewPool.getLastRequestedUrl(tabId)
            if (url.isNotEmpty() && url != lastRequestedUrl) {
                tabWebViewPool.setLastRequestedUrl(tabId, url)
                targetWebView.loadUrl(url)
            }
        },
        onRelease = { hostContainer ->
            if (attachedTabId.isNotEmpty()) {
                tabWebViewPool.peekWebView(attachedTabId)?.let { activeView ->
                    thumbnailCache.captureFromWebView(attachedTabId, activeView)
                    activeView.onPause()
                }
            }
            hostContainer.removeAllViews()
            attachedTabId = ""
        }
    )
}