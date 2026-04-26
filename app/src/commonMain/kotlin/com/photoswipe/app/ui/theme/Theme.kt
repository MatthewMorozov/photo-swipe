package com.photoswipe.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFCE93D8),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color(0xFF003258),
    onSecondary = Color(0xFF00201D),
    onTertiary = Color(0xFF3B0054),
    onBackground = Color(0xFFE3E3E3),
    onSurface = Color(0xFFE3E3E3),
)

@Composable
fun PhotoSwipeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
