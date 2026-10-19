package com.tdownload.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.tdownload.app.WhatsAppStatuses
import com.tdownload.app.WhatsAppStatuses.Source
import com.tdownload.app.WhatsAppStatuses.Status
import com.tdownload.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val statusThumbnails = LruCache<String, Bitmap>(80)

/** Browse the statuses you've viewed in WhatsApp and save the ones you want. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusTab(modifier: Modifier, resumeTick: Int, onOpenViewer: (StatusViewerRequest) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val installed = remember { Source.entries.filter { WhatsAppStatuses.isInstalled(context, it) } }
    var grantTick by remember { mutableIntStateOf(0) }
    val savedTickState = remember { mutableIntStateOf(0) }
    var savedTick by savedTickState
    var refreshTick by remember { mutableIntStateOf(0) }
    val granted = remember(grantTick, resumeTick) { installed.filter { WhatsAppStatuses.grantedTree(context, it) != null } }

    var pendingSource by remember { mutableStateOf<Source?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val source = pendingSource
        pendingSource = null
        if (tree != null && source != null) {
            if (WhatsAppStatuses.connect(context, source, tree)) {
                grantTick++
            } else {
                Toast.makeText(context, "That's not the Statuses folder. Choose the folder shown, then tap \"Use this folder\".", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun connect(source: Source) {
        pendingSource = source
        folderPicker.launch(source.pickerHint)
    }

    // Keeps showing the previous list while a refresh runs, so the grid doesn't flash empty.
    val statuses by produceState<List<Status>?>(null, granted, resumeTick, refreshTick) {
        value = withContext(Dispatchers.IO) {
            granted.flatMap { WhatsAppStatuses.list(context, it) }.sortedByDescending { it.modified }
        }
    }

    var filter by rememberSaveable { mutableStateOf("All") }
    val selected = remember { mutableStateListOf<String>() }

    val shown = remember(statuses, filter) {
        statuses.orEmpty().filter { filter == "All" || (filter == "Videos") == it.isVideo }
    }

    fun save(list: List<Status>) {
        scope.launch {
            val todo = list.filterNot { WhatsAppStatuses.isSaved(context, it) }
            if (todo.isEmpty()) {
                Toast.makeText(context, "Already downloaded", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val result = runCatching { withContext(Dispatchers.IO) { WhatsAppStatuses.save(context, todo) } }
            savedTick++
            selected.clear()
            Toast.makeText(
                context,
                result.fold({ "Downloaded $it — find ${if (it == 1) "it" else "them"} in your Library" }, { "Couldn't download: ${it.message ?: "unknown error"}" }),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // The viewer is hosted by HomeScreen, above the whole app (including the bottom navigation).
    fun open(status: Status) = onOpenViewer(
        StatusViewerRequest(
            items = shown,
            startIndex = shown.indexOf(status).coerceAtLeast(0),
            savedTick = savedTickState,
            isSaved = { WhatsAppStatuses.isSaved(context, it) },
            onDownload = { save(listOf(it)) },
        )
    )

    Column(modifier = modifier.fillMaxSize().background(ColorBackground).statusBarsPadding()) {
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected.clear() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = ColorTextPrimary)
                    }
                    Text("${selected.size} selected", color = ColorTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { save(shown.filter { it.id in selected }) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                ) { Text("Download", fontWeight = FontWeight.SemiBold) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Status", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)
                if (granted.isNotEmpty()) {
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = ColorTextSecondary)
                    }
                }
            }
        }

        when {
            installed.isEmpty() -> StatusMessage(
                "WhatsApp isn't installed",
                "Install WhatsApp or WhatsApp Business to save its statuses here.",
            )
            granted.isEmpty() -> ConnectCard(installed, ::connect)
            else -> {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("All", "Photos", "Videos").forEach { OptionChip(it, selected = filter == it) { filter = it } }
                }
                when {
                    statuses == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ColorPrimary, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                    shown.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth()) {
                        StatusMessage(
                            if (statuses.orEmpty().isEmpty()) "No statuses yet" else "Nothing here",
                            if (statuses.orEmpty().isEmpty()) "Open a status in WhatsApp first, then come back and tap refresh."
                            else "No ${filter.lowercase()} among your viewed statuses.",
                        ) {
                            WhatsAppStatuses.launchIntent(context, granted.first())?.let { intent ->
                                TextButton(onClick = { context.startActivity(intent) }) { Text("Open WhatsApp", color = ColorPrimary) }
                            }
                        }
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(shown, key = { it.id }) { status ->
                            val isSelected = status.id in selected
                            val isSaved = remember(savedTick, status.id) { WhatsAppStatuses.isSaved(context, status) }
                            StatusTile(
                                status = status,
                                selected = isSelected,
                                saved = isSaved,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (selected.isNotEmpty()) {
                                            if (isSelected) selected.remove(status.id) else selected.add(status.id)
                                        } else open(status)
                                    },
                                    onLongClick = { if (isSelected) selected.remove(status.id) else selected.add(status.id) },
                                ),
                            )
                        }
                    }
                }
                ConnectionRow(installed, granted, ::connect) {
                    WhatsAppStatuses.disconnect(context, it)
                    grantTick++
                    selected.clear()
                }
            }
        }
    }

}

@Composable
private fun ConnectCard(installed: List<Source>, onConnect: (Source) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        SurfaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Save WhatsApp statuses", color = ColorTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "1. Open WhatsApp and view the statuses you want to keep.\n" +
                        "2. Come back here and allow access to the Statuses folder. Android will open it for you — just tap \"Use this folder\".",
                    color = ColorTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Text(
                    "Only that one folder is shared with TDownloader. Nothing leaves your phone.",
                    color = ColorTextTertiary,
                    fontSize = 12.sp,
                )
                installed.forEach { source ->
                    Button(
                        onClick = { onConnect(source) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    ) { Text(if (installed.size > 1) "Allow ${source.label}" else "Allow access", fontWeight = FontWeight.SemiBold) }
                }
                WhatsAppStatuses.launchIntent(context, installed.first())?.let { intent ->
                    TextButton(onClick = { context.startActivity(intent) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open WhatsApp first", color = ColorPrimary)
                    }
                }
            }
        }
    }
}

/** Small connect/disconnect controls for each installed WhatsApp app, under the grid. */
@Composable
private fun ConnectionRow(installed: List<Source>, granted: List<Source>, onConnect: (Source) -> Unit, onDisconnect: (Source) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        installed.forEach { source ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(source.label, color = ColorTextTertiary, fontSize = 12.sp)
                if (source in granted) {
                    TextButton(onClick = { onDisconnect(source) }) { Text("Disconnect", color = ColorTextSecondary, fontSize = 12.sp) }
                } else {
                    TextButton(onClick = { onConnect(source) }) { Text("Connect", color = ColorPrimary, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(title: String, hint: String, action: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = ColorTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = ColorTextTertiary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            action()
        }
    }
}

@Composable
private fun StatusTile(status: Status, selected: Boolean, saved: Boolean, modifier: Modifier) {
    Box(modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))) {
        StatusThumbnail(status, Modifier.fillMaxSize().alpha(if (selected) 0.5f else 1f))
        if (status.isVideo) {
            Icon(
                Icons.Filled.PlayArrow, contentDescription = "Video", tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp),
            )
        }
        if (saved) {
            Icon(
                Icons.Filled.CheckCircle, contentDescription = "Saved", tint = ColorSuccess,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp),
            )
        }
        if (selected) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(ColorPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = ColorOnPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun StatusThumbnail(status: Status, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(statusThumbnails.get(status.id), status.id) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    if (status.isVideo) videoFrame(context, status.uri) else decodeImage(context, status.uri, 512)
                }.getOrNull()
            }?.also { statusThumbnails.put(status.id, it) }
        }
    }
    Box(modifier.background(ColorSurface)) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Everything the full-screen viewer needs. HomeScreen hosts it, so it can cover the navigation bar too. */
