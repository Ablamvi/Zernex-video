package com.zernex.video.data.datasource

import android.content.ContentResolver
import android.os.Build
import android.provider.MediaStore
import com.zernex.video.core.database.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreDataSource(private val resolver: ContentResolver) {
    suspend fun scan(): List<VideoEntity> = withContext(Dispatchers.IO) {
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        if (Build.VERSION.SDK_INT >= 29) projection += MediaStore.Video.Media.RELATIVE_PATH
        val out = ArrayList<VideoEntity>()
        resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection.toTypedArray(), null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val iAdd = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val iMod = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val iW = c.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val iH = c.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val iRel = c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            val iB = -1
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                out += VideoEntity(
                    id = id,
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build().toString(),
                    name = c.getString(iName) ?: "Vidéo",
                    mimeType = c.getString(iMime) ?: "video/*",
                    relativePath = if (iRel >= 0 && !c.isNull(iRel)) c.getString(iRel) else null,
                    sizeBytes = c.getLong(iSize),
                    durationMs = c.getLong(iDur),
                    dateAdded = c.getLong(iAdd),
                    dateModified = c.getLong(iMod),
                    width = if (iW >= 0 && !c.isNull(iW)) c.getInt(iW) else 0,
                    height = if (iH >= 0 && !c.isNull(iH)) c.getInt(iH) else 0,
                    fps = 0f,
                    bitrate = if (iB >= 0 && !c.isNull(iB)) c.getLong(iB) else 0L,
                    favorite = false, lastPositionMs = 0L, playCount = 0, lastPlayed = 0L, seriesId = null
                )
            }
        }
        out
    }
}
