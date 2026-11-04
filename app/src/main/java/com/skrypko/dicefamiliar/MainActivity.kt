package com.skrypko.dicefamiliar

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import com.skrypko.dicefamiliar.data.lan.LanNavigation
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.ui.navigation.AppNavHost
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import com.skrypko.dicefamiliar.ui.theme.isDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: AppSettingsRepository

    @Inject
    lateinit var lanNavigation: LanNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLanIntent(intent)
        enableEdgeToEdge()
        setContent {
            // Wait for the saved theme (a few ms) so the app doesn't flash the wrong colours.
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent
            val dark = isDarkTheme(current.themeId, current.darkMode)
            // Status and navigation bar icons follow the theme, not the system dark mode.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            DiceFamiliarTheme(themeId = current.themeId, darkMode = current.darkMode, dynamicColor = current.dynamicColor) {
                AppNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLanIntent(intent)
    }

    /** A tap on a roll-request notification opens the Dice tab with the request. */
    private fun handleLanIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(LanNavigation.EXTRA_OPEN_REQUEST, false) == true) {
            intent.removeExtra(LanNavigation.EXTRA_OPEN_REQUEST)
            lanNavigation.requestOpened()
        }
    }
}
