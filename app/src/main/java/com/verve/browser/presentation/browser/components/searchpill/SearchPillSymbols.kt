package com.verve.browser.presentation.browser.components.searchpill

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_right
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Keyboard_arrow_down
import com.composables.icons.materialsymbols.rounded.Keyboard_arrow_up
import com.composables.icons.materialsymbols.rounded.Refresh
import com.composables.icons.materialsymbols.rounded.Search

internal object SearchPillSymbols {
    val Search: ImageVector get() = MaterialSymbols.Rounded.Search
    val Close: ImageVector get() = MaterialSymbols.Rounded.Close
    val Refresh: ImageVector get() = MaterialSymbols.Rounded.Refresh
    val KeyboardArrowUp: ImageVector get() = MaterialSymbols.Rounded.Keyboard_arrow_up
    val KeyboardArrowDown: ImageVector get() = MaterialSymbols.Rounded.Keyboard_arrow_down
    val ArrowRight: ImageVector get() = MaterialSymbols.Rounded.Arrow_right
}