package com.autodict.data.integration

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleTasksRequestBuilderTest {

    @Test
    fun taskJson_med_berre_tittel() {
        val json = GoogleTasksRequestBuilder.taskJson("Ringe tannlegen")
        assertEquals("{\"title\":\"Ringe tannlegen\"}", json)
    }

    @Test
    fun taskJson_med_notat_og_forfallsdato() {
        val json = GoogleTasksRequestBuilder.taskJson(
            title = "Ringe tannlegen",
            notes = "Frå dagboka",
            dueIso = "2026-06-03T12:00:00Z",
        )
        assertEquals(
            "{\"title\":\"Ringe tannlegen\",\"notes\":\"Frå dagboka\",\"due\":\"2026-06-03T12:00:00Z\"}",
            json,
        )
    }

    @Test
    fun taskJson_ignorerer_tomt_notat() {
        val json = GoogleTasksRequestBuilder.taskJson("Ringe tannlegen", notes = "   ")
        assertEquals("{\"title\":\"Ringe tannlegen\"}", json)
    }

    @Test
    fun taskJson_escaper_sitat_og_backslash_og_nyline() {
        val json = GoogleTasksRequestBuilder.taskJson("Sei \"hei\" \\ på ny\nlinje")
        assertEquals("{\"title\":\"Sei \\\"hei\\\" \\\\ på ny\\nlinje\"}", json)
    }
}
