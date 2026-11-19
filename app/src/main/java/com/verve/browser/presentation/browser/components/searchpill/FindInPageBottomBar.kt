package com.verve.browser.presentation.browser.components.searchpill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FindInPagePillContent(
    query: String,
    activeMatchOrdinal: Int,
    totalMatches: Int,
    contentColor: Color,
    onQueryChange: (String) -> Unit,
    onFindPrevious: () -> Unit,
    onFindNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hasMatches = totalMatches > 0

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = contentColor
            ),
            cursorBrush = SolidColor(contentColor),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (hasMatches) {
                        onFindNext()
                    }
                    keyboardController?.hide()
                }
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Find in page",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = contentColor.copy(alpha = 0.6f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (query.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            val counterText = if (hasMatches) {
                "${activeMatchOrdinal + 1}/$totalMatches"
            } else {
                "0/0"
            }
            Text(
                text = counterText,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = contentColor.copy(alpha = if (hasMatches) 0.78f else 0.45f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(contentColor.copy(alpha = 0.24f))
        )

        Spacer(modifier = Modifier.width(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            FindPillActionButton(
                icon = Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Previous match",
                enabled = hasMatches,
                tint = contentColor,
                onClick = onFindPrevious
            )

            FindPillActionButton(
                icon = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Next match",
                enabled = hasMatches,
                tint = contentColor,
                onClick = onFindNext
            )

            FindPillActionButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Close find in page",
                enabled = true,
                tint = contentColor,
                onClick = {
                    keyboardController?.hide()
                    onClose()
                }
            )
        }
    }
}

@Composable
private fun FindPillActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (enabled) 0.9f else 0.36f),
            modifier = Modifier.size(22.dp)
        )
    }
}