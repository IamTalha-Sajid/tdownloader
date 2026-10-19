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
        /** Optional IntArray: indexes of the post's items to download (default: all). */
        const val EXTRA_ITEMS = "items"
        const val CHANNEL_ID = "tdownload_channel"
        private val notifIdCounter = AtomicInteger(1000)

        fun platformFromUrl(url: String) = Site.detect(url)?.label.orEmpty()
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
        val items = intent.getIntArrayExtra(EXTRA_ITEMS)
        val notifId = notifIdCounter.getAndIncrement()
        startForeground(notifId, progressNotif("Starting…", progress = 0, indeterminate = true).build())
        // A retired version must not download anything, whichever screen started this (app or share sheet).
        if (UpdateChecker.isBlocked(this)) {
            showError(getSystemService(NOTIFICATION_SERVICE) as NotificationManager, notifId, "This version is no longer supported — please update TDownloader")
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        DownloadTracker.start(notifId, Site.detect(url), url)

        scope.launch { runDownload(url, format, audioOnly, items, notifId, startId) }
        return START_NOT_STICKY
    }

    private suspend fun runDownload(url: String, format: String, audioOnly: Boolean, items: IntArray?, notifId: Int, startId: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        var isInstagram = InstagramFetcher.isInstagramUrl(url)

        try {
            YtdlManager.ensureInit(applicationContext)

            val outDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TDownload"
            ).apply { mkdirs() }

            val processId = UUID.randomUUID().toString()

            // %(playlist_index)s disambiguates carousel items (e.g. multi-image Instagram posts),
            // which otherwise all share the same caption-derived title and overwrite each other.
            // %(ext)s (not a hardcoded "mp4") keeps images as .jpg/.webp instead of mislabeling them.
            var outTemplate = "${outDir.absolutePath}/%(title).80s%(playlist_index|)s.%(ext)s"
            var targetUrl = url
            var ytFormat = format
            var extraHeaders = emptyMap<String, String>()

            val directFetch = Site.detect(url)?.resolver
            if (directFetch != null) {
                // Public posts resolve without a login. Videos/images/carousels are downloaded directly;
                // audio-only is the one case that still needs yt-dlp's ffmpeg, so it extracts from the
                // direct video URL instead. Anything we can't resolve falls through to yt-dlp.
                DownloadTracker.progress(notifId, 0, "Fetching post…")
                val resolved = directFetch(url)
                // Honour an item selection from the UI; ignore it if it doesn't match what we resolved now.
                val post = resolved?.let { r ->
                    val chosen = r.items.filterIndexed { i, _ -> items == null || i in items }
                    if (chosen.isEmpty()) r else r.copy(items = chosen)
                }
                post?.let { DownloadTracker.title(notifId, it.baseName) }
                val video = post?.items?.firstOrNull { it.isVideo }
                if (post != null && audioOnly && video != null) {
                    targetUrl = video.url
                    extraHeaders = video.headers
                    outTemplate = "${outDir.absolutePath}/${post.baseName}.%(ext)s"
                    // A direct URL has a single muxed format, which "bestaudio" (audio-only) can't select.
                    ytFormat = "ba*/best"
                    isInstagram = false // the rest of the pipeline now sees a plain direct media URL
                } else if (post != null) {
                    val files = downloadPost(post, outDir, nm, notifId)
                    finishWithFiles(nm, notifId, url, files, if (files.size == 1) post.baseName else "${files.size} items")
                    return
                }
            }

            // yt-dlp hard-errors ("No video formats found!") when a format selector's first
            // "/"-alternative is a bestvideo+bestaudio merge and the entry has no video-codec
            // formats at all — true for image slides in an Instagram carousel. Drop those
            // merge alternatives for Instagram and keep only the plain best[...] fallbacks
            // (Instagram serves pre-muxed video anyway, so nothing is lost for real videos).
            val safeFormat = if (isInstagram) {
                ytFormat.split("/").filterNot { it.contains("bestvideo") }.joinToString("/").ifBlank { "best" }
            } else ytFormat

            val req = YoutubeDLRequest(targetUrl).apply {
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
                // Cookies/Referer a direct media URL needs (TikTok's CDN 403s without them).
                extraHeaders.filterKeys { it != "User-Agent" }.forEach { (name, value) -> addOption("--add-header", "$name:$value") }
                if (isInstagram && !url.contains("instagram.com/p/")) {
                    // The graphql API's carousel handling has a bug that hard-errors on some
                    // image slides in "/p/" posts, so only use it for single-video reels/tv.
                    addOption("--extractor-args", "instagram:api=graphql")
                }
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
                        DownloadTracker.title(notifId, lastTitle)
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
                        DownloadTracker.progress(notifId, 100, "Merging… $lastTitle")
                        return@execute
                    }
                    line.contains("[Merger]") || line.contains("[ffmpeg]") -> {
                        nm.notify(notifId, progressNotif(
                            title = "Merging…",
                            subText = lastTitle,
                            progress = 100,
                            indeterminate = true,
                        ).build())
                        DownloadTracker.progress(notifId, 100, "Merging… $lastTitle")
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
                DownloadTracker.progress(notifId, pct, if (detail.isNotBlank()) detail else lastTitle)
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
                DownloadTracker.progress(notifId, 0, "Fetching images…")
                // --no-mtime: gallery-dl otherwise stamps files with the post's original
                // publish date, which broke our download-time-based newFiles scan below.
                val gdlArgs = mutableListOf("-D", outDir.absolutePath, "--no-input", "--no-mtime", "--http-timeout", "30")
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
                finishWithFiles(nm, notifId, url, newFiles, if (newFiles.size == 1) lastTitle else "${newFiles.size} items")
            } else if (hadError != null) {
                throw hadError!!
            } else {
                showError(nm, notifId, "Saved (path unknown)")
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val reason = when {
                isInstagram && (msg.contains("empty", ignoreCase = true) || msg.contains("login", ignoreCase = true) || msg.contains("logged", ignoreCase = true)) ->
                    "Couldn't fetch this Instagram post — it may be private or removed"
                msg.contains("private", ignoreCase = true) ->
                    "Private content can't be downloaded"
                msg.contains("network", ignoreCase = true) || msg.contains("Unable to connect", ignoreCase = true) ->
                    "Network error — check your connection"
                Site.detect(url) == Site.X ->
                    "Couldn't find photos or video in this X post — it may be private, deleted, or text-only"
                Site.detect(url).let { it == Site.TikTok || it == Site.Facebook } ->
                    "Couldn't fetch this ${Site.detect(url)?.label} video — it may be private, removed, or unavailable in your region"
                else -> msg.take(80).ifBlank { "Unknown error" }
            }
            showError(nm, notifId, reason)
        } finally {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
    }

    /** Downloads every item of a resolved post, returning the saved files in post order. */
    private fun downloadPost(
        post: DirectPost, outDir: File, nm: NotificationManager, notifId: Int,
    ): List<File> {
        val count = post.items.size
        return post.items.mapIndexed { i, media ->
            val stem = if (count > 1) "${post.baseName} ${i + 1}" else post.baseName
            var dest = File(outDir, "$stem.${media.extension}")
            // Don't clobber a different earlier download that happens to share a caption.
            if (dest.exists()) dest = File(outDir, "$stem [${post.id}].${media.extension}")
            DirectHttp.download(media, dest) { done, total ->
                val fraction = if (total > 0) done.toFloat() / total else 0f
                val pct = (((i + fraction) / count) * 100).toInt().coerceIn(0, 100)
                val detail = buildString {
                    if (count > 1) append("${i + 1} of $count · ")
                    append(formatSize(done))
                    if (total > 0) append(" / ${formatSize(total)}")
                }
                nm.notify(notifId, progressNotif("Downloading $pct%", detail, pct, indeterminate = false).build())
                DownloadTracker.progress(notifId, pct, detail)
            }
            dest
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

    private suspend fun finishWithFiles(nm: NotificationManager, notifId: Int, url: String, files: List<File>, title: String) {
        MediaScannerConnection.scanFile(applicationContext, files.map { it.absolutePath }.toTypedArray(), null, null)
        for (f in files) {
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
        nm.notify(notifId, doneNotif(title, files.last().absolutePath).build())
        DownloadTracker.done(notifId, title)
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
        DownloadTracker.fail(notifId, reason)
    }

    private fun buildOpenIntent(path: String): PendingIntent {
        val file = File(path)
        val uri: Uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
        val mime = when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "webp", "heic" -> "image/*"
            "m4a", "mp3" -> "audio/*"
            else -> "video/*"
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
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
