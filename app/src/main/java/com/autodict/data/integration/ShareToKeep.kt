package com.autodict.data.integration

import android.content.Context
import android.content.Intent

/**
 * Del ei oppføring vidare som eit notat via Android sitt system-delingspanel (t.d. til Google
 * Keep). Heilt offline – ingen nettverkskall, berre eit vanleg `ACTION_SEND`-intent. Sjølve
 * trykket frå brukaren er stadfestinga (kjerneprinsipp 5 i CLAUDE.md); appen sender ingenting
 * utan at brukaren aktivt vel eit mottakar-program i systemdialogen.
 */
object ShareToKeep {

    /** @return true om delingsdialogen vart starta, false om ingen app kan handtere han. */
    fun share(context: Context, title: String, body: String): Boolean {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(sendIntent, "Del til Keep").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Ekstern intent; feiler pent når eininga manglar ei app som kan ta imot (offline-først).
        return runCatching { context.startActivity(chooser) }.isSuccess
    }
}
