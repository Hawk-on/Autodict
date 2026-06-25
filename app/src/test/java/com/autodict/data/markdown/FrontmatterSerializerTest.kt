package com.autodict.data.markdown

import com.autodict.domain.model.DiaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontmatterSerializerTest {

    private val entry = DiaryEntry(
        id = "2026-06-02T14-03-12-blabaertur",
        created = "2026-06-02T14:03:12+02:00",
        updated = "2026-06-02T14:05:00+02:00",
        title = "Blåbærtur: på fjellet",
        audio = "2026-06-02T14-03-12-blabaertur.wav",
        durationSeconds = 42,
        language = "nn",
        transcribed = true,
        model = "nb-whisper-small-q5_0",
        tags = listOf("tur", "fjell"),
        body = "Fyrste linje.\n\nAndre avsnitt med æ, ø og å.",
    )

    @Test
    fun roundtrip_bevarar_alle_felt() {
        val parsed = FrontmatterSerializer.parse(FrontmatterSerializer.serialize(entry))
        assertEquals(entry, parsed)
    }

    @Test
    fun roundtrip_minimal_oppforing() {
        val minimal = DiaryEntry(
            id = "2026-01-01T00-00-00",
            created = "2026-01-01T00:00:00+01:00",
            title = "Utan tittel",
            body = "",
        )
        val parsed = FrontmatterSerializer.parse(FrontmatterSerializer.serialize(minimal))
        assertEquals(minimal, parsed)
    }

    @Test
    fun tittel_med_kolon_blir_sitert() {
        val md = FrontmatterSerializer.serialize(entry)
        assertTrue(md.contains("title: \"Blåbærtur: på fjellet\""))
    }

    @Test
    fun parse_handterer_crlf() {
        val md = FrontmatterSerializer.serialize(entry).replace("\n", "\r\n")
        assertEquals(entry, FrontmatterSerializer.parse(md))
    }
}