class StatusViewerRequest(
    val items: List<Status>,
    val startIndex: Int,
    val savedTick: IntState,
    val isSaved: (Status) -> Boolean,
    val onDownload: (Status) -> Unit,
)

/**
 * Full-screen browser for the statuses: swipe between them, download the one on screen. It is a layer
 * inside the app's own window (not a Dialog), so the status/navigation bar insets are the real ones and
 * the Download bar always sits fully on screen.
 */
@Composable
fun StatusViewerOverlay(request: StatusViewerRequest, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val items = request.items
    val pager = rememberPagerState(initialPage = request.startIndex) { items.size }
    val current = items[pager.currentPage.coerceIn(items.indices)]
    val saved = remember(request.savedTick.intValue, current.id) { request.isSaved(current) }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swallow touches so nothing underneath (the status grid) reacts to them.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(if (current.isVideo) "Video" else "Photo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${current.source.label} · ${dateFormat.format(Date(current.modified))}", color = ColorTextSecondary, fontSize = 12.sp)
            }
            Text("${pager.currentPage + 1} / ${items.size}", color = ColorTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(end = 16.dp))
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val status = items[page]
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(ColorSurface),
                contentAlignment = Alignment.Center,
            ) {
                if (status.isVideo) StatusVideo(status.uri, active = pager.currentPage == page) else StatusImage(status.uri)
            }
        }

        Surface(color = ColorSurface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = Modifier.padding(top = 12.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { request.onDownload(current) },
                    enabled = !saved,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorPrimary,
                        contentColor = ColorOnPrimary,
                        disabledContainerColor = ColorSuccess.copy(alpha = 0.15f),
                        disabledContentColor = ColorSuccess,
                    ),
                ) {
                    Icon(if (saved) Icons.Filled.CheckCircle else Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (saved) "Downloaded" else "Download", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                if (saved) {
                    Text("Saved to Downloads/TDownload and your Library", color = ColorTextTertiary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusImage(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { decodeImage(context, uri, 2048) }.getOrNull() }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

/** Plays only while it's the page on screen. Tap to pause or resume. */
@Composable
private fun StatusVideo(uri: Uri, active: Boolean) {
    val context = LocalContext.current
    val video = remember {
        VideoView(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        }
    }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(video) { onDispose { video.stopPlayback() } }
    LaunchedEffect(active, prepared) {
        if (!prepared) return@LaunchedEffect
        if (active) video.start() else { video.pause(); video.seekTo(0) }
    }
    LaunchedEffect(prepared) {
        while (prepared) {
            playing = video.isPlaying
            progress = if (video.duration > 0) video.currentPosition / video.duration.toFloat() else 0f
            delay(200)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // VideoView keeps the video's aspect ratio but sits at the top-left of its parent; a FrameLayout
        // with centre gravity puts it in the middle of the page.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    addView(video)
                    video.setVideoURI(uri)
                    video.setOnPreparedListener { it.isLooping = true; prepared = true }
                    video.setOnClickListener { if (video.isPlaying) video.pause() else video.start() }
                }
            },
        )
        if (prepared && active && !playing) {
            Box(
                modifier = Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        if (prepared) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = ColorPrimary,
                trackColor = Color.White.copy(alpha = 0.15f),
            )
        }
    }
}

private fun videoFrame(context: android.content.Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.frameAtTime
    } finally {
        retriever.release()
    }
}

private fun decodeImage(context: android.content.Context, uri: Uri, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
