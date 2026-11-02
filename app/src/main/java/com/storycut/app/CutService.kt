package com.storycut.app

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import java.io.File
import kotlin.math.min

@UnstableApi
class CutService : Service() {
    companion object {
        const val CHANNEL = "storycut_export"
        const val ACTION_START = "com.storycut.app.START"
        const val ACTION_STOP = "com.storycut.app.STOP"
        const val PREFS = "storycut_state"
    }
    private val handler = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private val progressHolder = ProgressHolder()
    private var source: Uri? = null
    private var totalMs = 0L
    private var stepMs = 30_000L
    private var current = 0
    private var count = 0
    private var dir: File? = null
    private var active = false
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val state = transformer?.getProgress(progressHolder)
            val fractional = if (state == Transformer.PROGRESS_STATE_AVAILABLE) progressHolder.progress / 100f else 0f
            val percent = ((current + fractional) / count.coerceAtLeast(1) * 100).toInt().coerceIn(0, 99)
            save("progress", percent)
            notifyProgress(percent, "Processando take ${current + 1} de $count")
            handler.postDelayed(this, 700)
        }
    }
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Processamento StoryCut", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            active = false; handler.removeCallbacks(tick); transformer?.cancel(); save("running", false)
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START && !active) {
            val raw = intent.getStringExtra("uri") ?: return START_NOT_STICKY
            source = Uri.parse(raw)
            stepMs = intent.getLongExtra("step", 30_000L).coerceAtLeast(1000L)
            totalMs = intent.getLongExtra("total", 0L)
            if (totalMs <= 0L) { stopSelf(); return START_NOT_STICKY }
            count = ((totalMs + stepMs - 1) / stepMs).toInt()
            current = 0
            dir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "storycut_${System.currentTimeMillis()}").apply { mkdirs() }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().putString("folder", dir!!.absolutePath)
                .putInt("count", count).putInt("ready", 0).putInt("progress", 0).putBoolean("running", true).apply()
            active = true
            val notif = notification(0, "Preparando processamento")
            if (Build.VERSION.SDK_INT >= 29) startForeground(11, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING.takeIf { Build.VERSION.SDK_INT >= 35 } ?: ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(11, notif)
            handler.post(tick)
            exportNext()
        }
        return START_NOT_STICKY
    }
    private fun exportNext() {
        if (!active) return
        if (current >= count) {
            active = false; handler.removeCallbacks(tick)
            save("progress", 100); save("running", false); save("done", true)
            notifyProgress(100, "Todos os $count takes estão prontos")
            stopForeground(STOP_FOREGROUND_DETACH); stopSelf(); return
        }
        val start = current * stepMs
        val end = min(start + stepMs, totalMs)
        val output = File(dir, "take_${(current + 1).toString().padStart(4, '0')}.mp4")
        val item = MediaItem.Builder().setUri(source!!).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build()
        ).build()
        transformer = Transformer.Builder(this).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                current++
                save("ready", current)
                exportNext()
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                active = false; handler.removeCallbacks(tick)
                save("running", false); save("error", "Falha no take ${current + 1}: ${exportException.message}")
                notifyProgress(getSharedPreferences(PREFS, MODE_PRIVATE).getInt("progress", 0), "Erro no processamento")
                stopForeground(STOP_FOREGROUND_DETACH); stopSelf()
            }
        }).build()
        try { transformer!!.start(item, output.absolutePath) }
        catch (e: Exception) {
            active = false; handler.removeCallbacks(tick); save("running", false); save("error", e.message ?: "Erro desconhecido")
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }
    private fun notification(percent: Int, title: String): Notification {
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("StoryCut").setContentText(title).setContentIntent(pending)
            .setOnlyAlertOnce(true).setOngoing(active).setProgress(100, percent, false).build()
    }
    private fun notifyProgress(percent: Int, title: String) {
        getSystemService(NotificationManager::class.java).notify(11, notification(percent, title))
    }
    private fun save(key: String, value: Int) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(key, value).apply() }
    private fun save(key: String, value: Boolean) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(key, value).apply() }
    private fun save(key: String, value: String) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(key, value).apply() }
}
