package com.zernex.video.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFF8A2A)
private val Background = Color(0xFF090A0C)
private val Surface = Color(0xFF111317)
private val SurfaceVariant = Color(0xFF1A1D22)
private val TextPrimary = Color(0xFFF4F5F7)
private val TextSecondary = Color(0xFFA8ADB7)

@Composable
fun ZernexTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Orange,
        onPrimary = Color.Black,
        secondary = Color(0xFFFFB36B),
        background = Background,
        onBackground = TextPrimary,
        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = TextSecondary,
        outline = Color(0xFF30343B)
    )
    MaterialTheme(colorScheme = colors, content = content)
}
