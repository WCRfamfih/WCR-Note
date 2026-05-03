package com.example.ainote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ainote.data.settings.UserSettings
import com.example.ainote.ui.navigation.AppNavGraph
import com.example.ainote.ui.theme.AiNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AiNoteApplication).container
        setContent {
            val settings by container.settingsDataStore.settings.collectAsState(initial = UserSettings())
            AiNoteTheme(
                themeMode = settings.themeMode,
                accentColorPreset = settings.accentColorPreset
            ) {
                AppNavGraph(container = container)
            }
        }
    }
}
