package com.tdownload.app

import androidx.annotation.DrawableRes

/** The sites TDownloader knows about, detected from a pasted or shared link. */
enum class Site(val label: String, @DrawableRes val icon: Int, val supported: Boolean) {
    Instagram("Instagram", R.drawable.ic_instagram, supported = true),
    YouTube("YouTube", R.drawable.ic_youtube, supported = true),
    TikTok("TikTok", R.drawable.ic_tiktok, supported = true),
    X("X", R.drawable.ic_x, supported = true),
    Facebook("Facebook", R.drawable.ic_facebook, supported = true),
    WhatsApp("WhatsApp", R.drawable.ic_whatsapp, supported = true);

    /**
     * Sites whose posts we resolve ourselves (no login) into direct media URLs. That is what makes a
     * preview and per-item selection possible; other sites are handed straight to yt-dlp.
     */
    val resolver: ((String) -> DirectPost?)?
        get() = when (this) {
            Instagram -> InstagramFetcher::fetch
            TikTok -> TikTokFetcher::fetch
            X -> XFetcher::fetch
            else -> null
        }

    companion object {
        fun detect(url: String): Site? = when {
            InstagramFetcher.isInstagramUrl(url) -> Instagram
            url.contains("youtube.com") || url.contains("youtu.be") -> YouTube
            TikTokFetcher.isTikTokUrl(url) -> TikTok
            XFetcher.isXUrl(url) -> X
            url.contains("facebook.com") || url.contains("fb.watch") || url.contains("fb.com") -> Facebook
            else -> null
        }
    }
}
