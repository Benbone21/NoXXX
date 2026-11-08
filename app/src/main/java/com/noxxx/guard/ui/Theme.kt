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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
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
 * NoXXX's own look, not stock Material colors.
 *  - Temple (default): warm white, stone and gold by day; warm charcoal and gold at night.
 *  - Night: a night sky giving way to sunrise, with gold light. Always dark.
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

/** Containers are slightly see-through so the sunrise scene shows behind them. */
private val Night = darkColorScheme(
    primary = Color(0xFFFFCF85), onPrimary = Color(0xFF1A1430),
    primaryContainer = Color(0xFF3A2E52), onPrimaryContainer = Color(0xFFFFE5BD),
    secondary = Color(0xFFC2C6DE), onSecondary = Color(0xFF1A1E3A),
    secondaryContainer = Color(0xFF232A50), onSecondaryContainer = Color(0xFFE5E8F7),
    tertiary = Color(0xFFF0A65F), onTertiary = Color(0xFF2B1600),
    tertiaryContainer = Color(0xFF4A2F2A), onTertiaryContainer = Color(0xFFFFDDBE),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410), errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0B0F26), onBackground = Color(0xFFF3F1F8),
    surface = Color(0xFF0B0F26), onSurface = Color(0xFFF3F1F8),
    surfaceVariant = Color(0xB3161C40), onSurfaceVariant = Color(0xFFC2C6DE),
    outline = Color(0xFF5D6390), outlineVariant = Color(0xFF262D55),
    surfaceContainerLowest = Color(0xB3060A1D), surfaceContainerLow = Color(0xB30E1433),
    surfaceContainer = Color(0xB3121838), surfaceContainerHigh = Color(0xCC161C40), surfaceContainerHighest = Color(0xB3121838),
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
    fun serif(s: androidx.compose.ui.text.TextStyle, w: Int = 600) = s.copy(fontFamily = Serif, fontWeight = FontWeight(w))
    return t.copy(
        displayLarge = serif(t.displayLarge), displayMedium = serif(t.displayMedium), displaySmall = serif(t.displaySmall),
        headlineLarge = serif(t.headlineLarge), headlineMedium = serif(t.headlineMedium), headlineSmall = serif(t.headlineSmall),
        titleLarge = serif(t.titleLarge, 600).copy(fontSize = t.titleLarge.fontSize * 1.15f),
    )
}

enum class Palette(val label: String, val description: String) {
    TEMPLE("Temple", "Warm white and gold by day, charcoal and gold at night"),
    NIGHT("Night sunrise", "A night sky giving way to sunrise"),
}

/** The chosen look, remembered on this phone. Not part of the locked settings. */
object Appearance {
    private const val PREFS = "appearance"
    private val _palette = MutableStateFlow(Palette.TEMPLE)
    val palette: StateFlow<Palette> = _palette.asStateFlow()

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("palette", null)
        _palette.value = Palette.entries.firstOrNull { it.name == saved } ?: Palette.TEMPLE
    }

    fun set(context: Context, palette: Palette) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("palette", palette.name).apply()
        _palette.value = palette
    }
}

/** Which look is active, for screens that draw something special (the sunrise scene). */
val LocalPalette = staticCompositionLocalOf { Palette.TEMPLE }

@Composable
fun NoXXXTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = Appearance.palette.collectAsState().value
    val scheme = when (palette) {
        Palette.NIGHT -> Night
        Palette.TEMPLE -> if (dark) TempleDark else TempleLight
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = typography(), shapes = NoXXXShapes, content = content)
    }
}
