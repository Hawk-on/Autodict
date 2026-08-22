package com.autodict.ui.quickrecord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodict.data.audio.RecorderState
import com.autodict.ui.common.RecordingPermissions
import com.autodict.ui.record.RecordViewModel

/**
 * Opptaksskjermen for låseskjermen: stor teljar, og berre dei kontrollane ein treng med
 * telefonen i handa. Ingen dagbok-innhald, sidan skjermen er synleg utan autentisering.
 */
@Composable
fun QuickRecordScreen(
    onClose: () -> Unit,
    viewModel: RecordViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.recorderState.collectAsStateWithLifecycle()
    val canRecord = RecordingPermissions.canRecord(context)

    // Start med ein gong. Poenget med denne skjermen er at eit trykk på sideknappen skal
    // vere nok – å måtte trykkje ein gong til her ville tatt vekk heile gevinsten.
    LaunchedEffect(canRecord) {
        if (canRecord && !state.isActive) viewModel.start()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!canRecord) {
                // Løyve kan ikkje spørjast om her; dialogen ville hamna bak låseskjermen.
                Text(
                    "Autodict manglar tilgang til mikrofonen.\n" +
                        "Lås opp og opne appen for å gi tilgang.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onClose) { Text("Lukk") }
                return@Column
            }

            val paused = state is RecorderState.Paused

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (paused) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    if (paused) "PAUSE" else "TEK OPP",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (paused) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                formatElapsed(state.elapsedMsOrZero),
                style = MaterialTheme.typography.displayLarge,
            )

            Spacer(Modifier.height(48.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { viewModel.discard(); onClose() },
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text("Forkast", style = MaterialTheme.typography.bodyMedium)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = { viewModel.stop(); onClose() },
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stopp", modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Stopp", style = MaterialTheme.typography.bodyMedium)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { if (paused) viewModel.resume() else viewModel.pause() },
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            if (paused) Icons.Default.Mic else Icons.Default.Pause,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(if (paused) "Hald fram" else "Pause", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                "Opptaket ligg klart i dagboka neste gong du opnar Autodict.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
