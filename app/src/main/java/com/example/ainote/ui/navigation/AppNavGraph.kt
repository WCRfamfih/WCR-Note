package com.example.ainote.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ainote.di.AppContainer
import com.example.ainote.ui.editor.NoteEditorScreen
import com.example.ainote.ui.notes.NoteListScreen
import com.example.ainote.ui.settings.AiSettingsScreen
import com.example.ainote.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "notes",
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable("notes") {
            NoteListScreen(
                repository = container.noteRepository,
                settingsDataStore = container.settingsDataStore,
                onOpenNote = { id -> navController.navigate("editor/$id") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable(
            route = "editor/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: return@composable
            NoteEditorScreen(
                noteId = noteId,
                noteRepository = container.noteRepository,
                aiRepository = container.aiRepository,
                settingsDataStore = container.settingsDataStore,
                onOpenSettings = { navController.navigate("settings") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                dataStore = container.settingsDataStore,
                aiRepository = container.aiRepository,
                onOpenAiSettings = { navController.navigate("ai_settings") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("ai_settings") {
            AiSettingsScreen(
                dataStore = container.settingsDataStore,
                aiRepository = container.aiRepository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
