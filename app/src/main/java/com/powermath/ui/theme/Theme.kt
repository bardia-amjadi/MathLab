package com.powermath.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.powermath.data.FontPref
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.powermath.data.AccentPref
import com.powermath.data.ThemePref

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DB7FF), onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF1B3A5C), onPrimaryContainer = Color(0xFFD3E6FF),
    secondary = Color(0xFFB4BCC8), onSecondary = Color(0xFF1A1D21),
    secondaryContainer = Color(0xFF2A2E35), onSecondaryContainer = Color(0xFFE3E6EA),
    background = Color(0xFF0E0F11), onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF17191C), onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF202328), onSurfaceVariant = Color(0xFF9AA0A8),
    outline = Color(0xFF3A3E45), error = Color(0xFFFF8A80), onError = Color(0xFF3B0A06)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5FAE), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E6FB), onPrimaryContainer = Color(0xFF00213F),
    secondary = Color(0xFF50565F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E6EB), onSecondaryContainer = Color(0xFF1A1D21),
    background = Color(0xFFF6F7F9), onBackground = Color(0xFF16181B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF16181B),
    surfaceVariant = Color(0xFFECEEF1), onSurfaceVariant = Color(0xFF5F656D),
    outline = Color(0xFFC4C8CE), error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF)
)

/** primary, onPrimary, primaryContainer, onPrimaryContainer */
private class AccentColors(val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color)

private fun accentColors(a: AccentPref, dark: Boolean): AccentColors = when (a) {
    AccentPref.YELLOW -> if (dark) AccentColors(Color(0xFFFFD54A), Color(0xFF3A2C00), Color(0xFF4D3B00), Color(0xFFFFE9A6))
                         else AccentColors(Color(0xFF8A6500), Color(0xFFFFFFFF), Color(0xFFFFE9A6), Color(0xFF3A2C00))
    AccentPref.RED -> if (dark) AccentColors(Color(0xFFFF8A80), Color(0xFF410002), Color(0xFF7A1F1A), Color(0xFFFFDAD6))
                      else AccentColors(Color(0xFFB3261E), Color(0xFFFFFFFF), Color(0xFFFFDAD6), Color(0xFF410002))
    AccentPref.BLUE -> if (dark) AccentColors(Color(0xFF7DB7FF), Color(0xFF00213F), Color(0xFF1B3A5C), Color(0xFFD3E6FF))
                       else AccentColors(Color(0xFF1B5FAE), Color(0xFFFFFFFF), Color(0xFFD6E6FB), Color(0xFF00213F))
    AccentPref.GREEN -> if (dark) AccentColors(Color(0xFF7DDC9A), Color(0xFF00391B), Color(0xFF1B5130), Color(0xFFC0F2CE))
                        else AccentColors(Color(0xFF1E7A3C), Color(0xFFFFFFFF), Color(0xFFC8F0D3), Color(0xFF00210D))
    AccentPref.PURPLE -> if (dark) AccentColors(Color(0xFFD0A8FF), Color(0xFF3A0A6B), Color(0xFF52308A), Color(0xFFEBDCFF))
                         else AccentColors(Color(0xFF6B3FB5), Color(0xFFFFFFFF), Color(0xFFEBDCFF), Color(0xFF260056))
}

/** False when the user turned animations off in Settings; animated components check this. */
val LocalAnimations = compositionLocalOf { true }

fun fontFamilyOf(f: FontPref): FontFamily? = when (f) {
    FontPref.DEFAULT -> null
    FontPref.MONO -> FontFamily.Monospace
    FontPref.SERIF -> FontFamily.Serif
}

/** The swatch colour shown in the settings picker. */
fun accentSwatch(a: AccentPref): Color = accentColors(a, dark = true).primary

@Composable
fun PowerMathTheme(pref: ThemePref, accent: AccentPref, animations: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (pref) {
        ThemePref.SYSTEM -> isSystemInDarkTheme()
        ThemePref.DARK -> true
        ThemePref.LIGHT -> false
    }
    val base: ColorScheme = if (dark) DarkColors else LightColors
    val ac = accentColors(accent, dark)
    val spec = tween<Color>(durationMillis = 450)
    val primary by animateColorAsState(ac.primary, spec, label = "primary")
    val onPrimary by animateColorAsState(ac.onPrimary, spec, label = "onPrimary")
    val container by animateColorAsState(ac.container, spec, label = "container")
    val onContainer by animateColorAsState(ac.onContainer, spec, label = "onContainer")
    val scheme = base.copy(primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onContainer)
    CompositionLocalProvider(LocalAnimations provides animations) {
        MaterialTheme(colorScheme = scheme) {
            // A Surface is what gives plain Text the right (light-on-dark / dark-on-light) colour.
            Surface(color = scheme.background, contentColor = scheme.onBackground, content = content)
        }
    }
}

/** Distinct, colour-blind-friendly curve colours (used by the graph screen). */
val GraphColors = listOf(
    Color(0xFF5AA9FF), Color(0xFFFF8C5A), Color(0xFF6FD08C),
    Color(0xFFD98CFF), Color(0xFFFFD35A), Color(0xFF5ADBD0)
)
