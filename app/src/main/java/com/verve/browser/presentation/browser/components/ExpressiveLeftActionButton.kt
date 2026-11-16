package com.verve.browser.presentation.browser.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.browser.util.DropShadow

@Composable
fun ExpressiveLeftActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "leftButtonContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "leftButtonContentColor"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .DropShadow(
                fillColor = containerColor,
                cornerRadius = 18.dp
            )
            .size(56.dp),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        shadowElevation = 0.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Page Menu",
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}