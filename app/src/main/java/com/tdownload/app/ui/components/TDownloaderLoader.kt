package com.tdownload.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tdownload.app.ui.theme.ColorPrimary

/** 2c "Trace": arrow bobs while the tray draws and erases itself. Drawn on a 100×100 grid. */
@Composable
fun TDownloaderLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = ColorPrimary,
    speed: Float = 1f,
) {
    val t = rememberInfiniteTransition(label = "tdLoader")
    val bob by t.animateFloat(
        initialValue = -7f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween((700 / speed).toInt(), easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val draw by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween((1800 / speed).toInt(), easing = FastOutSlowInEasing)),
        label = "draw",
    )

    val arrow = remember {
        Path().apply {
            moveTo(43f, 12f); lineTo(57f, 12f); lineTo(57f, 42f); lineTo(71f, 42f)
            lineTo(50f, 64f); lineTo(29f, 42f); lineTo(43f, 42f); close()
        }
    }
    val tray = remember { Path().apply { moveTo(19f, 56f); lineTo(19f, 81f); lineTo(81f, 81f); lineTo(81f, 56f) } }
    val measure = remember { PathMeasure().apply { setPath(tray, false) } }
    val segment = remember { Path() }
    val stroke = remember { Stroke(width = 11f, cap = StrokeCap.Butt, join = StrokeJoin.Miter) }

    Canvas(modifier.size(size)) {
        val len = measure.length
        val (start, end) = if (draw < 0.5f) 0f to len * draw * 2f else len * (draw - 0.5f) * 2f to len
        segment.reset()
        measure.getSegment(start, end, segment, true)
        scale(this.size.width / 100f, this.size.height / 100f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            translate(top = bob) { drawPath(arrow, color) }
            drawPath(segment, color, style = stroke)
        }
    }
}
