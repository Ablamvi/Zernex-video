package com.zernex.video.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zernex.video.domain.model.VideoItem

@Composable
fun VideoCard(v: VideoItem, onOpen: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(132.dp)) {
                VideoThumbnail(v, Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)))
                if (v.favorite) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.58f)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "Favori", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(5.dp))
                    }
                }
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        if (v.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (v.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = Color.White
                    )
                }
                if (v.durationMs > 0L) {
                    DurationChip(v.durationMs, Modifier.align(Alignment.BottomEnd).padding(7.dp))
                }
                ProgressLine(v)
            }
            Spacer(Modifier.height(9.dp))
            Text(v.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(v.resolution, color = Color.Gray, fontSize = 10.sp)
                if (v.folderName.isNotBlank()) Text("• ${v.folderName}", color = Color.Gray, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun VideoRailCard(
    v: VideoItem,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 220.dp,
) {
    Card(
        modifier = modifier.width(width).clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(124.dp)) {
                VideoThumbnail(v, Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)))
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(if (v.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = Color.White)
                }
                if (v.durationMs > 0L) DurationChip(v.durationMs, Modifier.align(Alignment.BottomEnd).padding(7.dp))
                ProgressLine(v)
            }
            Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(v.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                Text(v.resolution + " • " + v.folderName, maxLines = 1, color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun VideoHeroCard(v: VideoItem, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(205.dp)) {
            VideoThumbnail(v, Modifier.fillMaxWidth().height(205.dp), widthPx = 960, heightPx = 540)
            Box(
                Modifier.fillMaxWidth().height(110.dp).align(Alignment.BottomCenter)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            )
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text("Continuer à regarder", color = Color.White.copy(alpha = 0.74f), fontSize = 11.sp)
                Text(v.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            }
            ProgressLine(v, modifier = Modifier.align(Alignment.BottomCenter), horizontalPadding = 14.dp)
        }
    }
}

@Composable
private fun DurationChip(ms: Long, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.72f)) {
        Text(formatDuration(ms), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ProgressLine(v: VideoItem, modifier: Modifier = Modifier, horizontalPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    if (v.durationMs <= 0L || v.lastPositionMs <= 0L) return
    androidx.compose.material3.LinearProgressIndicator(
        progress = { (v.lastPositionMs.toFloat() / v.durationMs.toFloat()).coerceIn(0f, 1f) },
        modifier = modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = horizontalPadding).height(3.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.White.copy(alpha = 0.28f)
    )
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
