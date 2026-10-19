package com.tdownload.app

import android.content.Context

/**
 * Lets the maintainer retire old versions. The app reads `update.json` from this repository's `main`
 * branch (a plain static file — no server). See the README for the format.
 *
 * The last answer is cached, so a retired version stays blocked even if the phone goes offline. If the
 * file has never been reachable, nothing is blocked.
 */
object UpdateChecker {
    private const val INFO_URL = "https://raw.githubusercontent.com/IamTalha-Sajid/tdownloader/main/update.json"
    private const val PREFS = "update_check"
    private const val KEY_INFO = "info"
    private const val KEY_DISMISSED = "dismissed_latest"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** The last answer we got, judged against the installed version. Instant, works offline. */
    fun cachedStatus(context: Context): UpdateStatus =
        UpdateRules.evaluate(currentVersion(context), prefs(context).getString(KEY_INFO, null)?.let(UpdateRules::parse))

    /** Blocking network call. Falls back to the cached answer if the file can't be fetched or understood. */
    fun refresh(context: Context): UpdateStatus {
        val raw = runCatching { DirectHttp.get(INFO_URL).body }.getOrNull()
        val info = raw?.let(UpdateRules::parse) ?: return cachedStatus(context)
        prefs(context).edit().putString(KEY_INFO, raw).apply()
        return UpdateRules.evaluate(currentVersion(context), info)
    }

    /** Downloads must be refused while this is true. */
    fun isBlocked(context: Context): Boolean = cachedStatus(context) is UpdateStatus.Required

    fun dismissedLatest(context: Context): String? = prefs(context).getString(KEY_DISMISSED, null)

    /** "Later" on the optional prompt: don't ask again until an even newer version is announced. */
    fun dismiss(context: Context, latest: String) {
        prefs(context).edit().putString(KEY_DISMISSED, latest).apply()
    }
}
