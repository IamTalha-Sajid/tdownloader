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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    // Bumped on resume so History re-checks whether each file still exists on disk
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
            if (event == Lifecycle.Event.ON_RESUME) {
                cookiesExist.value = File(context.filesDir, "cookies.txt").exists()
                resumeTick++
            }
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
                resumeTick = resumeTick,
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
    val tabs = listOf(
        "Instagram" to com.tdownload.app.R.drawable.ic_instagram,
        "YouTube" to com.tdownload.app.R.drawable.ic_youtube,
        "WhatsApp" to com.tdownload.app.R.drawable.ic_whatsapp,
        "TikTok" to com.tdownload.app.R.drawable.ic_tiktok,
        "Facebook" to com.tdownload.app.R.drawable.ic_facebook,
    )
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
            tabs.forEachIndexed { i, (label, iconRes) ->
                val tint = if (pagerState.currentPage == i) ColorPrimary else ColorTextSecondary
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    icon = {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (pagerState.currentPage == i) FontWeight.SemiBold else FontWeight.Normal,
                            color = tint,
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
                        if (!cookiesExist.value) {
                            item { InfoCard("Instagram requires login for all downloads — even public posts. Add your session ID below.") }
                        }
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
                        item { ComingSoonCard(platform = "WhatsApp") }
                    }
                    3 -> {
                        item { ComingSoonCard(platform = "TikTok") }
                    }
                    4 -> {
                        item { ComingSoonCard(platform = "Facebook") }
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
    resumeTick: Int,
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
                        DownloadItem(record, context, resumeTick)
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

private sealed class DlState {
    object Starting : DlState()
    data class Progress(val percent: Int, val text: String) : DlState()
    data class Done(val title: String) : DlState()
    data class Error(val message: String) : DlState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadInputCard(
    placeholder: String,
    onDownload: (url: String, quality: Quality) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var showQuality by remember { mutableStateOf(false) }
    var dlState by remember { mutableStateOf<DlState?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (dlState == null) return
                when (intent.action) {
                    DownloadService.ACTION_DOWNLOAD_PROGRESS -> {
                        val pct = intent.getIntExtra(DownloadService.EXTRA_PROGRESS_PERCENT, 0)
                        val text = intent.getStringExtra(DownloadService.EXTRA_PROGRESS_TEXT) ?: ""
                        dlState = DlState.Progress(pct, text)
                    }
                    DownloadService.ACTION_DOWNLOAD_COMPLETE -> {
                        val title = intent.getStringExtra(DownloadService.EXTRA_COMPLETE_TITLE) ?: "Saved"
                        dlState = DlState.Done(title)
                    }
                    DownloadService.ACTION_DOWNLOAD_ERROR -> {
                        val msg = intent.getStringExtra(DownloadService.EXTRA_ERROR_MESSAGE) ?: "Download failed"
                        dlState = DlState.Error(msg)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(DownloadService.ACTION_DOWNLOAD_PROGRESS)
            addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE)
            addAction(DownloadService.ACTION_DOWNLOAD_ERROR)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

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
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = ColorPrimary)
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
                dlState = DlState.Starting
                onDownload(trimmed, quality)
            },
            onDismiss = { showQuality = false },
        )
    }

    dlState?.let { state ->
        DownloadProgressDialog(
            state = state,
            onBackground = { dlState = null; url = "" },
        )
    }
}

@Composable
private fun DownloadProgressDialog(state: DlState, onBackground: () -> Unit) {
    Dialog(onDismissRequest = onBackground) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ColorSurface,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    when (state) {
                        is DlState.Starting -> CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = ColorPrimary,
                            trackColor = ColorSurfaceVariant,
                            strokeWidth = 5.dp,
                        )
                        is DlState.Progress -> {
                            CircularProgressIndicator(
                                progress = { state.percent / 100f },
                                modifier = Modifier.fillMaxSize(),
                                color = ColorPrimary,
                                trackColor = ColorSurfaceVariant,
                                strokeWidth = 5.dp,
                            )
                            Text("${state.percent}%", color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        is DlState.Done -> Icon(
                            Icons.Filled.Download, contentDescription = null,
                            tint = ColorSuccess, modifier = Modifier.size(36.dp),
                        )
                        is DlState.Error -> Icon(
                            Icons.Filled.Download, contentDescription = null,
                            tint = ColorError, modifier = Modifier.size(36.dp),
                        )
                    }
                }

                Text(
                    when (state) {
                        is DlState.Starting -> "Starting download…"
                        is DlState.Progress -> state.text.ifBlank { "Downloading…" }
                        is DlState.Done -> "Saved ✓"
                        is DlState.Error -> "Download failed"
                    },
                    color = ColorTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (state is DlState.Done) {
                    Text(state.title, color = ColorTextTertiary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                if (state is DlState.Error) {
                    Text(state.message, color = ColorTextTertiary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }

                Button(
                    onClick = onBackground,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                ) {
                    Text(
                        when (state) {
                            is DlState.Done, is DlState.Error -> "Done"
                            else -> "Do it in background"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
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
private fun ComingSoonCard(platform: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Download, contentDescription = null, tint = ColorTextTertiary, modifier = Modifier.size(40.dp))
            Text("$platform support coming soon", color = ColorTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("We're working on it — check back later.", color = ColorTextTertiary, fontSize = 13.sp)
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
private fun DownloadItem(record: DownloadRecord, context: Context, resumeTick: Int) {
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
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/*")
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
