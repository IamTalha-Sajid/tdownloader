package com.tdownload.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_AUDIO_ONLY = "audio_only"
        const val EXTRA_COOKIES_FILE = "cookies_file"
        const val CHANNEL_ID = "tdownload_channel"
        private val notifIdCounter = AtomicInteger(1000)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val format = intent.getStringExtra(EXTRA_FORMAT)
            ?: "bestvideo[ext=mp4][vcodec^=avc]+bestaudio[ext=m4a]/bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
        val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)
        val cookiesFileName = intent.getStringExtra(EXTRA_COOKIES_FILE) ?: "cookies.txt"
        val notifId = notifIdCounter.getAndIncrement()
        startForeground(notifId, progressNotif("Starting…", progress = 0, indeterminate = true).build())

        scope.launch { runDownload(url, format, audioOnly, cookiesFileName, notifId, startId) }
        return START_NOT_STICKY
    }

    private suspend fun runDownload(url: String, format: String, audioOnly: Boolean, cookiesFileName: String, notifId: Int, startId: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val isInstagram = url.contains("instagram.com") || url.contains("instagr.am")

        try {
            YtdlManager.ensureInit(applicationContext)

            val outDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TDownload"
            ).apply { mkdirs() }

            val cookiesFile = File(filesDir, cookiesFileName)
            val processId = UUID.randomUUID().toString()

            val ext = if (audioOnly) "%(ext)s" else "mp4"
            val outTemplate = if (audioOnly)
                "${outDir.absolutePath}/%(title).80s.%(ext)s"
            else
                "${outDir.absolutePath}/%(title).80s.$ext"

            val req = YoutubeDLRequest(url).apply {
                addOption("-o", outTemplate)
                addOption("-f", format)
                if (!audioOnly) addOption("--merge-output-format", "mp4")
                if (audioOnly) {
                    addOption("--extract-audio")
                    addOption("--audio-format", "m4a")
                }
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--newline")
                addOption("--add-header", "User-Agent:Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36")
                if (isInstagram) {
                    addOption("--extractor-args", "instagram:api=graphql")
                }
                if (cookiesFile.exists()) addOption("--cookies", cookiesFile.absolutePath)
            }

            var lastTitle = "video"
            var downloadedPath: String? = null
            var streamIndex = 0  // tracks video(1) vs audio(2) pass

            YoutubeDL.getInstance().execute(req, processId) { progress, etaSeconds, line ->
                when {
                    line.contains("[download] Destination:") -> {
                        downloadedPath = line.substringAfter("[download] Destination:").trim()
                        lastTitle = File(downloadedPath!!).nameWithoutExtension
                        streamIndex++
                    }
                    line.contains("[Merger]") || line.contains("[ffmpeg]") -> {
                        nm.notify(notifId, progressNotif(
                            title = "Merging…",
                            subText = lastTitle,
                            progress = 100,
                            indeterminate = true,
                        ).build())
                        return@execute
                    }
                }

                val pct = progress.toInt().coerceIn(0, 100)
                val parsed = parseProgressLine(line)
                val streamLabel = when (streamIndex) {
                    1 -> "Video"
                    2 -> "Audio"
                    else -> "Downloading"
                }
                val detail = buildString {
                    if (parsed.speed.isNotBlank()) append(parsed.speed)
                    if (parsed.downloaded.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(parsed.downloaded)
                        if (parsed.total.isNotBlank()) append(" / ${parsed.total}")
                    }
                    if (etaSeconds > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${formatEta(etaSeconds)} left")
                    }
                }
                nm.notify(notifId, progressNotif(
                    title = "$streamLabel $pct%",
                    subText = if (detail.isNotBlank()) detail else lastTitle,
                    progress = pct,
                    indeterminate = pct == 0,
                ).build())
            }

            val finalPath = downloadedPath ?: findLatestFile(outDir)
            if (finalPath != null) {
                MediaScannerConnection.scanFile(
                    applicationContext, arrayOf(finalPath), arrayOf("video/*"), null
                )
                DownloadStore.append(
                    applicationContext,
                    DownloadRecord(lastTitle, finalPath, System.currentTimeMillis())
                )
                nm.notify(notifId, doneNotif(lastTitle, finalPath).build())
            } else {
                showError(nm, notifId, "Saved (path unknown)")
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val reason = when {
                isInstagram && (msg.contains("empty", ignoreCase = true) || msg.contains("login", ignoreCase = true) || msg.contains("logged", ignoreCase = true)) ->
                    "Instagram requires login — add session ID in TDownloader"
                msg.contains("private", ignoreCase = true) ->
                    "Private content — login required"
                msg.contains("network", ignoreCase = true) || msg.contains("Unable to connect", ignoreCase = true) ->
                    "Network error — check your connection"
                else -> msg.take(80).ifBlank { "Unknown error" }
            }
            showError(nm, notifId, reason)
        } finally {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
    }

    private fun progressNotif(title: String, subText: String = "", progress: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(subText)
            .setColor(0xFFFFD60A.toInt())
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)

    private data class ProgressInfo(val speed: String, val downloaded: String, val total: String)

    // Parses: [download]  47.3% of 123.45MiB at 1.23MiB/s ETA 00:45
    private fun parseProgressLine(line: String): ProgressInfo {
        val speedMatch = Regex("""at\s+([\d.]+\s*\w+/s)""").find(line)
        val sizeMatch = Regex("""([\d.]+\s*\w+iB)\s+/\s+([\d.]+\s*\w+iB)""").find(line)
        val ofMatch = Regex("""of\s+([\d.]+\s*\w+iB)""").find(line)
        return ProgressInfo(
            speed = speedMatch?.groupValues?.get(1) ?: "",
            downloaded = sizeMatch?.groupValues?.get(1) ?: "",
            total = sizeMatch?.groupValues?.get(2) ?: ofMatch?.groupValues?.get(1) ?: "",
        )
    }

    private fun formatEta(seconds: Long): String = when {
        seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds}s"
    }

    private fun doneNotif(title: String, path: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Saved ✓")
            .setContentText(title)
            .setColor(0xFF34D399.toInt())
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(buildOpenIntent(path))

    private fun showError(nm: NotificationManager, notifId: Int, reason: String) {
        nm.notify(
            notifId,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Failed")
                .setContentText(reason)
                .setColor(0xFFF87171.toInt())
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun buildOpenIntent(path: String): PendingIntent {
        val file = File(path)
        val uri: Uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return PendingIntent.getActivity(
            this, path.hashCode(), viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun findLatestFile(dir: File): String? =
        dir.listFiles()?.maxByOrNull { it.lastModified() }?.absolutePath

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            enableLights(false)
            enableVibration(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
