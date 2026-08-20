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

class TargetLanguageTest {

    @Test
    fun resolvesCodesToLanguage() {
        assertEquals(TargetLanguage.NYNORSK, TargetLanguage.fromCode("nn"))
        assertEquals(TargetLanguage.BOKMAAL, TargetLanguage.fromCode("no"))
        // Eldre oppføringar kan ha «nb» i frontmatter.
        assertEquals(TargetLanguage.BOKMAAL, TargetLanguage.fromCode("nb"))
    }

    @Test
    fun fallsBackToDefaultOnUnknownOrMissing() {
        assertEquals(TargetLanguage.DEFAULT, TargetLanguage.fromCode(null))
        assertEquals(TargetLanguage.DEFAULT, TargetLanguage.fromCode(""))
        assertEquals(TargetLanguage.DEFAULT, TargetLanguage.fromCode("klingon"))
    }

    @Test
    fun otherFlipsBetweenTargets() {
        assertEquals(TargetLanguage.NYNORSK, TargetLanguage.BOKMAAL.other)
        assertEquals(TargetLanguage.BOKMAAL, TargetLanguage.NYNORSK.other)
    }

    @Test
    fun codesMapToWhisperLanguages() {
        // Målforma må vere ein kode whisper faktisk kjenner igjen.
        TargetLanguage.entries.forEach { language ->
            assertEquals(language.code, WhisperLanguage.forEntry(language.code))
        }
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
