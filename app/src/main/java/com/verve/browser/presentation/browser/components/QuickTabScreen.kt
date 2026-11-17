package com.verve.browser.presentation.browser.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.browser.BrowserTab
import com.verve.browser.presentation.browser.TabStack
import com.verve.browser.presentation.theme.VerveBrowserTheme

private const val ReferenceDeviceWidthDp = 412f
private const val ReferenceCardWidthDp = 269f
private const val ReferenceCardHeightDp = 564f
private const val ReferenceCardAspectRatio = ReferenceCardWidthDp / ReferenceCardHeightDp

@Composable
fun QuickTabsScreen(
    tabs: List<BrowserTab>,
    tabStacks: List<TabStack>,
    activeTabId: String,
    thumbnailProvider: (String) -> ImageBitmap?,
    onBackClick: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTabClick: () -> Unit,
    onCreateTabStack: (List<String>, String) -> Unit = { _, _ -> },
    onAddTabToStack: (String, String) -> Unit = { _, _ -> },
    onRemoveTabFromStack: (String) -> Unit = {},
    onToggleStackCollapsed: (String) -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onLayoutToggleClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Always use the system wallpaper/theme engine rather than the active webpage's seed color
    VerveBrowserTheme(siteSeedColor = null) {
        var isStackSheetOpen by remember { mutableStateOf(false) }
        var focusedTabId by remember { mutableStateOf(activeTabId) }

        // Place newest tabs at index 0 (far right / top of the stack) and older tabs to the left
        val stackTabs = remember(tabs) { tabs.asReversed() }

        LaunchedEffect(stackTabs, activeTabId) {
            if (stackTabs.none { it.id == focusedTabId }) {
                focusedTabId = stackTabs.firstOrNull { it.id == activeTabId }?.id
                    ?: stackTabs.firstOrNull()?.id
                            ?: activeTabId
            }
        }

        val orderedTabs = remember(stackTabs, focusedTabId) {
            if (stackTabs.isEmpty()) {
                emptyList()
            } else {
                val focusIndex = stackTabs.indexOfFirst { it.id == focusedTabId }.coerceAtLeast(0)
                stackTabs.drop(focusIndex) + stackTabs.take(focusIndex)
            }
        }

        val focusedTab = orderedTabs.firstOrNull()
        val focusedStack = remember(focusedTab, tabStacks) {
            focusedTab?.stackId?.let { stackId -> tabStacks.firstOrNull { it.id == stackId } }
        }

        val topSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        val bottomNavInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)

        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(topSafeInsets)
            ) {
                QuickTabsTopBar(
                    stackCount = tabStacks.size,
                    onBackClick = onBackClick,
                    onHistoryClick = onHistoryClick,
                    onLayoutToggleClick = onLayoutToggleClick
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val isLandscape = maxWidth > maxHeight
                    val chipAndGapHeight = 46.dp
                    val availableCardHeight = (maxHeight - chipAndGapHeight).coerceAtLeast(160.dp)

                    val cardWidth: Dp
                    val cardHeight: Dp
                    if (isLandscape) {
                        cardHeight = (availableCardHeight * 0.92f).coerceIn(150.dp, 310.dp)
                        cardWidth = (cardHeight * 1.15f).coerceIn(180.dp, (maxWidth * 0.52f).coerceAtLeast(180.dp))
                    } else {
                        val fullScreenWidth = maxWidth + 32.dp
                        val proportionalWidth = fullScreenWidth * (ReferenceCardWidthDp / ReferenceDeviceWidthDp)
                        val proportionalHeight = proportionalWidth / ReferenceCardAspectRatio
                        val maxAllowedHeight = (availableCardHeight * 0.89f).coerceAtLeast(220.dp)

                        cardHeight = proportionalHeight.coerceAtMost(maxAllowedHeight)
                        cardWidth = (cardHeight * ReferenceCardAspectRatio).coerceAtLeast(160.dp)
                    }

                    QuickTabsCardStack(
                        tabs = orderedTabs,
                        allTabsInOrder = stackTabs,
                        activeStack = focusedStack,
                        thumbnailProvider = thumbnailProvider,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        onFocusTab = { targetId -> focusedTabId = targetId },
                        onOpenFocusedTab = { tabId ->
                            if (tabId == activeTabId) {
                                onBackClick()
                            } else {
                                onSelectTab(tabId)
                            }
                        },
                        onCloseTab = { tabId ->
                            val closingIndex = stackTabs.indexOfFirst { it.id == tabId }
                            if (closingIndex != -1 && tabId == focusedTabId && stackTabs.size > 1) {
                                // Focus the next card in the stack, or the previous card if closing the leftmost one
                                val nextFocusTab = stackTabs.getOrNull(closingIndex + 1)
                                    ?: stackTabs.getOrNull(closingIndex - 1)
                                if (nextFocusTab != null) {
                                    focusedTabId = nextFocusTab.id
                                }
                            }
                            onCloseTab(tabId)
                        }
                    )
                }

                QuickTabsBottomBar(
                    hasStacks = tabStacks.isNotEmpty(),
                    onLayersClick = { isStackSheetOpen = true },
                    onNewTabClick = onNewTabClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.windowInsetsPadding(bottomNavInsets)
                )
            }

            if (isStackSheetOpen && focusedTab != null) {
                TabStackManagementDialog(
                    activeTab = focusedTab,
                    allTabs = stackTabs,
                    tabStacks = tabStacks,
                    onDismiss = { isStackSheetOpen = false },
                    onCreateTabStack = { tabIds, title ->
                        onCreateTabStack(tabIds, title)
                        isStackSheetOpen = false
                    },
                    onAddTabToStack = { tabId, stackId ->
                        onAddTabToStack(tabId, stackId)
                        isStackSheetOpen = false
                    },
                    onRemoveTabFromStack = { tabId ->
                        onRemoveTabFromStack(tabId)
                        isStackSheetOpen = false
                    },
                    onToggleStackCollapsed = { stackId ->
                        onToggleStackCollapsed(stackId)
                    }
                )
            }
        }
    }
}