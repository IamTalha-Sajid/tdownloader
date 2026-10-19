package com.tdownload.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tdownload.app.UpdateChecker
import com.tdownload.app.UpdateInfo
import com.tdownload.app.UpdateStatus
import com.tdownload.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps the app's UI. If this version has been retired it shows [ForceUpdateScreen] instead of
 * [content], so nothing in the app can be used; if only a newer version exists it offers a dismissible
 * prompt. The cached answer applies instantly; the network refresh updates it a moment later.
 */
@Composable
fun UpdateGate(showOptionalPrompt: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(UpdateChecker.cachedStatus(context)) }
    LaunchedEffect(Unit) { status = withContext(Dispatchers.IO) { UpdateChecker.refresh(context) } }

    when (val current = status) {
        is UpdateStatus.Required -> ForceUpdateScreen(current.info, UpdateChecker.currentVersion(context))
        else -> {
            content()
            if (showOptionalPrompt && current is UpdateStatus.Available) {
                var dismissed by remember(current.info.latest) {
                    mutableStateOf(UpdateChecker.dismissedLatest(context) == current.info.latest)
                }
                if (!dismissed) {
                    UpdateAvailableDialog(
                        info = current.info,
                        installed = UpdateChecker.currentVersion(context),
                        onLater = {
                            UpdateChecker.dismiss(context, current.info.latest)
                            dismissed = true
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ForceUpdateScreen(info: UpdateInfo, installed: String) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = ColorPrimary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Text("Update required", color = ColorTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            info.message.ifBlank { "This version of TDownloader is no longer supported. Please update to keep downloading." },
            color = ColorTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text("Installed $installed · required ${info.minimum} or newer", color = ColorTextTertiary, fontSize = 12.sp)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { runCatching { uriHandler.openUri(info.url) } },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
        ) { Text("Update now", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        Text("Your downloads and library stay on your phone.", color = ColorTextTertiary, fontSize = 12.sp)
    }
}

@Composable
private fun UpdateAvailableDialog(info: UpdateInfo, installed: String, onLater: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = ColorSurface,
        title = { Text("Update available", color = ColorTextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                buildString {
                    append("Version ${info.latest} is out — you're on $installed.")
                    if (info.message.isNotBlank()) append("\n\n${info.message}")
                },
                color = ColorTextSecondary,
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = { runCatching { uriHandler.openUri(info.url) }; onLater() }) {
                Text("Update", color = ColorPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onLater) { Text("Later", color = ColorTextSecondary) } },
    )
}
