package com.verve.browser.presentation.browser.components.searchpill

import android.annotation.SuppressLint
import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.net.toUri
import com.verve.browser.R
import com.verve.browser.presentation.browser.components.pillTertiaryLoadingGradient
import com.verve.browser.presentation.browser.util.DropShadow
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ExpressiveSearchPill(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onUrlSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentUrl: String = "",
    isWebMode: Boolean = false,
    isCollapsed: Boolean = false,
    isPageLoading: Boolean = false,
    isThemeLoading: Boolean = false,
    isIncognito: Boolean = false,
    isFindInPage: Boolean = false,
    findQuery: String = "",
    findActiveMatchOrdinal: Int = 0,
    findTotalMatches: Int = 0,
    expandProgress: Float? = null,
    swipeContentShiftPx: Float = 0f,
    onThemeAnimationCompleted: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
    onExpandClick: () -> Unit = {},
    onSiteSettingsClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onStopLoadingClick: () -> Unit = {},
    onToggleIncognito: () -> Unit = {},
    onCopyUrl: (String) -> Unit = {},
    onFindQueryChange: (String) -> Unit = {},
    onFindPrevious: () -> Unit = {},
    onFindNext: () -> Unit = {},
    onCloseFindInPage: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var isEditingUrl by remember(isWebMode) { mutableStateOf(false) }
    var hasGainedFocus by remember(isEditingUrl) { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }

    // Drives the eye-closing animation immediately on tap, then triggers theme/ViewModel toggle
    var visualIncognito by remember { mutableStateOf(isIncognito) }

    LaunchedEffect(isIncognito) {
        visualIncognito = isIncognito
    }

    LaunchedEffect(visualIncognito) {
        if (visualIncognito != isIncognito) {
            delay(230L.milliseconds)
            onToggleIncognito()
        }
    }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = inputText,
                selection = TextRange(0, inputText.length)
            )
        )
    }

    LaunchedEffect(inputText) {
        if (textFieldValue.text != inputText) {
            textFieldValue = textFieldValue.copy(
                text = inputText,
                selection = if (isWebMode && isEditingUrl && !hasGainedFocus) {
                    TextRange(0, inputText.length)
                } else {
                    TextRange(inputText.length)
                }
            )
        }
    }

    val effectiveCollapsed = isWebMode && isCollapsed && !isEditingUrl && !isFindInPage
    val isPillLoading = isWebMode && !isEditingUrl && !isFindInPage && (isPageLoading || isThemeLoading)

    val containerColor by animateColorAsState(
        targetValue = if (isPillLoading) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "pillContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isPillLoading) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "pillContentColor"
    )

    val internalExpandProgress by animateFloatAsState(
        targetValue = if (effectiveCollapsed) 0f else 1f,
        animationSpec = ExpressiveToolbarQuickSpring,
        label = "pillExpandProgress"
    )
    val clampedExpand = (expandProgress ?: internalExpandProgress).coerceIn(0f, 1f)
    val pillHeight = lerp(32.dp, 72.dp, clampedExpand)
    val iconSlidePx = with(density) { 14.dp.toPx() }

    val submitAction = {
        if (textFieldValue.text.isNotBlank()) {
            onUrlSubmitted(textFieldValue.text)
            focusManager.clearFocus()
            isSearchFocused = false
            onFocusChanged(false)
            if (isWebMode) isEditingUrl = false
        }
    }

    LaunchedEffect(isEditingUrl, isWebMode) {
        if (isWebMode && isEditingUrl) {
            textFieldValue = TextFieldValue(
                text = currentUrl,
                selection = TextRange(0, currentUrl.length)
            )
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .then(
                if (!isPillLoading) {
                    Modifier.DropShadow(fillColor = containerColor)
                } else {
                    Modifier
                }
            )
            .height(pillHeight)
            .pillTertiaryLoadingGradient(
                isLoading = isPillLoading,
                onAnimationCompleted = onThemeAnimationCompleted,
                containerColor = containerColor
            )
    ) {
        // Normal URL / Search Pill Interior
        AnimatedVisibility(
            visible = !isFindInPage,
            enter = fadeIn(animationSpec = tween(durationMillis = 190, delayMillis = 60)) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(durationMillis = 210, delayMillis = 50, easing = FastOutSlowInEasing)
                    ),
            exit = fadeOut(animationSpec = tween(durationMillis = 100)) +
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(durationMillis = 100)
                    ),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isWebMode && !isEditingUrl) {
                // Fade & tuck side icons smoothly in the upper half of expansion so they never
                // collide with the domain label while the pill narrows.
                val iconProgress = ((clampedExpand - 0.52f) / 0.48f).coerceIn(0f, 1f)
                val iconScale = 0.72f + 0.28f * iconProgress
                val domainTextScale = lerp(0.84f, 1.0f, clampedExpand)
                val domainHorizontalPadding = lerp(14.dp, 52.dp, clampedExpand)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = swipeContentShiftPx
                        }
                ) {
                    // Placed behind the side IconButtons so their click targets aren't occluded
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onLongClickLabel = "Copy link",
                                onLongClick = if (currentUrl.isNotBlank()) {
                                    {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        coroutineScope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(ClipData.newPlainText("url", currentUrl))
                                            )
                                        }
                                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                            Toast.makeText(
                                                context,
                                                "Link copied",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        onCopyUrl(currentUrl)
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    if (clampedExpand < 0.5f) {
                                        onExpandClick()
                                    } else {
                                        textFieldValue = TextFieldValue(
                                            text = currentUrl,
                                            selection = TextRange(0, currentUrl.length)
                                        )
                                        onInputTextChange(currentUrl)
                                        isEditingUrl = true
                                    }
                                }
                            )
                            .padding(horizontal = domainHorizontalPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        // Fixed 15.sp font size scaled on GPU avoids per-frame glyph re-layout jitter
                        Text(
                            text = extractDisplayDomain(currentUrl),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = contentColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            ),
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer {
                                scaleX = domainTextScale
                                scaleY = domainTextScale
                            }
                        )
                    }

                    if (iconProgress > 0.01f) {
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
                                    translationX = iconSlidePx * (1f - iconProgress)
                                }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_siteinfo),
                                contentDescription = "Site Settings",
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = if (isPageLoading) onStopLoadingClick else onRefreshClick,
                            enabled = iconProgress > 0.65f,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .graphicsLayer {
                                    alpha = iconProgress
                                    scaleX = iconScale
                                    scaleY = iconScale
                                    translationX = -iconSlidePx * (1f - iconProgress)
                                }
                        ) {
                            Icon(
                                imageVector = if (isPageLoading) SearchPillSymbols.Close else SearchPillSymbols.Refresh,
                                contentDescription = if (isPageLoading) "Stop Loading" else "Reload Page",
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = swipeContentShiftPx
                        }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = SearchPillSymbols.Search,
                        contentDescription = "Search",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            if (newValue.text != inputText) {
                                onInputTextChange(newValue.text)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                isSearchFocused = state.isFocused
                                onFocusChanged(state.isFocused)
                                if (state.isFocused) {
                                    if (!hasGainedFocus && isWebMode) {
                                        textFieldValue = textFieldValue.copy(
                                            selection = TextRange(0, textFieldValue.text.length)
                                        )
                                    }
                                    hasGainedFocus = true
                                } else if (hasGainedFocus && isWebMode) {
                                    isEditingUrl = false
                                }
                            },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = contentColor
                        ),
                        cursorBrush = SolidColor(contentColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { submitAction() },
                            onGo = { submitAction() },
                            onDone = { submitAction() }
                        ),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (textFieldValue.text.isEmpty()) {
                                    Text(
                                        text = "Search....",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = contentColor.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = !isWebMode && isSearchFocused,
                        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)),
                        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    focusRequester.requestFocus()
                                    visualIncognito = !visualIncognito
                                }
                                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            AnimatedVisibility(
                                visible = visualIncognito,
                                enter = fadeIn(tween(220)) + expandHorizontally(expandFrom = Alignment.End),
                                exit = fadeOut(tween(160)) + shrinkHorizontally(shrinkTowards = Alignment.End)
                            ) {
                                Text(
                                    text = "incognito",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = contentColor.copy(alpha = 0.72f),
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.2.sp
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

                            IncognitoEyesAnimatedIcon(
                                isClosed = visualIncognito,
                                tint = contentColor
                            )
                        }
                    }
                }
            }
        }

        // Find-in-Page Interior (crossfades in smoothly as the pill expands bilaterally)
        AnimatedVisibility(
            visible = isFindInPage,
            enter = fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 65)) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(durationMillis = 220, delayMillis = 55, easing = FastOutSlowInEasing)
                    ),
            exit = fadeOut(animationSpec = tween(durationMillis = 95)) +
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(durationMillis = 95)
                    ),
            modifier = Modifier.fillMaxSize()
        ) {
            FindInPagePillContent(
                query = findQuery,
                activeMatchOrdinal = findActiveMatchOrdinal,
                totalMatches = findTotalMatches,
                contentColor = contentColor,
                onQueryChange = onFindQueryChange,
                onFindPrevious = onFindPrevious,
                onFindNext = onFindNext,
                onClose = onCloseFindInPage
            )
        }
    }
}

