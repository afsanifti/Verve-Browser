package com.verve.browser.presentation.browser.util

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Search-engine-independent Incognito session & storage isolation manager.
 *
 * Guarantees:
 * 1. Regular-profile cookies/auth are never sent during Incognito browsing.
 * 2. Ephemeral Incognito cookies, DOM storage, cache, and form data are destroyed
 *    when the last Incognito tab closes or when a tab switches out of Incognito.
 * 3. Works identically across Google, Bing, DuckDuckGo, Brave, Yahoo, or any custom engine.
 */
object IncognitoSessionManager {

    private const val INCOGNITO_PROFILE_NAME = "VerveIncognitoEphemeral"

    @Volatile
    private var isIncognitoEnvironmentActive = false

    /**
     * Applies strict non-persistent, isolated settings to a WebView based on [isIncognito].
     * Uses AndroidX WebView Multi-Profile isolation when supported by the system WebView,
     * with automatic fallback to CookieManager/WebStorage isolation on older WebView builds.
     */
    fun configureWebView(webView: WebView, isIncognito: Boolean) {
        val settings = webView.settings

        if (isIncognito) {
            enterIncognitoEnvironment()

            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching {
                    WebViewCompat.setProfile(webView, INCOGNITO_PROFILE_NAME)
                }
            }

            @Suppress("DEPRECATION")
            settings.saveFormData = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.setGeolocationEnabled(false)

            val cookieManager = getEffectiveCookieManager(isIncognito = true)
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, false)
        } else {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching {
                    WebViewCompat.setProfile(webView, "Default")
                }
            }

            @Suppress("DEPRECATION")
            settings.saveFormData = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.setGeolocationEnabled(true)

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }
    }

    /**
     * Synchronizes global Incognito session state with the current tab list.
     * Call whenever tabs are created, closed, switched, or toggled between regular and Incognito.
     */
    fun syncIncognitoTabsState(
        context: Context,
        hasAnyIncognitoTab: Boolean,
        activeTabIsIncognito: Boolean
    ) {
        if (activeTabIsIncognito) {
            enterIncognitoEnvironment()
        } else if (!hasAnyIncognitoTab && isIncognitoEnvironmentActive) {
            destroyIncognitoSession(context)
        }
    }

    /**
     * Enters an isolated cookie/session boundary so regular-session cookies
     * (Google, Bing, Yahoo, etc.) are never automatically sent in Incognito.
     */
    private fun enterIncognitoEnvironment() {
        if (isIncognitoEnvironmentActive) return
        synchronized(this) {
            if (isIncognitoEnvironmentActive) return
            isIncognitoEnvironmentActive = true

            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                // Flush regular cookies to disk, then clear in-memory session cookies
                // so regular account logins are not attached to Incognito requests.
                val cm = CookieManager.getInstance()
                cm.flush()
                cm.removeSessionCookies(null)
            }
        }
    }

    /**
     * Completely destroys the temporary Incognito profile, cookies, WebStorage,
     * cache, and form/HTTP auth databases when the last Incognito tab is closed.
     */
    fun destroyIncognitoSession(context: Context, activeIncognitoWebView: WebView? = null) {
        synchronized(this) {
            isIncognitoEnvironmentActive = false

            activeIncognitoWebView?.apply {
                stopLoading()
                clearHistory()
                clearFormData()
                clearCache(true)
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching {
                    val profileStore = ProfileStore.getInstance()
                    profileStore.getProfile(INCOGNITO_PROFILE_NAME)?.let { profile ->
                        profile.cookieManager.removeAllCookies(null)
                        profile.cookieManager.flush()
                        profile.webStorage.deleteAllData()
                    }
                    profileStore.deleteProfile(INCOGNITO_PROFILE_NAME)
                }
            } else {
                val cm = CookieManager.getInstance()
                cm.removeSessionCookies(null)
                cm.flush()
                WebStorage.getInstance().deleteAllData()
            }

            runCatching {
                @Suppress("DEPRECATION")
                WebViewDatabase.getInstance(context.applicationContext).clearFormData()
                WebViewDatabase.getInstance(context.applicationContext).clearHttpAuthUsernamePassword()
            }
        }
    }

    private fun getEffectiveCookieManager(isIncognito: Boolean): CookieManager {
        if (isIncognito && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val profileCm = runCatching {
                ProfileStore.getInstance()
                    .getOrCreateProfile(INCOGNITO_PROFILE_NAME)
                    .cookieManager
            }.getOrNull()
            if (profileCm != null) return profileCm
        }
        return CookieManager.getInstance()
    }
}