package com.verve.browser.presentation.browser.components.searchpill.pageactions

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Arrow_back
import com.composables.icons.materialsymbols.rounded.Article
import com.composables.icons.materialsymbols.rounded.Bookmark
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Desktop_windows
import com.composables.icons.materialsymbols.rounded.Download
import com.composables.icons.materialsymbols.rounded.Find_in_page
import com.composables.icons.materialsymbols.rounded.Link
import com.composables.icons.materialsymbols.rounded.Lock_open
import com.composables.icons.materialsymbols.rounded.Public
import com.composables.icons.materialsymbols.rounded.Refresh
import com.composables.icons.materialsymbols.rounded.Remove
import com.composables.icons.materialsymbols.rounded.Settings
import com.composables.icons.materialsymbols.rounded.Share
import com.composables.icons.materialsymbols.rounded.Shield
import com.composables.icons.materialsymbols.rounded.Swap_horiz
import com.composables.icons.materialsymbols.rounded.Zoom_in
import com.composables.icons.materialsymbols.roundedfilled.Article as ArticleFilled
import com.composables.icons.materialsymbols.roundedfilled.Bookmark as BookmarkFilled
import com.composables.icons.materialsymbols.roundedfilled.Cookie as CookieFilled
import com.composables.icons.materialsymbols.roundedfilled.Desktop_windows as DesktopWindowsFilled
import com.composables.icons.materialsymbols.roundedfilled.Lock as LockFilled
import com.composables.icons.materialsymbols.roundedfilled.Shield as ShieldFilled

internal object PageActionsSymbols {
    val Add: ImageVector get() = MaterialSymbols.Rounded.Add
    val Remove: ImageVector get() = MaterialSymbols.Rounded.Remove
    val Link: ImageVector get() = MaterialSymbols.Rounded.Link
    val Check: ImageVector get() = MaterialSymbols.Rounded.Check
    val Refresh: ImageVector get() = MaterialSymbols.Rounded.Refresh
    val Close: ImageVector get() = MaterialSymbols.Rounded.Close
    val Share: ImageVector get() = MaterialSymbols.Rounded.Share
    val ChevronRight: ImageVector get() = MaterialSymbols.Rounded.Chevron_right

    val SwapHoriz: ImageVector get() = MaterialSymbols.Rounded.Swap_horiz
    val Settings: ImageVector get() = MaterialSymbols.Rounded.Settings
    val FindInPage: ImageVector get() = MaterialSymbols.Rounded.Find_in_page
    val Download: ImageVector get() = MaterialSymbols.Rounded.Download
    val ZoomIn: ImageVector get() = MaterialSymbols.Rounded.Zoom_in

    // Unfilled / Filled toggle pairs
    val Shield: ImageVector get() = MaterialSymbols.Rounded.Shield
    val ShieldFilled: ImageVector get() = MaterialSymbols.RoundedFilled.ShieldFilled

    val Bookmark: ImageVector get() = MaterialSymbols.Rounded.Bookmark
    val BookmarkFilled: ImageVector get() = MaterialSymbols.RoundedFilled.BookmarkFilled

    val DesktopWindows: ImageVector get() = MaterialSymbols.Rounded.Desktop_windows
    val DesktopWindowsFilled: ImageVector get() = MaterialSymbols.RoundedFilled.DesktopWindowsFilled

    val Article: ImageVector get() = MaterialSymbols.Rounded.Article
    val ArticleFilled: ImageVector get() = MaterialSymbols.RoundedFilled.ArticleFilled

    // Site Controls (Filled Lock & Cookie match Chrome's sheet)
    val Public: ImageVector get() = MaterialSymbols.Rounded.Public
    val Lock: ImageVector get() = MaterialSymbols.RoundedFilled.LockFilled
    val LockOpen: ImageVector get() = MaterialSymbols.Rounded.Lock_open
    val Cookie: ImageVector get() = MaterialSymbols.RoundedFilled.CookieFilled
    val ArrowBack: ImageVector get() = MaterialSymbols.Rounded.Arrow_back
    val Delete: ImageVector get() = MaterialSymbols.Rounded.Delete
}