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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodict.data.transcribe.TargetLanguage
import com.autodict.ui.common.AudioPlayerBar
import com.autodict.ui.common.AudioSource

/** Vis ei oppføring med tekst, avspeling og offline transkripsjon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    viewModel: EntryDetailViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

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
                        AudioPlayerBar(AudioSource.Content(uri))

                        val current = TargetLanguage.fromCode(entry.language)
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
                                    Text(if (entry.transcribed) "Transkriber på nytt" else "Transkriber")
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
                    }

                    ui.message?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }

                    HorizontalDivider()

                    Text(
                        text = entry.body.ifBlank { "(ingen tekst)" },
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    if (entry.transcribed && entry.model != null) {
                        Text(
                            "Transkribert med ${entry.model} " +
                                "(${TargetLanguage.fromCode(entry.language).displayName.lowercase()})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
