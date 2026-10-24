package com.xtt.toolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.xtt.toolbox.data.AppContainerProvider
import com.xtt.toolbox.data.settings.ThemeMode
import com.xtt.toolbox.ui.AppRoot
import com.xtt.toolbox.ui.theme.XttToolboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = AppContainerProvider.get(this).appSettingsStore
        setContent {
            val mode by settings.themeMode.collectAsState()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            XttToolboxTheme(darkTheme = darkTheme) {
                AppRoot()
            }
        }
    }
}
