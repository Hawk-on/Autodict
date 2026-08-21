package com.autodict.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodict.data.transcribe.TargetLanguage
import com.autodict.data.transcribe.WhisperModel

/**
 * Oppstartsrettleiing i fire steg. Same visuelle ramme for alle stega – stegindikator,
 * overskrift, innhald, knapperad – slik at det einaste som endrar seg er innhaldet.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) viewModel.onFolderPicked(uri) }

    // Løyvet er siste steg; uansett svar er rettleiinga ferdig – appen skal ikkje stå fast
    // fordi nokon sa nei, dei kan gi tilgang seinare når dei trykkjer på mikrofonen.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.complete(onDone) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StepIndicator(current = ui.stepNumber, total = ui.totalSteps)

        Text(
            "STEG ${ui.stepNumber} AV ${ui.totalSteps}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (ui.step) {
                OnboardingStep.FOLDER -> FolderStep(ui)
                OnboardingStep.LANGUAGE -> LanguageStep(ui, viewModel::selectLanguage)
                OnboardingStep.MODEL -> ModelStep(ui, viewModel)
                OnboardingStep.PERMISSION -> PermissionStep(ui)
            }

            ui.message?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        when (ui.step) {
            OnboardingStep.FOLDER -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (ui.folderName == null) "Vel mappe" else "Byt mappe")
                }
                TextButton(onClick = viewModel::next, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ui.folderName == null) "Hopp over — vel seinare" else "Hald fram")
                }
            }

            OnboardingStep.PERMISSION -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Gi tilgang og kom i gang")
                }
                TextButton(
                    onClick = { viewModel.complete(onDone) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ikkje no")
                }
            }

            else -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = viewModel::back, modifier = Modifier.height(56.dp)) {
                    Text("Tilbake")
                }
                Button(
                    onClick = viewModel::next,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text("Hald fram")
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (index < current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun FolderStep(ui: OnboardingUiState) {
    Title("Kvar skal dagboka bu?")
    Body(
        "Autodict har inga skjult database. Kvar oppføring blir ei vanleg Markdown-fil " +
            "med lydfila ved sida — i ei mappe du sjølv vel.",
    )

    Card {
        Text("Dagbok/", style = MaterialTheme.typography.bodyMedium)
        Text(
            "  2026/2026-08/",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "    morgontur.md\n    morgontur.opus",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Body(
        "Vel gjerne ei mappe som blir synka — Drive, Dropbox eller Syncthing. Då har du " +
            "dagboka på alle einingane dine.",
    )

    ui.folderName?.let { name ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Vald: $name", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LanguageStep(ui: OnboardingUiState, onSelect: (TargetLanguage) -> Unit) {
    Title("Kva målform skal teksten ha?")
    Body("Modellen skriv ut teksten i den målforma du vel — uansett kva dialekt du snakkar.")

    TargetLanguage.entries.forEach { language ->
        SelectableRow(
            selected = ui.language == language,
            onClick = { onSelect(language) },
            title = language.displayName,
            subtitle = if (language == TargetLanguage.BOKMAAL) {
                "Litt lågare feilrate"
            } else {
                "Same modell, nynorsk utskrift"
            },
        )
    }

    Card {
        Text(
            "Du seier",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("«Eg trur me må ta det på måndag i staden»", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Autodict skriv",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (ui.language == TargetLanguage.BOKMAAL) {
                "«Jeg tror vi må ta det på mandag i stedet»"
            } else {
                "«Eg trur vi må ta det på måndag i staden»"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Body("Du kan endre målform per oppføring seinare.")
}

@Composable
private fun ModelStep(ui: OnboardingUiState, viewModel: OnboardingViewModel) {
    Title("Språkmodell")
    Body(
        "NB-Whisper frå Nasjonalbiblioteket, trena på norsk. Køyrer heilt på telefonen — " +
            "dagboka di forlèt aldri eininga.",
    )

    WhisperModel.entries.forEach { model ->
        SelectableRow(
            selected = ui.selectedModelId == model.id,
            onClick = { viewModel.selectModel(model.id) },
            title = model.displayName,
            subtitle = "~${model.approxMb} MB",
            recommended = model == WhisperModel.DEFAULT,
        )
    }

    val fraction = ui.downloadFraction
    when {
        fraction != null -> Card {
            Text("Lastar ned … ${(fraction * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "Du kan halde fram medan det lastar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ui.modelDownloaded -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Modellen er lasta ned", style = MaterialTheme.typography.bodyMedium)
        }

        else -> OutlinedButton(
            onClick = viewModel::startDownload,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Last ned modell")
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(checked = ui.wifiOnly, onCheckedChange = viewModel::setWifiOnly)
        Text("Berre last ned på Wi-Fi", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PermissionStep(ui: OnboardingUiState) {
    Title("Éin ting til")
    Body("Autodict treng tilgang til mikrofonen for å ta opp. Lyden blir verande på telefonen.")

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
    }

    Card {
        SummaryRow("Mappe", ui.folderName ?: "Ikkje vald")
        SummaryRow("Målform", ui.language.displayName)
        SummaryRow(
            "Modell",
            WhisperModel.fromId(ui.selectedModelId).displayName + when {
                ui.downloadFraction != null -> " · lastar ned (${(ui.downloadFraction * 100).toInt()} %)"
                ui.modelDownloaded -> " · klar"
                else -> " · ikkje lasta ned"
            },
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String,
    recommended: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (recommended) {
                    Text(
                        "Tilrådd",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(18.dp),
        content = content,
    )
}
