package com.example.ainote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor
import com.example.ainote.data.settings.AccentColorPreset
import com.example.ainote.data.settings.ThemeMode

@Composable
fun AiNoteTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accentColorPreset: AccentColorPreset = AccentColorPreset.Violet,
    accentBrightnessOffset: Float = 0f,
    accentSaturationFactor: Float = 1f,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val adjustedAccent = accentColorPreset.adjusted(
        brightnessOffset = accentBrightnessOffset,
        saturationFactor = accentSaturationFactor
    )
    MaterialTheme(
        colorScheme = if (useDarkTheme) darkAccentScheme(adjustedAccent) else lightAccentScheme(adjustedAccent),
        content = content
    )
}

private fun darkAccentScheme(accent: AdjustedAccentColors) = darkColorScheme().copy(
    primary = accent.primary,
    onPrimary = Color.White,
    primaryContainer = accent.primary,
    onPrimaryContainer = Color.White,
    inversePrimary = accent.primary,
    secondary = accent.primary,
    onSecondary = Color.White,
    secondaryContainer = accent.primary,
    onSecondaryContainer = Color.White,
    tertiary = accent.tertiary,
    onTertiary = Color.White,
    tertiaryContainer = accent.tertiary,
    onTertiaryContainer = Color.White,
    surfaceVariant = Color(0xFF2B2B2F),
    onSurfaceVariant = Color(0xFFD0CDD3),
    outline = Color(0xFF9A969F),
    outlineVariant = Color(0xFF4B4850)
)

private fun lightAccentScheme(accent: AdjustedAccentColors) = lightColorScheme().copy(
    primary = accent.primary,
    onPrimary = Color.White,
    primaryContainer = accent.primary,
    onPrimaryContainer = Color.White,
    inversePrimary = accent.primary,
    secondary = accent.primary,
    onSecondary = Color.White,
    secondaryContainer = accent.primary,
    onSecondaryContainer = Color.White,
    tertiary = accent.tertiary,
    onTertiary = Color.White,
    tertiaryContainer = accent.tertiary,
    onTertiaryContainer = Color.White,
    surfaceVariant = Color(0xFFE8E6EC),
    onSurfaceVariant = Color(0xFF47464D),
    outline = Color(0xFF79767F),
    outlineVariant = Color(0xFFC9C5CF)
)

private data class AdjustedAccentColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

private fun AccentColorPreset.adjusted(
    brightnessOffset: Float,
    saturationFactor: Float
): AdjustedAccentColors {
    return AdjustedAccentColors(
        primary = primary.adjustColor(brightnessOffset, saturationFactor),
        secondary = secondary.adjustColor(brightnessOffset, saturationFactor),
        tertiary = tertiary.adjustColor(brightnessOffset, saturationFactor)
    )
}

private fun Color.adjustColor(brightnessOffset: Float, saturationFactor: Float): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * saturationFactor).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] + brightnessOffset).coerceIn(0f, 1f)
    return Color(AndroidColor.HSVToColor((alpha * 255).toInt(), hsv))
}
