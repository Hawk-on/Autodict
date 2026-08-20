package com.autodict

import android.app.UiModeManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autodict.data.storage.AppSettings
import com.autodict.ui.navigation.AutodictNavHost
import com.autodict.ui.theme.AutodictTheme
import com.autodict.ui.theme.ThemeMode
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val themeMode = AppSettings(applicationContext).themeMode.map { ThemeMode.fromId(it) }

        setContent {
            // Startar på systemvalet så første frame ikkje blinkar før DataStore har svart.
            val mode by themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DEFAULT)

            LaunchedEffect(mode) { applyNightMode(mode) }

            AutodictTheme(themeMode = mode) {
                AutodictNavHost()
            }
        }
    }

    /**
     * Fortel systemet kva temaet vårt er, så res-kvalifikatoren `values-night` følgjer
     * appvalet og ikkje berre systeminnstillinga. Utan dette ville windowBackground vere lys
     * ved oppstart for ein som har vald Mørkt i appen medan telefonen står i lyst tema –
     * altså eit kvitt blink kvar gong appen blir opna. Krev API 31.
     */
    private fun applyNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = getSystemService(UiModeManager::class.java) ?: return
        uiModeManager.setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }
}
