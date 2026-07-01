package com.tdownload.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tdownload.app.*
import com.tdownload.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var initState by remember { mutableStateOf("Initializing…") }
    val cookiesExist = remember { mutableStateOf(File(context.filesDir, "cookies.txt").exists()) }
    val history by DownloadStore.historyFlow(context).collectAsState(initial = emptyList())
    var navIndex by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    // Listen for download errors from DownloadService and show Snackbar
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val msg = intent.getStringExtra(DownloadService.EXTRA_ERROR_MESSAGE) ?: return
                scope.launch { snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long) }
            }
        }
        val filter = IntentFilter(DownloadService.ACTION_DOWNLOAD_ERROR)
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME)
                cookiesExist.value = File(context.filesDir, "cookies.txt").exists()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    val isReady = initState == "Ready"

    // Full-screen animated loader until yt-dlp is initialized
    SplashLoader(statusText = initState, visible = !isReady)

    Scaffold(
        containerColor = ColorBackground,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = ColorSurfaceVariant,
                    contentColor = ColorTextPrimary,
                    actionColor = ColorPrimary,
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = ColorSurface,
                tonalElevation = 0.dp,
            ) {
                NavItem(
                    icon = Icons.Filled.Download,
                    label = "Download",
                    selected = navIndex == 0,
                    onClick = { navIndex = 0 },
                )
                NavItem(
                    icon = Icons.Filled.History,
                    label = "History",
                    selected = navIndex == 1,
                    onClick = { navIndex = 1 },
                )
            }
        }
    ) { padding ->
        when (navIndex) {
            0 -> DownloadTab(
                modifier = Modifier.padding(padding),
                initState = initState,
                cookiesExist = cookiesExist,
                context = context,
            )
            1 -> HistoryTab(
                modifier = Modifier.padding(padding),
                history = history,
                context = context,
            )
        }
    }
}

@Composable
private fun NavigationBar(
    containerColor: androidx.compose.ui.graphics.Color,
    tonalElevation: androidx.compose.ui.unit.Dp,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.NavigationBar(
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        content = content,
    )
}

