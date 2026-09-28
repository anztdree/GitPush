package com.gitpush.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.R

// Palet ala GitHub dark (#0d1117)
val GreenPrimary = Color(0xFF3FB950)
val GreenDeep = Color(0xFF238636)
val BlueAccent = Color(0xFF58A6FF)
val PurpleAccent = Color(0xFFBC8CFF)
val RedDanger = Color(0xFFF85149)
val YellowWarn = Color(0xFFD29922)
val GrayMuted = Color(0xFF8B949E)

/**
 * Inter Variable — satu file TTF, empat bobot diambil lewat sumbu wght.
 * Tipografi aplikasi jauh lebih tajam & modern dibanding Roboto bawaan.
 */
@OptIn(ExperimentalTextApi::class)
val InterFont = FontFamily(
    Font(
        R.font.inter_variable, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.inter_variable, FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.inter_variable, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.inter_variable, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        R.font.inter_variable, FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

/**
 * Skala huruf aplikasi: judul rapat (letterSpacing negatif), teks isi lega
 * (lineHeight ≥ 1.45×), label kecil jelas. Semua Text ikut memakai Inter
 * karena fontFamily bawaan tiap gaya adalah InterFont.
 */
private fun gpTypography(): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.Bold,
        fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFont, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp
    )
)

private val GpShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

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
        typography = gpTypography(),
        shapes = GpShapes,
        content = content
    )
}
