package com.tdownload.app

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * One downloadable file resolved from a post. [headers] must accompany the download (e.g. cookies).
 * [thumbnailUrl] is a still image for previews (for photos, the photo itself).
 */
data class DirectMedia(
    val url: String,
    val isVideo: Boolean,
    val headers: Map<String, String> = emptyMap(),
    val thumbnailUrl: String? = null,
) {
    val extension: String
        get() {
            if (isVideo) return "mp4"
            val ext = runCatching { URL(url).path.substringAfterLast('.', "") }.getOrDefault("").lowercase()
            return if (ext in IMAGE_EXTENSIONS) ext else "jpg"
        }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic")
    }
}

/** A post resolved to direct media URLs by one of the per-site fetchers. */
data class DirectPost(
    val platform: String,
    val id: String,
    val owner: String,
    val caption: String,
    val items: List<DirectMedia>,
) {
    /** A readable, filesystem- and yt-dlp-template-safe file name stem for this post. */
    val baseName: String
        get() {
            val text = caption.lineSequence().firstOrNull().orEmpty()
                .replace(Regex("""[\\/:*?"<>|%]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(60)
                .trim()
            return text.ifBlank { "${platform}_$id" }
        }
}

/** HTTP plumbing shared by the per-site fetchers. All calls block — run them off the main thread. */
internal object DirectHttp {

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

    class Page(val body: String, val cookies: String)

    fun get(url: String, headers: Map<String, String> = emptyMap()): Page {
        val conn = open(url, headers)
        val cookies = conn.headerFields.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value }
            .joinToString("; ") { it.substringBefore(';') }
        return Page(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }, cookies)
    }

    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        open(url, headers).inputStream.use { it.readBytes() }

    /** Follows redirects (short links, share links) and returns the final URL. */
    fun finalUrl(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = open(url, headers)
        return try { conn.url.toString() } finally { conn.disconnect() }
    }

    /** Downloads [media] to [dest], reporting (bytesDone, bytesTotal or -1) at most ~5 times a second. */
    fun download(media: DirectMedia, dest: File, onProgress: (done: Long, total: Long) -> Unit) {
        val part = File(dest.parentFile, dest.name + ".part")
        try {
            val conn = open(media.url, media.headers)
            val total = conn.contentLengthLong
            var done = 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 200) {
                            lastReport = now
                            onProgress(done, total)
                        }
                    }
                }
            }
            onProgress(done, total)
            if (!part.renameTo(dest)) throw IOException("Couldn't save ${dest.name}")
        } finally {
            part.delete()
        }
    }

    private fun open(url: String, headers: Map<String, String>): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }.also {
            if (it.responseCode !in 200..299) throw IOException("HTTP ${it.responseCode}")
        }
}
