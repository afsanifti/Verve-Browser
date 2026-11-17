package com.verve.browser.presentation.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ExpressiveMenuItem(
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean = false,
    val leadingContent: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val bottomContent: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null
)

@Composable
fun SettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
fun ExpressiveSegmentedGroup(
    items: List<ExpressiveMenuItem>,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items.forEachIndexed { index, item ->
            val shape = segmentedItemShape(index = index, totalCount = items.size)
            val containerColor by animateColorAsState(
                targetValue = if (item.selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                label = "segmentedItemContainerColor"
            )
            val titleColor = if (item.selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            val subtitleColor = if (item.selected) {
                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                shape = shape,
                color = containerColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .then(
                        if (item.onClick != null) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClick = item.onClick
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (item.leadingContent != null) {
                            item.leadingContent.invoke()
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = titleColor
                            )
                            if (!item.subtitle.isNullOrBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = subtitleColor,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        if (item.trailingContent != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            item.trailingContent.invoke()
                        }
                    }

                    if (item.bottomContent != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        item.bottomContent.invoke()
                    }
                }
            }
        }
    }
}

private fun segmentedItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    val outer = 22.dp
    val inner = 4.dp
    return when {
        totalCount <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer,
            topEnd = outer,
            bottomStart = inner,
            bottomEnd = inner
        )
        index == totalCount - 1 -> RoundedCornerShape(
            topStart = inner,
            topEnd = inner,
            bottomStart = outer,
            bottomEnd = outer
        )
        else -> RoundedCornerShape(inner)
    }
}