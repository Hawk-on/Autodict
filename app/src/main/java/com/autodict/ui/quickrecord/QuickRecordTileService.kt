package com.autodict.ui.quickrecord

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Hurtiginnstillings-flis som startar eit opptak. Dra ned panelet – òg frå låseskjermen –
 * og trykk.
 *
 * Flisa opnar [QuickRecordActivity] i staden for å starte tenesta direkte. Det er ikkje ein
 * omveg: frå Android 14 kan ein mikrofon-teneste **berre** startast medan appen er synleg,
 * så eit direkte kall herifrå ville blitt avvist. Aktiviteten er synleg i det han kjem opp,
 * og då er starten lovleg.
 */
class QuickRecordTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Autodict"
            subtitle = "Ta opp"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, QuickRecordActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34 tok bort Intent-varianten; no må vi levere ein PendingIntent.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
