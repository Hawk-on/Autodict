package com.autodict.data.assistant

/** Resultatet av ei oppreinsking: tittel, reinskriven tekst og forslag til tags. */
data class PolishedEntry(
    val title: String,
    val body: String,
    val tags: List<String>,
)

/** Kvifor eit assistent-kall ikkje gjekk. Skilde slik at UI-et kan seie noko nyttig. */
sealed interface AssistantError {
    /** Ingen modell er sett opp – funksjonen er opt-in og av som standard. */
    data object NotConfigured : AssistantError

    /** Nettverket svarte ikkje. Offline er ein normaltilstand her, ikkje ein feil. */
    data class Offline(val detail: String) : AssistantError

    /** Leverandøren avviste nøkkelen. */
    data object Unauthorized : AssistantError

    /** HTTP-feil frå leverandøren, med det han sa. */
    data class Http(val code: Int, val message: String) : AssistantError

    /** Vi fekk svar, men ikkje noko vi kunne bruke. */
    data class BadResponse(val detail: String) : AssistantError
}

/**
 * Ein modell som kan hjelpe med dagboka. Leverandør-nøytralt med vilje: implementasjonane
 * snakkar anten Anthropic si Messages-form eller den OpenAI-kompatible
 * `/v1/chat/completions`-forma, og den siste dekkjer Ollama, LM Studio, llama.cpp sin
 * server, OpenRouter og fleire.
 *
 * Alt her er **opt-in**. Er ingenting sett opp, svarar implementasjonen
 * [AssistantError.NotConfigured] og appen fungerer som før (CLAUDE.md-prinsipp 4).
 */
interface DiaryAssistant {

    /** True når det finst nok konfigurasjon til å prøve eit kall. */
    val isConfigured: Boolean

    /**
     * Reinskriv transkripsjonen og foreslår tittel og tags.
     *
     * @param language målforma teksten skal halde seg til.
     */
    suspend fun polish(text: String, language: String): Result<PolishedEntry>

    /** Enkelt kall som stadfestar at oppsettet faktisk verkar. */
    suspend fun testConnection(): Result<String>
}

/** Feil pakka som [Throwable] så dei kan bere ein [AssistantError]. */
class AssistantException(val error: AssistantError) : Exception(error.toString())
