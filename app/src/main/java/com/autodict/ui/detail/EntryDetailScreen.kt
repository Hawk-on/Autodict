package com.autodict.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodict.data.transcribe.TargetLanguage
import com.autodict.ui.common.AudioPlayerBar
import com.autodict.ui.common.AudioSource
import com.autodict.data.actions.ActionType
import com.autodict.data.integration.CalendarIntentLauncher
import com.autodict.data.integration.ShareToKeep

/** Vis ei oppføring med tekst, avspeling og offline transkripsjon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    viewModel: EntryDetailViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.entry?.title?.ifBlank { "Oppføring" } ?: "Oppføring") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tilbake")
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
            val entry = ui.entry
            when {
                ui.loading -> Text("Lastar …")
                entry == null -> Text("Fann ikkje oppføringa.")
                else -> {
                    Text(entry.created, style = MaterialTheme.typography.bodySmall)

                    ui.audioUri?.let { uri ->
                        if (entry.transcribed) {
                            AudioPlayerBar(AudioSource.Content(uri))
                        }

                        val current = TargetLanguage.fromCode(entry.language)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!entry.transcribed) {
                                Button(
                                    onClick = { viewModel.transcribe() },
                                    enabled = !ui.transcribing,
                                ) {
                                    if (ui.transcribing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        Text("  Transkriberer …")
                                    } else {
                                        Text("Transkriber")
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.transcribe() },
                                    enabled = !ui.transcribing,
                                ) {
                                    if (ui.transcribing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                        Text("  Transkriberer …")
                                    } else {
                                        Text("Transkriber på nytt")
                                    }
                                }
                            }

                            // Same lyd, andre målforma – NB-Whisper normaliserer talen dit.
                            TextButton(
                                onClick = { viewModel.transcribe(current.other) },
                                enabled = !ui.transcribing,
                            ) {
                                Text("Som ${current.other.displayName.lowercase()}")
                            }
                        }

                        if (!entry.transcribed) {
                            AudioPlayerBar(AudioSource.Content(uri))
                        }
                    }

                    ui.message?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (ui.extractedActions.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Foreslåtte handlingar", style = MaterialTheme.typography.titleMedium)

                        ui.extractedActions.forEach { action ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                    Text(action.title, style = MaterialTheme.typography.bodyLarge)
                                    action.time?.let { time ->
                                        Text("Tidspunkt: $time", style = MaterialTheme.typography.bodySmall)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { viewModel.dismissAction(action) }) {
                                            Text("Avvis")
                                        }
                                        Button(onClick = {
                                            viewModel.approveAction(action)
                                            if (action.type == ActionType.CALENDAR_EVENT) {
                                                val launched = CalendarIntentLauncher.launch(
                                                    context,
                                                    action.title,
                                                    action.time,
                                                    entry.body,
                                                )
                                                if (!launched) {
                                                    viewModel.reportMessage("Fann inga kalender-app på eininga.")
                                                }
                                            } else {
                                                // Google Tasks har ingen del-mottakar; brukaren vel sjølv
                                                // kvar gjeremålet skal (t.d. ein oppgåve-app) i del-arket.
                                                val shared = ShareToKeep.share(
                                                    context,
                                                    action.title,
                                                    action.time?.let { "${action.title}\n\n$it" } ?: action.title,
                                                    chooserTitle = "Del gjeremål",
                                                )
                                                if (!shared) {
                                                    viewModel.reportMessage("Fann ingen app å dele gjeremålet til.")
                                                }
                                            }
                                        }) {
                                            Text(if (action.type == ActionType.CALENDAR_EVENT) "Legg til i kalender" else "Del gjeremål")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    if (entry.body.isNotBlank()) {
                        Text(
                            text = entry.body,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else if (entry.transcribed) {
                         Text(
                            text = "(ingen tekst transkribert)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (entry.transcribed && entry.model != null) {
                        Text(
                            "Transkribert med ${entry.model} " +
                                "(${TargetLanguage.fromCode(entry.language).displayName.lowercase()})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (entry.body.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val shared = ShareToKeep.share(context, entry.title.ifBlank { "Autodict" }, entry.body)
                                if (!shared) {
                                    viewModel.reportMessage("Fann ingen app å dele til.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Del til Keep")
                        }
                    }
                }
            }
        }
    }
}
