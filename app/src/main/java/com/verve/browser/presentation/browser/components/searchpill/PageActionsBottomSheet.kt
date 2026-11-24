package com.verve.browser.presentation.browser.components.searchpill

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.verve.browser.domain.model.AdBlockMode
import com.verve.browser.presentation.browser.components.searchpill.pageactions.PageActionsBottomControls
import com.verve.browser.presentation.browser.components.searchpill.pageactions.PageActionsMiddleSections
import com.verve.browser.presentation.browser.components.searchpill.pageactions.PageActionsTopBar
import com.verve.browser.presentation.browser.components.searchpill.pageactions.PageActionsTranslatePage
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PageActionsPage {
    MAIN,
    TRANSLATE
}

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
    adBlockMode: AdBlockMode = AdBlockMode.STANDARD,
    isQuicConnection: Boolean = false,
    onCopyLinkClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onStopLoadingClick: () -> Unit = {},
    onForwardClick: () -> Unit = {},
    onTranslateToggle: (Boolean) -> Unit = {},
    onTranslateLanguageSelected: (String) -> Unit = {},
    onTranslateSettingsClick: () -> Unit = {},
    onSiteControlsClick: () -> Unit = {},
    onFindOnPageClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onZoomClick: () -> Unit = {},
    onNewTabClick: () -> Unit = {},
    onShieldToggle: () -> Unit = {},
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
    var currentPage by remember { mutableStateOf(PageActionsPage.MAIN) }
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
            delay(1500L.milliseconds)
            showCopiedFeedback = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        }
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

        BackHandler(enabled = currentPage != PageActionsPage.MAIN) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentPage = PageActionsPage.MAIN
        }

        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ),
            transitionSpec = {
                val movingForward = targetState.ordinal > initialState.ordinal
                val slideIn = slideInHorizontally(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) { fullWidth ->
                    if (movingForward) fullWidth / 4 else -fullWidth / 4
                } + fadeIn(animationSpec = tween(durationMillis = 180))

                val slideOut = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) { fullWidth ->
                    if (movingForward) -fullWidth / 4 else fullWidth / 4
                } + fadeOut(animationSpec = tween(durationMillis = 140))

                slideIn togetherWith slideOut
            },
            label = "pageActionsSheetPage"
        ) { page ->
            when (page) {
                PageActionsPage.MAIN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PageActionsTopBar(
                            displayDomain = displayDomain,
                            shareUrl = currentUrl,
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
                                if (checked && translateLanguage.equals("English", ignoreCase = true)) {
                                    currentPage = PageActionsPage.TRANSLATE
                                } else {
                                    onTranslateToggle(checked)
                                }
                            },
                            onTranslateSettingsClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTranslateSettingsClick()
                                currentPage = PageActionsPage.TRANSLATE
                            },
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
                            adBlockMode = adBlockMode,
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
                                onShieldToggle()
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

                PageActionsPage.TRANSLATE -> {
                    PageActionsTranslatePage(
                        isTranslated = isTranslated,
                        selectedLanguage = translateLanguage,
                        onBackClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentPage = PageActionsPage.MAIN
                        },
                        onTranslateToggle = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTranslateToggle(checked)
                        },
                        onLanguageSelected = { option ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTranslateLanguageSelected(option.displayName)
                        }
                    )
                }
            }
        }
    }
}