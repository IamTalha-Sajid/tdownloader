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
 * Wraps the app's UI. If a newer version than this one exists it shows [ForceUpdateScreen] instead of
 * [content], so nothing in the app can be used until it is updated. The cached answer applies instantly;
 * the network refresh updates it a moment later.
 */
@Composable
fun UpdateGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(UpdateChecker.cachedStatus(context)) }
    LaunchedEffect(Unit) { status = withContext(Dispatchers.IO) { UpdateChecker.refresh(context) } }

    when (val current = status) {
        is UpdateStatus.Required -> ForceUpdateScreen(current.info, UpdateChecker.currentVersion(context))
        else -> content()
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
        Text("Installed $installed · latest ${info.latest}", color = ColorTextTertiary, fontSize = 12.sp)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { runCatching { uriHandler.openUri(info.url) } },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
        ) { Text("Update now", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        Text("Your downloads stay on your phone.", color = ColorTextTertiary, fontSize = 12.sp)
    }
}
