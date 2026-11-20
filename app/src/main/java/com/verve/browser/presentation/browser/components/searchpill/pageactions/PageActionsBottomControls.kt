package com.verve.browser.presentation.browser.components.searchpill.pageactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ToggleCornerSpring = spring<Dp>(
    dampingRatio = 0.92f,
    stiffness = 460f
)

@Composable
internal fun PageActionsBottomControls(
    isShieldEnabled: Boolean,
    isBookmarked: Boolean,
    isDesktopSite: Boolean,
    isReaderMode: Boolean,
    onNewTabClick: () -> Unit,
    onShieldToggle: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onDesktopSiteToggle: () -> Unit,
    onReaderModeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary "+ New tab" Pill Button
        Surface(
            onClick = onNewTabClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = PageActionsSymbols.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New tab",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Bottom 4 Shape-Morphing Quick Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveQuickToggleTile(
                icon = if (isShieldEnabled) PageActionsSymbols.ShieldFilled else PageActionsSymbols.Shield,
                contentDescription = "Ad and tracker shield",
                isActive = isShieldEnabled,
                onClick = onShieldToggle,
                modifier = Modifier.weight(1f)
            )

            ExpressiveQuickToggleTile(
                icon = if (isBookmarked) PageActionsSymbols.BookmarkFilled else PageActionsSymbols.Bookmark,
                contentDescription = "Bookmark page",
                isActive = isBookmarked,
                onClick = onBookmarkToggle,
                modifier = Modifier.weight(1f)
            )

            ExpressiveQuickToggleTile(
                icon = if (isDesktopSite) PageActionsSymbols.DesktopWindowsFilled else PageActionsSymbols.DesktopWindows,
                contentDescription = "Desktop site",
                isActive = isDesktopSite,
                onClick = onDesktopSiteToggle,
                modifier = Modifier.weight(1f)
            )

            ExpressiveQuickToggleTile(
                icon = if (isReaderMode) PageActionsSymbols.ArticleFilled else PageActionsSymbols.Article,
                contentDescription = "Reader mode",
                isActive = isReaderMode,
                onClick = onReaderModeToggle,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ExpressiveQuickToggleTile(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "quickToggleContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "quickToggleContentColor"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isActive) 18.dp else 32.dp,
        animationSpec = ToggleCornerSpring,
        label = "quickToggleCornerRadius"
    )

    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}