package com.autodict.data.assistant

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Snakkar med ein modell over HTTP – anten Anthropic si Messages-form eller den
 * OpenAI-kompatible `/v1/chat/completions`-forma.
 *
 * Rå [HttpURLConnection] og kotlinx.serialization, som resten av appen
 * ([com.autodict.data.transcribe.ModelDownloader] gjer det same). Eit SDK ville berre kunna
 * betene éin av leverandørane, og ville drege inn avhengnader i ein APK som nettopp er
 * krympa til under 7 MB.
 */
class HttpDiaryAssistant(
    private val config: AssistantConfig,
) : DiaryAssistant {

    private val json = Json { ignoreUnknownKeys = true }

    override val isConfigured: Boolean get() = config.isUsable

    override suspend fun polish(text: String, language: String): Result<PolishedEntry> {
        if (!config.isUsable) return failure(AssistantError.NotConfigured)

        return complete(
            system = PolishPrompt.system(language),
            user = PolishPrompt.user(text),
            maxTokens = 4096,
        ).mapCatching { raw ->
            PolishPrompt.parse(raw)
                ?: throw AssistantException(
                    AssistantError.BadResponse("Modellen svarte utan brukbar tekst."),
                )
        }
    }

    override suspend fun testConnection(): Result<String> {
        if (!config.isUsable) return failure(AssistantError.NotConfigured)

        return complete(
            system = "Du svarar berre med eitt ord.",
            user = "Svar med ordet OK.",
            maxTokens = 16,
        ).map { it.trim().take(40) }
    }

    // --- felles ---

    private suspend fun complete(system: String, user: String, maxTokens: Int): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = when (config.protocol) {
                    AssistantProtocol.ANTHROPIC -> anthropicBody(system, user, maxTokens)
                    AssistantProtocol.OPENAI_COMPATIBLE -> openAiBody(system, user, maxTokens)
                }
                val raw = post(endpoint(), body)
                when (config.protocol) {
                    AssistantProtocol.ANTHROPIC -> readAnthropic(raw)
                    AssistantProtocol.OPENAI_COMPATIBLE -> readOpenAi(raw)
                }
            }
        }

    private fun endpoint(): URL {
        val base = config.baseUrl.trimEnd('/')
        val path = when (config.protocol) {
            AssistantProtocol.ANTHROPIC -> "/v1/messages"
            AssistantProtocol.OPENAI_COMPATIBLE -> "/v1/chat/completions"
        }
        // Peikar brukaren alt på .../v1, skal vi ikkje leggje på v1 ein gong til.
        return URL(if (base.endsWith("/v1")) base.dropLast(3).trimEnd('/') + path else base + path)
    }

    private fun post(url: URL, body: JsonObject): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            // Ein lokal modell på svak maskinvare kan bruke lang tid på eit langt
            // transkript, så lesetimeouten må vere raus.
            readTimeout = 180_000
            setRequestProperty("Content-Type", "application/json")
            applyAuth(this)
        }

        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.w(TAG, "HTTP $code frå ${url.host}: ${detail.take(300)}")
                throw AssistantException(
                    if (code == 401 || code == 403) {
                        AssistantError.Unauthorized
                    } else {
                        AssistantError.Http(code, detail.take(300).ifBlank { conn.responseMessage.orEmpty() })
                    },
                )
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            // Offline er ein normaltilstand for denne appen, ikkje eit krasj.
            Log.i(TAG, "Nådde ikkje ${url.host}: ${e.message}")
            throw AssistantException(AssistantError.Offline(e.message ?: "ingen kontakt"))
        } finally {
            conn.disconnect()
        }
    }

    private fun applyAuth(conn: HttpURLConnection) {
        val token = when (val credential = config.credential) {
            is AssistantCredential.ApiKey -> credential.key
            is AssistantCredential.OAuth -> credential.accessToken
            AssistantCredential.None -> null
        } ?: return

        when (config.protocol) {
            AssistantProtocol.ANTHROPIC -> {
                if (config.credential is AssistantCredential.OAuth) {
                    conn.setRequestProperty("Authorization", "Bearer $token")
                } else {
                    conn.setRequestProperty("x-api-key", token)
                }
                conn.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
                conn.setRequestProperty("anthropic-beta", ANTHROPIC_FALLBACK_BETA)
            }

            AssistantProtocol.OPENAI_COMPATIBLE ->
                conn.setRequestProperty("Authorization", "Bearer $token")
        }
    }

    // --- Anthropic Messages ---

    private fun anthropicBody(system: String, user: String, maxTokens: Int): JsonObject =
        buildJsonObject {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("system", system)
            // Ei dagbok kan innehalde personlege ting som gjer at ein klassifikator seier
            // nei. Server-side fallback lèt førespurnaden gå gjennom på ein annan modell i
            // staden for å feile på noko brukaren har snakka inn.
            put("fallbacks", "default")
            put("messages", userMessage(user))
        }

    private fun readAnthropic(raw: String): String {
        val root = json.parseToJsonElement(raw).jsonObject

        root["stop_reason"]?.jsonPrimitive?.contentOrNullSafe()?.let { stop ->
            if (stop == "refusal") {
                throw AssistantException(
                    AssistantError.BadResponse("Modellen avslo å svare på dette innhaldet."),
                )
            }
        }

        // Innhaldet er ei liste av blokker. Med adaptiv tenking kan den første vere ei
        // thinking-blokk, så vi må plukke tekstblokka – ikkje berre ta content[0].
        val text = root["content"]?.jsonArray
            ?.map { it.jsonObject }
            ?.filter { it["type"]?.jsonPrimitive?.contentOrNullSafe() == "text" }
            ?.mapNotNull { it["text"]?.jsonPrimitive?.contentOrNullSafe() }
            ?.joinToString("\n")
            .orEmpty()

        if (text.isBlank()) {
            throw AssistantException(AssistantError.BadResponse("Tomt svar frå modellen."))
        }
        return text
    }

    // --- OpenAI-kompatibel ---

    private fun openAiBody(system: String, user: String, maxTokens: Int): JsonObject =
        buildJsonObject {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("stream", false)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", system)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", user)
                        },
                    )
                },
            )
        }

    private fun readOpenAi(raw: String): String {
        val root = json.parseToJsonElement(raw).jsonObject
        val text = root["choices"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNullSafe()
            .orEmpty()

        if (text.isBlank()) {
            throw AssistantException(AssistantError.BadResponse("Tomt svar frå modellen."))
        }
        return text
    }

    private fun userMessage(user: String): JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put("role", "user")
                put("content", user)
            },
        )
    }

    private fun <T> failure(error: AssistantError): Result<T> =
        Result.failure(AssistantException(error))

    private companion object {
        const val TAG = "autodict-assistant"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val ANTHROPIC_FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}

/** `jsonPrimitive.content` kastar på JsonNull; dette gir null i staden. */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
