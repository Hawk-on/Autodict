package com.autodict.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autodict.ui.theme.AutodictTheme

/**
 * Heimeskjerm: tek opp tale. Per M0 er dette ein plassholdar – opptakslogikken kjem i M2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onOpenList: () -> Unit,
    onOpenSettings: () -> Unit,
) {
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
            FloatingActionButton(onClick = { /* M2: start/stopp opptak */ }) {
                Icon(Icons.Default.Mic, contentDescription = "Ta opp")
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
            Text("Trykk på mikrofonen for å starte eit opptak.")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordScreenPreview() {
    AutodictTheme {
        RecordScreen(onOpenList = {}, onOpenSettings = {})
    }
}
