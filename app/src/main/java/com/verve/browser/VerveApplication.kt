package com.verve.browser

import android.app.Application
import com.verve.browser.core.adblock.NativeAdBlocker
import com.verve.browser.data.repository.AdBlockRepositoryImpl
import com.verve.browser.domain.repository.AdBlockRepository
import com.verve.browser.domain.usecase.ShouldBlockUrlUseCase

class VerveApplication : Application() {

    lateinit var adBlockRepository: AdBlockRepository
        private set

    lateinit var shouldBlockUrlUseCase: ShouldBlockUrlUseCase
        private set

    override fun onCreate() {
        super.onCreate()

        val nativeBlocker = NativeAdBlocker()
        adBlockRepository = AdBlockRepositoryImpl(nativeBlocker)

        val initialRules = listOf(
            "||doubleclick.net^",
            "||google-analytics.com^",
            "||googlesyndication.com^",
            "||adservice.google.com^",
            "||quantserve.com^"
        )
        adBlockRepository.initializeEngine(initialRules)

        shouldBlockUrlUseCase = ShouldBlockUrlUseCase(adBlockRepository)
    }
}