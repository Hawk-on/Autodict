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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Vis dagbok")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Innstillingar")
                    }
                },
            )
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

            Spacer(modifier = Modifier.weight(1f))

            val recordingState = state
            if (recordingState is RecorderState.Recording) {
                Text("Tek opp …", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatElapsed(recordingState.elapsedMs),
                    style = MaterialTheme.typography.displayLarge,
                )
            } else {
                Text(
                    "Trykk på mikrofonen for å starte eit opptak.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(48.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Big FAB with ripple effect for recording
            val targetScale = if (recordingState is RecorderState.Recording) 1f + (recordingState.amplitude * 1.5f) else 1f
            val animatedScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(durationMillis = 100),
                label = "amplitude_scale",
            )

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                if (isRecording) {
                    // Pulsing background based on amplitude
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(animatedScale)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                }

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
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    if (isRecording) {
                        Icon(Icons.Default.Stop, contentDescription = "Stopp opptak", modifier = Modifier.size(36.dp))
                    } else {
                        Icon(Icons.Default.Mic, contentDescription = "Ta opp", modifier = Modifier.size(36.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
