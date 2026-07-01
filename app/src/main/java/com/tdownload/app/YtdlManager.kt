package com.tdownload.app

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

object YtdlManager {
    @Volatile private var initialized = false

    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            initialized = true
        }
    }

    fun getVersion(context: Context): String =
        runCatching { YoutubeDL.getInstance().version(context) ?: "unknown" }.getOrDefault("unknown")

    fun update(context: Context): YoutubeDL.UpdateStatus =
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            ?: YoutubeDL.UpdateStatus.DONE
}
