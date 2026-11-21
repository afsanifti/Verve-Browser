package com.verve.browser.presentation.browser.components.quicktab

import android.content.Context

internal object QuickTabPreferences {
    private const val PREFS_NAME = "verve_quick_tab_prefs"
    private const val KEY_IS_GRID_VIEW = "is_grid_view"

    @Volatile
    private var cachedIsGridView: Boolean? = null

    fun isGridView(context: Context): Boolean {
        cachedIsGridView?.let { return it }
        val stored = runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_IS_GRID_VIEW, false)
        }.getOrDefault(false)
        cachedIsGridView = stored
        return stored
    }

    fun setGridView(context: Context, isGridView: Boolean) {
        cachedIsGridView = isGridView
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_GRID_VIEW, isGridView)
                .apply()
        }
    }
}