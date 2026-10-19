package com.tdownload.app

import org.json.JSONObject

/**
 * Resolves a public TikTok video or photo post to direct media URLs without any login, by reading the
 * public web page. With a mobile user agent TikTok serves a page that embeds the post as JSON in
 * `<script id="api-data">`. The media CDN rejects requests that lack the cookies set by that page, so
 * they travel with each [DirectMedia]. Returns null for anything it can't resolve (private, removed,
 * region-blocked, page format changed) so the caller can fall back to yt-dlp.
 */
object TikTokFetcher {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro XL) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.82 Mobile Safari/537.36"
    private val VIDEO_ID = Regex("""/(?:video|photo|v)/(\d{10,})""")
    private val API_DATA = Regex("""<script id="api-data" type="application/json">\s*(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    fun isTikTokUrl(url: String) = url.contains("tiktok.com")

    /** Blocking network call — run off the main thread. */
    fun fetch(url: String): DirectPost? = runCatching {
        val pageHeaders = mapOf("User-Agent" to USER_AGENT)
        // Short links (vm./vt.tiktok.com, tiktok.com/t/…) redirect to the canonical video URL.
        val pageUrl = if (idOf(url) != null) url else DirectHttp.finalUrl(url, pageHeaders)
        val id = idOf(pageUrl) ?: return null
        val page = DirectHttp.get(pageUrl, pageHeaders)
        parse(id, page.body, page.cookies)
    }.getOrNull()

    internal fun idOf(url: String): String? = VIDEO_ID.find(url)?.groupValues?.get(1)

    internal fun parse(id: String, html: String, cookies: String): DirectPost? {
        val json = API_DATA.find(html)?.groupValues?.get(1) ?: return null
        val item = JSONObject(json).optJSONObject("videoDetail")?.optJSONObject("itemInfo")
            ?.optJSONObject("itemStruct") ?: return null

        val headers = buildMap {
            put("User-Agent", USER_AGENT)
            put("Referer", "https://www.tiktok.com/")
            if (cookies.isNotBlank()) put("Cookie", cookies)
        }

        val images = item.optJSONObject("imagePost")?.optJSONArray("images")
        val items = if (images != null) {
            (0 until images.length()).map { i ->
                val url = images.optJSONObject(i)?.optJSONObject("imageURL")?.optJSONArray("urlList")?.optString(0)
                DirectMedia(url.orEmpty(), isVideo = false, headers = headers, thumbnailUrl = url)
            }
        } else {
            val video = item.optJSONObject("video")
            val url = video?.optString("playAddr").orEmpty().ifBlank { video?.optString("downloadAddr").orEmpty() }
            val cover = video?.optString("cover").orEmpty().ifBlank { video?.optString("originCover").orEmpty() }
            listOf(DirectMedia(url, isVideo = true, headers = headers, thumbnailUrl = cover.ifBlank { null }))
        }
        // Like Instagram carousels: never return a post with a slide we couldn't resolve.
        if (items.isEmpty() || items.any { it.url.isBlank() }) return null

        val owner = item.optJSONObject("author")?.optString("uniqueId").orEmpty()
        return DirectPost("tiktok", id, owner, item.optString("desc"), items)
    }
}
