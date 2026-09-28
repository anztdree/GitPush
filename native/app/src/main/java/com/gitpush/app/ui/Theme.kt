package com.gitpush.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palet ala GitHub dark (#0d1117)
val GreenPrimary = Color(0xFF3FB950)
val GreenDeep = Color(0xFF238636)
val BlueAccent = Color(0xFF58A6FF)
val PurpleAccent = Color(0xFFBC8CFF)
val RedDanger = Color(0xFFF85149)
val YellowWarn = Color(0xFFD29922)
val GrayMuted = Color(0xFF8B949E)

private val DarkColors = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = GreenDeep,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = BlueAccent,
    onSecondary = Color(0xFF0D1117),
    secondaryContainer = Color(0xFF1F3A52),
    onSecondaryContainer = Color(0xFFA5D6FF),
    tertiary = PurpleAccent,
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainer = Color(0xFF161B22),
    surfaceContainerHigh = Color(0xFF1C2128),
    surfaceContainerHighest = Color(0xFF21262D),
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D),
    error = RedDanger,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3D1E20),
    onErrorContainer = Color(0xFFFFB3AD)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A7F37),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2F8D2),
    onPrimaryContainer = Color(0xFF04260F),
    secondary = Color(0xFF0969DA),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEFF2F5),
    onSurfaceVariant = Color(0xFF59636E),
    outline = Color(0xFFD0D7DE),
    error = Color(0xFFCF222E)
)

@Composable
fun GitPushTheme(content: @Composable () -> Unit) {
    val mode = Store.themeMode.value
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
