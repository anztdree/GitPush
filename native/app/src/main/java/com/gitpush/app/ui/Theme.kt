package com.gitpush.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
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

// Palet ala GitHub dark — dimodernisasi (lebih dalam, kontras lebih kaya)
val GreenPrimary = Color(0xFF3FB950)
val GreenDeep = Color(0xFF238636)
val GreenGlow = Color(0xFF56D364)
val BlueAccent = Color(0xFF58A6FF)
val PurpleAccent = Color(0xFFBC8CFF)
val PinkAccent = Color(0xFFFF7B72)
val RedDanger = Color(0xFFF85149)
val YellowWarn = Color(0xFFD29922)
val GrayMuted = Color(0xFF8B949E)

/** Gradien identitas GitPush — hijau GitHub yang hidup (dipakai hero & logo). */
val GreenGradient = Brush.linearGradient(listOf(Color(0xFF1F6F33), GreenDeep, GreenPrimary))
val GreenGradientVert = Brush.verticalGradient(listOf(GreenDeep, Color(0xFF196C32)))
/** Gradien lembut untuk aksen kartu (gelap): transparan → hijau tipis. */
val GreenTintGradient = Brush.linearGradient(
    listOf(GreenPrimary.copy(alpha = 0.16f), GreenPrimary.copy(alpha = 0.03f))
)

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
    onPrimary = Color(0xFF04260F),
    primaryContainer = GreenDeep,
    onPrimaryContainer = Color(0xFFD2F8D2),
    secondary = BlueAccent,
    onSecondary = Color(0xFF0D1117),
    secondaryContainer = Color(0xFF1F3A52),
    onSecondaryContainer = Color(0xFFA5D6FF),
    tertiary = PurpleAccent,
    background = Color(0xFF0A0E14),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF10161D),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1A222B),
    onSurfaceVariant = Color(0xFF93A1AF),
    surfaceContainer = Color(0xFF10161D),
    surfaceContainerHigh = Color(0xFF161D26),
    surfaceContainerHighest = Color(0xFF1C242E),
    surfaceBright = Color(0xFF222B36),
    surfaceDim = Color(0xFF0A0E14),
    outline = Color(0xFF2B3440),
    outlineVariant = Color(0xFF1E2630),
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
    background = Color(0xFFF4F7F9),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEDF1F4),
    onSurfaceVariant = Color(0xFF59636E),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF6F8FA),
    surfaceContainerHighest = Color(0xFFEFF2F5),
    outline = Color(0xFFD0D7DE),
    outlineVariant = Color(0xFFE4E9ED),
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
