package com.verve.browser

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.verve.browser.presentation.browser.BrowserScreen
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.theme.VerveBrowserTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as? VerveApplication
        if (app == null) {
            Log.e("MainActivity", "Application context is not VerveApplication. Check AndroidManifest.xml!")
            finish()
            return
        }

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BrowserViewModel(app.shouldBlockUrlUseCase) as T
            }
        }

        val viewModel = ViewModelProvider(this, viewModelFactory)[BrowserViewModel::class.java]

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            VerveBrowserTheme(siteSeedColor = uiState.siteSeedColor) {
                Surface {
                    BrowserScreen(viewModel = viewModel)
                }
            }
        }
    }
}