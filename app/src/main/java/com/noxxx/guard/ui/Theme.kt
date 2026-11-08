package com.noxxx.guard.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noxxx.guard.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * NoXXX's own look, not stock Material colors: Temple. Light is warm white, stone and gold; dark is
 * warm charcoal and gold. It follows the phone unless set to Light or Dark.
 * Headings use Cormorant Garamond (SIL Open Font License, bundled in res/font).
 */

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun cormorant(weight: Int, italic: Boolean = false) = Font(
    if (italic) R.font.cormorant_italic else R.font.cormorant,
    FontWeight(weight),
    if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Serif for headings, scripture and the streak number. */
val Serif = FontFamily(
    cormorant(500), cormorant(600), cormorant(700),
    cormorant(500, italic = true), cormorant(600, italic = true),
)

private val TempleLight = lightColorScheme(
    primary = Color(0xFF8A6A2B), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1E6CC), onPrimaryContainer = Color(0xFF3B2C0B),
    secondary = Color(0xFF2F4B67), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE6F0), onSecondaryContainer = Color(0xFF13283C),
    tertiary = Color(0xFF4F6F8F), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3ECF4), onTertiaryContainer = Color(0xFF1B3046),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFBFAF6), onBackground = Color(0xFF2B2A27),
    surface = Color(0xFFFBFAF6), onSurface = Color(0xFF2B2A27),
    surfaceVariant = Color(0xFFF4F1EA), onSurfaceVariant = Color(0xFF6B665C),
    outline = Color(0xFFA39C8C), outlineVariant = Color(0xFFECE6D8),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF7F4EE), surfaceContainerHigh = Color(0xFFF4F1EA), surfaceContainerHighest = Color(0xFFFFFFFF),
)

private val TempleDark = darkColorScheme(
    primary = Color(0xFFD4B26A), onPrimary = Color(0xFF2A1F05),
    primaryContainer = Color(0xFF3A2F18), onPrimaryContainer = Color(0xFFEBD39C),
    secondary = Color(0xFFA9C3DD), onSecondary = Color(0xFF10263A),
    secondaryContainer = Color(0xFF2A3B4C), onSecondaryContainer = Color(0xFFD7E5F2),
    tertiary = Color(0xFFA9C3DD), onTertiary = Color(0xFF10263A),
    tertiaryContainer = Color(0xFF26323E), onTertiaryContainer = Color(0xFFD7E5F2),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410), errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141311), onBackground = Color(0xFFEDE8DD),
    surface = Color(0xFF141311), onSurface = Color(0xFFEDE8DD),
    surfaceVariant = Color(0xFF25221E), onSurfaceVariant = Color(0xFFA69F90),
    outline = Color(0xFF6E685C), outlineVariant = Color(0xFF2E2B26),
    surfaceContainerLowest = Color(0xFF100F0D), surfaceContainerLow = Color(0xFF1A1917),
    surfaceContainer = Color(0xFF1C1B18), surfaceContainerHigh = Color(0xFF25221E), surfaceContainerHighest = Color(0xFF1F1D1A),
)

private val NoXXXShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun typography(): Typography {
    val t = Typography()
    // "lnum": full-height numbers. Cormorant defaults to old-style figures, where 0 looks like a small o.
    fun serif(s: androidx.compose.ui.text.TextStyle, w: Int = 600) = s.copy(fontFamily = Serif, fontWeight = FontWeight(w), fontFeatureSettings = "lnum")
    return t.copy(
        displayLarge = serif(t.displayLarge), displayMedium = serif(t.displayMedium), displaySmall = serif(t.displaySmall),
        headlineLarge = serif(t.headlineLarge), headlineMedium = serif(t.headlineMedium), headlineSmall = serif(t.headlineSmall),
        titleLarge = serif(t.titleLarge, 600).copy(fontSize = t.titleLarge.fontSize * 1.15f),
    )
}

enum class ThemeMode(val label: String, val description: String) {
    SYSTEM("Match phone", "Light or dark, following your phone's setting"),
    LIGHT("Light", "Warm white, stone and gold"),
    DARK("Dark", "Warm charcoal and gold"),
}

/** The chosen theme, remembered on this phone. Not part of the locked settings. */
object Appearance {
    private const val PREFS = "appearance"
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", null)
        _mode.value = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
        _mode.value = mode
    }
}

@Composable
fun NoXXXTheme(content: @Composable () -> Unit) {
    val system = isSystemInDarkTheme()
    val dark = when (Appearance.mode.collectAsState().value) {
        ThemeMode.SYSTEM -> system
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) TempleDark else TempleLight, typography = typography(), shapes = NoXXXShapes, content = content)
}
