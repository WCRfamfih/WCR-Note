package com.example.ainote.ui.editor

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.UserSettings
import com.example.ainote.ui.components.AiActionBottomSheet
import com.example.ainote.ui.components.AiActionResultCard
import com.example.ainote.ui.components.AiCompletionCard
import com.example.ainote.ui.components.AiStatusCard
import com.example.ainote.ui.components.DocumentAssistToolbar
import com.example.ainote.ui.components.GhostTextEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long,
    noteRepository: NoteRepository,
    aiRepository: AiRepository,
    settingsDataStore: SettingsDataStore,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: NoteEditorViewModel = viewModel(
        factory = NoteEditorViewModel.Factory(noteId, noteRepository, aiRepository, settingsDataStore)
    )
    val state by viewModel.uiState.collectAsState()
    val settings by settingsDataStore.settings.collectAsState(initial = UserSettings())
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val keyboardHeightPx = rememberKeyboardHeightPx()
    val keyboardVisible = keyboardHeightPx > with(density) { 96.dp.roundToPx() }
    var showAiMenu by remember { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }
    val canShowGhostText = state.completion.suggestion != null &&
        state.content.selection.collapsed &&
        canShowInlineGhostText(state.content)
    val contentScrollState = rememberScrollState()

    BackHandler {
        viewModel.saveNow(onBack)
    }

    if (showAiMenu) {
        AiActionBottomSheet(
            onDismiss = { showAiMenu = false },
            onActionClick = { action ->
                showAiMenu = false
                viewModel.runManualAction(action)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u7f16\u8f91\u7b14\u8bb0") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveNow(onBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                },
                actions = {
                    IconButton(onClick = { showAiMenu = true }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "\u0041\u0049 \u64cd\u4f5c")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "\u8bbe\u7f6e")
                    }
                }
            )
        },
        bottomBar = {
            if (bodyFocused && keyboardVisible) {
                DocumentAssistToolbar(
                    onAction = viewModel::applyMarkdownFormat,
                    modifier = Modifier.padding(bottom = with(density) { keyboardHeightPx.toDp() })
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(contentScrollState)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("\u6807\u9898") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            GhostTextEditor(
                value = state.content,
                onValueChange = { value: TextFieldValue ->
                    bodyFocused = true
                    viewModel.updateContent(value)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp),
                ghostText = state.completion.suggestion.takeIf { canShowGhostText },
                textSizeSp = settings.editorTextSizeSp,
                onAcceptGhostText = viewModel::acceptCompletion,
                onDismissGhostText = viewModel::dismissCompletion,
                onRetryGhostText = viewModel::retryCompletion,
                onFocusChanged = { bodyFocused = it }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (canShowGhostText) {
                    "${state.wordCount} \u5b57\uff0c\u70b9\u51fb\u7070\u8272\u5efa\u8bae\u63a5\u53d7\u8865\u5168"
                } else {
                    "${state.wordCount} \u5b57\uff0c\u81ea\u52a8\u4fdd\u5b58"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (state.completion.loading || state.manualAi.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            state.completion.suggestion?.takeUnless { canShowGhostText }?.let { suggestion ->
                AiCompletionCard(
                    text = suggestion,
                    onAccept = viewModel::acceptCompletion,
                    onDismiss = viewModel::dismissCompletion
                )
            }
            state.manualAi.result?.let { result ->
                AiActionResultCard(
                    actionLabel = state.manualAi.actionLabel ?: "\u7ed3\u679c",
                    text = result,
                    primaryActionLabel = if (state.manualAi.replaceSelection) "\u66ff\u6362\u9009\u533a" else "\u63d2\u5165",
                    onAccept = viewModel::acceptManualAiResult,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(result))
                        viewModel.markManualAiResultCopied()
                    },
                    onDismiss = viewModel::dismissManualAiResult
                )
            }
            state.manualAi.errorMessage?.let { message ->
                AiStatusCard(
                    title = "\u0041\u0049 ${state.manualAi.actionLabel ?: "\u64cd\u4f5c"}",
                    message = message,
                    isError = true,
                    onRetry = viewModel::retryManualAction,
                    onDismiss = viewModel::dismissManualAiStatus
                )
            }
            state.manualAi.statusMessage?.let { message ->
                AiStatusCard(
                    title = "\u0041\u0049 ${state.manualAi.actionLabel ?: "\u64cd\u4f5c"}",
                    message = message,
                    isError = false,
                    onDismiss = viewModel::dismissManualAiStatus
                )
            }
        }
    }
}

@Composable
private fun rememberKeyboardHeightPx(): Int {
    val view = LocalView.current
    var keyboardHeight by remember { mutableStateOf(0) }
    DisposableEffect(view) {
        val visibleFrame = Rect()
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            view.rootView.getWindowVisibleDisplayFrame(visibleFrame)
            val hiddenHeight = (view.rootView.height - visibleFrame.bottom).coerceAtLeast(0)
            keyboardHeight = hiddenHeight
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    return keyboardHeight
}

private fun canShowInlineGhostText(value: TextFieldValue): Boolean {
    if (!value.selection.collapsed) return false
    val cursor = value.selection.start.coerceIn(0, value.text.length)
    val textAfterCursor = value.text.drop(cursor)
    return textAfterCursor.isEmpty() || textAfterCursor.first() == '\n'
}
