package com.example.ainote.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ainote.di.AppContainer
import com.example.ainote.domain.model.NoteContentType
import com.example.ainote.ui.editor.KnowledgeExtractionTaskScreen
import com.example.ainote.ui.editor.NoteEditorScreen
import com.example.ainote.ui.editor.NoteKnowledgeScopeScreen
import com.example.ainote.ui.notes.CardOpenTransitionOrigin
import com.example.ainote.ui.notes.NoteListScreen
import com.example.ainote.ui.settings.AiDebugLogScreen
import com.example.ainote.ui.settings.AiSettingsScreen
import com.example.ainote.ui.settings.DisplaySettingsScreen
import com.example.ainote.ui.settings.SettingsScreen

private const val NotesRoute = "notes"
private const val KnowledgeRoute = "knowledge"
private const val EditorRoute = "editor/{contentType}/{noteId}"
private const val ExtractKnowledgeRoute = "editor/{contentType}/{noteId}/extract_knowledge"
private const val KnowledgeScopeRoute = "editor/note/{noteId}/knowledge_scope"
private const val SettingsRoute = "settings"
private const val DisplaySettingsRoute = "display_settings"
private const val KnowledgeSettingsRoute = "knowledge_settings"
private const val AiSettingsRoute = "ai_settings"
private const val AiDebugLogRoute = "ai_debug_log"

