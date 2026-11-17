package com.verve.browser.presentation.browser.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.browser.BrowserTab
import com.verve.browser.presentation.browser.TabStack

@Composable
internal fun TabStackManagementDialog(
    activeTab: BrowserTab,
    allTabs: List<BrowserTab>,
    tabStacks: List<TabStack>,
    onDismiss: () -> Unit,
    onCreateTabStack: (List<String>, String) -> Unit,
    onAddTabToStack: (String, String) -> Unit,
    onRemoveTabFromStack: (String) -> Unit,
    onToggleStackCollapsed: (String) -> Unit
) {
    val ungroupedNormalTabs = remember(allTabs) {
        allTabs.filter { !it.isIncognito && it.stackId == null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Tab Stacks",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (activeTab.stackId != null) {
                    Text(
                        text = "Current tab is in a stack.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { onRemoveTabFromStack(activeTab.id) }) {
                        Text("Remove current tab from stack")
                    }
                } else if (!activeTab.isIncognito && tabStacks.isNotEmpty()) {
                    Text(
                        text = "Add current tab to an existing stack:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    tabStacks.forEach { stack ->
                        TextButton(onClick = { onAddTabToStack(activeTab.id, stack.id) }) {
                            Text("Add to \"${stack.title}\"")
                        }
                    }
                }

                if (ungroupedNormalTabs.size >= 2) {
                    TextButton(
                        onClick = {
                            val title = resolveTabChipLabel(ungroupedNormalTabs.first())
                            onCreateTabStack(ungroupedNormalTabs.map { it.id }, title)
                        }
                    ) {
                        Text("Stack ${ungroupedNormalTabs.size} open tabs")
                    }
                }

                if (tabStacks.isNotEmpty()) {
                    tabStacks.forEach { stack ->
                        val count = allTabs.count { it.stackId == stack.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleStackCollapsed(stack.id) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stack.title} ($count tabs)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (stack.isCollapsed) "Expand" else "Collapse",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (ungroupedNormalTabs.size < 2) {
                    Text(
                        text = "Open at least 2 normal tabs to create a stack. Incognito tabs are never saved or stacked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}