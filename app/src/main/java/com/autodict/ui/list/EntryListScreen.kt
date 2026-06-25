package com.autodict.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodict.domain.model.DiaryEntry

/** Listar dagbok-oppføringar lese frå mappa, nyaste først. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    onOpenEntry: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: EntryListViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Dagbok") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tilbake")
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> CenteredMessage(Modifier.padding(padding), "Lastar …")
            !ui.hasFolder -> CenteredMessage(
                Modifier.padding(padding),
                "Inga lagringsmappe vald. Vel ei mappe i Innstillingar.",
            )
            ui.entries.isEmpty() -> CenteredMessage(
                Modifier.padding(padding),
                "Ingen oppføringar enno. Ta opp den fyrste!",
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(ui.entries, key = { it.id }) { entry ->
                    EntryRow(entry = entry, onClick = { onOpenEntry(entry.id) })
                    HorizontalDivider()
                }
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = entry.title.ifBlank { "Utan tittel" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.created,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.body.isNotBlank()) {
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CenteredMessage(modifier: Modifier, message: String) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}
