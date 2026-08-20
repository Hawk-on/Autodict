package com.autodict.data.integration

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.ZonedDateTime

/**
 * Hjelpeklasse for å opprette ein kalender-intent ferdig utfylt med info.
 */
object CalendarIntentLauncher {

    fun launch(context: Context, title: String, timeIso: String?, body: String) {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, "Frå Autodict:\n\n$body")

        if (timeIso != null) {
            try {
                val zdt = ZonedDateTime.parse(timeIso)
                val millis = zdt.toInstant().toEpochMilli()
                intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, millis + 60 * 60 * 1000) // Standard 1 time
            } catch (e: Exception) {
                // Ignore parse errors, just don't pre-fill time
            }
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
