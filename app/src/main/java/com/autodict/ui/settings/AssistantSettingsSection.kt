package com.autodict.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.autodict.data.assistant.AssistantPreset

/**
 * Oppsett for assistenten. Bevisst laga slik at «kva skjer med teksten min» står rett ved
 * valet av leverandør – ei dagbok er personleg, og det skal ikkje vere uklart om innhaldet
 * går ut av eininga.
 */
@Composable
fun ColumnScope.AssistantSettingsSection(
    ui: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    HorizontalDivider()
    Text("Assistent", style = MaterialTheme.typography.titleMedium)
    Text(
        "Ein språkmodell kan reinskrive transkripsjonen og foreslå tittel. Av som standard – " +
            "appen fungerer heilt utan.",
        style = MaterialTheme.typography.bodyMedium,
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = ui.assistant.enabled, onCheckedChange = viewModel::setAssistantEnabled)
        Spacer(Modifier.width(8.dp))
        Text("Bruk assistent", style = MaterialTheme.typography.bodyMedium)
    }

    if (!ui.assistant.enabled) return

    AssistantPreset.entries.forEach { preset ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.selectAssistantPreset(preset) },
        ) {
            RadioButton(
                selected = ui.assistant.preset == preset,
                onClick = { viewModel.selectAssistantPreset(preset) },
            )
            Column {
                Text(preset.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    preset.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    OutlinedTextField(
        value = ui.assistant.baseUrl,
        onValueChange = viewModel::setAssistantBaseUrl,
        label = { Text("Adresse") },
        placeholder = { Text("https://api.example.com") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = ui.assistant.model,
        onValueChange = viewModel::setAssistantModel,
        label = { Text("Modell") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (ui.assistant.preset.needsKey) {
        OutlinedTextField(
            value = ui.assistantKeyInput,
            onValueChange = viewModel::setAssistantKey,
            label = { Text("API-nøkkel") },
            placeholder = {
                // Nøkkelen blir aldri lesen tilbake i klartekst etter omstart, så feltet er
                // tomt sjølv når ein nøkkel er lagra. Sei det, i staden for å la det sjå ut
                // som om han er borte.
                Text(if (ui.assistantHasStoredKey) "Lagra – skriv inn på nytt for å byte" else "")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Nøkkelen blir kryptert med Android Keystore og forlèt aldri eininga.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    OutlinedButton(
        onClick = viewModel::testAssistant,
        enabled = !ui.assistantTesting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (ui.assistantTesting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Testar …")
        } else {
            Text("Test tilkopling")
        }
    }

    ui.assistantTestResult?.let { result ->
        Text(result, style = MaterialTheme.typography.bodySmall)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Teksten blir send til den valde modellen når du trykkjer «Reinskriv» – aldri " +
                "automatisk. Lydfila blir aldri send.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
