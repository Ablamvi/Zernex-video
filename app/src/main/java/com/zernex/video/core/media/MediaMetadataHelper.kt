package com.zernex.video.core.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

class MediaMetadataHelper(private val resolver: ContentResolver) {
    fun frame(uri: Uri, timeMs: Long = 0L, maxWidth: Int? = null, maxHeight: Int? = null): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                else retriever.setDataSource(afd.fileDescriptor)
                val bitmap = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null && maxWidth != null && maxHeight != null) scaleToBounds(bitmap, maxWidth, maxHeight) else bitmap
            }
        } finally { retriever.release() }
    }.getOrNull()

    private fun scaleToBounds(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap
        val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }
}