@Composable
private fun RowScope.NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (badge != null) {
                BadgedBox(badge = { Badge(containerColor = ColorPrimary) { Text(badge, color = ColorOnPrimary, fontSize = 10.sp) } }) {
                    Icon(icon, contentDescription = label)
                }
            } else {
                Icon(icon, contentDescription = label)
            }
        },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadTab(
    modifier: Modifier,
    initState: String,
    cookiesExist: MutableState<Boolean>,
    context: Context,
) {
    val tabs = listOf("Instagram", "YouTube", "TikTok")
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ColorPrimary)) { append("T") }
                    withStyle(SpanStyle(color = ColorTextPrimary)) { append("Downloader") }
                },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (initState == "Ready") {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(ColorSuccess))
                    Text("Ready", fontSize = 12.sp, color = ColorTextTertiary)
                } else {
                    CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 2.dp, color = ColorPrimary)
                    Text(initState, fontSize = 12.sp, color = ColorTextTertiary)
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.padding(horizontal = 20.dp),
            containerColor = ColorBackground,
            contentColor = ColorPrimary,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = ColorPrimary,
                )
            },
            divider = {},
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (pagerState.currentPage == i) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (pagerState.currentPage == i) ColorPrimary else ColorTextSecondary,
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (page) {
                    0 -> {
                        item {
                            DownloadInputCard(
                                placeholder = "Paste Instagram link…",
                                onDownload = { url, quality -> startDownload(context, url, quality, "cookies.txt") },
                            )
                        }
                        item { InfoCard("Instagram requires login for all downloads — even public posts. Add your session ID below.") }
                        item { InstagramLoginCard(context, cookiesExist) }
                    }
                    1 -> {
                        item {
                            DownloadInputCard(
                                placeholder = "Paste YouTube link…",
                                onDownload = { url, quality -> startDownload(context, url, quality) },
                            )
                        }
                    }
                    2 -> {
                        item {
                            DownloadInputCard(
                                placeholder = "Paste TikTok link…",
                                onDownload = { url, quality -> startDownload(context, url, quality, "cookies_tiktok.txt") },
                            )
                        }
                        item { InfoCard("Public videos work without login. Login only needed for private content.") }
                        item {
                            PlatformLoginCard(
                                context = context,
                                platform = "TikTok",
                                cookiesFile = "cookies_tiktok.txt",
                                cookiesExist = remember { mutableStateOf(File(context.filesDir, "cookies_tiktok.txt").exists()) },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTab(
    modifier: Modifier,
    history: List<DownloadRecord>,
    context: Context,
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding(),
    ) {
        Text(
            "History",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = ColorTextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Download, contentDescription = null, tint = ColorTextTertiary, modifier = Modifier.size(48.dp))
                    Text("No downloads yet", color = ColorTextTertiary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(history, key = { it.id }) { record ->
                    SwipeToDeleteItem(
                        onDelete = { scope.launch { DownloadStore.delete(context, record.id) } }
                    ) {
                        DownloadItem(record, context)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteItem(onDelete: () -> Unit, content: @Composable () -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadInputCard(
    placeholder: String,
    onDownload: (url: String, quality: Quality) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var showQuality by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, color = ColorTextTertiary, fontSize = 14.sp) },
                singleLine = true,
                trailingIcon = {
                    if (url.isBlank()) {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { url = it }
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = ColorTextTertiary)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = {
                    keyboard?.hide()
                    if (url.isNotBlank()) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showQuality = true }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorPrimary,
                    unfocusedBorderColor = ColorOutline,
                    cursorColor = ColorPrimary,
                ),
                shape = RoundedCornerShape(10.dp),
            )
            Button(
                onClick = { keyboard?.hide(); if (url.isNotBlank()) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showQuality = true } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorPrimary,
                    contentColor = ColorOnPrimary,
                ),
                enabled = url.isNotBlank(),
            ) {
                Text("Download", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showQuality) {
        QualityPickerSheet(
            onSelect = { quality ->
                showQuality = false
                val trimmed = url.trim()
                url = ""
                onDownload(trimmed, quality)
            },
            onDismiss = { showQuality = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityPickerSheet(onSelect: (Quality) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ColorSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ColorOutline) },
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                "Select Quality",
                color = ColorTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            QUALITIES.forEach { quality ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(quality) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(quality.label, color = ColorTextPrimary, fontSize = 15.sp)
                        if (quality.label == "Best") {
                            Text("highest available resolution", color = ColorTextTertiary, fontSize = 12.sp)
                        } else if (quality.audioOnly) {
                            Text("audio only · m4a", color = ColorTextTertiary, fontSize = 12.sp)
                        }
                    }
                    if (quality.label == "Best") {
                        Surface(
                            color = ColorPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text("recommended", color = ColorPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                if (quality != QUALITIES.last()) HorizontalDivider(color = ColorOutline, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InstagramLoginCard(context: Context, cookiesExist: MutableState<Boolean>) {
    if (cookiesExist.value) {
        // Collapsed single-line when logged in
        Surface(modifier = Modifier.fillMaxWidth(), color = ColorSurface, shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Logged in ✓", color = ColorSuccess, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { File(context.filesDir, "cookies.txt").delete(); cookiesExist.value = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = ColorError),
                    ) { Text("Log out", fontSize = 12.sp) }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, WebViewActivity::class.java)
                                    .putExtra(WebViewActivity.EXTRA_PLATFORM, "Instagram")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = ColorTextSecondary),
                    ) { Text("Re-login", fontSize = 12.sp) }
                }
            }
        }
    } else {
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Account", color = ColorTextSecondary, fontSize = 12.sp)
                    Text("Not logged in", color = ColorTextTertiary, fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, WebViewActivity::class.java)
                                .putExtra(WebViewActivity.EXTRA_PLATFORM, "Instagram")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Login", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun PlatformLoginCard(
    context: Context,
    platform: String,
    cookiesFile: String,
    cookiesExist: MutableState<Boolean>,
) {
    if (cookiesExist.value) {
        Surface(modifier = Modifier.fillMaxWidth(), color = ColorSurface, shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Logged in ✓", color = ColorSuccess, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { File(context.filesDir, cookiesFile).delete(); cookiesExist.value = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = ColorError),
                    ) { Text("Log out", fontSize = 12.sp) }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, WebViewActivity::class.java)
                                    .putExtra(WebViewActivity.EXTRA_COOKIES_FILE, cookiesFile)
                                    .putExtra(WebViewActivity.EXTRA_PLATFORM, platform)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = ColorTextSecondary),
                    ) { Text("Re-login", fontSize = 12.sp) }
                }
            }
        }
    } else {
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Account", color = ColorTextSecondary, fontSize = 12.sp)
                    Text("Not logged in", color = ColorTextTertiary, fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, WebViewActivity::class.java)
                                .putExtra(WebViewActivity.EXTRA_COOKIES_FILE, cookiesFile)
                                .putExtra(WebViewActivity.EXTRA_PLATFORM, platform)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Login", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ColorSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(ColorPrimary, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
            )
            Text(message, color = ColorTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        }
    }
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
private fun DownloadItem(record: DownloadRecord, context: Context) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val file = File(record.path)
                if (file.exists()) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            },
        color = ColorSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.title, color = ColorTextPrimary, fontSize = 14.sp, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.format(Date(record.timestamp)), color = ColorTextTertiary, fontSize = 11.sp)
                    if (record.platform.isNotBlank()) {
                        Text("·", color = ColorTextTertiary, fontSize = 11.sp)
                        Text(record.platform, color = ColorPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    if (record.fileSizeBytes > 0) {
                        Text("·", color = ColorTextTertiary, fontSize = 11.sp)
                        Text(formatFileSize(record.fileSizeBytes), color = ColorTextTertiary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

fun startDownload(context: Context, url: String, quality: Quality, cookiesFile: String = "cookies.txt") {
    val urlRegex = Regex("""https?://\S+""")
    val extracted = urlRegex.find(url)?.value ?: url
    if (extracted.isBlank()) return
    Intent(context, DownloadService::class.java).also {
        it.putExtra(DownloadService.EXTRA_URL, extracted)
        it.putExtra(DownloadService.EXTRA_FORMAT, quality.format)
        it.putExtra(DownloadService.EXTRA_AUDIO_ONLY, quality.audioOnly)
        it.putExtra(DownloadService.EXTRA_COOKIES_FILE, cookiesFile)
        context.startForegroundService(it)
    }
}
