package com.zernex.video.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val PlayerOverlay = Color.Black.copy(alpha = 0.72f)
private const val SEEK_STEP_MS = 10_000L

private enum class SeekDirection { BACKWARD, FORWARD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(video: com.zernex.video.domain.model.VideoItem, onBack: () -> Unit, onProgress: (Long) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp

    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var crop by rememberSaveable { mutableStateOf(false) }
    var rotation by rememberSaveable { mutableIntStateOf(0) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var sleepMinutes by rememberSaveable { mutableIntStateOf(0) }
    var externalSubtitle by remember { mutableStateOf<android.net.Uri?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var positionMs by rememberSaveable { mutableLongStateOf(video.lastPositionMs) }
    var durationMs by rememberSaveable { mutableLongStateOf(0L) }
    var playing by rememberSaveable { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf<SeekDirection?>(null) }
    var seekToken by remember { mutableIntStateOf(0) }
    var fastSeekDirection by remember { mutableStateOf<SeekDirection?>(null) }
    var fastSeekWasPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(playing, controlsVisible, menuExpanded, locked) {
        activity?.window?.let { window ->
            if (playing) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.BLACK
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                val immersivePlayback = playing && !controlsVisible && !menuExpanded && !locked
                if (immersivePlayback) {
                    hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                } else {
                    show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            externalSubtitle = uri
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    val player = remember(video.id) {
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .build()
            .apply {
                setMediaItem(MediaItem.Builder().setUri(video.uri).build())
                prepare()
                seekTo(video.lastPositionMs)
                playWhenReady = true
            }
    }

    LaunchedEffect(externalSubtitle) {
        val subtitle = externalSubtitle ?: return@LaunchedEffect
        val extension = subtitle.toString().substringAfterLast('.', "srt").lowercase()
        val mime = when (extension) {
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt" -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val config = MediaItem.SubtitleConfiguration.Builder(subtitle)
            .setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val position = player.currentPosition
        player.setMediaItem(MediaItem.Builder().setUri(video.uri).setSubtitleConfigurations(listOf(config)).build(), position)
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }

    LaunchedEffect(crop) {
        // Never crop merely because the device is landscape. The video stays
        // inside the available screen bounds unless the user explicitly chooses
        // the fill/crop mode from the player menu.
        player.videoScalingMode = if (crop) C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING else C.VIDEO_SCALING_MODE_SCALE_TO_FIT
    }

    LaunchedEffect(rotation) {
        activity?.requestedOrientation = when (rotation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            player.pause()
            sleepMinutes = 0
        }
    }

    LaunchedEffect(controlsVisible, locked, menuExpanded, dragging, fastSeekDirection, interactionToken) {
        if (controlsVisible && !locked && !menuExpanded && !dragging && fastSeekDirection == null) {
            delay(3000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(player, dragging) {
        while (true) {
            if (!dragging) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.takeIf { it > 0 } ?: 0L
                playing = player.isPlaying
                if (positionMs > 0) onProgress(positionMs)
            }
            delay(400)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs = player.duration.takeIf { it > 0 } ?: 0L
            }
        }
        player.addListener(listener)
        onDispose {
            onProgress(player.currentPosition)
            player.removeListener(listener)
            player.release()
            activity?.let { host ->
                host.window.statusBarColor = android.graphics.Color.TRANSPARENT
                host.window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.enableEdgeToEdge(host.window)
                WindowInsetsControllerCompat(host.window, host.window.decorView).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                    show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                        androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                }
                host.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // Restore the host activity's normal inset policy when leaving the player.
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(seekToken) {
        if (seekToken == 0) return@LaunchedEffect
        delay(900)
        seekDirection = null
    }

    LaunchedEffect(fastSeekDirection) {
        val direction = fastSeekDirection ?: return@LaunchedEffect
        val wasPlaying = player.isPlaying
        fastSeekWasPlaying = wasPlaying

        // 2× is a real live playback speed: keep the video playing while the
        // gesture is held instead of repeatedly seeking a paused player.
        player.setPlaybackSpeed(2f)
        player.play()

        try {
            while (isActive) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                if (positionMs > 0) onProgress(positionMs)
                delay(100L)
            }
        } finally {
            // Releasing/canceling the gesture immediately restores the normal speed
            // and the playback state that existed before the 2× gesture.
            player.setPlaybackSpeed(speed)
            if (wasPlaying) player.play() else player.pause()
            fastSeekWasPlaying = false
        }
    }

    fun performSeek(direction: SeekDirection) {
        val delta = if (direction == SeekDirection.FORWARD) SEEK_STEP_MS else -SEEK_STEP_MS
        val knownDuration = player.duration.takeIf { it > 0L } ?: durationMs
        val target = (player.currentPosition + delta).coerceIn(0L, knownDuration.coerceAtLeast(0L))
        player.seekTo(target)
        positionMs = target
        seekDirection = direction
        seekToken++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    setPlayer(player)
                    useController = false
                    resizeMode = if (crop) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowSubtitleButton(false)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.resizeMode = if (crop) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = zoom, scaleY = zoom)
                .clipToBounds()
        )

        // One permanent gesture surface. It never disappears with the controls.
        // All player gestures are coordinated here so taps, double-taps and long-press
        // cannot compete with another full-screen pointerInput layer.
        val latestLocked by rememberUpdatedState(locked)
        val latestControlsVisible by rememberUpdatedState(controlsVisible)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            try {
                                awaitRelease()
                            } finally {
                                if (fastSeekDirection != null) {
                                    fastSeekDirection = null
                                }
                            }
                        },
                        onLongPress = { offset ->
                            if (latestLocked) return@detectTapGestures
                            val edge = size.width * 0.33f
                            val direction = when {
                                offset.x <= edge -> SeekDirection.BACKWARD
                                offset.x >= size.width - edge -> SeekDirection.FORWARD
                                else -> null
                            }
                            if (direction != null) {
                                controlsVisible = true
                                fastSeekDirection = direction
                                interactionToken++
                            }
                        },
                        onDoubleTap = { offset ->
                            if (latestLocked) return@detectTapGestures
                            val leftZone = size.width * 0.42f
                            val rightZone = size.width * 0.58f
                            when {
                                offset.x <= leftZone -> performSeek(SeekDirection.BACKWARD)
                                offset.x >= rightZone -> performSeek(SeekDirection.FORWARD)
                                else -> {
                                    if (player.isPlaying) player.pause() else player.play()
                                    playing = player.isPlaying
                                    controlsVisible = true
                                    interactionToken++
                                }
                            }
                        },
                        onTap = {
                            if (latestLocked) return@detectTapGestures
                            controlsVisible = !latestControlsVisible
                            if (controlsVisible) interactionToken++
                        }
                    )
                }
        )

        if (seekDirection != null && !locked && fastSeekDirection == null) {
            SeekFeedback(direction = seekDirection!!)
        }
        if (fastSeekDirection != null && !locked) {
            FastSeekFeedback(direction = fastSeekDirection!!)
        }

        if (controlsVisible && !locked) {
            TopPlayerBar(
                title = video.name,
                onBack = onBack,
                onMenu = { menuExpanded = true }
            )

            IconButton(
                onClick = { locked = true; controlsVisible = false; fastSeekDirection = null },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(40.dp)
            ) {
                Icon(Icons.Default.LockOpen, "Verrouiller", tint = Color.White, modifier = Modifier.size(22.dp))
            }

            IconButton(
                onClick = { rotation = if (landscape) 1 else 2; interactionToken++ },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(40.dp)
            ) {
                TiltedPhoneIcon()
            }

            PlayerControls(
                positionMs = positionMs,
                durationMs = durationMs,
                playing = playing,
                onSeek = { player.seekTo(it); positionMs = it; onProgress(it) },
                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                dragging = dragging,
                onDragging = { dragging = it },
                onPrevious = { player.seekToPreviousMediaItem() },
                onNext = { player.seekToNextMediaItem() },
                onUserActivity = { interactionToken++ }
            )
        }

        // In locked mode the lock is intentionally the only visible control.
        if (locked) {
            IconButton(
                onClick = { locked = false; controlsVisible = true },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(40.dp)
            ) {
                Icon(Icons.Default.Lock, "Déverrouiller", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        if (sleepMinutes > 0 && !locked && controlsVisible) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
                color = PlayerOverlay,
                shape = MaterialTheme.shapes.small
            ) {
                Text("Arrêt dans $sleepMinutes min", color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }

    if (menuExpanded) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { menuExpanded = false }
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.33f)
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                color = Color.Black.copy(alpha = 0.64f),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                val menuScroll = rememberScrollState()
                Column(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(width = 34.dp, height = 3.dp),
                            color = Color.White.copy(alpha = 0.42f),
                            shape = MaterialTheme.shapes.small
                        ) {}
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Réglages du lecteur",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (menuScroll.maxValue > 0) {
                            Text(
                                "↕",
                                color = Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(menuScroll)
                    ) {
                        PlayerSettingRow("Sous-titres", Icons.Default.Subtitles) {
                            menuExpanded = false
                            subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/ttml+xml"))
                        }
                        PlayerSettingRow("Vitesse : ${speed}x", Icons.Default.Speed) {
                            speed = when (speed) { 0.25f -> 0.5f; 0.5f -> 1f; 1f -> 1.5f; 1.5f -> 2f; else -> 0.25f }
                        }
                        PlayerSettingRow(if (crop) "Adapter à l'écran" else "Remplir l'écran", Icons.Default.AspectRatio) { crop = !crop }
                        PlayerSettingRow("Rotation", Icons.Default.ScreenRotation) {
                            rotation = when (rotation) { 0 -> if (landscape) 1 else 2; 1 -> 2; else -> 1 }
                        }
                        PlayerSettingRow(if (zoom > 1f) "Réinitialiser le zoom" else "Zoom", Icons.Default.CenterFocusStrong) { zoom = if (zoom > 1f) 1f else 1.5f }
                        PlayerSettingRow(if (sleepMinutes == 0) "Minuteur 15 min" else "Arrêt dans $sleepMinutes min", Icons.Default.Timer) {
                            sleepMinutes = when (sleepMinutes) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
                        }
                        if (Build.VERSION.SDK_INT >= 26) {
                            PlayerSettingRow("Image dans l'image", Icons.Default.PictureInPictureAlt) {
                                activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                                menuExpanded = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TopPlayerBar(title: String, onBack: () -> Unit, onMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .height(48.dp)
            .padding(top = 2.dp, start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, "Retour", tint = Color.White, modifier = Modifier.size(22.dp)) }
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Réglages", tint = Color.White, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun BoxScope.PlayerControls(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    dragging: Boolean,
    onDragging: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onUserActivity: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
            ThinProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = { onUserActivity(); onSeek(it) },
                onDragStateChanged = { onDragging(it); onUserActivity() },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(formatTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onUserActivity(); onPrevious() }) { Icon(Icons.Default.SkipPrevious, "Précédente", tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(18.dp))
            Text(
                "−10",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .width(48.dp)
                    .clickable { onUserActivity(); onSeek((positionMs - SEEK_STEP_MS).coerceAtLeast(0L)) }
            )
            Spacer(Modifier.width(22.dp))
            IconButton(onClick = { onUserActivity(); onTogglePlay() }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Lecture", tint = Color.White, modifier = Modifier.size(42.dp)) }
            Spacer(Modifier.width(22.dp))
            Text(
                "+15",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .width(48.dp)
                    .clickable { onUserActivity(); onSeek((positionMs + 15_000L).coerceAtMost(durationMs)) }
            )
            Spacer(Modifier.width(18.dp))
            IconButton(onClick = { onUserActivity(); onNext() }) { Icon(Icons.Default.SkipNext, "Suivante", tint = Color.White, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun ThinProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .height(12.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0L && size.width > 0) {
                        onSeek((offset.x / size.width * durationMs).toLong().coerceIn(0L, durationMs))
                    }
                }
            }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { onDragStateChanged(true) },
                    onDragEnd = { onDragStateChanged(false) },
                    onDragCancel = { onDragStateChanged(false) }
                ) { change, _ ->

                    if (durationMs > 0L && size.width > 0) {
                        onSeek((change.position.x / size.width * durationMs).toLong().coerceIn(0L, durationMs))
                    }
                }
            }
    ) {
        val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val y = size.height / 2f
        drawLine(Color.White.copy(alpha = 0.30f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * fraction, y), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun PlayerSettingRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.16f), thickness = 1.dp)
    }
}

@Composable
private fun TiltedPhoneIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val bodyWidth = size.width * 0.44f
        val bodyHeight = size.height * 0.72f
        val left = (size.width - bodyWidth) / 2f
        val top = (size.height - bodyHeight) / 2f
        rotate(degrees = -16f, pivot = center) {
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.8.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.7.dp.toPx())
            )
        }
    }
}

@Composable
private fun BoxScope.FastSeekFeedback(direction: SeekDirection) {
    val forward = direction == SeekDirection.FORWARD
    val alpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(180), label = "fastSeekAlpha")
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 66.dp)
            .graphicsLayer(alpha = alpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!forward) {
            FastSeekTriangles(direction = direction)
            Spacer(Modifier.width(8.dp))
            Text("2×", color = Color.White, style = MaterialTheme.typography.titleMedium)
        } else {
            Text("2×", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            FastSeekTriangles(direction = direction)
        }
    }
}

