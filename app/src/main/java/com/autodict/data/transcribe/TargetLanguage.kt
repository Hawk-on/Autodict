package com.autodict.data.transcribe

/**
 * Målforma transkripsjonen siktar mot. NB-Whisper **normaliserer** talen mot målforma –
 * snakkar du dialekt, kjem teksten ut som bokmål eller nynorsk avhengig av valet her.
 *
 * Bokmål har lågast feilrate (~8 % WER mot ~13 % for nynorsk), difor standard.
 */
enum class TargetLanguage(val code: String, val displayName: String) {
    BOKMAAL("no", "Bokmål"),
    NYNORSK("nn", "Nynorsk");

    /** Den andre målforma – brukt til «transkriber om att som …». */
    val other: TargetLanguage
        get() = if (this == BOKMAAL) NYNORSK else BOKMAAL

    companion object {
        val DEFAULT = BOKMAAL

        fun fromCode(code: String?): TargetLanguage =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) }
                ?: if (WhisperLanguage.forEntry(code) == NYNORSK.code) NYNORSK else DEFAULT
    }
}
