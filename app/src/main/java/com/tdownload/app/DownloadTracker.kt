package com.tdownload.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

sealed interface DownloadState {
    data object Starting : DownloadState
    data class Progress(val percent: Int, val detail: String) : DownloadState
    data class Done(val title: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

data class ActiveDownload(val id: Int, val site: Site?, val title: String, val state: DownloadState)

/**
 * In-process view of running (and just-finished) downloads. [DownloadService] writes to it and the UI
 * observes it, so every download — started in-app or from the share sheet — shows up the same way.
 * The service runs in the app's own process, so this needs no IPC.
 */
object DownloadTracker {
    private val _downloads = MutableStateFlow<List<ActiveDownload>>(emptyList())
    val downloads: StateFlow<List<ActiveDownload>> = _downloads

    fun start(id: Int, site: Site?, title: String) =
        _downloads.update { it + ActiveDownload(id, site, title, DownloadState.Starting) }

    fun title(id: Int, title: String) = change(id) { it.copy(title = title) }
    fun progress(id: Int, percent: Int, detail: String) = change(id) { it.copy(state = DownloadState.Progress(percent, detail)) }
    fun done(id: Int, title: String) = change(id) { it.copy(title = title, state = DownloadState.Done(title)) }
    fun fail(id: Int, message: String) = change(id) { it.copy(state = DownloadState.Failed(message)) }
    fun dismiss(id: Int) = _downloads.update { list -> list.filterNot { it.id == id } }

    private fun change(id: Int, transform: (ActiveDownload) -> ActiveDownload) =
        _downloads.update { list -> list.map { if (it.id == id) transform(it) else it } }
}
