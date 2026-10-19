package com.tdownload.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tdownload.app.*
import com.tdownload.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** The whole app, behind the update gate: a retired version shows "Update required" instead. */
@Composable
fun HomeScreen() {
    UpdateGate { HomeContent() }
}

@Composable
private fun HomeContent() {
    val context = LocalContext.current
    var initState by remember { mutableStateOf("Initializing…") }
    val history by DownloadStore.historyFlow(context).collectAsState(initial = emptyList())
    var navIndex by rememberSaveable { mutableIntStateOf(0) }
    // Bumped on resume so the lists re-check whether each file still exists on disk
    // (it may have been deleted outside the app while it was backgrounded).
    var resumeTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                YtdlManager.ensureInit(context)
                withContext(Dispatchers.Main) { initState = "Updating…" }
                YtdlManager.update(context)
                withContext(Dispatchers.Main) { initState = "Ready" }
            }.onFailure {
                withContext(Dispatchers.Main) { initState = "Error: ${it.message?.take(40)}" }
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    val isReady = initState == "Ready"

    // Full-screen animated loader until yt-dlp is initialized
    SplashLoader(statusText = initState, visible = !isReady)

    // The status viewer covers the whole app (bottom navigation included), so it lives above the Scaffold.
    var statusViewer by remember { mutableStateOf<StatusViewerRequest?>(null) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = ColorBackground,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar(containerColor = ColorSurface, tonalElevation = 0.dp) {
                    NavItem("Home", selected = navIndex == 0, icon = { Icon(Icons.Filled.Home, contentDescription = "Home") }) { navIndex = 0 }
                    NavItem("Status", selected = navIndex == 1, icon = {
                        Icon(painterResource(com.tdownload.app.R.drawable.ic_whatsapp), contentDescription = "WhatsApp Status", modifier = Modifier.size(24.dp))
                    }) { navIndex = 1 }
                    NavItem("Library", selected = navIndex == 2, icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = "Library") }) { navIndex = 2 }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).background(ColorBackground)) {
                Box(Modifier.weight(1f)) {
                    when (navIndex) {
                        0 -> HomeTab(
                            modifier = Modifier,
                            initState = initState,
                            recent = history,
                            resumeTick = resumeTick,
                            onSeeAll = { navIndex = 2 },
                        )
                        1 -> StatusTab(modifier = Modifier, resumeTick = resumeTick, onOpenViewer = { statusViewer = it })
                        2 -> LibraryTab(
                            modifier = Modifier,
                            history = history,
                            context = context,
                            resumeTick = resumeTick,
                        )
                    }
                }
                Footer()
            }
        }
        statusViewer?.let { StatusViewerOverlay(it, onClose = { statusViewer = null }) }
    }
}

private const val PORTFOLIO_URL = "https://talha.is-a.dev"

@Composable
private fun Footer() {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Built with ❤️ by ", color = ColorTextTertiary, fontSize = 11.sp)
        // Only the name is the link; the padding keeps it an easy touch target.
        Text(
            "Talha Sajid",
            color = ColorPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable { uriHandler.openUri(PORTFOLIO_URL) }
                .padding(horizontal = 2.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun RowScope.NavItem(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label, fontSize = 11.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = ColorPrimary,
            selectedTextColor = ColorPrimary,
            unselectedIconColor = ColorTextTertiary,
            unselectedTextColor = ColorTextTertiary,
            indicatorColor = ColorSurfaceVariant,
        ),
    )
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "gif", "bmp")
private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "wav", "ogg", "opus")

private fun typeOf(record: DownloadRecord): String = when (File(record.path).extension.lowercase()) {
    in IMAGE_EXTENSIONS -> "Photos"
    in AUDIO_EXTENSIONS -> "Audio"
    else -> "Video"
}

