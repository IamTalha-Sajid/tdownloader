package com.tdownload.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.tdownload.app.DirectHttp
import com.tdownload.app.ui.theme.ColorSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_SIDE_PX = 512
private val remoteImageCache = LruCache<String, Bitmap>(40)

/** A tiny image loader for post previews (the app has no image library). Shows a blank tile until loaded. */
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, headers: Map<String, String> = emptyMap()) {
    val bitmap by produceState<Bitmap?>(initialValue = url?.let { remoteImageCache.get(it) }, key1 = url) {
        value = when {
            url == null -> null
            else -> remoteImageCache.get(url) ?: withContext(Dispatchers.IO) {
                runCatching { decodeSampled(DirectHttp.getBytes(url, headers)) }.getOrNull()
            }?.also { remoteImageCache.put(url, it) }
        }
    }
    Box(modifier.background(ColorSurface)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun decodeSampled(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
