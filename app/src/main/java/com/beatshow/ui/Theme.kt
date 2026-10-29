package com.beatshow.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF7A45)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF2B0E00),
    primaryContainer = Color(0xFF5C2410),
    onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFF7FD1C7),
    onSecondary = Color(0xFF00201C),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceContainer = Color(0xFF1E1E1E),
)

@Composable
fun BeatShowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
