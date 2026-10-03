package com.zernex.video

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.zernex.video.domain.model.SeriesGroup
import com.zernex.video.domain.model.SeriesSeason
import com.zernex.video.domain.model.VideoItem
import com.zernex.video.ui.components.VideoThumbnail
import com.zernex.video.ui.player.PlayerScreen
import com.zernex.video.ui.theme.ZernexTheme

private val Orange = Color(0xFFFF7A00)
private val Background = Color(0xFF08090B)
private val Surface = Color(0xFF111318)
private val Surface2 = Color(0xFF181B21)

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private var permissionGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionGranted = hasVideoPermission()
        if (permissionGranted) viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        permissionGranted = hasVideoPermission()
        if (permissionGranted) viewModel.refresh()
        setContent {
            ZernexTheme {
                Surface(Modifier.fillMaxSize(), color = Background) {
                    ZernexApp(viewModel, permissionGranted) { requestVideoPermission() }
                }
            }
        }
    }

    private fun hasVideoPermission(): Boolean = when {
        Build.VERSION.SDK_INT >= 34 ->
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        Build.VERSION.SDK_INT >= 33 ->
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        else -> ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestVideoPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= 34) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            arrayOf(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }
}

@Composable
private fun ZernexApp(vm: MainViewModel, permission: Boolean, requestPermission: () -> Unit) {
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    if (!permission) {
        PermissionScreen(requestPermission)
        return
    }
    if (selectedVideo != null) {
        val video = selectedVideo!!
        LaunchedEffect(video.id) { vm.markPlayed(video, video.lastPositionMs) }
        PlayerScreen(video = video, onBack = { selectedVideo = null }, onProgress = { vm.savePosition(video, it) })
        return
    }

    val videos by vm.videos.collectAsState()
    val series by vm.series.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var grid by rememberSaveable { mutableStateOf(true) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }

    val filteredVideos = videos.filter { it.name.contains(query, ignoreCase = true) }
    val visibleVideos = when (sort) {
        1 -> filteredVideos.sortedBy { it.name.lowercase() }
        2 -> filteredVideos.sortedByDescending { it.durationMs }
        3 -> filteredVideos.sortedByDescending { it.sizeBytes }
        4 -> filteredVideos.sortedByDescending { it.height }
        else -> filteredVideos.sortedByDescending { it.dateAdded }
    }
    val visibleSeries = series.filter { group ->
        query.isBlank() || group.title.contains(query, ignoreCase = true) || group.allEpisodes.any { it.name.contains(query, ignoreCase = true) }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PremiumTopBar(
                tab = tab,
                query = query,
                onQueryChange = { query = it },
                onRefresh = vm::refresh,
                grid = grid,
                onGridToggle = { grid = !grid },
                showSortMenu = showSortMenu,
                onSortMenuChange = { showSortMenu = it },
                onSortChange = { sort = it }
            )
        },
        bottomBar = { BottomBar(tab) { tab = it } }
    ) { padding ->
        if (tab == 0) {
            LibraryTab(padding, visibleVideos, videos.size, visibleSeries, loading, grid, { selectedVideo = it }, vm::toggleFavorite)
        } else {
            SeriesTab(padding, visibleSeries, loading) { selectedVideo = it }
        }
    }
}

