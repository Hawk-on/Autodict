package com.autodict.data.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.autodict.MainActivity
import com.autodict.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Foreground service som eig opptaket.
 *
 * Utan denne er opptak i bakgrunnen **stille**: frå API 29 blokkerer Android mikrofonen for
 * appar som ikkje er i framgrunnen, og [android.media.AudioRecord] held fram med å levere
 * nullar i staden for å feile. Låste du skjermen midt i eit opptak, fekk du eit opptak av
 * ingenting – utan feilmelding.
 *
 * Tenesta blir starta med [start] og stoppa når opptaket er ferdig eller forkasta. Sjølve
 * lyden ligg i [RecordingController], så skjermen kan koma og gå fritt.
 */
class RecordingService : Service() {

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val path = intent.getStringExtra(EXTRA_FILE)
                if (path == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startInForeground()
                if (!RecordingController.start(File(path))) {
                    Log.e(TAG, "AudioRecorder ville ikkje starte")
                    stopAndRelease()
                }
            }

            ACTION_PAUSE -> RecordingController.pause()
            ACTION_RESUME -> RecordingController.resume()

            ACTION_STOP -> {
                RecordingController.stop()
                // Skriv utkastet til disk før vi stoppar: blir prosessen rydda før brukaren
                // opnar appen, er opptaket framleis der.
                RecordingController.result.value?.let { result ->
                    PendingDraftStore.save(this, result, RecordingController.startedAtMillis)
                }
                stopAndRelease()
            }

            ACTION_DISCARD -> {
                RecordingController.discard()
                stopAndRelease()
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        createChannel()
        val notification = buildNotification(RecordingController.state.value)

        // API 34 krev at typen blir oppgitt ved start; eldre versjonar les han frå manifestet.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Hald teljaren i varselet i takt med opptaket.
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        RecordingController.state
            .onEach { state ->
                if (state.isActive) {
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
            .launchIn(newScope)
    }

    private fun stopAndRelease() {
        scope?.cancel()
        scope = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun buildNotification(state: RecorderState): Notification {
        val paused = state is RecorderState.Paused
        val elapsed = state.elapsedMsOrZero / 1000
        val time = "%02d:%02d".format(elapsed / 60, elapsed % 60)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(if (paused) "Opptak på pause" else "Tek opp …")
            .setContentText(time)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setUsesChronometer(false)
            // Synleg med kontrollar på låseskjermen. Varselet avslører berre at appen tek
            // opp og kor lenge – ikkje noko av innhaldet i dagboka.
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    null,
                    if (paused) "Hald fram" else "Pause",
                    servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, requestCode = 1),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Stopp",
                    servicePendingIntent(ACTION_STOP, requestCode = 2),
                ).build(),
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Opptak",
            // LOW: varselet skal vere synleg og stabilt, men aldri lage lyd midt i eit opptak.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Vist medan Autodict tek opp lyd."
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    companion object {
        private const val TAG = "autodict-recording"
        private const val CHANNEL_ID = "autodict.recording"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.autodict.action.START_RECORDING"
        const val ACTION_STOP = "com.autodict.action.STOP_RECORDING"
        const val ACTION_PAUSE = "com.autodict.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.autodict.action.RESUME_RECORDING"
        const val ACTION_DISCARD = "com.autodict.action.DISCARD_RECORDING"

        private const val EXTRA_FILE = "file"

        fun start(context: Context, file: File) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_FILE, file.absolutePath)
            context.startForegroundService(intent)
        }

        fun send(context: Context, action: String) {
            val intent = Intent(context, RecordingService::class.java).setAction(action)
            // Tenesta køyrer alt i framgrunnen her, så startService held.
            context.startService(intent)
        }
    }
}
