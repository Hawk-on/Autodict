package com.autodict.data.assistant

/**
 * Trådformatet ein leverandør snakkar. To former dekkjer nesten alt som finst:
 * Anthropic har si eiga, og alle andre har kopiert OpenAI si.
 */
enum class AssistantProtocol { ANTHROPIC, OPENAI_COMPATIBLE }

/**
 * Korleis appen autentiserer seg. Abstrahert som eigen type fordi leverandørane skil lag
 * her: Ollama på eige nett vil ha ingenting, Claude vil ha ein nøkkel, og OpenRouter har
 * ekte OAuth med PKCE. Då slepp vi å byggje om når OAuth kjem.
 */
sealed interface AssistantCredential {
    /** Ingen autentisering – lokale modellar på eige nett. */
    data object None : AssistantCredential

    data class ApiKey(val key: String) : AssistantCredential

    /**
     * OAuth. Ikkje i bruk enno; ligg her for at resten av koden skal vere forma rett.
     *
     * Merk at OAuth ikkje fjernar behovet for trygg lagring – ein mobilapp er ein public
     * client, så det må vere PKCE, og refresh-tokenet hamnar framleis på eininga.
     */
    data class OAuth(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long,
    ) : AssistantCredential
}

/**
 * Ferdigoppsett brukaren kan velje i staden for å fylle ut alt sjølv. Base-URL og
 * standardmodell er berre utgangspunkt – alt kan overstyrast.
 */
enum class AssistantPreset(
    val displayName: String,
    val protocol: AssistantProtocol,
    val baseUrl: String,
    val defaultModel: String,
    val needsKey: Boolean,
    val hint: String,
) {
    CLAUDE(
        displayName = "Claude",
        protocol = AssistantProtocol.ANTHROPIC,
        baseUrl = "https://api.anthropic.com",
        defaultModel = "claude-opus-5",
        needsKey = true,
        hint = "Nøkkel frå console.anthropic.com. Teksten blir send til Anthropic.",
    ),
    OLLAMA(
        displayName = "Ollama",
        protocol = AssistantProtocol.OPENAI_COMPATIBLE,
        baseUrl = "http://192.168.1.10:11434",
        defaultModel = "llama3.1",
        needsKey = false,
        hint = "Køyrer på di eiga maskin. Teksten forlèt aldri nettet ditt.",
    ),
    LM_STUDIO(
        displayName = "LM Studio",
        protocol = AssistantProtocol.OPENAI_COMPATIBLE,
        baseUrl = "http://192.168.1.10:1234",
        defaultModel = "local-model",
        needsKey = false,
        hint = "Køyrer på di eiga maskin. Teksten forlèt aldri nettet ditt.",
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI-kompatibel",
        protocol = AssistantProtocol.OPENAI_COMPATIBLE,
        baseUrl = "",
        defaultModel = "",
        needsKey = true,
        hint = "Alt som snakkar /v1/chat/completions – OpenRouter, Groq, vLLM, OpenAI.",
    ),
    ;

    companion object {
        val DEFAULT = CLAUDE

        fun fromId(id: String?): AssistantPreset =
            entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

/**
 * Heile oppsettet for assistenten. [enabled] er hovudbrytaren – står han av, blir det
 * aldri sendt noko, uansett kva resten inneheld.
 */
data class AssistantConfig(
    val enabled: Boolean = false,
    val preset: AssistantPreset = AssistantPreset.DEFAULT,
    val baseUrl: String = AssistantPreset.DEFAULT.baseUrl,
    val model: String = AssistantPreset.DEFAULT.defaultModel,
    val credential: AssistantCredential = AssistantCredential.None,
) {
    val protocol: AssistantProtocol get() = preset.protocol

    /**
     * Nok på plass til at eit kall er meiningsfullt. Ein leverandør som ikkje krev nøkkel
     * treng berre adresse og modell.
     */
    val isUsable: Boolean
        get() = enabled &&
            baseUrl.isNotBlank() &&
            model.isNotBlank() &&
            (!preset.needsKey || credential !is AssistantCredential.None)
}
