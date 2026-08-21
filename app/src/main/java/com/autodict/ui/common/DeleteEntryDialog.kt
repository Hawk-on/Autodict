package com.autodict.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.autodict.domain.model.DiaryEntry

/**
 * Stadfesting før ei oppføring blir sletta. Delt mellom lista (sveip) og detaljskjermen,
 * så ordlyden ikkje driv frå kvarandre – to ulike skildringar av kva sletting gjer ville
 * vore verre enn ingen.
 *
 * Teksten seier kva som faktisk skjer i staden for eit vagt «kan ikkje angrast»: dagboka
 * *er* filer, så det er filene som forsvinn, og kva som skjer vidare er opp til
 * synkingtenesta – ikkje oss.
 */
@Composable
fun DeleteEntryDialog(
    entry: DiaryEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Slette oppføringa?") },
        text = {
            Text(
                buildString {
                    append("«")
                    append(entry.title.ifBlank { "Utan tittel" })
                    append("» blir sletta frå mappa – ")
                    append(if (entry.audio != null) "både teksten og lydopptaket." else "tekstfila.")
                    append(" Er mappa synka, hamnar dei i papirkorga til synkingtenesta.")
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Slett", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Avbryt") }
        },
    )
}
