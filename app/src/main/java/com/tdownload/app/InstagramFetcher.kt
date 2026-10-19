package com.tdownload.app

import org.json.JSONObject
import org.json.JSONTokener

/**
 * Resolves a public Instagram post to direct media URLs without any login, by reading the
 * logged-out `/p/<code>/embed/` page, which embeds the post's media as JSON. Handles videos/reels,
 * single images and carousels. Returns null for anything it can't fully resolve (private, removed,
 * embedding disabled, page format changed) so the caller can fall back to yt-dlp / gallery-dl.
 */
object InstagramFetcher {

    private const val CONTEXT_KEY = "\"contextJSON\":"
    private val SHORTCODE = Regex("""(?:instagram\.com|instagr\.am)/(?:[\w.]+/)?(?:p|reels?|tv)/([\w-]+)""")

    fun isInstagramUrl(url: String) = url.contains("instagram.com") || url.contains("instagr.am")

    /** Blocking network call — run off the main thread. */
    fun fetch(url: String): DirectPost? = runCatching {
        val code = shortcodeOf(url) ?: shortcodeOf(DirectHttp.finalUrl(url)) ?: return null
        parse(code, DirectHttp.get("https://www.instagram.com/p/$code/embed/").body)
    }.getOrNull()

    internal fun shortcodeOf(url: String): String? = SHORTCODE.find(url)?.groupValues?.get(1)

    internal fun parse(shortcode: String, html: String): DirectPost? {
        val start = html.indexOf(CONTEXT_KEY).takeIf { it >= 0 }?.plus(CONTEXT_KEY.length) ?: return null
        val contextJson = JSONTokener(html.substring(start)).nextValue() as? String ?: return null
        val post = JSONObject(contextJson).optJSONObject("gql_data")?.optJSONObject("shortcode_media") ?: return null

        val edges = post.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        val nodes = if (edges != null) {
            (0 until edges.length()).mapNotNull { edges.optJSONObject(it)?.optJSONObject("node") }
        } else listOf(post)

        val items = nodes.mapNotNull(::mediaOf)
        // A partial carousel would silently drop slides — treat it as unresolved instead.
        if (items.isEmpty() || items.size != nodes.size) return null

        val caption = post.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
            ?.optJSONObject(0)?.optJSONObject("node")?.optString("text").orEmpty()
        val owner = post.optJSONObject("owner")?.optString("username").orEmpty()
        return DirectPost("instagram", shortcode, owner, caption, items)
    }

    private fun mediaOf(node: JSONObject): DirectMedia? {
        val still = node.optString("display_url").takeIf { it.isNotBlank() }
        if (node.optBoolean("is_video")) {
            // Never fall back to display_url as the media itself — for a video it's only the thumbnail.
            return node.optString("video_url").takeIf { it.isNotBlank() }
                ?.let { DirectMedia(it, isVideo = true, thumbnailUrl = still) }
        }
        return still?.let { DirectMedia(it, isVideo = false, thumbnailUrl = it) }
    }
}
