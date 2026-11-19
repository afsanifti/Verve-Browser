package com.verve.browser.presentation.browser.components.searchpill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val MinZoomPercent = 50
private const val MaxZoomPercent = 200
private const val DefaultZoomPercent = 100
private const val ZoomStepPercent = 5

@Composable
fun PageZoomBottomBar(
    zoomPercent: Int,
    onZoomPercentChange: (Int) -> Unit,
    onInteractionStateChange: (isInteracting: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bottomWindowInsets: WindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
) {
    val haptic = LocalHapticFeedback.current
    var lastHapticZoom by remember { mutableIntStateOf(zoomPercent) }

    val applyZoom: (Int) -> Unit = { rawValue ->
        val clamped = rawValue.coerceIn(MinZoomPercent, MaxZoomPercent)
        if (clamped != zoomPercent) {
            if (clamped == DefaultZoomPercent && lastHapticZoom != DefaultZoomPercent) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastHapticZoom = clamped
            onZoomPercentChange(clamped)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(bottomWindowInsets)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Top Centered Capsule: "74 % | Reset"
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$zoomPercent %",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onInteractionStateChange(false)
                            applyZoom(DefaultZoomPercent)
                        }
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Reset",
                        color = if (zoomPercent != DefaultZoomPercent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // 2. Bottom Stadium Slider Bar: "-  [===|--------•]  +"
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onInteractionStateChange(false)
                        val next = ((zoomPercent - 1) / ZoomStepPercent) * ZoomStepPercent
                        applyZoom(next)
                    },
                    enabled = zoomPercent > MinZoomPercent,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Zoom out",
                        tint = if (zoomPercent > MinZoomPercent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Slider(
                    value = zoomPercent.toFloat(),
                    onValueChange = { sliderValue ->
                        onInteractionStateChange(true)
                        applyZoom(sliderValue.roundToInt())
                    },
                    onValueChangeFinished = {
                        onInteractionStateChange(false)
                    },
                    valueRange = MinZoomPercent.toFloat()..MaxZoomPercent.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .width(IntrinsicSize.Min)
                )

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onInteractionStateChange(false)
                        val next = ((zoomPercent / ZoomStepPercent) + 1) * ZoomStepPercent
                        applyZoom(next)
                    },
                    enabled = zoomPercent < MaxZoomPercent,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Zoom in",
                        tint = if (zoomPercent < MaxZoomPercent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}