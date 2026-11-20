package com.verve.browser.presentation.browser.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

object DefaultBrowserHelper {

    fun isDefaultBrowser(context: Context): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    }

    fun createRequestDefaultBrowserIntent(context: Context): Intent {
        val roleManager = context.getSystemService(RoleManager::class.java)
        return if (roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
        ) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
        } else {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
    }
}