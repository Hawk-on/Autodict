package com.autodict.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodict.data.transcribe.TargetLanguage
import com.autodict.data.transcribe.WhisperModel
import com.autodict.ui.theme.ThemeMode

private const val SPONSORS_URL = "https://github.com/sponsors/Hawk-on"

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
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    // Meldingar (t.d. frå Google-konto-kopling langt nede i skjermen) skal vere synlege uansett
    // kor langt brukaren har scrolla – ein snackbar er synleg over heile skjermen.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) {
        ui.message?.let { snackbarHostState.showSnackbar(it) }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            HorizontalDivider()
            Text("Utsjånad", style = MaterialTheme.typography.titleMedium)
            Text(
                "Det mørke temaet brukar ein løfta blågrå botn i staden for svart, og dempa " +
                    "tekst i staden for rein kvit – mildare for auga i lange lesetøktar.",
                style = MaterialTheme.typography.bodySmall,
            )
            ThemeMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectThemeMode(mode) },
                ) {
                    RadioButton(
                        selected = ui.themeMode == mode,
                        onClick = { viewModel.selectThemeMode(mode) },
                    )
                    Text(mode.displayName, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()
            Text("Transkripsjonsmodell (offline)", style = MaterialTheme.typography.titleMedium)
            Text(
                "NB-Whisper (norsk), q5_0-kvantisert. Lastast ned ved behov.",
                style = MaterialTheme.typography.bodySmall,
            )
            WhisperModel.entries.forEach { model ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectModel(model.id) },
                ) {
                    RadioButton(
                        selected = ui.selectedModelId == model.id,
                        onClick = { viewModel.selectModel(model.id) },
                    )
                    Text(
                        "${model.displayName} · ~${model.approxMb} MB",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = ui.wifiOnly, onCheckedChange = { viewModel.setWifiOnly(it) })
                Spacer(Modifier.width(8.dp))
                Text("Berre last ned på Wi-Fi", style = MaterialTheme.typography.bodyMedium)
            }
            val fraction = ui.downloadFraction
            when {
                fraction != null -> {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Lastar ned … ${(fraction * 100).toInt()} %",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ui.modelDownloaded -> {
                    Text("✓ Modell lasta ned", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { viewModel.deleteModel() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Slett modell") }
                }
                else -> {
                    Button(
                        onClick = { viewModel.startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Last ned modell") }
                }
            }

            HorizontalDivider()
            Text("Transkripsjon", style = MaterialTheme.typography.titleMedium)
            Text(
                "Målforma modellen skriv ut. Talen blir normalisert dit – snakkar du dialekt, " +
                    "kjem teksten ut i valt målform. Bokmål har litt lågare feilrate enn nynorsk.",
                style = MaterialTheme.typography.bodySmall,
            )
            TargetLanguage.entries.forEach { language ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectLanguage(language) },
                ) {
                    RadioButton(
                        selected = ui.language == language,
                        onClick = { viewModel.selectLanguage(language) },
                    )
                    Text(language.displayName, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = ui.autoTranscribe,
                    onCheckedChange = { viewModel.setAutoTranscribe(it) },
                )
                Spacer(Modifier.width(8.dp))
                Text("Transkriber automatisk etter opptak", style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()
            Text("Lydarkiv", style = MaterialTheme.typography.titleMedium)
            Text(
                "Opptak blir arkiverte som Opus (~0,2 MB/min). Tapsfri WAV tek ~5,6 MB/min, " +
                    "men er uendra lyd rett frå mikrofonen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = ui.keepOriginalWav,
                    onCheckedChange = { viewModel.setKeepOriginalWav(it) },
                )
                Spacer(Modifier.width(8.dp))
                Text("Arkiver tapsfri WAV i staden", style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()
            Text("Diagnostikk", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sjekkar native bibliotek, modellfil og modell-lasting – bruk denne når " +
                    "«Transkriber» ikkje ser ut til å gjere noko.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { viewModel.runDiagnostics() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test transkripsjon")
            }
            ui.diagnostics?.let { report ->
                Text(
                    report,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            HorizontalDivider()
            Text("Om", style = MaterialTheme.typography.titleMedium)
            Text(
                "Autodict er gratis og fri programvare (AGPL-3.0).",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = {
                    // Ekstern lenkje; feiler pent utan nett/nettlesar (offline-først).
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, SPONSORS_URL.toUri()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Støtt utviklinga 💜")
            }
        }
    }
}
