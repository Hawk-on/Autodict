package com.autodict.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Løyva eit opptak treng.
 *
 * `POST_NOTIFICATIONS` er med fordi opptaket køyrer i ei foreground service: utan løyvet blir
 * varselet skjult på API 33+, og då har brukaren ingen måte å sjå at appen tek opp – eller å
 * stoppe det utan å opne appen. Tenesta køyrer likevel, så løyvet er ønskjeleg, ikkje påkravd.
 */
object RecordingPermissions {

    val required: Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    /** Berre mikrofonen er avgjerande – utan han kan vi ikkje ta opp i det heile. */
    fun canRecord(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
