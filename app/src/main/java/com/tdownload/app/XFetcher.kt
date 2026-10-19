package com.tdownload.app

import org.json.JSONObject
import java.net.URI

/**
 * Resolves a public X (Twitter) post's photos, videos and GIFs to direct media URLs without a login,
 * through X's public embed API (`cdn.syndication.twimg.com/tweet-result`) — the same endpoint that
 * renders tweets embedded on other websites. Returns null for anything it can't resolve (deleted,
 * protected, age-restricted, no media) so the caller can fall back to yt-dlp.
 */
object XFetcher {

    private val TWEET_ID = Regex("""/status(?:es)?/(\d+)""")
    private val TCO_LINK = Regex("""\s*https?://t\.co/\S+""")

    /** Host-based, since "x.com" as a substring would also match sites like "netflix.com". */
    fun isXUrl(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "x.com" || host.endsWith(".x.com") ||
            host == "twitter.com" || host.endsWith(".twitter.com") || host == "t.co"
    }

    /** Blocking network call — run off the main thread. */
    fun fetch(url: String): DirectPost? = runCatching {
        // t.co short links redirect to the tweet URL.
        val id = idOf(url) ?: idOf(DirectHttp.finalUrl(url)) ?: return null
        parse(id, DirectHttp.get("https://cdn.syndication.twimg.com/tweet-result?id=$id&token=a").body)
    }.getOrNull()

    internal fun idOf(url: String): String? = TWEET_ID.find(url)?.groupValues?.get(1)

    internal fun parse(id: String, json: String): DirectPost? {
        val tweet = JSONObject(json)
        val details = tweet.optJSONArray("mediaDetails") ?: return null
        val items = (0 until details.length()).map { mediaOf(details.optJSONObject(it)) }
        // Never return a post with a media item we couldn't resolve (it would silently drop it).
        if (items.isEmpty() || items.any { it == null }) return null

        val owner = tweet.optJSONObject("user")?.optString("screen_name").orEmpty()
        // The API HTML-escapes the text; "&amp;" goes last so "&amp;lt;" doesn't become "<".
        val caption = tweet.optString("text").replace(TCO_LINK, "").trim()
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        return DirectPost("x", id, owner, caption, items.filterNotNull())
    }

    private fun mediaOf(media: JSONObject?): DirectMedia? {
        val still = media?.optString("media_url_https")?.takeIf { it.isNotBlank() } ?: return null
        return when (media.optString("type")) {
            // "name=orig" is the original upload; the bare URL is a much smaller rendition.
            "photo" -> DirectMedia("$still?name=orig", isVideo = false, thumbnailUrl = "$still?name=small")
            "video", "animated_gif" -> {
                val variants = media.optJSONObject("video_info")?.optJSONArray("variants")
                val best = variants?.let { v ->
                    (0 until v.length()).mapNotNull { v.optJSONObject(it) }
                        .filter { it.optString("content_type") == "video/mp4" && it.optString("url").isNotBlank() }
                        .maxByOrNull { it.optInt("bitrate") }
                }
                best?.let { DirectMedia(it.optString("url"), isVideo = true, thumbnailUrl = still) }
            }
            else -> null
        }
    }
}
