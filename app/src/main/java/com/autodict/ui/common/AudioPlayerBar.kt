package com.autodict.ui.common

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Kvar lyden skal spelast frå: ei fil i dagbok-mappa (SAF) eller ei lokal cache-fil. */
sealed interface AudioSource {
    data class Content(val uri: Uri) : AudioSource
    data class LocalFile(val path: String) : AudioSource
}

/**
 * Avspelingslinje med spel/pause, søkjefelt og tid – for både detaljskjermen og utkastet.
 *
 * Brukar [MediaPlayer] direkte (ingen ekstra avhengnad); posisjonen blir polla medan lyden
 * spelar, og pollinga stoppar når du dreg i søkjefeltet så det ikkje hoppar under fingeren.
 */
@Composable
fun AudioPlayerBar(
    source: AudioSource,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(source) {
        val mp = MediaPlayer()
        val ok = runCatching {
            when (source) {
                is AudioSource.Content -> mp.setDataSource(context, source.uri)
                is AudioSource.LocalFile -> mp.setDataSource(source.path)
            }
            mp.setOnCompletionListener {
                isPlaying = false
                positionMs = 0
                runCatching { mp.seekTo(0) }
            }
            mp.prepare()
        }.isSuccess

        if (ok) {
            player = mp
            durationMs = mp.duration.coerceAtLeast(0)
        } else {
            failed = true
            runCatching { mp.release() }
        }

        onDispose {
            isPlaying = false
            player = null
            if (ok) runCatching { mp.release() }
        }
    }

    LaunchedEffect(isPlaying, scrubbing) {
        while (isPlaying && !scrubbing) {
            positionMs = player?.currentPosition ?: positionMs
            delay(200)
        }
    }

    if (failed) {
        Text(
            "Klarte ikkje opne lydfila.",
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier,
        )
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = {
                val mp = player ?: return@IconButton
                if (isPlaying) {
                    runCatching { mp.pause() }
                    isPlaying = false
                } else {
                    runCatching { mp.start() }
                    isPlaying = true
                }
            },
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Spel av",
            )
        }

        Slider(
            value = positionMs.toFloat(),
            onValueChange = {
                scrubbing = true
                positionMs = it.toInt()
            },
            onValueChangeFinished = {
                runCatching { player?.seekTo(positionMs) }
                scrubbing = false
            },
            valueRange = 0f..(durationMs.takeIf { it > 0 } ?: 1).toFloat(),
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

/** millisekund → `m:ss`. */
private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
