package com.autodict.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodict.data.transcribe.TargetLanguage
import com.autodict.ui.common.AudioPlayerBar
import com.autodict.ui.common.AudioSource

/**
 * Rediger og lagre eit nytt opptak: spel av, transkriber offline, rett teksten – og lagre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EntryEditViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    // Utleidd frå ui-staten (ikkje eit kall inn i ViewModel-en) så den blir lesen på nytt
    // ved kvar rekomposisjon.
    val hasUnsavedWork = ui.audioPath != null || ui.title.isNotBlank() || ui.body.isNotBlank()

    // Tilbake medan vi lagrar ville kansellert skrivinga og kasta både opptak og
    // transkripsjon. Mot ei sky-synka mappe (Drive) tek lagringa fleire sekund, så det er
    // nettopp då det er mest freistande å trykkje tilbake. Lagringa er kort og avgrensa,
    // så vi svelgjer tilbake heilt der; elles spør vi før vi forkastar.
    //
    // Transkribering blokkerer vi derimot ikkje: med medium-modellen tek ho fleire minutt,
    // og å låse brukaren inne så lenge er verre enn å miste eit opptak ein sjølv vel bort.
    BackHandler(enabled = ui.saving || hasUnsavedWork) {
        if (!ui.saving) confirmDiscard = true
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Forkaste opptaket?") },
            text = { Text("Opptaket og teksten er ikkje lagra enno, og går tapt.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Forkast") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Hald fram") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ny oppføring") },
                navigationIcon = {
                    IconButton(
                        onClick = { if (hasUnsavedWork) confirmDiscard = true else onBack() },
                        enabled = !ui.saving,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tilbake")
                    }
                },
                actions = {
                    if (ui.saving) {
                        // Ei deaktivert hake ser ut som om ingenting skjer. Spinneren seier
                        // at appen arbeider, og teksten under kva ho ventar på.
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                    } else {
                        IconButton(
                            onClick = { viewModel.save(onSaved) },
                            enabled = !ui.transcribing,
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Lagre")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ui.saveStage?.let { stage ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Column {
                        Text(stage.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Lagring til ei sky-synka mappe kan ta litt tid. Ikkje gå tilbake – " +
                                "vi opnar lista når alt er skrive.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            ui.audioPath?.let { path ->
                AudioPlayerBar(AudioSource.LocalFile(path))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.transcribe() },
                        enabled = !ui.transcribing,
                    ) {
                        if (ui.transcribing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text("  Transkriberer …")
                        } else {
                            Text(if (ui.transcribedModel != null) "Transkriber på nytt" else "Transkriber")
                        }
                    }

                    // Målform styrer kva modellen normaliserer talen mot (òg dialekt).
                    TargetLanguage.entries.forEach { language ->
                        FilterChip(
                            selected = ui.language == language,
                            onClick = { viewModel.onLanguageChange(language) },
                            enabled = !ui.transcribing,
                            label = { Text(language.displayName) },
                        )
                    }
                }

                // Assistenten er einaste staden teksten kan forlate eininga, så det skal
                // vere eit medvite trykk – aldri noko som skjer av seg sjølv.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.polish() },
                        enabled = !ui.polishing && !ui.transcribing && ui.body.isNotBlank(),
                    ) {
                        if (ui.polishing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("  Reinskriv …")
                        } else {
                            Text("Reinskriv")
                        }
                    }

                    if (ui.rawTranscript != null) {
                        TextButton(onClick = { viewModel.undoPolish() }) {
                            Text("Angre")
                        }
                    }
                }

                ui.message?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = ui.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Tittel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.body,
                onValueChange = viewModel::onBodyChange,
                label = { Text("Tekst") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                minLines = 8,
            )
            ui.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
