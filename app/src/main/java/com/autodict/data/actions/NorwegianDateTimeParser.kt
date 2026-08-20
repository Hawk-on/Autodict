package com.autodict.data.actions

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Enkel parser for norske tidspunktuttrykk.
 * Tar høgde for både bokmål og nynorsk for vanlege uttrykk.
 */
object NorwegianDateTimeParser {

    // Uttrykk for relative dagar
    private val TODAY_WORDS = listOf("i dag", "idag")
    private val TOMORROW_WORDS = listOf("i morgon", "imorgon", "i morgen", "imorgen")
    private val DAY_AFTER_TOMORROW_WORDS = listOf("i overmorgon", "iovermorgon", "i overmorgen", "iovermorgen")

    // Uttrykk for tidspunkt
    private val TIME_REGEX = Regex("(?:klokka|kl)\\.?\\s*(\\d{1,2})(?:[:.](\\d{2}))?", RegexOption.IGNORE_CASE)

    // Dagar i veka (både bokmål og nynorsk)
    private val DAYS_MAP = mapOf(
        "måndag" to DayOfWeek.MONDAY, "mandag" to DayOfWeek.MONDAY,
        "tysdag" to DayOfWeek.TUESDAY, "tirsdag" to DayOfWeek.TUESDAY,
        "onsdag" to DayOfWeek.WEDNESDAY,
        "torsdag" to DayOfWeek.THURSDAY,
        "fredag" to DayOfWeek.FRIDAY,
        "laurdag" to DayOfWeek.SATURDAY, "lørdag" to DayOfWeek.SATURDAY,
        "søndag" to DayOfWeek.SUNDAY
    )

    /**
     * Prøver å parse ein dato/tid frå teksten relativt til opptakstidspunktet.
     * Returnerer ein ISO-8601 formata streng, eller null om han ikkje fann noko fornuftig.
     */
    fun parse(text: String, createdIso: String): String? {
        val lowerText = text.lowercase(Locale.Builder().setLanguage("no").setRegion("NO").build())

        val baseDate = try {
            ZonedDateTime.parse(createdIso).toLocalDate()
        } catch (e: Exception) {
            LocalDate.now()
        }

        var targetDate: LocalDate? = null

        // Sjekk relative dagar
        if (TODAY_WORDS.any { lowerText.contains(it) }) {
            targetDate = baseDate
        } else if (TOMORROW_WORDS.any { lowerText.contains(it) }) {
            targetDate = baseDate.plusDays(1)
        } else if (DAY_AFTER_TOMORROW_WORDS.any { lowerText.contains(it) }) {
            targetDate = baseDate.plusDays(2)
        } else {
            // Sjekk neste [vekedag]
            for ((dayName, dayOfWeek) in DAYS_MAP) {
                if (lowerText.contains("neste $dayName") || lowerText.contains("på $dayName") || lowerText.contains(dayName)) {
                    targetDate = baseDate.with(TemporalAdjusters.next(dayOfWeek))
                    break
                }
            }
        }

        if (targetDate == null) return null

        // Prøv å finn klokkeslett
        val match = TIME_REGEX.find(lowerText)
        var targetTime: LocalTime? = null

        if (match != null) {
            val hour = match.groupValues[1].toIntOrNull()
            val minStr = match.groupValues.getOrNull(2)
            val minute = if (!minStr.isNullOrEmpty()) minStr.toInt() else 0

            if (hour != null && hour in 0..23 && minute in 0..59) {
                targetTime = LocalTime.of(hour, minute)
            }
        }

        val dateTime = if (targetTime != null) {
            LocalDateTime.of(targetDate, targetTime)
        } else {
            // Standardtid viss berre dato er funne
            LocalDateTime.of(targetDate, LocalTime.of(12, 0))
        }

        return try {
            val createdZdt = ZonedDateTime.parse(createdIso)
            ZonedDateTime.of(dateTime, createdZdt.zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } catch (e: Exception) {
            dateTime.toString()
        }
    }
}
