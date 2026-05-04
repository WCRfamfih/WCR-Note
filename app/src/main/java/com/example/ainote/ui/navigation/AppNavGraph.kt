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
import com.example.ainote.domain.model.NoteContentType
import com.example.ainote.ui.editor.NoteEditorScreen
import com.example.ainote.ui.notes.NoteListScreen
import com.example.ainote.ui.settings.AiDebugLogScreen
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
                contentType = NoteContentType.Note,
                onOpenNote = { id -> navController.navigate("editor/note/$id") },
                onOpenNotes = {},
                onOpenKnowledge = { navController.navigate("knowledge") { launchSingleTop = true } },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("knowledge") {
            NoteListScreen(
                repository = container.noteRepository,
                settingsDataStore = container.settingsDataStore,
                contentType = NoteContentType.Knowledge,
                onOpenNote = { id -> navController.navigate("editor/knowledge/$id") },
                onOpenNotes = { navController.navigate("notes") { launchSingleTop = true } },
                onOpenKnowledge = {},
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable(
            route = "editor/{contentType}/{noteId}",
            arguments = listOf(
                navArgument("contentType") { type = NavType.StringType },
                navArgument("noteId") { type = NavType.LongType }
            )
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: return@composable
            val contentType = NoteContentType.from(entry.arguments?.getString("contentType"))
            NoteEditorScreen(
                noteId = noteId,
                contentType = contentType,
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
                noteRepository = container.noteRepository,
                onOpenAiSettings = { navController.navigate("ai_settings") },
                onOpenKnowledgeSettings = { navController.navigate("knowledge_settings") },
                onOpenAiDebugLog = { navController.navigate("ai_debug_log") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("knowledge_settings") {
            com.example.ainote.ui.settings.KnowledgeSettingsScreen(
                dataStore = container.settingsDataStore,
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
        composable("ai_debug_log") {
            AiDebugLogScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
