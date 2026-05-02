package com.example.ainote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ainote.ui.navigation.AppNavGraph
import com.example.ainote.ui.theme.AiNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AiNoteApplication).container
        setContent {
            AiNoteTheme {
                AppNavGraph(container = container)
            }
        }
    }
}
