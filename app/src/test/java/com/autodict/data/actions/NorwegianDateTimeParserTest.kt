package com.autodict.data.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class NorwegianDateTimeParserTest {

    // Onsdag 2026-06-03T10:00:00+02:00
    private val created = "2026-06-03T10:00:00+02:00"

    @Test
    fun parse_i_dag_utan_tid_brukar_middag() {
        val result = NorwegianDateTimeParser.parse("Skal gjere det i dag", created)
        assertNotNull(result)
        val parsed = OffsetDateTime.parse(result)
        assertEquals(2026, parsed.year)
        assertEquals(6, parsed.monthValue)
        assertEquals(3, parsed.dayOfMonth)
        assertEquals(12, parsed.hour)
        assertEquals(0, parsed.minute)
    }

    @Test
    fun parse_i_morgon_med_klokkeslett() {
        val result = NorwegianDateTimeParser.parse("Møte i morgon kl. 14:30", created)
        assertNotNull(result)
        val parsed = OffsetDateTime.parse(result)
        assertEquals(4, parsed.dayOfMonth)
        assertEquals(14, parsed.hour)
        assertEquals(30, parsed.minute)
    }

    @Test
    fun parse_imorgen_bokmaal_variant() {
        val result = NorwegianDateTimeParser.parse("Avtale imorgen klokka 9", created)
        assertNotNull(result)
        val parsed = OffsetDateTime.parse(result)
        assertEquals(4, parsed.dayOfMonth)
        assertEquals(9, parsed.hour)
        assertEquals(0, parsed.minute)
    }

    @Test
    fun parse_i_overmorgon() {
        val result = NorwegianDateTimeParser.parse("Ring i overmorgon", created)
        assertNotNull(result)
        val parsed = OffsetDateTime.parse(result)
        assertEquals(5, parsed.dayOfMonth)
    }

    @Test
    fun parse_neste_vekedag() {
        // created er onsdag; neste fredag = 5. juni
        val result = NorwegianDateTimeParser.parse("Møte neste fredag kl. 11", created)
        assertNotNull(result)
        val parsed = OffsetDateTime.parse(result)
        assertEquals(5, parsed.dayOfMonth)
        assertEquals(11, parsed.hour)
    }

    @Test
    fun parse_pa_mandag_brukar_neste_mandag() {
        // created er onsdag 3.; neste måndag = 8. juni
        val result = NorwegianDateTimeParser.parse("Time hjå legen på måndag", created)
        assertNotNull(result)
        val parsed = OffsetDateTime.parse(result)
        assertEquals(8, parsed.dayOfMonth)
    }

    @Test
    fun parse_klokke_med_punktum_som_skilje() {
        val result = NorwegianDateTimeParser.parse("Møte i dag kl 15.45", created)
        assertNotNull(result)
        val parsed = OffsetDateTime.parse(result)
        assertEquals(15, parsed.hour)
        assertEquals(45, parsed.minute)
    }

    @Test
    fun parse_ugyldig_time_faller_tilbake_til_middag() {
        val result = NorwegianDateTimeParser.parse("Møte i dag kl. 25:00", created)
        assertNotNull(result)
        val parsed = OffsetDateTime.parse(result)
        assertEquals(12, parsed.hour)
        assertEquals(0, parsed.minute)
    }

    @Test
    fun parse_utan_datoord_returnerer_null() {
        assertNull(NorwegianDateTimeParser.parse("Berre ein vanleg setning utan tid", created))
    }

    @Test
    fun parse_malforma_createdIso_brukar_lokal_dato_som_base() {
        // Skal ikkje kaste; kan framleis finne relativ dag frå LocalDate.now()
        val result = NorwegianDateTimeParser.parse("i morgon kl. 10", "ikkje-ein-dato")
        assertNotNull(result)
        // Resultatet er anten ISO offset-streng eller LocalDateTime-streng
        assertTrue(result!!.isNotBlank())
    }

    @Test
    fun parse_beheld_offset_fra_created() {
        val result = NorwegianDateTimeParser.parse("i dag kl. 16", created)
        assertNotNull(result)
        val parsed = OffsetDateTime.parse(result)
        assertEquals(ZoneOffset.ofHours(2), parsed.offset)
    }

    @Test
    fun parse_idag_sammenskrive() {
        val result = NorwegianDateTimeParser.parse("Gjer det idag", created)
        assertNotNull(result)
        assertEquals(3, OffsetDateTime.parse(result).dayOfMonth)
    }
}
