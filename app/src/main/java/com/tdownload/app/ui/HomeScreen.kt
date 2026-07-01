package com.tdownload.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var initState by remember { mutableStateOf("Initializing…") }
    val cookiesExist = remember { mutableStateOf(File(context.filesDir, "cookies.txt").exists()) }
    val history by DownloadStore.historyFlow(context).collectAsState(initial = emptyList())
    var navIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                YtdlManager.ensureInit(context)
                withContext(Dispatchers.Main) { initState = "Ready" }
                YtdlManager.update(context)
            }.onFailure {
                withContext(Dispatchers.Main) { initState = "Error: ${it.message?.take(40)}" }
            }
        }
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

    Scaffold(
        containerColor = ColorBackground,
        contentWindowInsets = WindowInsets(0),
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
                    badge = if (history.isNotEmpty()) history.size.toString() else null,
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

@Composable
private fun DownloadTab(
    modifier: Modifier,
    initState: String,
    cookiesExist: MutableState<Boolean>,
    context: Context,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Instagram", "YouTube", "TikTok")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding(),
    ) {
        // Header
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

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.padding(horizontal = 20.dp),
            containerColor = ColorSurface,
            contentColor = ColorPrimary,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ColorPrimary,
                )
            },
            divider = {},
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == i) ColorPrimary else ColorTextSecondary,
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (selectedTab) {
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

@Composable
private fun HistoryTab(
    modifier: Modifier,
    history: List<DownloadRecord>,
    context: Context,
) {
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
                Text("No downloads yet", color = ColorTextTertiary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(history) { record ->
                    DownloadItem(record, context)
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun DownloadInputCard(
    placeholder: String,
    onDownload: (url: String, quality: Quality) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var showQuality by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, color = ColorTextTertiary, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = {
                    keyboard?.hide()
                    if (url.isNotBlank()) showQuality = true
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorPrimary,
                    unfocusedBorderColor = ColorOutline,
                    cursorColor = ColorPrimary,
                ),
                shape = RoundedCornerShape(10.dp),
            )
            Button(
                onClick = { keyboard?.hide(); if (url.isNotBlank()) showQuality = true },
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
        InlineQualityPicker(
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

@Composable
private fun InlineQualityPicker(onSelect: (Quality) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorSurface,
        title = { Text("Select Quality", color = ColorTextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                QUALITIES.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(quality) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(quality.label, color = ColorTextPrimary, fontSize = 15.sp)
                        Text(
                            when {
                                quality.label == "Best" -> "recommended"
                                quality.audioOnly -> "m4a"
                                else -> ""
                            },
                            color = if (quality.label == "Best") ColorPrimary else ColorTextTertiary,
                            fontSize = 11.sp,
                        )
                    }
                    if (quality != QUALITIES.last()) HorizontalDivider(color = ColorOutline, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ColorTextSecondary) } },
    )
}

@Composable
private fun InstagramLoginCard(context: Context, cookiesExist: MutableState<Boolean>) {
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Account", color = ColorTextSecondary, fontSize = 12.sp)
                Text(
                    if (cookiesExist.value) "Logged in ✓" else "Not logged in",
                    color = if (cookiesExist.value) ColorSuccess else ColorTextTertiary,
                    fontSize = 14.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cookiesExist.value) {
                    OutlinedButton(
                        onClick = { File(context.filesDir, "cookies.txt").delete(); cookiesExist.value = false },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorError),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorError),
                    ) { Text("Log out", fontSize = 12.sp) }
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
                ) { Text(if (cookiesExist.value) "Re-login" else "Login", fontSize = 12.sp) }
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
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Account", color = ColorTextSecondary, fontSize = 12.sp)
                Text(
                    if (cookiesExist.value) "Logged in ✓" else "Not logged in",
                    color = if (cookiesExist.value) ColorSuccess else ColorTextTertiary,
                    fontSize = 14.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cookiesExist.value) {
                    OutlinedButton(
                        onClick = { File(context.filesDir, cookiesFile).delete(); cookiesExist.value = false },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorError),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorError),
                    ) { Text("Log out", fontSize = 12.sp) }
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
                ) { Text(if (cookiesExist.value) "Re-login" else "Login", fontSize = 12.sp) }
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
        Text(message, color = ColorTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
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
            Column(modifier = Modifier.weight(1f)) {
                Text(record.title, color = ColorTextPrimary, fontSize = 14.sp, maxLines = 1)
                Text(fmt.format(Date(record.timestamp)), color = ColorTextTertiary, fontSize = 11.sp)
            }
        }
    }
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
