package com.verve.browser.presentation.browser.components.searchpill

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.browser.components.searchpill.pageactions.PageActionsBottomControls
import com.verve.browser.presentation.browser.components.searchpill.pageactions.PageActionsMiddleSections
import com.verve.browser.presentation.browser.components.searchpill.pageactions.PageActionsTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageActionsBottomSheet(
    currentUrl: String,
    isPageLoading: Boolean,
    canGoForward: Boolean,
    isTranslated: Boolean,
    translateLanguage: String,
    isShieldEnabled: Boolean,
    isBookmarked: Boolean,
    isDesktopSite: Boolean,
    isReaderMode: Boolean,
    onDismissRequest: () -> Unit,
    isQuicConnection: Boolean = false,
    onCopyLinkClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onStopLoadingClick: () -> Unit = {},
    onForwardClick: () -> Unit = {},
    onTranslateToggle: (Boolean) -> Unit = {},
    onTranslateSettingsClick: () -> Unit = {},
    onSiteControlsClick: () -> Unit = {},
    onFindOnPageClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onZoomClick: () -> Unit = {},
    onNewTabClick: () -> Unit = {},
    onShieldToggle: (Boolean) -> Unit = {},
    onBookmarkToggle: (Boolean) -> Unit = {},
    onDesktopSiteToggle: (Boolean) -> Unit = {},
    onReaderModeToggle: (Boolean) -> Unit = {}
) {
    var allowPartialDuringOpen by remember { mutableStateOf(true) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        confirmValueChange = { target ->
            allowPartialDuringOpen || target != SheetValue.PartiallyExpanded
        }
    )
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showCopiedFeedback by remember { mutableStateOf(false) }

    val displayDomain = remember(currentUrl) {
        extractDisplayDomain(currentUrl).ifBlank { "New Tab" }
    }

    val copyCurrentUrl = {
        if (currentUrl.isNotBlank()) {
            coroutineScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("url", currentUrl))
                )
            }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            showCopiedFeedback = true
            onCopyLinkClick()
        }
    }

    LaunchedEffect(showCopiedFeedback) {
        if (showCopiedFeedback) {
            delay(1500L)
            showCopiedFeedback = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null
    ) {
        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.targetValue }
                .collect { target ->
                    when (target) {
                        SheetValue.PartiallyExpanded -> sheetState.expand()
                        SheetValue.Expanded -> allowPartialDuringOpen = false
                        SheetValue.Hidden -> Unit
                    }
                }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageActionsTopBar(
                displayDomain = displayDomain,
                showCopiedFeedback = showCopiedFeedback,
                isPageLoading = isPageLoading,
                canGoForward = canGoForward,
                isQuicConnection = isQuicConnection,
                onCopyUrlClick = copyCurrentUrl,
                onReloadOrStopClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isPageLoading) onStopLoadingClick() else onRefreshClick()
                    onDismissRequest()
                },
                onForwardClick = {
                    if (canGoForward) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onForwardClick()
                        onDismissRequest()
                    }
                }
            )

            PageActionsMiddleSections(
                isTranslated = isTranslated,
                translateLanguage = translateLanguage,
                onTranslateToggle = { checked ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTranslateToggle(checked)
                },
                onTranslateSettingsClick = onTranslateSettingsClick,
                onSiteControlsClick = {
                    onDismissRequest()
                    onSiteControlsClick()
                },
                onFindOnPageClick = {
                    onDismissRequest()
                    onFindOnPageClick()
                },
                onDownloadsClick = {
                    onDismissRequest()
                    onDownloadsClick()
                },
                onZoomClick = {
                    onDismissRequest()
                    onZoomClick()
                }
            )

            PageActionsBottomControls(
                isShieldEnabled = isShieldEnabled,
                isBookmarked = isBookmarked,
                isDesktopSite = isDesktopSite,
                isReaderMode = isReaderMode,
                onNewTabClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismissRequest()
                    onNewTabClick()
                },
                onShieldToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onShieldToggle(!isShieldEnabled)
                },
                onBookmarkToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBookmarkToggle(!isBookmarked)
                },
                onDesktopSiteToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDesktopSiteToggle(!isDesktopSite)
                },
                onReaderModeToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onReaderModeToggle(!isReaderMode)
                }
            )
        }
    }
}