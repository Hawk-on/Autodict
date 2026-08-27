package com.autodict.data.assistant

/**
 * Prompt og svartolking for oppreinsking.
 *
 * **Kvifor ikkje JSON:** ein 3B-modell på Ollama held ikkje JSON-kontrakt pålitleg – han
 * gløymer hermeteikn, legg på ```-gjerde, eller skriv forklaringar rundt. Eit linjebasert
 * format med ein tydeleg skiljelinje er langt meir robust, og [parse] er skriven for å
 * tåle at modellen rotar litt uansett.
 */
object PolishPrompt {

    private const val SEPARATOR = "---"

    fun system(language: String): String = """
        Du hjelper med ei personleg taledagbok. Du får ein ordrett transkripsjon av tale og
        skal reinskrive han.

        Reglar:
        - Behald forfattaren si eiga stemme og ordval. Dette er ei dagbok, ikkje ein rapport.
        - Fjern fyllord, falske startar og gjentakingar som kjem av at det var tale.
        - Del inn i avsnitt. Rett teiknsetjing og openbare feilhøyringar.
        - Skriv på $language.
        - Ikkje legg til noko som ikkje vart sagt. Ikkje samanfatt, ikkje forkort innhaldet,
          ikkje kommenter teksten. Du reinskriv, du skriv ikkje om.

        Svar nøyaktig slik, utan noko før eller etter:

        TITTEL: <kort tittel, høgst seks ord>
        TAGS: <null til tre stikkord, komma mellom>
        $SEPARATOR
        <den reinskrivne teksten>
    """.trimIndent()

    fun user(text: String): String = "Transkripsjon:\n\n$text"

    /**
     * Tolkar svaret mildt. Manglar skiljelinja, reknar vi heile svaret som tekst – ein
     * tittel vi ikkje fann er eit lite tap, men å kaste den reinskrivne teksten fordi
     * formatet var litt feil ville vore eit stort eit.
     *
     * Returnerer null berre når det ikkje finst tekst i det heile.
     */
    fun parse(raw: String): PolishedEntry? {
        val cleaned = stripCodeFence(raw).trim()
        if (cleaned.isEmpty()) return null

        var title = ""
        var tags = emptyList<String>()
        val bodyLines = mutableListOf<String>()

        var seenSeparator = false
        for (line in cleaned.lines()) {
            val trimmed = line.trim()
            when {
                // Hovudlinjene blir berre lesne før skiljelinja; etter han er alt tekst,
                // så ein dagbok-setning som byrjar med "Tags:" ikkje blir eten.
                !seenSeparator && trimmed.startsWith(TITLE_PREFIX, ignoreCase = true) ->
                    title = trimmed.removePrefix(trimmed.take(TITLE_PREFIX.length)).trim()

                !seenSeparator && trimmed.startsWith(TAGS_PREFIX, ignoreCase = true) ->
                    tags = trimmed.removePrefix(trimmed.take(TAGS_PREFIX.length))
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                !seenSeparator && trimmed == SEPARATOR -> seenSeparator = true

                seenSeparator -> bodyLines += line
            }
        }

        // Ingen skiljelinje: modellen svarte i fritekst. Behald alt som tekst.
        val body = if (seenSeparator) {
            bodyLines.joinToString("\n").trim()
        } else {
            cleaned
        }

        if (body.isEmpty()) return null
        return PolishedEntry(title = title, body = body, tags = tags)
    }

    /** Modellar legg gjerne heile svaret i ei ```-blokk sjølv om ein bad dei la vere. */
    private fun stripCodeFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```")
            .substringAfter('\n', "")
            .substringBeforeLast("```")
    }

    private const val TITLE_PREFIX = "TITTEL:"
    private const val TAGS_PREFIX = "TAGS:"
}