@Composable
private fun IncognitoEyesAnimatedIcon(
    isClosed: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val eyelidProgress by animateFloatAsState(
        targetValue = if (isClosed) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "eyelidClosingProgress"
    )

    val blinkSquash = 1f - 0.22f * sin(eyelidProgress * Math.PI).toFloat()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(22.dp)
            .graphicsLayer {
                scaleY = blinkSquash
                scaleX = 1f + (1f - blinkSquash) * 0.25f
                translationY = eyelidProgress * 1.2.dp.toPx()
            }
    ) {
        if (eyelidProgress < 1f) {
            Icon(
                painter = painterResource(id = R.drawable.ic_eyes),
                contentDescription = if (isClosed) null else "Enter Incognito",
                tint = tint,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val lidTopY = size.height * eyelidProgress
                        clipRect(
                            left = 0f,
                            top = lidTopY,
                            right = size.width,
                            bottom = size.height
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
            )
        }

        if (eyelidProgress > 0f) {
            Icon(
                painter = painterResource(id = R.drawable.ic_eyes_closed),
                contentDescription = if (isClosed) "Exit Incognito" else null,
                tint = tint,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val lidBottomY = size.height * eyelidProgress
                        clipRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = lidBottomY
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
            )
        }
    }
}

@SuppressLint("UseKtx")
internal fun extractDisplayDomain(url: String): String {
    return runCatching {
        val host = url.toUri().host
        if (!host.isNullOrBlank()) {
            host.removePrefix("www.")
        } else {
            url
        }
    }.getOrDefault(url)
}