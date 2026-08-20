package com.autodict.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ny oppføring") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tilbake")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save(onSaved) },
                        enabled = !ui.saving && !ui.transcribing,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Lagre")
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
