package com.tdownload.app

import org.json.JSONObject

/** What the maintainer's `update.json` says. */
data class UpdateInfo(val latest: String, val url: String, val message: String)

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus

    /** A newer version exists: this one is outdated and the app must not be used until it is updated. */
    data class Required(val info: UpdateInfo) : UpdateStatus
}

/**
 * The decisions behind the update gate, with no Android dependencies so they can be tested on a plain JVM.
 * Every uncertain case (missing file, bad JSON, unreadable version) resolves to "no restriction": a broken
 * or unreachable `update.json` must never lock people out of the app.
 */
internal object UpdateRules {
    const val DEFAULT_URL = "https://github.com/IamTalha-Sajid/tdownloader/releases/latest"

    private val LEADING_VERSION = Regex("""^\d+(\.\d+)*""")

    /** "1.3.0" -> [1, 3, 0]; "1.3.0-beta" -> [1, 3, 0]; anything not starting with a number -> null. */
    fun versionParts(version: String): List<Int>? =
        LEADING_VERSION.find(version.trim())?.value?.split('.')?.map { it.toIntOrNull() ?: return null }

    fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /** `latest` must be a valid version, otherwise the whole file is ignored. */
    fun parse(json: String): UpdateInfo? = runCatching {
        val o = JSONObject(json)
        val latest = o.optString("latest")
        if (versionParts(latest) == null) return null
        UpdateInfo(latest, o.optString("url").ifBlank { DEFAULT_URL }, o.optString("message"))
    }.getOrNull()

    /** Anything older than `latest` is outdated and blocked. */
    fun evaluate(installed: String, info: UpdateInfo?): UpdateStatus {
        val current = versionParts(installed) ?: return UpdateStatus.UpToDate
        info ?: return UpdateStatus.UpToDate
        val latest = versionParts(info.latest) ?: return UpdateStatus.UpToDate
        return if (compare(current, latest) < 0) UpdateStatus.Required(info) else UpdateStatus.UpToDate
    }
}
