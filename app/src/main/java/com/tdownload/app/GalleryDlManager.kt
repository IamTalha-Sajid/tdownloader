package com.tdownload.app

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Runs the bundled gallery-dl.pyz (a self-contained Python zipapp) using the exact same
 * Python install youtubedl-android already extracted for yt-dlp. Reverse-engineered from
 * youtubedl-android's YoutubeDL.init() bytecode: the real interpreter files (with all their
 * .so deps, e.g. libandroid-support.so) live under
 *   noBackupFilesDir/youtubedl-android/packages/python/usr
 * (unzipped there from libpython.zip.so on first init) — NOT under ApplicationInfo.nativeLibraryDir,
 * which only holds the bare libpython.so executable itself.
 *
 * Exists because yt-dlp's Instagram extractor hard-errors on carousels containing
 * image slides ("No video formats found!") — a known, unfixed yt-dlp limitation.
 * gallery-dl is a pure-Python gallery/image downloader that handles these correctly.
 */
object GalleryDlManager {
    @Volatile private var pyzFile: File? = null

    fun ensureInit(context: Context): File {
        pyzFile?.let { return it }
        synchronized(this) {
            pyzFile?.let { return it }
            val dest = File(context.filesDir, "gallery_dl.pyz")
            context.assets.open("gallery_dl.pyz").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            pyzFile = dest
            return dest
        }
    }

    fun run(context: Context, args: List<String>, onLine: (String) -> Unit): Int {
        val pyz = ensureInit(context)
        // Requires YtdlManager.ensureInit() to have already run at least once, so this
        // directory exists (DownloadService always does this before any download attempt).
        val pythonDir = File(File(context.noBackupFilesDir, "youtubedl-android/packages"), "python")
        val pythonExe = File(context.applicationInfo.nativeLibraryDir, "libpython.so")
        val libDir = "${pythonDir.absolutePath}/usr/lib"
        val pythonHome = "${pythonDir.absolutePath}/usr"

        val command = mutableListOf(pythonExe.absolutePath, pyz.absolutePath)
        command.addAll(args)

        val process = ProcessBuilder(command).apply {
            redirectErrorStream(true)
            environment()["LD_LIBRARY_PATH"] = libDir
            environment()["SSL_CERT_FILE"] = "${pythonDir.absolutePath}/usr/etc/tls/cert.pem"
            environment()["PYTHONHOME"] = pythonHome
            environment()["HOME"] = pythonHome
        }.start()

        // Never write anything to stdin — close it so gallery-dl can't block waiting on an
        // interactive prompt (e.g. a confirmation) that would otherwise hang forever.
        process.outputStream.close()

        // Drain stdout on its own thread so a network hang doesn't also block the
        // waitFor(timeout) below from ever getting a chance to kill the process.
        Log.d("GalleryDl", "exec: ${command.joinToString(" ")}")
        val readerThread = Thread {
            runCatching {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.forEachLine { Log.d("GalleryDl", it); onLine(it) }
                }
            }
        }.apply { isDaemon = true; start() }

        val finished = process.waitFor(3, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
        }
        readerThread.join(5000)
        val exitCode = if (finished) process.exitValue() else -1
        Log.d("GalleryDl", "exit=$exitCode")
        return exitCode
    }
}
