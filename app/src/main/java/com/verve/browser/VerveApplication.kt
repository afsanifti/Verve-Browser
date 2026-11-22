package com.verve.browser

import android.app.Application
import com.verve.browser.core.adblock.NativeAdBlocker
import com.verve.browser.data.adblock.AdBlockUpdateWorker
import com.verve.browser.data.local.TabSessionStorage
import com.verve.browser.data.repository.AdBlockRepositoryImpl
import com.verve.browser.domain.repository.AdBlockRepository
import com.verve.browser.domain.usecase.ShouldBlockUrlUseCase
import com.verve.browser.presentation.theme.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VerveApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var adBlockRepository: AdBlockRepository
        private set

    lateinit var shouldBlockUrlUseCase: ShouldBlockUrlUseCase
        private set

    lateinit var tabSessionStorage: TabSessionStorage
        private set

    override fun onCreate() {
        super.onCreate()
        if (android.os.Process.isIsolated()) {
            return
        }
        ThemePreferences.init(this)

        tabSessionStorage = TabSessionStorage(this)

        val nativeBlocker = NativeAdBlocker()
        adBlockRepository = AdBlockRepositoryImpl(this, nativeBlocker)

        val bootstrapRules = listOf(
            "||doubleclick.net^",
            "||google-analytics.com^",
            "||googlesyndication.com^",
            "||adservice.google.com^",
            "||quantserve.com^"
        )
        adBlockRepository.initializeEngine(bootstrapRules)

        shouldBlockUrlUseCase = ShouldBlockUrlUseCase(adBlockRepository)
        adBlockRepository.prepareAsyncEngineInit()

        appScope.launch(Dispatchers.IO) {
            adBlockRepository.initializeEngineAsync()
        }
    }
}