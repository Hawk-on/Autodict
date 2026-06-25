package com.autodict.data.markdown

import com.autodict.domain.model.DiaryEntry

/**
 * Serialiserer [DiaryEntry] til/frå Markdown med YAML-frontmatter.
 *
 * Vi held YAML-en enkel og flat (berre `key: value` + ei tag-liste), og skriv difor vår eigen
 * minimale (de)serialiserar i staden for å dra inn SnakeYAML. Reint Kotlin → unit-testbart.
 *
 * Format:
 * ```
 * ---
 * id: 2026-06-02T14-03-12-notat
 * created: 2026-06-02T14:03:12+02:00
 * title: "Eit notat"
 * audio: 2026-06-02T14-03-12-notat.wav
 * duration_seconds: 42
 * language: no
 * transcribed: false
 * tags: [tur, fjell]
 * ---
 *
 * Brødtekst …
 * ```
 */
object FrontmatterSerializer {

    private const val DELIMITER = "---"

    fun serialize(entry: DiaryEntry): String = buildString {
        appendLine(DELIMITER)
        appendLine("id: ${scalar(entry.id)}")
        appendLine("created: ${scalar(entry.created)}")
        entry.updated?.let { appendLine("updated: ${scalar(it)}") }
        appendLine("title: ${scalar(entry.title)}")
        entry.audio?.let { appendLine("audio: ${scalar(it)}") }
        appendLine("duration_seconds: ${entry.durationSeconds}")
        appendLine("language: ${scalar(entry.language)}")
        appendLine("transcribed: ${entry.transcribed}")
        entry.model?.let { appendLine("model: ${scalar(it)}") }
        appendLine("tags: ${list(entry.tags)}")
        appendLine(DELIMITER)
        appendLine()
        append(entry.body)
    }

    fun parse(text: String): DiaryEntry {
        val normalized = text.replace("\r\n", "\n")
        val lines = normalized.split("\n")

        if (lines.firstOrNull()?.trim() != DELIMITER) {
            // Inga frontmatter – behandle alt som brødtekst med eit fallback-id.
            return DiaryEntry(id = "", created = "", body = normalized.trim())
        }

        val fields = mutableMapOf<String, String>()
        var index = 1
        while (index < lines.size && lines[index].trim() != DELIMITER) {
            val line = lines[index]
            val colon = line.indexOf(':')
            if (colon > 0) {
                val key = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                fields[key] = value
            }
            index++
        }
        // Hopp over avsluttande "---" og éi eventuell tom linje.
        index++
        if (index < lines.size && lines[index].isBlank()) index++
        val body = lines.drop(index).joinToString("\n").trimEnd()

        return DiaryEntry(
            id = unquote(fields["id"].orEmpty()),
            created = unquote(fields["created"].orEmpty()),
            updated = fields["updated"]?.let(::unquote),
            title = unquote(fields["title"].orEmpty()),
            audio = fields["audio"]?.let(::unquote),
            durationSeconds = fields["duration_seconds"]?.trim()?.toIntOrNull() ?: 0,
            language = unquote(fields["language"] ?: "no"),
            transcribed = fields["transcribed"]?.trim().toBoolean(),
            model = fields["model"]?.let(::unquote),
            tags = parseList(fields["tags"]),
            body = body,
        )
    }

    /** Skriv ein skalar; siter (med escaping) berre når det trengst for trygg attlesing. */
    private fun scalar(value: String): String {
        val needsQuote = value.isEmpty() ||
            value.first().isWhitespace() || value.last().isWhitespace() ||
            value.any { it == ':' || it == '"' || it == '#' || it == '\n' } ||
            value.first() in charArrayOf('[', ']', '{', '}', '\'', '&', '*', '!', '|', '>', '%', '@', '`')
        return if (!needsQuote) value else "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    private fun unquote(raw: String): String {
        val v = raw.trim()
        if (v.length >= 2 && v.first() == '"' && v.last() == '"') {
            return v.substring(1, v.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return v
    }

    private fun list(items: List<String>): String =
        items.joinToString(prefix = "[", postfix = "]") { scalar(it) }

    private fun parseList(raw: String?): List<String> {
        val v = raw?.trim() ?: return emptyList()
        if (!v.startsWith("[") || !v.endsWith("]")) return emptyList()
        val inner = v.substring(1, v.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        return inner.split(",").map { unquote(it.trim()) }.filter { it.isNotEmpty() }
    }
}
