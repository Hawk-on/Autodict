package com.autodict.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodict.data.audio.RecorderState

/**
 * Heimeskjerm: ta opp tale. Ved stopp blir [onRecorded] kalla med eit utkast som
 * navigasjonen sender vidare til redigeringsskjermen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onOpenList: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecorded: (RecordedDraft) -> Unit,
    viewModel: RecordViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.recorderState.collectAsStateWithLifecycle()
    val hasFolder by viewModel.hasFolder.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshFolder() }
    LaunchedEffect(draft) {
        draft?.let {
            onRecorded(it)
            viewModel.consumeDraft()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.start() }

    val isRecording = state is RecorderState.Recording

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Autodict") },
                actions = {
                    IconButton(onClick = onOpenList) {
                        Icon(Icons.Default.List, contentDescription = "Vis dagbok")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Innstillingar")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        viewModel.stop()
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            ) {
                if (isRecording) {
                    Icon(Icons.Default.Stop, contentDescription = "Stopp opptak")
                } else {
                    Icon(Icons.Default.Mic, contentDescription = "Ta opp")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasFolder) {
                Text(
                    "Inga lagringsmappe vald enno. Vel ei mappe før du lagrar oppføringar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Opne innstillingar")
                }
            }

            val recordingState = state
            if (recordingState is RecorderState.Recording) {
                Text("Tek opp …", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatElapsed(recordingState.elapsedMs),
                    style = MaterialTheme.typography.displaySmall,
                )
            } else {
                Text(
                    "Trykk på mikrofonen for å starte eit opptak.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
