package com.verve.browser.presentation.browser.components.searchpill

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.browser.util.DropShadow

@Composable
fun ExpressiveLeftActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonWidth: Dp = 56.dp
) {
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.tertiaryContainer,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "leftButtonContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onTertiaryContainer,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "leftButtonContentColor"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(buttonWidth)
            .height(56.dp)
            .DropShadow(
                fillColor = containerColor,
                cornerRadius = 18.dp
            ),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        shadowElevation = 0.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = SearchPillSymbols.KeyboardArrowUp,
                contentDescription = "Page Menu",
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}