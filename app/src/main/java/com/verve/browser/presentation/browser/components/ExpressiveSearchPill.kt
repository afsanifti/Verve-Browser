package com.verve.browser.presentation.browser.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.verve.browser.presentation.browser.util.DropShadow

@Composable
fun ExpressiveSearchPill(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onUrlSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentUrl: String = "",
    isWebMode: Boolean = false,
    isCollapsed: Boolean = false,
    expandProgress: Float? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onExpandClick: () -> Unit = {},
    onSiteSettingsClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    var isEditingUrl by remember(isWebMode) { mutableStateOf(false) }
    var hasGainedFocus by remember(isEditingUrl) { mutableStateOf(false) }

    val effectiveCollapsed = isWebMode && isCollapsed && !isEditingUrl
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    val internalExpandProgress by animateFloatAsState(
        targetValue = if (effectiveCollapsed) 0f else 1f,
        animationSpec = ExpressiveToolbarQuickSpring,
        label = "pillExpandProgress"
    )
    val resolvedExpand = (expandProgress ?: internalExpandProgress).coerceAtLeast(0f)
    val clampedExpand = resolvedExpand.coerceIn(0f, 1f)
    val pillHeight = lerp(30.dp, 72.dp, resolvedExpand.coerceIn(0f, 1.03f))
    val iconSlidePx = with(density) { 10.dp.toPx() }

    val submitAction = {
        if (inputText.isNotBlank()) {
            onUrlSubmitted(inputText)
            focusManager.clearFocus()
            onFocusChanged(false)
            if (isWebMode) isEditingUrl = false
        }
    }

    LaunchedEffect(isEditingUrl, isWebMode) {
        if (isWebMode && isEditingUrl) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        modifier = modifier
            .DropShadow(fillColor = containerColor)
            .height(pillHeight),
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 0.dp
    ) {
        if (isWebMode && !isEditingUrl) {
            val iconProgress = ((clampedExpand - 0.30f) / 0.70f).coerceIn(0f, 1f)
            val iconScale = 0.68f + 0.32f * iconProgress
            val domainHorizontalPadding = lerp(14.dp, 54.dp, clampedExpand)
            val domainFontSize = lerp(12.sp, 17.sp, clampedExpand)

            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = onSiteSettingsClick,
                    enabled = iconProgress > 0.65f,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .graphicsLayer {
                            alpha = iconProgress
                            scaleX = iconScale
                            scaleY = iconScale
                            translationX = -iconSlidePx * (1f - iconProgress)
                        }
                ) {
                    Icon(
                        imageVector = TuneVectorIcon,
                        contentDescription = "Site Settings",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (clampedExpand < 0.5f) {
                                onExpandClick()
                            } else {
                                onInputTextChange(currentUrl)
                                isEditingUrl = true
                            }
                        }
                        .padding(horizontal = domainHorizontalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = extractDisplayDomain(currentUrl),
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = domainFontSize,
                            fontWeight = if (clampedExpand < 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onRefreshClick,
                    enabled = iconProgress > 0.65f,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .graphicsLayer {
                            alpha = iconProgress
                            scaleX = iconScale
                            scaleY = iconScale
                            translationX = iconSlidePx * (1f - iconProgress)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reload Page",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            onFocusChanged(state.isFocused)
                            if (state.isFocused) {
                                hasGainedFocus = true
                            } else if (hasGainedFocus && isWebMode) {
                                isEditingUrl = false
                            }
                        },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimaryContainer),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { submitAction() },
                        onGo = { submitAction() },
                        onDone = { submitAction() }
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Search....",
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

internal fun extractDisplayDomain(url: String): String {
    return runCatching {
        val host = Uri.parse(url).host
        if (!host.isNullOrBlank()) {
            host.removePrefix("www.")
        } else {
            url
        }
    }.getOrDefault(url)
}

private val TuneVectorIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Tune",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M3,17v2h6v-2L3,17z M3,5v2h10L13,5L3,5z M13,21v-2h8v-2h-8v-2h-2v6h2z " +
                        "M7,9v2L3,11v2h4v2h2L9,9L7,9z M21,13v-2L11,11v2h10z " +
                        "M15,9h2L17,7h4L21,5h-4L17,3h-2v6z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}