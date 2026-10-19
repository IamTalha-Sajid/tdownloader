package com.tdownload.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tdownload.app.*
import com.tdownload.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val URL_REGEX = Regex("""https?://\S+""")

private sealed interface Preview {
    data object Idle : Preview
    data object Loading : Preview
    data class Ready(val post: DirectPost) : Preview
    data object Failed : Preview
}

/** One screen for every site: paste a link, see what it is, pick what to keep, download. */
@Composable
fun HomeTab(
    modifier: Modifier,
    initState: String,
    recent: List<DownloadRecord>,
    resumeTick: Int,
    onSeeAll: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    var text by rememberSaveable { mutableStateOf("") }
    var audio by rememberSaveable { mutableStateOf(false) }
    var qualityIndex by rememberSaveable { mutableIntStateOf(0) }
    val downloads by DownloadTracker.downloads.collectAsState()

    val link = remember(text) { URL_REGEX.find(text)?.value }
    val site = remember(link) { link?.let(Site::detect) }

    // Resolve the post in the background so we can preview it. Re-runs (and cancels the previous run)
    // whenever the link changes, so the delay doubles as a debounce while typing or pasting.
    val preview by produceState<Preview>(Preview.Idle, link, site) {
        val resolver = site?.resolver
        if (link == null || resolver == null) {
            value = Preview.Idle
            return@produceState
        }
        value = Preview.Loading
        delay(350)
        value = withContext(Dispatchers.IO) { resolver(link) }?.let { Preview.Ready(it) } ?: Preview.Failed
    }
    val post = (preview as? Preview.Ready)?.post
    val selected = remember(post) { mutableStateListOf<Int>().also { list -> post?.items?.indices?.let(list::addAll) } }

    val problem = when {
        text.isBlank() -> null
        link == null -> "That doesn't look like a link."
        site == null -> "TDownloader doesn't support this site yet."
        !site.supported -> "${site.label} downloads are coming soon."
        preview is Preview.Failed -> "Couldn't read this post — it may be private or removed."
        else -> null
    }
    val ready = link != null && site?.supported == true && (post == null || selected.isNotEmpty())
    // Video/Audio toggle: for sites we preview it needs a video in the post; sites without a preview
    // (Facebook) can't know, so they always offer it. YouTube has its own quality chips instead.
    val offersMode = site != null && site != Site.YouTube && (site.resolver == null || post?.items?.any { it.isVideo } == true)
    val audioMode = audio && offersMode
    val options = link?.let(::qualitiesFor) ?: QUALITIES
    val quality = when {
        site == Site.YouTube -> QUALITIES[qualityIndex]
        audioMode -> options.last()
        else -> options.first()
    }

    fun startNow() {
        val target = link ?: return
        keyboard?.hide()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val items = if (post != null && selected.size != post.items.size) selected.sorted() else null
        startDownload(context, target, quality, items)
        text = ""
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(ColorBackground).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(initState) }

        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste a link…", color = ColorTextTertiary, fontSize = 14.sp) },
                singleLine = true,
                isError = problem != null,
                supportingText = problem?.let { { Text(it, fontSize = 12.sp) } },
                trailingIcon = {
                    if (text.isBlank()) {
                        IconButton(onClick = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { text = it } }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = ColorPrimary)
                        }
                    } else {
                        IconButton(onClick = { text = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = ColorTextSecondary)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (ready) startNow() else keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorPrimary,
                    unfocusedBorderColor = ColorOutline,
                    cursorColor = ColorPrimary,
                    errorBorderColor = ColorError,
                    errorSupportingTextColor = ColorError,
                ),
                shape = RoundedCornerShape(12.dp),
            )
        }

        if (site != null && site.supported) {
            item {
                ActionCard(
                    site = site,
                    preview = preview,
                    selected = selected,
                    showModeToggle = offersMode,
                    audio = audio,
                    onAudio = { audio = it },
                    qualityIndex = qualityIndex,
                    onQuality = { qualityIndex = it },
                    buttonLabel = downloadLabel(post, selected.size, audioMode),
                    enabled = ready,
                    onDownload = ::startNow,
                )
            }
        } else if (text.isBlank()) {
            item { SupportedPlatforms() }
        }

        if (downloads.isNotEmpty()) {
            item { SectionTitle("Downloading") }
            items(downloads, key = { it.id }) { ActiveDownloadCard(it) }
        }

        if (recent.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionTitle("Recent")
                    TextButton(onClick = onSeeAll) { Text("See all", color = ColorPrimary, fontSize = 13.sp) }
                }
            }
            items(recent.take(3), key = { it.id }) { record ->
                SwipeToDeleteItem(onDelete = { scope.launch { DownloadStore.delete(context, record.id) } }) {
                    DownloadItem(record, context, resumeTick)
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun downloadLabel(post: DirectPost?, selectedCount: Int, audio: Boolean): String = when {
    audio -> "Download audio"
    post != null && post.items.size > 1 -> "Download $selectedCount of ${post.items.size}"
    else -> "Download"
}

@Composable
private fun Header(initState: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
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
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = ColorTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SupportedPlatforms() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Supported Platforms")
        // Icons only, evenly spread, so all of them fit on one line without scrolling. WhatsApp is left
        // out: this row is about pasting links, and statuses have none (they live in the Status tab).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Site.entries.filter { it != Site.WhatsApp }.forEach { PlatformIcon(it) }
        }
    }
}

