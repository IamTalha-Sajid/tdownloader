package com.tdownload.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tdownload.app.ui.theme.*

data class Quality(val label: String, val format: String, val audioOnly: Boolean = false)

val QUALITIES = listOf(
    Quality("Best",   "bestvideo[ext=mp4][vcodec^=avc]+bestaudio[ext=m4a]/bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"),
    Quality("1080p",  "bestvideo[ext=mp4][vcodec^=avc][height<=1080]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best[height<=1080]"),
    Quality("720p",   "bestvideo[ext=mp4][vcodec^=avc][height<=720]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best[height<=720]"),
    Quality("480p",   "bestvideo[ext=mp4][vcodec^=avc][height<=480]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best[height<=480]"),
    Quality("Audio",  "bestaudio[ext=m4a]/bestaudio", audioOnly = true),
)

class ShareActivity : ComponentActivity() {

    private val urlRegex = Regex("""https?://\S+""")

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { showQualityPicker(pendingUrl) }

    private var pendingUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val url = sharedText?.let { urlRegex.find(it)?.value }

        if (url.isNullOrBlank()) {
            Toast.makeText(this, "No link found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingUrl = url
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showQualityPicker(url)
        }
    }

    private fun showQualityPicker(url: String) {
        setContent {
            QualityPickerSheet(
                url = url,
                onSelect = { quality -> startDownload(url, quality) },
                onDismiss = { finish() },
            )
        }
    }

    private fun startDownload(url: String, quality: Quality) {
        Intent(this, DownloadService::class.java).also {
            it.putExtra(DownloadService.EXTRA_URL, url)
            it.putExtra(DownloadService.EXTRA_FORMAT, quality.format)
            it.putExtra(DownloadService.EXTRA_AUDIO_ONLY, quality.audioOnly)
            startForegroundService(it)
        }
        finish()
    }
}

@Composable
private fun QualityPickerSheet(
    url: String,
    onSelect: (Quality) -> Unit,
    onDismiss: () -> Unit,
) {
    // Dim background — tap outside to dismiss
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(ColorSurface)
                .clickable(enabled = false) {} // block pass-through
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ColorOutline)
                    .align(Alignment.CenterHorizontally)
            )

            Text(
                "Select Quality",
                fontSize = 16.sp,
                color = ColorTextPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            HorizontalDivider(color = ColorOutline, thickness = 0.5.dp)

            QUALITIES.forEach { quality ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(quality) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(quality.label, fontSize = 15.sp, color = ColorTextPrimary)
                    if (quality.label == "Best") {
                        Text(
                            "recommended",
                            fontSize = 11.sp,
                            color = ColorPrimary,
                        )
                    }
                    if (quality.audioOnly) {
                        Text(
                            "m4a",
                            fontSize = 11.sp,
                            color = ColorTextTertiary,
                        )
                    }
                }
                if (quality != QUALITIES.last()) {
                    HorizontalDivider(
                        color = ColorOutline,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}
