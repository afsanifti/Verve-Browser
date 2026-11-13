package com.verve.browser.presentation.browser.viewmodel

import com.verve.browser.presentation.browser.BrowserUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

internal class ToolbarAndCurtainController(
    private val uiState: MutableStateFlow<BrowserUiState>
) {

    fun setToolbarCollapsed(collapsed: Boolean, isManual: Boolean = false) {
        uiState.update { state ->
            if (state.currentUrl.isEmpty() || state.isSearchFocused) {
                if (!state.isToolbarCollapsed && !state.isToolbarManuallyCollapsed) {
                    state
                } else {
                    state.copy(
                        isToolbarCollapsed = false,
                        isToolbarManuallyCollapsed = false
                    )
                }
            } else if (isManual) {
                state.copy(
                    isToolbarCollapsed = collapsed,
                    isToolbarManuallyCollapsed = collapsed
                )
            } else {
                if (state.isToolbarManuallyCollapsed && !collapsed) {
                    state
                } else if (state.isToolbarCollapsed == collapsed) {
                    state
                } else {
                    state.copy(isToolbarCollapsed = collapsed)
                }
            }
        }
    }

    fun updateBottomCurtainProgress(progress: Float) {
        uiState.update { state ->
            val clamped = if (state.currentUrl.isEmpty()) 0f else progress.coerceIn(0f, 1f)
            val normalized = when {
                clamped < 0.005f -> 0f
                clamped > 0.995f -> 1f
                else -> clamped
            }
            if (abs(state.bottomCurtainProgress - normalized) < 0.002f) {
                state
            } else {
                state.copy(bottomCurtainProgress = normalized)
            }
        }
    }

    fun updateHasFixedBottomBar(hasBar: Boolean) {
        uiState.update { state ->
            val target = if (state.currentUrl.isEmpty()) false else hasBar
            if (state.hasFixedBottomBar == target) {
                state
            } else {
                state.copy(hasFixedBottomBar = target)
            }
        }
    }
}