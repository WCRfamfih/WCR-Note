package com.example.ainote.ui.editor

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.EditorFontPreset
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.UserSettings
import com.example.ainote.domain.model.AiActionType
import com.example.ainote.domain.model.KnowledgeExtractionLaunchArgs
import com.example.ainote.domain.model.NoteContentType
import com.example.ainote.ui.components.AiActionBottomSheet
import com.example.ainote.ui.components.AiActionResultCard
import com.example.ainote.ui.components.AiCompletionCard
import com.example.ainote.ui.components.AiStatusCard
import com.example.ainote.ui.components.DocumentAssistToolbar
import com.example.ainote.ui.components.GhostTextEditor
import com.example.ainote.ui.components.stripMarkdownMarkers
import com.example.ainote.ui.export.ExportedNoteImage
import com.example.ainote.ui.export.NoteImageExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long,
    contentType: NoteContentType = NoteContentType.Note,
    noteRepository: NoteRepository,
    aiRepository: AiRepository,
    settingsDataStore: SettingsDataStore,
    onOpenKnowledgeScope: (Long) -> Unit,
    onOpenKnowledgeExtraction: (KnowledgeExtractionLaunchArgs) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: NoteEditorViewModel = viewModel(
        factory = NoteEditorViewModel.Factory(noteId, contentType, noteRepository, aiRepository, settingsDataStore)
    )
    val state by viewModel.uiState.collectAsState()
    val settings by settingsDataStore.settings.collectAsState(initial = UserSettings())
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val keyboardHeightPx = rememberKeyboardHeightPx()
    val keyboardVisible = keyboardHeightPx > with(density) { 96.dp.roundToPx() }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAiMenu by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    var exportedImage by remember { mutableStateOf<ExportedNoteImage?>(null) }
    var bodyFocused by remember { mutableStateOf(false) }

    val isEditingText = bodyFocused && keyboardVisible
    val isKnowledge = state.contentType == NoteContentType.Knowledge
    val canShowGhostText = state.completion.suggestion != null &&
        state.content.selection.collapsed &&
        canShowInlineGhostText(state.content)
    val contentScrollState = rememberScrollState()
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val editorLineHeightSp = settings.editorTextSizeSp * (settings.editorLineSpacingPercent / 100f)
    val editorLetterSpacingSp = settings.editorLetterSpacingTenthSp / 10f
    val editorFontFamily = remember(settings.editorFontPreset) {
        settings.editorFontPreset.toFontFamily()
    }

    BackHandler {
        viewModel.saveNow(onBack)
    }

    if (showAiMenu) {
        AiActionBottomSheet(
            onDismiss = { showAiMenu = false },
            onActionClick = { action ->
                showAiMenu = false
                if (action == AiActionType.ExtractToKnowledge) {
                    onOpenKnowledgeExtraction(viewModel.buildKnowledgeExtractionLaunch(settings))
                } else {
                    viewModel.runManualAction(action)
                }
            }
        )
    }

    if (showShareMenu) {
        ModalBottomSheet(onDismissRequest = { showShareMenu = false }) {
            ListItem(
                headlineContent = { Text("复制纯文本") },
                supportingContent = { Text("去除 Markdown 标记后复制。") },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                modifier = Modifier.clickable {
                    clipboardManager.setText(AnnotatedString(stripMarkdownMarkers(state.content.text)))
                    showShareMenu = false
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }
            )
            ListItem(
                headlineContent = { Text("导出图片") },
                supportingContent = { Text("生成预览图后再保存或分享。") },
                leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                modifier = Modifier.clickable {
                    showShareMenu = false
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            NoteImageExporter.createShareImage(
                                context = context.applicationContext,
                                title = state.title,
                                content = state.content.text,
                                backgroundColor = backgroundColor,
                                textColor = textColor
                            )
                        }
                        result
                            .onSuccess { exportedImage = it }
                            .onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "图片导出失败：${error.message ?: "未知错误"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    state.knowledgeOverflowPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissKnowledgeOverflow,
            title = { Text("知识发送确认") },
            text = {
                Text("识别到的知识超出阈值（${prompt.limit}），当前共识别到 ${prompt.matchCount} 条，是否全部发送？")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmKnowledgeOverflow) {
                    Text("全部发送")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissKnowledgeOverflow) {
                    Text("取消")
                }
            }
        )
    }

    exportedImage?.let { image ->
        val previewBitmap = remember(image.uri) {
            context.contentResolver.openInputStream(image.uri)?.use(BitmapFactory::decodeStream)
        }
        ModalBottomSheet(onDismissRequest = { exportedImage = null }) {
            previewBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "图片预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .padding(horizontal = 16.dp)
                )
            }
            ListItem(
                headlineContent = { Text("保存到相册") },
                leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                modifier = Modifier.clickable {
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            NoteImageExporter.saveImageToGallery(context.applicationContext, image.uri, image.fileName)
                        }
                        val message = result.fold(
                            onSuccess = { "已保存：$it" },
                            onFailure = { "保存失败：${it.message ?: "未知错误"}" }
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            )
            ListItem(
                headlineContent = { Text("立即分享") },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                modifier = Modifier.clickable {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, image.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "分享图片"))
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    LaunchedEffect(state.completion.errorMessage, settings.showCompletionErrorToast) {
        val message = state.completion.errorMessage ?: return@LaunchedEffect
        if (!settings.showCompletionErrorToast) {
            viewModel.dismissCompletion()
            return@LaunchedEffect
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "重试",
            withDismissAction = true
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.retryCompletion()
        } else {
            viewModel.dismissCompletion()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { viewModel.saveNow(onBack) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (isEditingText) {
                            IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                            }
                            IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做")
                            }
                        }
                        IconButton(onClick = { showAiMenu = true }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI 操作")
                        }
                        if (!isEditingText) {
                            IconButton(onClick = { showShareMenu = true }) {
                                Icon(Icons.Default.Share, contentDescription = "分享")
                            }
                        }
                        if (!isEditingText && state.showKnowledgeButton) {
                            IconButton(onClick = { onOpenKnowledgeScope(state.noteId) }) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = "知识识别 ${state.knowledgeScopeSummary.enabledKnowledgeCount}/${state.knowledgeScopeSummary.totalKnowledgeCount}"
                                )
                            }
                        }
                        if (isKnowledge) {
                            TextButton(onClick = { viewModel.toggleGlobalKnowledge(!state.isGlobalKnowledge) }) {
                                Text(if (state.isGlobalKnowledge) "全局开" else "全局关")
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                )
                if (state.completion.loading || state.manualAi.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (canShowGhostText) {
                        "${state.wordCount} 字，点击灰字接受补全"
                    } else {
                        "${state.wordCount} 字，自动保存"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (bodyFocused && keyboardVisible) {
                    DocumentAssistToolbar(
                        onAction = viewModel::applyMarkdownFormat,
                        markdownToolsEnabled = !isKnowledge,
                        modifier = Modifier.padding(bottom = with(density) { keyboardHeightPx.toDp() })
                    )
                }
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
            TextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "标题",
                        style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(Modifier.height(12.dp))
            GhostTextEditor(
                value = state.content,
                onValueChange = { value ->
                    bodyFocused = true
                    viewModel.updateContent(value)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp),
                ghostText = state.completion.suggestion.takeIf { canShowGhostText },
                textSizeSp = settings.editorTextSizeSp,
                lineHeightSp = editorLineHeightSp,
                letterSpacingSp = editorLetterSpacingSp,
                fontFamily = editorFontFamily,
                onAcceptGhostText = viewModel::acceptCompletion,
                onDismissGhostText = viewModel::dismissCompletion,
                onRetryGhostText = viewModel::retryCompletion,
                renderMarkdown = !settings.showMarkdownMarkers && !isKnowledge,
                onFocusChanged = { bodyFocused = it }
            )
            Spacer(Modifier.height(8.dp))
            state.completion.suggestion?.takeUnless { canShowGhostText }?.let { suggestion ->
                AiCompletionCard(
                    text = suggestion,
                    onAccept = viewModel::acceptCompletion,
                    onDismiss = viewModel::dismissCompletion
                )
            }
            state.manualAi.result?.let { result ->
                AiActionResultCard(
                    actionLabel = state.manualAi.actionLabel ?: "结果",
                    text = result,
                    primaryActionLabel = if (state.manualAi.replaceSelection) "替换选区" else "插入",
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
                    title = "AI ${state.manualAi.actionLabel ?: "操作"}",
                    message = message,
                    isError = true,
                    onRetry = viewModel::retryManualAction,
                    onDismiss = viewModel::dismissManualAiStatus
                )
            }
            state.manualAi.statusMessage?.let { message ->
                AiStatusCard(
                    title = "AI ${state.manualAi.actionLabel ?: "操作"}",
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

private fun EditorFontPreset.toFontFamily(): FontFamily {
    return when (this) {
        EditorFontPreset.System -> FontFamily.Default
        EditorFontPreset.Sans -> FontFamily.SansSerif
        EditorFontPreset.Serif -> FontFamily.Serif
        EditorFontPreset.Monospace -> FontFamily.Monospace
        EditorFontPreset.Cursive -> FontFamily.Cursive
    }
}
