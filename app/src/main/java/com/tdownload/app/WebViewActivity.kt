package com.tdownload.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tdownload.app.ui.theme.*
import java.io.File

class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_HOST = "host"
        const val EXTRA_COOKIES_FILE = "cookies_file"
        const val EXTRA_LOGIN_URL = "login_url"
        const val EXTRA_PLATFORM = "platform"

        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cookiesFile = intent.getStringExtra(EXTRA_COOKIES_FILE) ?: "cookies.txt"
        val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: "Instagram"

        setContent {
            TDownloadTheme {
                LoginMethodScreen(
                    platform = platform,
                    cookiesFile = cookiesFile,
                    existingId = readExistingSessionId(cookiesFile),
                    onSaveSessionId = { sessionId -> saveSessionId(sessionId, cookiesFile, platform) },
                    onSaveCookiesFromWebView = { saveCookiesFromWebView(cookiesFile, platform) },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun readExistingSessionId(cookiesFile: String): String {
        val file = File(filesDir, cookiesFile)
        if (!file.exists()) return ""
        return file.readLines()
            .firstOrNull { it.contains("\tsessionid\t") }
            ?.substringAfterLast("\t")?.trim() ?: ""
    }

    private fun saveSessionId(sessionId: String, cookiesFile: String, platform: String) {
        val trimmed = sessionId.trim()
        if (trimmed.isBlank()) {
            Toast.makeText(this, "Please enter your session ID", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Saving…", Toast.LENGTH_SHORT).show()
        Thread {
            val expiry = System.currentTimeMillis() / 1000 + 60 * 60 * 24 * 365
            val domain = cookieDomain(platform)
            val content = buildString {
                appendLine("# Netscape HTTP Cookie File")
                appendLine("$domain\tTRUE\t/\tTRUE\t$expiry\tsessionid\t$trimmed")
                if (platform == "Instagram") {
                    val csrf = fetchPublicCookie("https://www.instagram.com/", "csrftoken")
                    if (csrf.isNotBlank())
                        appendLine("$domain\tTRUE\t/\tTRUE\t$expiry\tcsrftoken\t$csrf")
                }
            }
            File(filesDir, cookiesFile).writeText(content)
            runOnUiThread {
                Toast.makeText(this, "$platform login saved ✓", Toast.LENGTH_LONG).show()
                finish()
            }
        }.start()
    }

    private fun saveCookiesFromWebView(cookiesFile: String, platform: String) {
        CookieManager.getInstance().flush()
        val domain = cookieDomain(platform)
        val rawUrl = "https://www.${domain.trimStart('.')}"
        val raw = CookieManager.getInstance().getCookie(rawUrl)
            ?: CookieManager.getInstance().getCookie(domain)
        if (raw.isNullOrBlank()) {
            Toast.makeText(this, "No cookies found — are you logged in?", Toast.LENGTH_LONG).show()
            return
        }
        val expiry = System.currentTimeMillis() / 1000 + 60 * 60 * 24 * 365
        val content = buildString {
            appendLine("# Netscape HTTP Cookie File")
            raw.split(";").forEach { pair ->
                val parts = pair.trim().split("=", limit = 2)
                val name = parts[0].trim()
                val value = parts.getOrNull(1)?.trim() ?: ""
                if (name.isNotEmpty())
                    appendLine("$domain\tTRUE\t/\tTRUE\t$expiry\t$name\t$value")
            }
        }
        File(filesDir, cookiesFile).writeText(content)
        Toast.makeText(this, "Logged in ✓ Cookies saved", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun cookieDomain(platform: String) = when (platform) {
        "Instagram" -> ".instagram.com"
        "TikTok"   -> ".tiktok.com"
        else       -> ".${platform.lowercase()}.com"
    }

    private fun fetchPublicCookie(url: String, name: String): String = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("User-Agent", DESKTOP_UA)
        conn.connect()
        val cookies = conn.headerFields["Set-Cookie"] ?: return@runCatching ""
        conn.disconnect()
        cookies.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("$name=")?.substringBefore(";")?.trim() ?: ""
    }.getOrDefault("")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginMethodScreen(
    platform: String,
    cookiesFile: String,
    existingId: String,
    onSaveSessionId: (String) -> Unit,
    onSaveCookiesFromWebView: () -> Unit,
    onCancel: () -> Unit,
) {
    var showWebView by remember { mutableStateOf(false) }

    if (showWebView) {
        WebViewLoginScreen(
            platform = platform,
            onSaveCookies = onSaveCookiesFromWebView,
            onBack = { showWebView = false },
        )
    } else {
        SessionIdScreen(
            platform = platform,
            existingId = existingId,
            onSave = onSaveSessionId,
            onCancel = onCancel,
            onTryWebView = { showWebView = true },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewLoginScreen(
    platform: String,
    onSaveCookies: () -> Unit,
    onBack: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf(platform) }
    var currentUrl by remember { mutableStateOf("") }

    // System back returns to session ID screen instead of exiting the activity
    BackHandler { onBack() }

    val loginUrl = when (platform) {
        "TikTok" -> "https://www.tiktok.com/login"
        else     -> "https://www.instagram.com/accounts/login/"
    }

    val isOnLoginPage = currentUrl.isBlank() || currentUrl.contains("/login", ignoreCase = true) ||
        currentUrl.contains("/accounts/", ignoreCase = true)

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onBack) { Text("← Back") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pageTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("Log in, then tap the button →", fontSize = 10.sp, color = ColorTextTertiary)
                    }
                    Button(
                        onClick = onSaveCookies,
                        enabled = !isOnLoginPage,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorPrimary,
                            contentColor = ColorOnPrimary,
                        ),
                    ) { Text("I'm logged in ✓") }
                }
                if (isLoading) LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = ColorPrimary,
                )
            }
        }

        AndroidView(
            factory = { ctx ->
                // Clear old WebView cookies so we get a fresh login
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()

                WebView(ctx).apply {
                    with(settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        // Desktop Chrome UA — bypasses mobile WebView detection
                        userAgentString = WebViewActivity.DESKTOP_UA
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            isLoading = true
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            pageTitle = view.title?.takeIf { it.isNotBlank() } ?: platform
                            currentUrl = url
                            isLoading = false
                            view.evaluateJavascript(
                                "Object.defineProperty(navigator,'webdriver',{get:()=>undefined});",
                                null
                            )
                        }
                    }
                    loadUrl(loginUrl)
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
fun SessionIdScreen(
    platform: String = "Instagram",
    existingId: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    onTryWebView: (() -> Unit)? = null,
) {
    var sessionId by remember { mutableStateOf(existingId) }
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val siteUrl = when (platform) {
        "Instagram" -> "instagram.com"
        "TikTok"   -> "tiktok.com"
        else       -> "${platform.lowercase()}.com"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("$platform Login", fontSize = 22.sp, color = ColorTextPrimary, fontWeight = FontWeight.Bold)

        // Option 1 — WebView (recommended, no PC needed)
        if (onTryWebView != null) {
            Surface(color = ColorSurfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = ColorPrimary, shape = RoundedCornerShape(4.dp)) {
                            Text("  Option 1  ", fontSize = 11.sp, color = ColorOnPrimary, fontWeight = FontWeight.Bold)
                        }
                        Text("Login in-app — no PC needed", fontSize = 13.sp, color = ColorTextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Opens $siteUrl in a desktop browser inside the app. Log in normally, then tap \"I'm logged in ✓\".",
                        fontSize = 13.sp, color = ColorTextSecondary,
                    )
                    Button(
                        onClick = onTryWebView,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    ) {
                        Text("Open browser & log in", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = ColorOutline)
                Text("or", fontSize = 11.sp, color = ColorTextTertiary)
                HorizontalDivider(modifier = Modifier.weight(1f), color = ColorOutline)
            }
        }

        // Option 2 — Manual session ID
        Surface(color = ColorSurfaceVariant, shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = ColorOutline, shape = RoundedCornerShape(4.dp)) {
                        Text(if (onTryWebView != null) "  Option 2  " else "  Manual  ", fontSize = 11.sp, color = ColorTextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Text("Paste session ID from PC", fontSize = 13.sp, color = ColorTextPrimary, fontWeight = FontWeight.SemiBold)
                }
                listOf(
                    "Open $siteUrl in Chrome on your PC and log in",
                    "Press F12 → Application tab → Cookies → $siteUrl",
                    "Find sessionid, copy its value, paste below",
                ).forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${i+1}.", color = ColorPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(step, color = ColorTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        OutlinedTextField(
            value = sessionId,
            onValueChange = { sessionId = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            label = { Text("Paste sessionid here") },
            placeholder = { Text("e.g. 12345678%3AabcXYZ…", color = ColorTextTertiary) },
            singleLine = false,
            minLines = 2,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onSave(sessionId) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ColorPrimary,
                unfocusedBorderColor = ColorOutline,
                focusedLabelColor = ColorPrimary,
                cursorColor = ColorPrimary,
            ),
            shape = RoundedCornerShape(10.dp),
        )

        OutlinedButton(
            onClick = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { sessionId = it } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorPrimary),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorOutline),
        ) { Text("Paste from clipboard") }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, ColorOutline),
            ) { Text("Cancel") }
            Button(
                onClick = { keyboard?.hide(); onSave(sessionId) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
            ) { Text("Save") }
        }
    }
}