@Composable
private fun PlatformIcon(site: Site) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(ColorSurfaceVariant)
            .alpha(if (site.supported) 1f else 0.55f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(site.icon), contentDescription = site.label, tint = ColorTextSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ActionCard(
    site: Site,
    preview: Preview,
    selected: MutableList<Int>,
    showModeToggle: Boolean,
    audio: Boolean,
    onAudio: (Boolean) -> Unit,
    qualityIndex: Int,
    onQuality: (Int) -> Unit,
    buttonLabel: String,
    enabled: Boolean,
    onDownload: () -> Unit,
) {
    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(site.icon), contentDescription = null, tint = ColorPrimary, modifier = Modifier.size(18.dp))
                Text(site.label, color = ColorTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val owner = (preview as? Preview.Ready)?.post?.owner.orEmpty()
                if (owner.isNotBlank()) Text("@$owner", color = ColorTextTertiary, fontSize = 13.sp)
            }

            when (preview) {
                is Preview.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = ColorPrimary)
                    Text("Reading post…", color = ColorTextTertiary, fontSize = 13.sp)
                }
                is Preview.Ready -> PostPreview(preview.post, selected)
                else -> Unit
            }

            if (site == Site.YouTube) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QUALITIES.forEachIndexed { i, q ->
                        OptionChip(q.label, selected = i == qualityIndex) { onQuality(i) }
                    }
                }
            } else if (showModeToggle) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionChip("Video", selected = !audio) { onAudio(false) }
                    OptionChip("Audio", selected = audio) { onAudio(true) }
                }
            }

            Button(
                onClick = onDownload,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
            ) {
                Text(buttonLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ColorSurfaceVariant,
            labelColor = ColorTextSecondary,
            selectedContainerColor = ColorPrimary.copy(alpha = 0.18f),
            selectedLabelColor = ColorPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = ColorOutline,
            selectedBorderColor = ColorPrimary,
        ),
    )
}

@Composable
private fun PostPreview(post: DirectPost, selected: MutableList<Int>) {
    val multi = post.items.size > 1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (post.caption.isNotBlank()) {
            Text(post.caption, color = ColorTextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(post.items.size) { i ->
                val media = post.items[i]
                val isSelected = i in selected
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (isSelected && multi) Modifier.border(BorderStroke(2.dp, ColorPrimary), RoundedCornerShape(10.dp)) else Modifier)
                        .clickable(enabled = multi) { if (isSelected) selected.remove(i) else selected.add(i) },
                ) {
                    RemoteImage(
                        url = media.thumbnailUrl,
                        headers = media.headers,
                        modifier = Modifier.fillMaxSize().alpha(if (isSelected || !multi) 1f else 0.4f),
                    )
                    if (media.isVideo) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp),
                        )
                    }
                    if (multi) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) ColorPrimary else Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = ColorOnPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        if (multi) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${selected.size} of ${post.items.size} selected", color = ColorTextTertiary, fontSize = 12.sp)
                val all = selected.size == post.items.size
                Text(
                    if (all) "Select none" else "Select all",
                    color = ColorPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        selected.clear()
                        if (!all) selected.addAll(post.items.indices)
                    },
                )
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(download: ActiveDownload) {
    val state = download.state
    if (state is DownloadState.Done) {
        // The file is already in Recent, so the card only needs to confirm briefly.
        LaunchedEffect(download.id) {
            delay(4000)
            DownloadTracker.dismiss(download.id)
        }
    }
    Surface(color = ColorSurfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                download.site?.let {
                    Icon(painterResource(it.icon), contentDescription = null, tint = ColorTextSecondary, modifier = Modifier.size(18.dp))
                }
                Text(
                    download.title.takeUnless { it.startsWith("http") } ?: "${download.site?.label ?: "Link"} download",
                    color = ColorTextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when (state) {
                    is DownloadState.Progress -> if (state.percent > 0) Text("${state.percent}%", color = ColorTextSecondary, fontSize = 13.sp)
                    is DownloadState.Done -> Icon(Icons.Filled.CheckCircle, contentDescription = "Saved", tint = ColorSuccess, modifier = Modifier.size(20.dp))
                    is DownloadState.Failed -> IconButton(onClick = { DownloadTracker.dismiss(download.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = ColorTextSecondary, modifier = Modifier.size(18.dp))
                    }
                    DownloadState.Starting -> Unit
                }
            }
            when (state) {
                DownloadState.Starting -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = ColorPrimary, trackColor = ColorSurface)
                is DownloadState.Progress -> {
                    if (state.percent > 0) {
                        LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = ColorPrimary, trackColor = ColorSurface)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = ColorPrimary, trackColor = ColorSurface)
                    }
                    if (state.detail.isNotBlank()) Text(state.detail, color = ColorTextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                is DownloadState.Done -> Text("Saved ✓", color = ColorSuccess, fontSize = 12.sp)
                is DownloadState.Failed -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = ColorError, modifier = Modifier.size(14.dp))
                    Text(state.message, color = ColorTextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
