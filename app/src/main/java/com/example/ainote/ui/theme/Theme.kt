package com.example.ainote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
        colorScheme = if (useDarkTheme) {
            darkColorScheme(
                primary = accentColorPreset.primary,
                secondary = accentColorPreset.secondary,
                tertiary = accentColorPreset.tertiary
            )
        } else {
            lightColorScheme(
                primary = accentColorPreset.primary,
                secondary = accentColorPreset.secondary,
                tertiary = accentColorPreset.tertiary
            )
        },
        content = content
    )
}
