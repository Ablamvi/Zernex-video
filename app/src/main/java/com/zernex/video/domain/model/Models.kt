package com.zernex.video.domain.model

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
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
    val favorite: Boolean = false,
    val lastPositionMs: Long = 0L,
    val playCount: Int = 0,
    val lastPlayed: Long = 0L,
    val seriesId: String? = null
) {
    val folderName: String
        get() = (relativePath ?: "Vidéo").trimEnd('/').substringAfterLast('/').ifBlank { "Vidéo" }

    val resolution: String
        get() = if (width > 0 && height > 0) "${width}×${height}" else "—"
}

data class Series(
    val id: String,
    val title: String,
    val season: Int,
    val episodeCount: Int
)

data class SeriesSeason(
    val season: Int,
    val episodes: List<VideoItem>
)

data class SeriesGroup(
    val id: String,
    val title: String,
    val seasons: List<SeriesSeason>
) {
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
    val seasonCount: Int get() = seasons.size
    val allEpisodes: List<VideoItem> get() = seasons.flatMap { it.episodes }
}
