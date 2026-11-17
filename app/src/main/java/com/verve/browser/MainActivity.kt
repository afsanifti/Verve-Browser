package com.verve.browser

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.verve.browser.data.update.UpdateCheckWorker
import com.verve.browser.data.update.UpdateNotificationHelper
import com.verve.browser.presentation.browser.BrowserScreen
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.theme.ThemePreferences
import com.verve.browser.presentation.theme.VerveBrowserTheme

class MainActivity : ComponentActivity() {

    private var openUpdateSheetTrigger by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePreferences.init(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        UpdateCheckWorker.scheduleEveryTwoHours(this)
        handleUpdateNotificationIntent(intent)

        val app = application as? VerveApplication
        if (app == null) {
            Log.e("MainActivity", "Application context is not VerveApplication. Check AndroidManifest.xml!")
            finish()
            return
        }

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BrowserViewModel(
                    shouldBlockUrlUseCase = app.shouldBlockUrlUseCase,
                    tabSessionStorage = app.tabSessionStorage
                ) as T
            }
        }

        val viewModel = ViewModelProvider(this, viewModelFactory)[BrowserViewModel::class.java]

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            VerveBrowserTheme(
                siteSeedColor = uiState.siteSeedColor,
                isIncognito = uiState.isIncognito
            ) {
                Surface {
                    BrowserScreen(
                        viewModel = viewModel,
                        openUpdateSheetRequested = openUpdateSheetTrigger,
                        onUpdateSheetRequestHandled = { openUpdateSheetTrigger = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateNotificationIntent(intent)
    }

    private fun handleUpdateNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateNotificationHelper.EXTRA_OPEN_UPDATE_SHEET, false) == true) {
            intent.removeExtra(UpdateNotificationHelper.EXTRA_OPEN_UPDATE_SHEET)
            openUpdateSheetTrigger = true
        }
    }
}