@Composable
private fun LibraryTab(
    modifier: Modifier,
    history: List<DownloadRecord>,
    context: Context,
    resumeTick: Int,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var siteFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val sites = remember(history) { history.map { it.platform }.filter { it.isNotBlank() }.distinct() }
    val filtered = remember(history, query, typeFilter, siteFilter) {
        history.filter { record ->
            (query.isBlank() || record.title.contains(query.trim(), ignoreCase = true)) &&
                (typeFilter == null || typeOf(record) == typeFilter) &&
                (siteFilter == null || record.platform == siteFilter)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding(),
    ) {
        Text(
            "Library",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = ColorTextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        if (history.isEmpty()) {
            EmptyState("No downloads yet", "Paste a link on Home to get started.")
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text("Search downloads", color = ColorTextTertiary, fontSize = 14.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ColorTextTertiary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = ColorTextSecondary)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorPrimary,
                    unfocusedBorderColor = ColorOutline,
                    cursorColor = ColorPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Video", "Audio", "Photos").forEach { type ->
                    OptionChip(type, selected = typeFilter == type) { typeFilter = if (typeFilter == type) null else type }
                }
                sites.forEach { site ->
                    OptionChip(site, selected = siteFilter == site) { siteFilter = if (siteFilter == site) null else site }
                }
            }
            if (filtered.isEmpty()) {
                EmptyState("No matches", "Try a different search or filter.") {
                    TextButton(onClick = { query = ""; typeFilter = null; siteFilter = null }) {
                        Text("Clear filters", color = ColorPrimary)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { record ->
                        SwipeToDeleteItem(
                            onDelete = { scope.launch { DownloadStore.delete(context, record.id) } }
                        ) {
                            DownloadItem(record, context, resumeTick)
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, hint: String, action: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Download, contentDescription = null, tint = ColorTextTertiary, modifier = Modifier.size(48.dp))
            Text(title, color = ColorTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = ColorTextTertiary, fontSize = 13.sp)
            action()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDeleteItem(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
        positionalThreshold = { it * 0.4f },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) ColorError else Color.Transparent,
                label = "swipe_bg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(color)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
        content = { content() },
    )
}

@Composable
fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ColorSurface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
internal fun DownloadItem(record: DownloadRecord, context: Context, resumeTick: Int) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    // Re-checked on every app resume so an item flips to "Deleted" as soon as the file is
    // removed outside the app (Files app, gallery, MediaStore cleanup, etc.).
    val fileExists = remember(record.path, resumeTick) { File(record.path).exists() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = fileExists) {
                val file = File(record.path)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val mime = when (typeOf(record)) {
                    "Photos" -> "image/*"
                    "Audio" -> "audio/*"
                    else -> "video/*"
                }
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            },
        color = ColorSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).alpha(if (fileExists) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadThumbnail(record.path)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.title, color = ColorTextPrimary, fontSize = 14.sp, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.format(Date(record.timestamp)), color = ColorTextTertiary, fontSize = 11.sp)
                    if (record.platform.isNotBlank()) {
                        Text("·", color = ColorTextTertiary, fontSize = 11.sp)
                        Text(record.platform, color = ColorPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    if (!fileExists) {
                        Text("·", color = ColorTextTertiary, fontSize = 11.sp)
                        Text("Deleted", color = ColorError, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    } else if (record.fileSizeBytes > 0) {
                        Text("·", color = ColorTextTertiary, fontSize = 11.sp)
                        Text(formatFileSize(record.fileSizeBytes), color = ColorTextTertiary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// Small in-memory cache so scrolling the history list doesn't redecode the same
// file repeatedly — thumbnails are cheap to keep around for the app's lifetime.
private val thumbnailCache = java.util.Collections.synchronizedMap(
    LinkedHashMap<String, android.graphics.Bitmap?>()
)

@Composable
private fun DownloadThumbnail(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = thumbnailCache[path], key1 = path) {
        if (!thumbnailCache.containsKey(path)) {
            value = withContext(Dispatchers.IO) { decodeThumbnail(path) }
            thumbnailCache[path] = value
        }
    }
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ColorSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Outlined.Download, contentDescription = null, tint = ColorTextTertiary, modifier = Modifier.size(22.dp))
        }
    }
}

private fun decodeThumbnail(path: String): android.graphics.Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    return runCatching {
        when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                android.graphics.BitmapFactory.decodeFile(path, opts)
            }
            "m4a", "mp3", "aac", "wav", "ogg" -> null
            else -> {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    retriever.frameAtTime
                } finally {
                    retriever.release()
                }
            }
        }
    }.getOrNull()
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

/** [items] are indexes into the post's items to download; null means all of them. */
fun startDownload(context: Context, url: String, quality: Quality, items: List<Int>? = null) {
    val urlRegex = Regex("""https?://\S+""")
    val extracted = urlRegex.find(url)?.value ?: url
    if (extracted.isBlank()) return
    Intent(context, DownloadService::class.java).also {
        it.putExtra(DownloadService.EXTRA_URL, extracted)
        it.putExtra(DownloadService.EXTRA_FORMAT, quality.format)
        it.putExtra(DownloadService.EXTRA_AUDIO_ONLY, quality.audioOnly)
        if (items != null) it.putExtra(DownloadService.EXTRA_ITEMS, items.toIntArray())
        context.startForegroundService(it)
    }
}
