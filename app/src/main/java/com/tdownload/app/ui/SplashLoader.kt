package com.tdownload.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tdownload.app.ui.theme.ColorBackground
import com.tdownload.app.ui.theme.ColorOnPrimary
import com.tdownload.app.ui.theme.ColorPrimary
import com.tdownload.app.ui.theme.ColorTextPrimary
import com.tdownload.app.ui.theme.ColorTextTertiary
import kotlinx.coroutines.delay

@Composable
fun SplashLoader(statusText: String, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(400)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ColorBackground),
            contentAlignment = Alignment.Center,
        ) {
            PulsingRings()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BouncingArrow()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = ColorPrimary)) { append("T") }
                            withStyle(SpanStyle(color = ColorTextPrimary)) { append("Downloader") }
                        },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    AnimatedStatusText(statusText)
                }
            }
        }
    }
}

@Composable
private fun PulsingRings() {
    val infiniteTransition = rememberInfiniteTransition(label = "rings")

    // Two rings offset by half a cycle
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1_scale",
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1_alpha",
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOut, delayMillis = 900),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring2_scale",
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOut, delayMillis = 900),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring2_alpha",
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale1)
                .background(ColorPrimary.copy(alpha = alpha1), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale2)
                .background(ColorPrimary.copy(alpha = alpha2), CircleShape)
        )
    }
}

@Composable
private fun BouncingArrow() {
    val infiniteTransition = rememberInfiniteTransition(label = "arrow")

    val offsetY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrow_y",
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .offset(y = offsetY.dp)
            .background(ColorPrimary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = ColorOnPrimary,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun AnimatedStatusText(text: String) {
    // Dots animate: "Initializing." → ".." → "..."
    var dots by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        while (true) {
            dots = "."; delay(400)
            dots = ".."; delay(400)
            dots = "..."; delay(400)
            dots = ""; delay(400)
        }
    }

    val display = if (text.endsWith("…") || text.endsWith("...")) {
        text.trimEnd('…', '.') + dots
    } else {
        text
    }

    Text(
        display,
        fontSize = 13.sp,
        color = ColorTextTertiary,
        fontWeight = FontWeight.Normal,
    )
}
