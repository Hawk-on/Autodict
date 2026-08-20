package com.autodict.data.transcribe

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperLanguageTest {

    @Test
    fun mapsNynorskToNn() {
        assertEquals("nn", WhisperLanguage.forEntry("nn"))
        assertEquals("nn", WhisperLanguage.forEntry("Nynorsk"))
    }

    @Test
    fun mapsBokmaalAndDefaultToNo() {
        // Whisper har ingen «nb»-kode – bokmål køyrer som «no».
        assertEquals("no", WhisperLanguage.forEntry("nb"))
        assertEquals("no", WhisperLanguage.forEntry("no"))
        assertEquals("no", WhisperLanguage.forEntry("  NO "))
        assertEquals("no", WhisperLanguage.forEntry(null))
        assertEquals("no", WhisperLanguage.forEntry(""))
    }

    @Test
    fun passesThroughOtherLanguages() {
        assertEquals("en", WhisperLanguage.forEntry("EN"))
    }
}

class TranscriptMergeTest {

    @Test
    fun usesTranscriptWhenBodyIsEmpty() {
        assertEquals("Hei på deg", TranscriptMerge.merge("", " Hei på deg ", alreadyTranscribed = false))
    }

    @Test
    fun appendsBelowManualText() {
        val merged = TranscriptMerge.merge("Manuelt notat", "Transkript", alreadyTranscribed = false)
        assertEquals("Manuelt notat\n\nTranskript", merged)
    }

    @Test
    fun replacesPreviousTranscriptOnRetranscribe() {
        val merged = TranscriptMerge.merge("Gammalt transkript", "Nytt transkript", alreadyTranscribed = true)
        assertEquals("Nytt transkript", merged)
    }

    @Test
    fun keepsBodyWhenTranscriptIsBlank() {
        assertEquals("Manuelt notat", TranscriptMerge.merge("Manuelt notat", "   ", alreadyTranscribed = false))
    }
}
