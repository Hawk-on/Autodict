package com.autodict.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodict.data.audio.RecorderState
import com.autodict.domain.model.DiaryEntry
import com.autodict.ui.record.RecordViewModel
import com.autodict.ui.record.RecordedDraft
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Heimeskjerm: **dagboka sjølv**, med mikrofonen som ein FAB over.
 *
 * Tidlegare var heimen ein tom opptaksskjerm og lista låg bak eit ikon. Det gjorde at ein
 * måtte heilt tilbake til heim for å ta opp etter å ha lese noko, og at det ein faktisk
 * lagar var gøymd. No skjer opptaket i eit ark **over** lista, så ein forlèt aldri dagboka.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRecorded: (RecordedDraft) -> Unit,
    listViewModel: HomeListViewModel = viewModel(),
    recordViewModel: RecordViewModel = viewModel(),
) {
    val context = LocalContext.current
    val ui by listViewModel.ui.collectAsStateWithLifecycle()
    val recorderState by recordViewModel.recorderState.collectAsStateWithLifecycle()
    val draft by recordViewModel.draft.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { listViewModel.refresh() }
    LaunchedEffect(draft) {
        draft?.let {
            onRecorded(it)
            recordViewModel.consumeDraft()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) recordViewModel.start() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dagbok") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Innstillingar")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        recordViewModel.start()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.size(68.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Ta opp", modifier = Modifier.size(30.dp))
            }
        },
    ) { padding ->
        when {
            ui.loading -> CenteredMessage(Modifier.padding(padding), "Lastar …")

            !ui.hasFolder -> CenteredMessage(
                Modifier.padding(padding),
                "Inga lagringsmappe vald. Vel ei mappe i Innstillingar før du tek opp.",
            )

            ui.entries.isEmpty() -> CenteredMessage(
                Modifier.padding(padding),
                "Ingen oppføringar enno.\nTrykk på mikrofonen for å ta opp den fyrste.",
            )

            else -> EntryList(
                entries = ui.entries,
                onOpenEntry = onOpenEntry,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (recorderState.isActive) {
        ModalBottomSheet(
            // Opptaket skal ikkje kunne forsvinne ved eit uhell – arket lukkast berre via
            // Stopp eller Forkast.
            onDismissRequest = {},
            sheetState = sheetState,
        ) {
            RecordingSheet(
                state = recorderState,
                onStop = { recordViewModel.stop() },
                onPause = { recordViewModel.pause() },
                onResume = { recordViewModel.resume() },
                onDiscard = { recordViewModel.discard() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryList(
    entries: List<DiaryEntry>,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = remember(entries) {
        entries.groupBy { entry ->
            runCatching {
                val zdt = ZonedDateTime.parse(entry.created)
                val month = zdt.month.getDisplayName(TextStyle.FULL, NORWEGIAN)
                "${month.replaceFirstChar { it.titlecase(NORWEGIAN) }} ${zdt.year}"
            }.getOrDefault("Ukjend dato")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Plass til FAB-en, så siste oppføring ikkje blir liggjande under han.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
    ) {
        grouped.forEach { (monthYear, monthEntries) ->
            stickyHeader(key = monthYear) {
                Text(
                    text = monthYear.uppercase(NORWEGIAN),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(monthEntries, key = { it.id }) { entry ->
                EntryRow(entry = entry, onClick = { onOpenEntry(entry.id) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun EntryRow(entry: DiaryEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.title.ifBlank { "Utan tittel" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Berre relevant når det finst lyd å transkribere – ei rein tekstoppføring
            // manglar ikkje noko.
            if (!entry.transcribed && entry.audio != null) {
                Spacer(Modifier.width(8.dp))
                Badge("Ikkje transkribert")
            }
        }

        Text(
            text = formatMeta(entry),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (entry.body.isNotBlank()) {
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Opptaksarket: tid, nivåmålar og kontrollar. */
@Composable
private fun RecordingSheet(
    state: RecorderState,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    val paused = state is RecorderState.Paused
    val amplitude = (state as? RecorderState.Recording)?.amplitude ?: 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
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
                text = if (paused) "PAUSE" else "TEK OPP",
                style = MaterialTheme.typography.labelLarge,
                color = if (paused) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = formatElapsed(state.elapsedMsOrZero),
            style = MaterialTheme.typography.displayLarge,
        )

        Spacer(Modifier.height(8.dp))

        LevelMeter(amplitude = amplitude, active = !paused)

        Spacer(Modifier.height(28.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            SheetAction(
                label = "Forkast",
                onClick = onDiscard,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Stopp er hovudhandlinga: størst, og i primærfargen.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onStop,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stopp opptak", modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("Stopp", style = MaterialTheme.typography.bodySmall)
            }

            SheetAction(
                label = if (paused) "Hald fram" else "Pause",
                onClick = { if (paused) onResume() else onPause() },
            ) {
                Icon(
                    if (paused) Icons.Default.Mic else Icons.Default.Pause,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) { icon() }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Nivåmålar. Poenget er ikkje presisjon, men å svare på «høyrer han meg i det heile?» –
 * stille rom og ein blokkert mikrofon ser heilt like ut utan denne.
 */
@Composable
private fun LevelMeter(amplitude: Float, active: Boolean) {
    // Faste vekter gir eit stabilt mønster som pustar med lyden, i staden for at kvar strek
    // hoppar for seg (som ville lese som støy).
    val weights = remember { listOf(0.25f, 0.45f, 0.7f, 0.5f, 0.85f, 1f, 0.75f, 0.95f, 0.6f, 0.8f, 0.45f, 0.65f, 0.3f) }
    val level by animateFloatAsState(
        targetValue = if (active) amplitude else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "level",
    )

    Row(
        modifier = Modifier.height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val activeColor = MaterialTheme.colorScheme.primary
        val idleColor = MaterialTheme.colorScheme.surfaceContainerHighest
        weights.forEach { weight ->
            val height = (6f + level * weight * 50f).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .background(
                        color = if (level * weight > 0.08f) activeColor else idleColor,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun CenteredMessage(modifier: Modifier, message: String) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val NORWEGIAN: Locale = Locale.forLanguageTag("no-NO")

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatMeta(entry: DiaryEntry): String {
    val time = runCatching {
        ZonedDateTime.parse(entry.created)
            .format(DateTimeFormatter.ofPattern("d. MMM HH:mm", NORWEGIAN))
    }.getOrDefault(entry.created)

    if (entry.durationSeconds <= 0) return time
    val minutes = entry.durationSeconds / 60
    val seconds = entry.durationSeconds % 60
    val length = if (minutes > 0) "$minutes min $seconds s" else "$seconds s"
    return "$time · $length"
}