@Composable
private fun FastSeekTriangles(direction: SeekDirection) {
    var activeIndex by remember(direction) { mutableIntStateOf(if (direction == SeekDirection.FORWARD) 0 else 2) }

    LaunchedEffect(direction) {
        while (isActive) {
            delay(180L)
            activeIndex = if (direction == SeekDirection.FORWARD) {
                (activeIndex + 1) % 3
            } else {
                (activeIndex + 2) % 3
            }
        }
    }

    Canvas(modifier = Modifier.size(width = 54.dp, height = 24.dp)) {
        val triangleWidth = size.width / 3f
        val centerY = size.height / 2f
        val halfHeight = size.height * 0.32f

        repeat(3) { index ->
            val left = index * triangleWidth
            val path = androidx.compose.ui.graphics.Path().apply {
                if (direction == SeekDirection.FORWARD) {
                    moveTo(left, centerY - halfHeight)
                    lineTo(left + triangleWidth, centerY)
                    lineTo(left, centerY + halfHeight)
                } else {
                    moveTo(left + triangleWidth, centerY - halfHeight)
                    lineTo(left, centerY)
                    lineTo(left + triangleWidth, centerY + halfHeight)
                }
                close()
            }
            drawPath(path, Color.White.copy(alpha = if (index == activeIndex) 1f else 0.22f))
        }
    }
}

@Composable
private fun BoxScope.SeekFeedback(direction: SeekDirection) {
    val forward = direction == SeekDirection.FORWARD
    var shown by remember(direction) { mutableStateOf(false) }
    LaunchedEffect(direction) { shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(180), label = "seekAlpha")
    val scale by animateFloatAsState(if (shown) 1f else 0.82f, tween(220), label = "seekScale")
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 72.dp)
            .graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale),
        color = Color.Black.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (forward) Icons.Default.FastForward else Icons.Default.FastRewind, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (forward) "+10 s" else "−10 s", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
