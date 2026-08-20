package com.autodict.data.actions

import com.autodict.domain.model.DiaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ein enkel offline, regelbasert uttrekkjar.
 * Ser etter typiske nøkkelord for møter, avtalar og gjeremål.
 * M5 i milepælsplanen.
 */
class RuleBasedExtractor : ActionExtractor {

    // Nøkkelord for handlingar – lengste frasar først, elles blir spesifikke reglar uoppnåelege.
    private val CALENDAR_KEYWORDS = listOf(
        "hugs å møte",
        "husk å møte",
        "møte med",
        "time hjå",
        "time hos",
        "avtale",
        "møte",
    )
    private val TASK_KEYWORDS = listOf(
        "må gjere",
        "må gjøre",
        "skal ringe",
        "må ringe",
        "hugs å",
        "husk å",
    )

    override suspend fun extractActions(entry: DiaryEntry): List<ExtractedAction> = withContext(Dispatchers.Default) {
        val actions = mutableListOf<ExtractedAction>()
        val sentences = entry.body.split(Regex("[.!?\n]")).map { it.trim() }.filter { it.isNotEmpty() }

        for (sentence in sentences) {
            val lowerSentence = sentence.lowercase()

            // Sjekk kalenderhendingar først
            var isCalendarEvent = false
            for (keyword in CALENDAR_KEYWORDS) {
                if (lowerSentence.contains(keyword)) {
                    // Ekstraher meininga som tittel
                    val title = formatTitle(sentence, keyword)
                    val time = NorwegianDateTimeParser.parse(sentence, entry.created)

                    actions.add(ExtractedAction(
                        title = title.replaceFirstChar { it.uppercase() },
                        time = time,
                        type = ActionType.CALENDAR_EVENT
                    ))
                    isCalendarEvent = true
                    break // Berre eitt treff per setning
                }
            }

            if (isCalendarEvent) continue

            // Sjekk gjeremål
            for (keyword in TASK_KEYWORDS) {
                if (lowerSentence.contains(keyword)) {
                    val title = formatTitle(sentence, keyword)
                    val time = NorwegianDateTimeParser.parse(sentence, entry.created)

                    actions.add(ExtractedAction(
                        title = title.replaceFirstChar { it.uppercase() },
                        time = time,
                        type = ActionType.TASK
                    ))
                    break
                }
            }
        }

        actions
    }

    /**
     * Prøver å lage ein rein tittel frå setninga ved å fjerne nøkkelordet og trimme.
     * Viss det blir for kort, bruk heile setninga.
     */
    private fun formatTitle(sentence: String, keyword: String): String {
        val idx = sentence.lowercase().indexOf(keyword)
        if (idx >= 0) {
            val after = sentence.substring(idx + keyword.length).trim()
            if (after.length > 5) {
                return after.replaceFirstChar { it.uppercase() }
            }
        }
        return sentence
    }
}
