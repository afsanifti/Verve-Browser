package com.verve.browser.presentation.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Account_circle
import com.composables.icons.materialsymbols.rounded.Arrow_back
import com.composables.icons.materialsymbols.rounded.Public
import com.composables.icons.materialsymbols.rounded.System_update
import com.composables.icons.materialsymbols.roundedfilled.Check_circle as CheckCircleFilled

internal object SettingsSymbols {
    val AccountCircle: ImageVector get() = MaterialSymbols.Rounded.Account_circle
    val ArrowBack: ImageVector get() = MaterialSymbols.Rounded.Arrow_back
    val CheckCircle: ImageVector get() = MaterialSymbols.RoundedFilled.CheckCircleFilled
    val Public: ImageVector get() = MaterialSymbols.Rounded.Public
    val SystemUpdate: ImageVector get() = MaterialSymbols.Rounded.System_update
}