@Composable
private fun PremiumTopBar(
    tab: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    grid: Boolean,
    onGridToggle: () -> Unit,
    showSortMenu: Boolean,
    onSortMenuChange: (Boolean) -> Unit,
    onSortChange: (Int) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Background).statusBarsPadding().padding(top = 5.dp, bottom = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ZERNEX", color = Orange, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                Text(if (tab == 0) "Votre bibliothèque vidéo" else "Vos séries", color = Color(0xFF8F949E), fontSize = 11.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Actualiser", tint = Color.White) }
            if (tab == 0) {
                Box {
                    IconButton(onClick = { onSortMenuChange(true) }) { Icon(Icons.Default.Sort, "Trier", tint = Color.White) }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { onSortMenuChange(false) }) {
                        listOf("Récents", "Nom A-Z", "Plus longues", "Plus volumineuses", "Résolution").forEachIndexed { index, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { onSortChange(index); onSortMenuChange(false) })
                        }
                    }
                }
                IconButton(onClick = onGridToggle) {
                    Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, "Affichage", tint = Color.White)
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
            singleLine = true,
            placeholder = { Text(if (tab == 0) "Rechercher une vidéo…" else "Rechercher une série…", color = Color(0xFF7F848E)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Orange) },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun LibraryTab(
    pad: PaddingValues,
    videos: List<VideoItem>,
    allVideoCount: Int,
    series: List<SeriesGroup>,
    loading: Boolean,
    grid: Boolean,
    onOpen: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit
) {
    if (loading) {
        Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator(color = Orange) }
        return
    }
    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { EmptyLibrary(if (allVideoCount == 0) "Aucune vidéo disponible" else "Aucun résultat") }
        return
    }

    val continueWatching = videos.filter { it.lastPositionMs > 5_000L && (it.durationMs <= 0L || it.lastPositionMs < it.durationMs - 15_000L) }.sortedByDescending { it.lastPlayed }
    val recentAdded = videos.sortedByDescending { it.dateAdded }
    val favorites = videos.filter { it.favorite }
    val detectedSeries = series.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        if (continueWatching.isNotEmpty()) {
            item { ContinueWatchingHero(continueWatching.first(), onOpen) }
            if (continueWatching.size > 1) item { VideoRailSection("Continuer à regarder", continueWatching.drop(1), onOpen, onFavorite) }
        }
        if (detectedSeries.isNotEmpty()) {
            item { SeriesRailSection(detectedSeries, onOpen) }
        }
        if (favorites.isNotEmpty()) item { VideoRailSection("Vos favoris", favorites, onOpen, onFavorite) }
        item { VideoRailSection("Ajoutées récemment", recentAdded, onOpen, onFavorite) }
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionTitle("Toutes les vidéos", videos.size, "Votre bibliothèque")
                if (grid) VideoGrid(videos, onOpen, onFavorite) else VideoListBlock(videos, onOpen, onFavorite)
            }
        }
    }
}

@Composable
private fun ContinueWatchingHero(video: VideoItem, onOpen: (VideoItem) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable { onOpen(video) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface2),
        elevation = CardDefaults.cardElevation(5.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            VideoThumbnail(video, Modifier.fillMaxSize(), widthPx = 960, heightPx = 540)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text("CONTINUER À REGARDER", color = Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(video.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Orange) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reprendre", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(progressLabel(video), color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
                }
            }
            ProgressLine(video, Modifier.align(Alignment.BottomCenter), 18.dp)
        }
    }
}

@Composable
private fun SeriesRailSection(series: List<SeriesGroup>, onOpen: (VideoItem) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("Séries", series.size, "Détection intelligente")
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(series, key = { it.id }) { group ->
                SeriesMiniCard(group) { group.allEpisodes.firstOrNull()?.let(onOpen) }
            }
        }
    }
}

@Composable
private fun SeriesMiniCard(group: SeriesGroup, onOpen: () -> Unit) {
    val preview = group.allEpisodes.firstOrNull()
    Card(Modifier.width(250.dp).clickable(onClick = onOpen), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column {
            Box(Modifier.fillMaxWidth().height(132.dp)) {
                if (preview != null) VideoThumbnail(preview, Modifier.fillMaxSize(), widthPx = 640, heightPx = 360)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))))
                Text("${group.seasonCount} saison${if (group.seasonCount > 1) "s" else ""}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
            }
            Column(Modifier.padding(11.dp)) {
                Text(group.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${group.episodeCount} épisode${if (group.episodeCount > 1) "s" else ""}", color = Color(0xFF969BA5), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun VideoRailSection(title: String, videos: List<VideoItem>, onOpen: (VideoItem) -> Unit, onFavorite: (VideoItem) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title, videos.size)
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(videos.take(12), key = { it.id }) { video ->
                VideoRailCardPremium(video, { onOpen(video) }, { onFavorite(video) })
            }
        }
    }
}

