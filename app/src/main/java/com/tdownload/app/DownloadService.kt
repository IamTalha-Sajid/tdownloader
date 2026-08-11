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
        const val ACTION_DOWNLOAD_ERROR = "com.tdownload.app.DOWNLOAD_ERROR"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val ACTION_DOWNLOAD_PROGRESS = "com.tdownload.app.DOWNLOAD_PROGRESS"
        const val EXTRA_PROGRESS_PERCENT = "progress_percent"
        const val EXTRA_PROGRESS_TEXT = "progress_text"
        const val ACTION_DOWNLOAD_COMPLETE = "com.tdownload.app.DOWNLOAD_COMPLETE"
        const val EXTRA_COMPLETE_TITLE = "complete_title"
        private val notifIdCounter = AtomicInteger(1000)

        fun platformFromUrl(url: String) = when {
            url.contains("instagram.com") || url.contains("instagr.am") -> "Instagram"
            url.contains("youtube.com") || url.contains("youtu.be") -> "YouTube"
            url.contains("tiktok.com") -> "TikTok"
            else -> ""
        }
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

            // %(playlist_index)s disambiguates carousel items (e.g. multi-image Instagram posts),
            // which otherwise all share the same caption-derived title and overwrite each other.
            // %(ext)s (not a hardcoded "mp4") keeps images as .jpg/.webp instead of mislabeling them.
            val outTemplate = "${outDir.absolutePath}/%(title).80s%(playlist_index|)s.%(ext)s"

            // yt-dlp hard-errors ("No video formats found!") when a format selector's first
            // "/"-alternative is a bestvideo+bestaudio merge and the entry has no video-codec
            // formats at all — true for image slides in an Instagram carousel. Drop those
            // merge alternatives for Instagram and keep only the plain best[...] fallbacks
            // (Instagram serves pre-muxed video anyway, so nothing is lost for real videos).
            val safeFormat = if (isInstagram) {
                format.split("/").filterNot { it.contains("bestvideo") }.joinToString("/").ifBlank { "best" }
            } else format

            val req = YoutubeDLRequest(url).apply {
                addOption("-o", outTemplate)
                addOption("-f", safeFormat)
                if (!audioOnly) addOption("--merge-output-format", "mp4")
                if (audioOnly) {
                    addOption("--extract-audio")
                    addOption("--audio-format", "m4a")
                }
                if (isInstagram) {
                    // Instagram carousel posts are yt-dlp "playlists" of images/videos — download all of them.
                    // Capped so a mistakenly shared profile/hashtag link can't trigger an unbounded scrape.
                    addOption("--playlist-items", "1-50")
                    // The Instagram extractor hard-errors on some image-only carousel slides
                    // ("No video formats found!"); without this one bad slide aborts the whole post.
                    addOption("--ignore-errors")
                } else {
                    addOption("--no-playlist")
                }
                addOption("--no-mtime")
                addOption("--newline")
                addOption("--add-header", "User-Agent:Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36")
                if (isInstagram && !url.contains("instagram.com/p/")) {
                    // The graphql API's carousel handling has a bug that hard-errors on some
                    // image slides in "/p/" posts, so only use it for single-video reels/tv.
                    addOption("--extractor-args", "instagram:api=graphql")
                }
                if (cookiesFile.exists()) addOption("--cookies", cookiesFile.absolutePath)
            }

            var lastTitle = ""
            val startTime = System.currentTimeMillis()

            var hadError: Exception? = null
            try {
            YoutubeDL.getInstance().execute(req, processId) { progress, etaSeconds, line ->
                when {
                    line.contains("[download] Destination:") -> {
                        val path = line.substringAfter("[download] Destination:").trim()
                        lastTitle = File(path).nameWithoutExtension
                    }
                    line.contains("[Merger] Merging formats into") || line.contains("[ExtractAudio] Destination:") -> {
                        val path = Regex("\"(.+)\"").find(line)?.groupValues?.get(1)
                            ?: line.substringAfter("Destination:").trim()
                        if (path.isNotBlank()) lastTitle = File(path).nameWithoutExtension
                        nm.notify(notifId, progressNotif(
                            title = "Merging…",
                            subText = lastTitle,
                            progress = 100,
                            indeterminate = true,
                        ).build())
                        broadcastProgress(100, "Merging… $lastTitle")
                        return@execute
                    }
                    line.contains("[Merger]") || line.contains("[ffmpeg]") -> {
                        nm.notify(notifId, progressNotif(
                            title = "Merging…",
                            subText = lastTitle,
                            progress = 100,
                            indeterminate = true,
                        ).build())
                        broadcastProgress(100, "Merging… $lastTitle")
                        return@execute
                    }
                }

                val pct = progress.toInt().coerceIn(0, 100)
                val parsed = parseProgressLine(line)
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
                    title = "Downloading $pct%",
                    subText = if (detail.isNotBlank()) detail else lastTitle,
                    progress = pct,
                    indeterminate = pct == 0,
                ).build())
                broadcastProgress(pct, if (detail.isNotBlank()) detail else lastTitle)
            }
            } catch (e: Exception) {
                // With --ignore-errors (Instagram carousels), yt-dlp can still exit non-zero after
                // successfully saving some entries — check what actually landed before giving up.
                if (!isInstagram) throw e
                hadError = e
            }

            // Resolve every file this run produced (one for a normal video/audio download,
            // several for a multi-image/video Instagram carousel) by mtime rather than trying
            // to track a single path through yt-dlp's per-stream/merge log lines. Recursive
            // because gallery-dl (image carousel fallback) may nest output into subdirectories.
            fun scanNewFiles() = outDir.walkTopDown()
                .filter { f ->
                    f.isFile && f.lastModified() >= startTime - 1000 &&
                        !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") && !f.name.endsWith(".temp")
                }
                .sortedBy { it.name }
                .toList()

            var newFiles = scanNewFiles()

            // yt-dlp's Instagram extractor hard-errors on carousels with image slides
            // ("No video formats found!") before per-entry --ignore-errors can even help —
            // it happens while eagerly building the carousel's entry list. gallery-dl handles
            // mixed image/video carousels correctly, so fall back to it for this specific failure.
            if (newFiles.isEmpty() && isInstagram &&
                (hadError?.message?.contains("No video formats found", ignoreCase = true) == true)
            ) {
                nm.notify(notifId, progressNotif("Fetching images…", progress = 0, indeterminate = true).build())
                broadcastProgress(0, "Fetching images…")
                // --no-mtime: gallery-dl otherwise stamps files with the post's original
                // publish date, which broke our download-time-based newFiles scan below.
                val gdlArgs = mutableListOf("-D", outDir.absolutePath, "--no-input", "--no-mtime", "--http-timeout", "30")
                if (cookiesFile.exists()) gdlArgs.addAll(listOf("-C", cookiesFile.absolutePath))
                gdlArgs.add(url)
                // gallery-dl prints one absolute file path per carousel item — freshly downloaded
                // ones plain, already-on-disk ones prefixed "# " (skipped). Either way it's a real,
                // successfully-fetched file, so read the list directly instead of scanning by mtime
                // (which misses re-runs where every item was already downloaded and thus skipped).
                val gdlPaths = mutableListOf<String>()
                val exitCode = runCatching {
                    GalleryDlManager.run(applicationContext, gdlArgs) { line ->
                        if (line.isNotBlank()) {
                            nm.notify(notifId, progressNotif("Fetching images…", subText = line.take(60), progress = 0, indeterminate = true).build())
                            val path = line.removePrefix("#").trim()
                            if (File(path).isFile) gdlPaths.add(path)
                        }
                    }
                }.getOrDefault(-1)
                newFiles = gdlPaths.map { File(it) }.distinct().sortedBy { it.name }
                if (newFiles.isEmpty()) newFiles = scanNewFiles()
                if (newFiles.isNotEmpty() || exitCode == 0) hadError = null
            }

            if (newFiles.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    applicationContext, newFiles.map { it.absolutePath }.toTypedArray(), null, null
                )
                for (f in newFiles) {
                    DownloadStore.append(
                        applicationContext,
                        DownloadRecord(
                            id = UUID.randomUUID().toString(),
                            title = f.nameWithoutExtension,
                            path = f.absolutePath,
                            timestamp = System.currentTimeMillis(),
                            platform = platformFromUrl(url),
                            fileSizeBytes = f.length(),
                        )
                    )
                }
                val doneTitle = if (newFiles.size == 1) lastTitle else "${newFiles.size} items"
                nm.notify(notifId, doneNotif(doneTitle, newFiles.last().absolutePath).build())
                broadcastComplete(doneTitle)
            } else if (hadError != null) {
                throw hadError!!
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

    private fun broadcastProgress(percent: Int, text: String) {
        sendBroadcast(Intent(ACTION_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS_PERCENT, percent)
            putExtra(EXTRA_PROGRESS_TEXT, text)
            setPackage(packageName)
        })
    }

    private fun broadcastComplete(title: String) {
        sendBroadcast(Intent(ACTION_DOWNLOAD_COMPLETE).apply {
            putExtra(EXTRA_COMPLETE_TITLE, title)
            setPackage(packageName)
        })
    }

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
        // The in-app progress modal only has a terminal state (Done/Error) via this broadcast —
        // every failure path must send it, or the modal is stuck showing progress forever.
        sendBroadcast(Intent(ACTION_DOWNLOAD_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, reason)
            setPackage(packageName)
        })
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
