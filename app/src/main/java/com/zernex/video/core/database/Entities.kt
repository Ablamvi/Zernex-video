package com.zernex.video.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val name: String,
    val mimeType: String,
    val relativePath: String?,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val fps: Float,
    val bitrate: Long,
    val favorite: Boolean,
    val lastPositionMs: Long,
    val playCount: Int,
    val lastPlayed: Long,
    val seriesId: String?
)
