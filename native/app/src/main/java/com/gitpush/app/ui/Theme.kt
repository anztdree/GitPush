package com.gitpush.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.R

// ===== Palet aksen (mengikuti tema aktif) =====
// Diisi ulang di GitPushTheme setiap tema dirender: gelap = palet GitHub dark,
// terang = padanan GitHub light. Semua layar membaca nama yang sama sehingga
// ikon/teks aksen selalu kontras di kedua tema tanpa mengubah satu layar pun.
var GreenPrimary = Color(0xFF3FB950) // dark: 0xFF3FB950 · light: 0xFF1A7F37
val GreenDeep = Color(0xFF238636) // tetap — kontras baik di kedua tema
val GreenGlow = Color(0xFF56D364) // khas tema gelap
var BlueAccent = Color(0xFF58A6FF) // dark: 0xFF58A6FF · light: 0xFF0969DA
var PurpleAccent = Color(0xFFBC8CFF) // dark: 0xFFBC8CFF · light: 0xFF8250DF
var PinkAccent = Color(0xFFFF7B72) // dark: 0xFFFF7B72 · light: 0xFFCF222E
var RedDanger = Color(0xFFF85149) // dark: 0xFFF85149 · light: 0xFFCF222E
var YellowWarn = Color(0xFFD29922) // dark: 0xFFD29922 · light: 0xFFB08800
var GrayMuted = Color(0xFF8B949E) // dark: 0xFF8B949E · light: 0xFF656D76

/** Terapkan palet aksen sesuai mode tema — dipanggil di awal GitPushTheme. */
private fun applyAccentPalette(dark: Boolean) {
    if (dark) {
        GreenPrimary = Color(0xFF3FB950)
        BlueAccent = Color(0xFF58A6FF)
        PurpleAccent = Color(0xFFBC8CFF)
        PinkAccent = Color(0xFFFF7B72)
        RedDanger = Color(0xFFF85149)
        YellowWarn = Color(0xFFD29922)
        GrayMuted = Color(0xFF8B949E)
    } else {
        GreenPrimary = Color(0xFF1A7F37)
        BlueAccent = Color(0xFF0969DA)
        PurpleAccent = Color(0xFF8250DF)
        PinkAccent = Color(0xFFCF222E)
        RedDanger = Color(0xFFCF222E)
        YellowWarn = Color(0xFFB08800)
        GrayMuted = Color(0xFF656D76)
    }
}

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
    primaryContainer = Color(0xFFDCF3E3),
    onPrimaryContainer = Color(0xFF033A16),
    secondary = Color(0xFF0969DA),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE9FA),
    onSecondaryContainer = Color(0xFF0A3060),
    tertiary = Color(0xFF8250DF),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFECE1FF),
    onTertiaryContainer = Color(0xFF2E0A5A),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFE9EEF3),
    onSurfaceVariant = Color(0xFF57606A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFCFD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF6F8FA),
    surfaceContainerHighest = Color(0xFFEFF2F6),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE0E5EA),
    outline = Color(0xFFD1D9E0),
    outlineVariant = Color(0xFFE4E9EE),
    error = Color(0xFFCF222E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE5E5),
    onErrorContainer = Color(0xFF82071E),
    inverseSurface = Color(0xFF2A3138),
    inverseOnSurface = Color(0xFFF0F3F6),
    inversePrimary = Color(0xFF3FB950),
    surfaceTint = Color(0xFF1A7F37)
)

@Composable
fun GitPushTheme(content: @Composable () -> Unit) {
    val mode = Store.themeMode.value
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    // Palet aksen mengikuti tema — HARUS sebelum content dirender agar semua
    // layar membaca nilai yang benar dalam pass komposisi yang sama.
    applyAccentPalette(dark)
    // Bilah sistem (status & navigasi) ikut tema — bukan lagi gelap permanen
    // dari tema XML (yang membuat mode terang terasa setengah jadi).
    val view = LocalView.current
    val statusBar = if (dark) Color(0xFF0A0E14) else Color(0xFFF6F8FA)
    val navBar = if (dark) Color(0xFF0A0E14) else Color(0xFFFFFFFF)
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = statusBar.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = navBar.toArgb()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = gpTypography(),
        shapes = GpShapes,
        content = content
    )
}
