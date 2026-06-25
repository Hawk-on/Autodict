package com.autodict.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Innstillingar (M1): vel lagringsmappe (SAF) og test at skriving/lesing fungerer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Innstillingar") },
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Lagringsmappe", style = MaterialTheme.typography.titleMedium)

            Text(
                if (ui.hasFolder) {
                    "Vald mappe: ${ui.folderName ?: "(ukjend)"}"
                } else {
                    "Inga mappe vald enno. Vel ei mappe der dagboka skal lagrast – " +
                        "gjerne ei mappe som blir synka (Dropbox/Drive/Syncthing)."
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (ui.hasFolder) "Byt mappe" else "Vel mappe")
            }

            if (ui.hasFolder) {
                OutlinedButton(
                    onClick = { viewModel.writeTestEntry() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Skriv testfil")
                }
                TextButton(
                    onClick = { viewModel.clearFolder() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Fjern mappe")
                }
            }

            ui.message?.let { message ->
                HorizontalDivider()
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
