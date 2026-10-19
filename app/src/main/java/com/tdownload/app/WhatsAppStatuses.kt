package com.tdownload.app

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Lists and saves the statuses WhatsApp has cached on this device (the ones you've viewed).
 *
 * Android doesn't let one app read another's `Android/media` folder directly, so the user grants access
 * to the Statuses folder once through the system folder picker (Storage Access Framework). Only that
 * folder is shared, nothing leaves the phone, and the grant is kept until the user disconnects.
 */
object WhatsAppStatuses {

    enum class Source(val pkg: String, val label: String, private val folder: String) {
        WhatsApp("com.whatsapp", "WhatsApp", "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
        Business("com.whatsapp.w4b", "WhatsApp Business", "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses");

        /** Where the folder picker should open, so the user only has to confirm. */
        val pickerHint: Uri get() = DocumentsContract.buildDocumentUri(STORAGE_AUTHORITY, "primary:$folder")
    }

    data class Status(
        val id: String,
        val uri: Uri,
        val name: String,
        val isVideo: Boolean,
        val modified: Long,
        val source: Source,
    )

    private const val TAG = "WhatsAppStatuses"
    private const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val PREFS = "whatsapp_status"
    private const val STATUSES_DIR = ".Statuses"
    private const val KEY_SAVED = "saved_ids"

    private class Child(val id: String, val name: String, val mime: String, val modified: Long) {
        val isDir get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isInstalled(context: Context, source: Source): Boolean =
        runCatching { context.packageManager.getPackageInfo(source.pkg, 0) }.isSuccess

    fun launchIntent(context: Context, source: Source): Intent? = context.packageManager.getLaunchIntentForPackage(source.pkg)

    /** The folder the user granted for [source], or null if none (or the grant was revoked). */
    fun grantedTree(context: Context, source: Source): Uri? {
        val tree = prefs(context).getString("tree_${source.pkg}", null)?.let(Uri::parse) ?: return null
        val stillGranted = context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        return tree.takeIf { stillGranted }
    }

    /** Keeps [tree] if it is (or contains) the Statuses folder; otherwise releases it and returns false. */
    fun connect(context: Context, source: Source, tree: Uri): Boolean {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (statusesDirId(context, tree) == null) {
            context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return false
        }
        prefs(context).edit().putString("tree_${source.pkg}", tree.toString()).apply()
        return true
    }

    fun disconnect(context: Context, source: Source) {
        grantedTree(context, source)?.let {
            runCatching { context.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        prefs(context).edit().remove("tree_${source.pkg}").apply()
    }

    /** Blocking — run off the main thread. Newest first. Empty if the folder can't be read. */
    fun list(context: Context, source: Source): List<Status> = runCatching {
        val tree = grantedTree(context, source) ?: return emptyList()
        val dirId = statusesDirId(context, tree) ?: return emptyList()
        children(context, tree, dirId)
            .filter { it.mime.startsWith("image/") || it.mime.startsWith("video/") }
            .map {
                Status(
                    id = it.id,
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, it.id),
                    name = it.name,
                    isVideo = it.mime.startsWith("video/"),
                    modified = it.modified,
                    source = source,
                )
            }
            .sortedByDescending { it.modified }
    }.getOrElse {
        Log.w(TAG, "Couldn't list ${source.label} statuses", it)
        emptyList()
    }

    fun isSaved(context: Context, status: Status) = status.id in savedIds(context)

    private fun savedIds(context: Context): Set<String> = prefs(context).getStringSet(KEY_SAVED, emptySet()).orEmpty()

    /** Copies [statuses] into Downloads/TDownload and the Library. Blocking copy — run off the main thread. */
    suspend fun save(context: Context, statuses: List<Status>): Int {
        val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TDownload").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        var saved = 0
        for (status in statuses) {
            val ext = status.name.substringAfterLast('.', if (status.isVideo) "mp4" else "jpg")
            val base = "WhatsApp status ${stamp.format(Date(status.modified))}"
            var dest = File(outDir, "$base.$ext")
            var n = 1
            while (dest.exists()) {
                dest = File(outDir, "$base ($n).$ext")
                n++
            }
            val input = context.contentResolver.openInputStream(status.uri) ?: throw IOException("Couldn't open ${status.name}")
            input.use { src -> dest.outputStream().use { src.copyTo(it) } }

            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
            DownloadStore.append(
                context,
                DownloadRecord(
                    id = UUID.randomUUID().toString(),
                    title = dest.nameWithoutExtension,
                    path = dest.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    platform = Site.WhatsApp.label,
                    fileSizeBytes = dest.length(),
                )
            )
            prefs(context).edit().putStringSet(KEY_SAVED, savedIds(context) + status.id).apply()
            saved++
        }
        return saved
    }

    /** The Statuses folder's document id: the granted folder itself, or its `.Statuses` child if a parent was picked. */
    private fun statusesDirId(context: Context, tree: Uri): String? {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        if (rootId.substringAfterLast('/') == STATUSES_DIR) return rootId
        return children(context, tree, rootId).firstOrNull { it.isDir && it.name == STATUSES_DIR }?.id
    }

    private fun children(context: Context, tree: Uri, parentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val result = mutableListOf<Child>()
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                result += Child(cursor.getString(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(), cursor.getLong(3))
            }
        }
        return result
    }
}
