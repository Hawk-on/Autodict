package com.autodict.ui.quickrecord

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autodict.data.storage.AppSettings
import com.autodict.ui.theme.AutodictTheme
import com.autodict.ui.theme.ThemeMode
import kotlinx.coroutines.flow.map

/**
 * Hurtigopptak, tilgjengeleg **frå låseskjermen**.
 *
 * Vi kan ikkje fange av/på-knappen – Android reserverer han for systemet – men Samsung og
 * fleire andre lèt deg mappe dobbelttrykk på sideknappen til «opne app». Peikar du den hit,
 * kjem du rett i opptak utan å låse opp. Same skjerm blir brukt av hurtiginnstillings-flisa.
 *
 * Skjermen viser berre teljar og kontrollar, aldri innhaldet i dagboka: han er synleg utan
 * autentisering, så alt som står her kan lesast av kven som helst som held telefonen.
 *
 * Det ferdige opptaket blir liggjande som utkast ([com.autodict.data.audio.PendingDraftStore])
 * og dukkar opp neste gong appen blir opna – vi opnar ikkje redigering over låseskjermen.
 */
class QuickRecordActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val themeMode = AppSettings(applicationContext).themeMode.map { ThemeMode.fromId(it) }

        setContent {
            val mode by themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DEFAULT)
            AutodictTheme(themeMode = mode) {
                QuickRecordScreen(onClose = { finish() })
            }
        }
    }

    /**
     * Vis over låseskjermen og slå på skjermen. `setShowWhenLocked` gir oss lov til å teikne
     * utan opplåsing; utan `setTurnScreenOn` ville skjermen bli verande svart om telefonen låg
     * i lomma.
     */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }
}
