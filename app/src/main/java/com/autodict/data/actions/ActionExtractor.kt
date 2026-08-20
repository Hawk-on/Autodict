package com.autodict.data.actions

import com.autodict.domain.model.DiaryEntry

/** Eit handlingspunkt trekt ut frå teksten. */
data class ExtractedAction(
    val title: String,
    val time: String? = null,
    val type: ActionType
)

enum class ActionType {
    CALENDAR_EVENT, TASK
}

/** Grensesnitt for å trekkje ut handlingar frå ei dagbok-oppføring. */
interface ActionExtractor {
    /**
     * Analyserer teksten i [entry] og finn moglege handlingspunkt.
     * Bør køyrast på ein bakgrunnstråd.
     */
    suspend fun extractActions(entry: DiaryEntry): List<ExtractedAction>
}
