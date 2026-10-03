package com.zernex.video.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zernex.video.core.media.MediaMetadataHelper
import com.zernex.video.domain.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ThumbnailMemoryCache {
    private val cache = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    @Synchronized fun get(key: String): Bitmap? = cache.get(key)
    @Synchronized fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

@Composable
fun VideoThumbnail(
    video: VideoItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    widthPx: Int = 640,
    heightPx: Int = 360,
) {
    val context = LocalContext.current
    val cacheKey = "${video.uri}|${video.dateModified}|$widthPx|$heightPx"
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = cacheKey) {
        value = ThumbnailMemoryCache.get(cacheKey)
            ?: withContext(Dispatchers.IO) {
                loadThumbnail(context, video, widthPx, heightPx)
            }?.also { ThumbnailMemoryCache.put(cacheKey, it) }
    }

    Box(modifier = modifier.background(Color(0xFF17171A)), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = video.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF242832), Color(0xFF111318), Color(0xFF0B0C0F))
                        )
                    )
                    .padding(12.dp)
            ) {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.align(Alignment.Center).size(34.dp)
                )
                Text(
                    video.name,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
    }
}

private fun loadThumbnail(
    context: android.content.Context,
    video: VideoItem,
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    val resolver = context.contentResolver
    val uri: Uri = video.uri
    if (Build.VERSION.SDK_INT >= 29) {
        runCatching {
            resolver.loadThumbnail(uri, Size(widthPx, heightPx), null)
        }.getOrNull()?.let { return scaleToBounds(it, widthPx, heightPx) }
    }

    val timeMs = when {
        video.durationMs > 0L -> (video.durationMs / 3L).coerceAtLeast(0L)
        else -> 0L
    }
    return MediaMetadataHelper(resolver).frame(uri, timeMs, widthPx, heightPx)
}

private fun scaleToBounds(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap
    val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true
    )
}
