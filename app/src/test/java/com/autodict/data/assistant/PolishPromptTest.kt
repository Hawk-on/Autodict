package com.autodict.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsaren er den delen som møter verkelege modellsvar, og små lokale modellar held ikkje
 * formatet perfekt. Testane her er difor mest om kva som skjer når svaret er litt feil.
 */
class PolishPromptTest {

    @Test
    fun `les tittel tags og tekst frå eit velforma svar`() {
        val result = PolishPrompt.parse(
            """
            TITTEL: Morgontur langs vatnet
            TAGS: natur, morgon
            ---
            Tåka låg heilt nede på vatnet i dag.

            Det var ikkje ein lyd å høyre.
            """.trimIndent(),
        )

        assertEquals("Morgontur langs vatnet", result?.title)
        assertEquals(listOf("natur", "morgon"), result?.tags)
        assertTrue(result?.body?.startsWith("Tåka låg") == true)
        assertTrue(result?.body?.endsWith("å høyre.") == true)
    }

    @Test
    fun `utan skiljelinje blir heile svaret tekst i staden for å gå tapt`() {
        val result = PolishPrompt.parse("Eg gjekk ein tur i dag. Det var fint vêr.")

        assertEquals("", result?.title)
        assertEquals("Eg gjekk ein tur i dag. Det var fint vêr.", result?.body)
    }

    @Test
    fun `svar pakka i kodegjerde blir pakka ut`() {
        val result = PolishPrompt.parse(
            """
            ```
            TITTEL: Eit notat
            ---
            Innhaldet her.
            ```
            """.trimIndent(),
        )

        assertEquals("Eit notat", result?.title)
        assertEquals("Innhaldet her.", result?.body)
    }

    @Test
    fun `tomme tags gir tom liste, ikkje ei liste med tomme strengar`() {
        val result = PolishPrompt.parse(
            """
            TITTEL: Utan stikkord
            TAGS:
            ---
            Tekst.
            """.trimIndent(),
        )

        assertEquals(emptyList<String>(), result?.tags)
    }

    @Test
    fun `ei setning som byrjar med TAGS etter skiljelinja blir verande tekst`() {
        val result = PolishPrompt.parse(
            """
            TITTEL: Om merkelappar
            ---
            TAGS: dette er noko eg faktisk sa høgt.
            """.trimIndent(),
        )

        assertEquals(emptyList<String>(), result?.tags)
        assertEquals("TAGS: dette er noko eg faktisk sa høgt.", result?.body)
    }

    @Test
    fun `tomt svar gir null`() {
        assertNull(PolishPrompt.parse(""))
        assertNull(PolishPrompt.parse("   \n  "))
    }

    @Test
    fun `svar med berre hovudlinjer og ingen tekst gir null`() {
        assertNull(
            PolishPrompt.parse(
                """
                TITTEL: Berre ein tittel
                ---
                """.trimIndent(),
            ),
        )
    }
}