@Composable
private fun VideoRailCardPremium(video: VideoItem, onOpen: () -> Unit, onFavorite: () -> Unit) {
    Card(Modifier.width(220.dp).clickable(onClick = onOpen), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column {
            Box(Modifier.fillMaxWidth().height(124.dp)) {
                VideoThumbnail(video, Modifier.fillMaxSize(), widthPx = 640, heightPx = 360)
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(if (video.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = Color.White)
                }
                if (video.durationMs > 0) DurationChip(video.durationMs, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                ProgressLine(video, Modifier.align(Alignment.BottomCenter))
            }
            Column(Modifier.padding(11.dp)) {
                Text(videoDisplayTitle(video), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(video.resolution + " • " + video.folderName, maxLines = 1, color = Color(0xFF8F949E), fontSize = 10.sp, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun VideoGrid(videos: List<VideoItem>, onOpen: (VideoItem) -> Unit, onFavorite: (VideoItem) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        videos.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { video -> VideoPremiumCard(video, { onOpen(video) }, { onFavorite(video) }, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VideoPremiumCard(video: VideoItem, onOpen: () -> Unit, onFavorite: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(138.dp)) {
                VideoThumbnail(video, Modifier.fillMaxSize(), widthPx = 640, heightPx = 360)
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(if (video.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = Color.White)
                }
                if (video.durationMs > 0) DurationChip(video.durationMs, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                ProgressLine(video, Modifier.align(Alignment.BottomCenter))
            }
            Column(Modifier.padding(11.dp)) {
                Text(videoDisplayTitle(video), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(video.resolution + " • " + video.folderName, color = Color(0xFF8F949E), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun VideoListBlock(videos: List<VideoItem>, onOpen: (VideoItem) -> Unit, onFavorite: (VideoItem) -> Unit) {
    Column(Modifier.fillMaxWidth()) { videos.forEach { VideoList(it, { onOpen(it) }, { onFavorite(it) }) } }
}

@Composable
private fun VideoList(v: VideoItem, onOpen: () -> Unit, onFav: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(118.dp, 70.dp).clip(RoundedCornerShape(12.dp))) {
            VideoThumbnail(v, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, widthPx = 360, heightPx = 220)
            if (v.durationMs > 0) DurationChip(v.durationMs, Modifier.align(Alignment.BottomEnd).padding(5.dp))
            ProgressLine(v, Modifier.align(Alignment.BottomCenter), 0.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(videoDisplayTitle(v), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(v.resolution + " • " + v.folderName, color = Color(0xFF8F949E), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (v.lastPositionMs > 0 && v.durationMs > 0) Text(progressLabel(v), color = Orange, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        }
        IconButton(onClick = onFav) { Icon(if (v.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favori", tint = if (v.favorite) Orange else Color(0xFF8F949E)) }
    }
}

@Composable
private fun SeriesTab(pad: PaddingValues, series: List<SeriesGroup>, loading: Boolean, onPlay: (VideoItem) -> Unit) {
    var selected by remember { mutableStateOf<SeriesGroup?>(null) }
    if (selected != null) {
        SeriesDetails(selected!!, onBack = { selected = null }, onOpen = onPlay, pad = pad)
        return
    }
    if (loading) {
        Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator(color = Orange) }
        return
    }
    if (series.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { EmptyLibrary("Aucune série détectée") }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Vos séries", fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("${series.size} série${if (series.size > 1) "s" else ""} reconnue${if (series.size > 1) "s" else ""} automatiquement", color = Color(0xFF8F949E), fontSize = 11.sp)
            }
        }
        items(series, key = { it.id }) { group -> SeriesPremiumCard(group) { selected = group } }
    }
}

@Composable
private fun SeriesPremiumCard(group: SeriesGroup, onOpen: () -> Unit) {
    val preview = group.allEpisodes.firstOrNull()
    val current = group.allEpisodes.firstOrNull { it.lastPositionMs > 5_000 && (it.durationMs <= 0 || it.lastPositionMs < it.durationMs - 15_000) }
    val watched = group.allEpisodes.count { it.durationMs > 0 && it.lastPositionMs >= it.durationMs - 15_000 }
    val progress = if (group.episodeCount == 0) 0f else watched.toFloat() / group.episodeCount
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable(onClick = onOpen), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Surface), elevation = CardDefaults.cardElevation(3.dp)) {
        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(132.dp, 88.dp).clip(RoundedCornerShape(15.dp))) {
                if (preview != null) VideoThumbnail(preview, Modifier.fillMaxSize(), widthPx = 520, heightPx = 350)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .6f)))))
                Text("${group.seasonCount}S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(group.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${group.seasonCount} saison${if (group.seasonCount > 1) "s" else ""} • ${group.episodeCount} épisode${if (group.episodeCount > 1) "s" else ""}", color = Color(0xFF8F949E), fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)), color = Orange, trackColor = Color.White.copy(alpha = .08f))
                Spacer(Modifier.height(6.dp))
                Text(current?.let { "▶ Continuer ${episodeLabel(it)}" } ?: "${(progress * 100).toInt()} % terminé", color = Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF777C86))
        }
    }
}

@Composable
private fun SeriesDetails(group: SeriesGroup, onBack: () -> Unit, onOpen: (VideoItem) -> Unit, pad: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(pad)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") }
            Column(Modifier.weight(1f)) {
                Text(group.title, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${group.seasonCount} saisons • ${group.episodeCount} épisodes", color = Color(0xFF8F949E), fontSize = 10.sp)
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            group.seasons.forEach { season ->
                item(key = "season-${group.id}-${season.season}") { SeasonHeader(season) }
                items(season.episodes, key = { it.id }) { video -> EpisodeRow(video, { onOpen(video) }) }
            }
        }
    }
}

@Composable
private fun SeasonHeader(season: SeriesSeason) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(9.dp), color = Orange) {
            Text("S${season.season.toString().padStart(2, '0')}", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("Saison ${season.season}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        Text("${season.episodes.size} épisode${if (season.episodes.size > 1) "s" else ""}", color = Color(0xFF8F949E), fontSize = 10.sp)
    }
}

@Composable
private fun EpisodeRow(video: VideoItem, onOpen: () -> Unit) {
    val parsed = com.zernex.video.domain.usecase.SeriesGrouper.parse(video.name)
    val done = video.durationMs > 0 && video.lastPositionMs >= video.durationMs - 15_000
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(128.dp, 72.dp).clip(RoundedCornerShape(12.dp))) {
            VideoThumbnail(video, Modifier.fillMaxSize(), widthPx = 400, heightPx = 230)
            Surface(Modifier.align(Alignment.TopStart).padding(6.dp), shape = RoundedCornerShape(7.dp), color = Color.Black.copy(alpha = .75f)) {
                Text("E${(parsed?.episode ?: 0).toString().padStart(2, '0')}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
            ProgressLine(video, Modifier.align(Alignment.BottomCenter), 0.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(videoDisplayTitle(video), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(if (done) "Épisode terminé" else progressLabel(video), color = if (done) Color(0xFF8FD18F) else Color(0xFF8F949E), fontSize = 10.sp)
        }
        if (done) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF8FD18F), modifier = Modifier.size(19.dp)) else Icon(Icons.Default.PlayArrow, null, tint = Orange, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionTitle(title: String, count: Int? = null, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (subtitle != null) Text(subtitle, color = Color(0xFF777C86), fontSize = 9.sp)
        }
        if (count != null) {
            Surface(shape = RoundedCornerShape(10.dp), color = Surface2) {
                Text(count.toString(), color = Color(0xFFB5BAC4), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
            }
        }
    }
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    NavigationBar(containerColor = Color(0xFF15151C)) {
        NavigationBarItem(selected = tab == 0, onClick = { onTab(0) }, icon = { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("Bibliothèque", fontSize = 10.sp) })
        NavigationBarItem(selected = tab == 1, onClick = { onTab(1) }, icon = { Icon(Icons.Default.Movie, null) }, label = { Text("Séries", fontSize = 10.sp) })
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Surface(Modifier.size(86.dp), shape = RoundedCornerShape(26.dp), color = Orange) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.VideoLibrary, null, tint = Color.White, modifier = Modifier.size(54.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        Text("ZERNEX Video", fontSize = 29.sp, fontWeight = FontWeight.Black)
        Text("Lecteur vidéo local hors-ligne", color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onGrant, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Orange)) {
            Icon(Icons.Default.VideoLibrary, null)
            Spacer(Modifier.width(8.dp))
            Text("Autoriser les vidéos")
        }
    }
}

@Composable
private fun EmptyLibrary(text: String) {
    Column(Modifier.fillMaxSize().padding(28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(Icons.Default.VideoLibrary, null, tint = Orange, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Actualise la bibliothèque après avoir ajouté des vidéos sur l'appareil.", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun DurationChip(ms: Long, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = .78f)) {
        Text(formatDuration(ms), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
private fun ProgressLine(v: VideoItem, modifier: Modifier = Modifier, horizontalPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    if (v.durationMs <= 0L || v.lastPositionMs <= 0L) return
    LinearProgressIndicator(
        progress = { (v.lastPositionMs.toFloat() / v.durationMs.toFloat()).coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding).height(3.dp),
        color = Orange,
        trackColor = Color.White.copy(alpha = .24f)
    )
}

private fun videoDisplayTitle(video: VideoItem): String =
    com.zernex.video.domain.usecase.SeriesGrouper.parse(video.name)?.let { "${it.title}  •  S${it.season.toString().padStart(2, '0')}E${it.episode.toString().padStart(2, '0')}" } ?: video.name

private fun episodeLabel(video: VideoItem): String =
    com.zernex.video.domain.usecase.SeriesGrouper.parse(video.name)?.let { "S${it.season.toString().padStart(2, '0')}E${it.episode.toString().padStart(2, '0')}" } ?: "reprendre"

private fun progressLabel(video: VideoItem): String {
    if (video.durationMs <= 0 || video.lastPositionMs <= 0) return "Prêt à regarder"
    val percent = ((video.lastPositionMs.toDouble() / video.durationMs) * 100).toInt().coerceIn(0, 100)
    return "$percent % vu"
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
