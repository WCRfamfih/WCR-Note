package com.example.ainote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ainote.data.settings.AccentColorPreset
import com.example.ainote.data.settings.ThemeMode

@Composable
fun AiNoteTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accentColorPreset: AccentColorPreset = AccentColorPreset.Violet,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) darkAccentScheme(accentColorPreset) else lightAccentScheme(accentColorPreset),
        content = content
    )
}

private fun darkAccentScheme(accent: AccentColorPreset) = darkColorScheme().copy(
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

private fun lightAccentScheme(accent: AccentColorPreset) = lightColorScheme().copy(
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