@Composable
fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val extractionMaterialKey = "knowledge_extraction_material"
    var lastCardOrigin by remember { mutableStateOf<CardOpenTransitionOrigin?>(null) }

    val settingsEnter: () -> EnterTransition = {
        slideInHorizontally(
            animationSpec = tween(220),
            initialOffsetX = { fullWidth -> fullWidth / 3 }
        ) + fadeIn(animationSpec = tween(220))
    }
    val settingsExit: () -> ExitTransition = {
        slideOutHorizontally(
            animationSpec = tween(220),
            targetOffsetX = { fullWidth -> -fullWidth / 4 }
        ) + fadeOut(animationSpec = tween(220))
    }
    val settingsPopEnter: () -> EnterTransition = {
        slideInHorizontally(
            animationSpec = tween(220),
            initialOffsetX = { fullWidth -> -fullWidth / 4 }
        ) + fadeIn(animationSpec = tween(220))
    }
    val settingsPopExit: () -> ExitTransition = {
        slideOutHorizontally(
            animationSpec = tween(220),
            targetOffsetX = { fullWidth -> fullWidth / 3 }
        ) + fadeOut(animationSpec = tween(220))
    }

    NavHost(
        navController = navController,
        startDestination = NotesRoute,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(NotesRoute) {
            NoteListScreen(
                repository = container.noteRepository,
                settingsDataStore = container.settingsDataStore,
                contentType = NoteContentType.Note,
                onOpenNote = { id, origin ->
                    lastCardOrigin = origin
                    navController.navigate("editor/note/$id")
                },
                onOpenNotes = {},
                onOpenKnowledge = { navController.navigate(KnowledgeRoute) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(SettingsRoute) }
            )
        }
        composable(KnowledgeRoute) {
            NoteListScreen(
                repository = container.noteRepository,
                settingsDataStore = container.settingsDataStore,
                contentType = NoteContentType.Knowledge,
                onOpenNote = { id, origin ->
                    lastCardOrigin = origin
                    navController.navigate("editor/knowledge/$id")
                },
                onOpenNotes = { navController.navigate(NotesRoute) { launchSingleTop = true } },
                onOpenKnowledge = {},
                onOpenSettings = { navController.navigate(SettingsRoute) }
            )
        }
        composable(
            route = EditorRoute,
            arguments = listOf(
                navArgument("contentType") { type = NavType.StringType },
                navArgument("noteId") { type = NavType.LongType }
            ),
            enterTransition = {
                if (initialState.destination.route in setOf(NotesRoute, KnowledgeRoute)) {
                    val origin = lastCardOrigin
                    if (origin != null) {
                        scaleIn(
                            animationSpec = tween(260),
                            initialScale = 0.35f,
                            transformOrigin = TransformOrigin(origin.pivotXFraction, origin.pivotYFraction)
                        ) + fadeIn(animationSpec = tween(220))
                    } else {
                        fadeIn(animationSpec = tween(180))
                    }
                } else {
                    fadeIn(animationSpec = tween(180))
                }
            },
            popExitTransition = {
                if (targetState.destination.route in setOf(NotesRoute, KnowledgeRoute)) {
                    val origin = lastCardOrigin
                    if (origin != null) {
                        scaleOut(
                            animationSpec = tween(240),
                            targetScale = 0.35f,
                            transformOrigin = TransformOrigin(origin.pivotXFraction, origin.pivotYFraction)
                        ) + fadeOut(animationSpec = tween(180))
                    } else {
                        fadeOut(animationSpec = tween(180))
                    }
                } else {
                    fadeOut(animationSpec = tween(180))
                }
            }
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: return@composable
            val contentType = NoteContentType.from(entry.arguments?.getString("contentType"))
            NoteEditorScreen(
                noteId = noteId,
                contentType = contentType,
                noteRepository = container.noteRepository,
                aiRepository = container.aiRepository,
                settingsDataStore = container.settingsDataStore,
                onOpenKnowledgeScope = { scopedNoteId ->
                    navController.navigate("editor/note/$scopedNoteId/knowledge_scope")
                },
                onOpenKnowledgeExtraction = { args ->
                    navController.currentBackStackEntry?.savedStateHandle?.set(extractionMaterialKey, args.material)
                    navController.navigate("editor/${args.contentType.storageValue}/${args.noteId}/extract_knowledge")
                },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = ExtractKnowledgeRoute,
            arguments = listOf(
                navArgument("contentType") { type = NavType.StringType },
                navArgument("noteId") { type = NavType.LongType }
            )
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: return@composable
            val initialMaterial = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>(extractionMaterialKey)
                .orEmpty()
            KnowledgeExtractionTaskScreen(
                noteId = noteId,
                initialMaterial = initialMaterial,
                noteRepository = container.noteRepository,
                aiRepository = container.aiRepository,
                settingsDataStore = container.settingsDataStore,
                onOpenKnowledge = { knowledgeId ->
                    navController.popBackStack()
                    navController.navigate("editor/knowledge/$knowledgeId")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = KnowledgeScopeRoute,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: return@composable
            NoteKnowledgeScopeScreen(
                noteId = noteId,
                noteRepository = container.noteRepository,
                settingsDataStore = container.settingsDataStore,
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoute) {
            SettingsScreen(
                dataStore = container.settingsDataStore,
                aiRepository = container.aiRepository,
                noteRepository = container.noteRepository,
                onOpenAiSettings = { navController.navigate(AiSettingsRoute) },
                onOpenDisplaySettings = { navController.navigate(DisplaySettingsRoute) },
                onOpenKnowledgeSettings = { navController.navigate(KnowledgeSettingsRoute) },
                onOpenAiDebugLog = { navController.navigate(AiDebugLogRoute) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = DisplaySettingsRoute,
            enterTransition = { settingsEnter() },
            exitTransition = { settingsExit() },
            popEnterTransition = { settingsPopEnter() },
            popExitTransition = { settingsPopExit() }
        ) {
            DisplaySettingsScreen(
                dataStore = container.settingsDataStore,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = KnowledgeSettingsRoute,
            enterTransition = { settingsEnter() },
            exitTransition = { settingsExit() },
            popEnterTransition = { settingsPopEnter() },
            popExitTransition = { settingsPopExit() }
        ) {
            com.example.ainote.ui.settings.KnowledgeSettingsScreen(
                dataStore = container.settingsDataStore,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = AiSettingsRoute,
            enterTransition = { settingsEnter() },
            exitTransition = { settingsExit() },
            popEnterTransition = { settingsPopEnter() },
            popExitTransition = { settingsPopExit() }
        ) {
            AiSettingsScreen(
                dataStore = container.settingsDataStore,
                aiRepository = container.aiRepository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = AiDebugLogRoute,
            enterTransition = { settingsEnter() },
            exitTransition = { settingsExit() },
            popEnterTransition = { settingsPopEnter() },
            popExitTransition = { settingsPopExit() }
        ) {
            AiDebugLogScreen(onBack = { navController.popBackStack() })
        }
    }
}
