package com.example.ainote.ui.editor

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ainote.ui.components.AiStatusCard
import com.example.ainote.ui.components.DocumentAssistToolbar
import com.example.ainote.ui.components.GhostTextEditor
import com.example.ainote.ui.components.stripMarkdownMarkers
import com.example.ainote.ui.export.ExportedNoteImage
import com.example.ainote.ui.export.NoteImageExporter
import com.example.ainote.ui.export.NoteImageRenderStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long,
    contentType: NoteContentType,
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
    val coroutineScope = rememberCoroutineScope()
    val keyboardHeightPx = rememberKeyboardHeightPx()
    val keyboardVisible = keyboardHeightPx > with(density) { 96.dp.roundToPx() }
    val snackbarHostState = remember { SnackbarHostState() }

    var showAiMenu by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    var showExtractionContextPrompt by remember { mutableStateOf(false) }
    var showJumpPageDialog by remember { mutableStateOf(false) }
    var jumpPageText by remember { mutableStateOf("") }
    var exportedImage by remember { mutableStateOf<ExportedNoteImage?>(null) }
    var bodyFocused by remember { mutableStateOf(false) }
    var currentEditorPage by remember { mutableStateOf(0) }
    var totalEditorPages by remember { mutableStateOf(1) }

    val isKnowledge = state.contentType == NoteContentType.Knowledge
    val isEditingText = bodyFocused && keyboardVisible
    val canShowGhostText = state.completion.previewRange != null && state.content.selection.collapsed
    val compactPagedEditing = settings.editorPaginationEnabled && keyboardVisible
    val contentScrollState = rememberScrollState()
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val editorLineHeightSp = settings.editorTextSizeSp * (settings.editorLineSpacingPercent / 100f)
    val editorLetterSpacingSp = settings.editorLetterSpacingTenthSp / 10f
    val editorFontFamily = remember(settings.editorFontPreset, settings.customEditorFontUri) {
        settings.toEditorFontFamily(context)
    }
    val renderMarkdown = !settings.showMarkdownMarkers && !isKnowledge

    LaunchedEffect(settings.editorPaginationEnabled, state.noteId) {
        currentEditorPage = 0
        totalEditorPages = 1
    }

    BackHandler {
        viewModel.saveNow(onBack)
    }

    if (showAiMenu) {
        AiActionBottomSheet(
            onDismiss = { showAiMenu = false },
            onActionClick = { action ->
                viewModel.dismissCompletion()
                showAiMenu = false
                if (action == AiActionType.ExtractToKnowledge) {
                    if (!settings.useFullNoteContext && state.content.selection.collapsed) {
                        showExtractionContextPrompt = true
                    } else {
                        onOpenKnowledgeExtraction(viewModel.buildKnowledgeExtractionLaunch(settings))
                    }
                } else {
                    viewModel.runManualAction(action)
                }
            }
        )
    }

    if (showExtractionContextPrompt) {
        AlertDialog(
            onDismissRequest = { showExtractionContextPrompt = false },
            title = { Text("发送整篇内容？") },
            text = { Text("检测到未开启“发送整篇上下文”，是否临时发送全部内容？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExtractionContextPrompt = false
                        onOpenKnowledgeExtraction(
                            viewModel.buildKnowledgeExtractionLaunch(settings, forceFullDocument = true)
                        )
                    }
                ) {
                    Text("发送全部")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExtractionContextPrompt = false
                        onOpenKnowledgeExtraction(viewModel.buildKnowledgeExtractionLaunch(settings))
                    }
                ) {
                    Text("保持当前范围")
                }
            }
        )
    }

    if (showJumpPageDialog && settings.editorPaginationEnabled) {
        AlertDialog(
            onDismissRequest = { showJumpPageDialog = false },
            title = { Text("跳转页码") },
            text = {
                TextField(
                    value = jumpPageText,
                    onValueChange = { jumpPageText = it.filter(Char::isDigit) },
                    singleLine = true,
                    placeholder = { Text("输入 1 - ${totalEditorPages.coerceAtLeast(1)}") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        jumpPageText.toIntOrNull()?.let { page ->
                            currentEditorPage = (page - 1).coerceIn(0, totalEditorPages.coerceAtLeast(1) - 1)
                        }
                        showJumpPageDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpPageDialog = false }) {
                    Text("取消")
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
                supportingContent = { Text("生成预览后再保存或分享。") },
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
                                textColor = textColor,
                                style = NoteImageRenderStyle(
                                    editorTextSizeSp = settings.editorTextSizeSp,
                                    editorLineSpacingPercent = settings.editorLineSpacingPercent,
                                    editorLetterSpacingSp = editorLetterSpacingSp,
                                    editorFontPreset = settings.editorFontPreset,
                                    customEditorFontUri = settings.customEditorFontUri,
                                    renderMarkdown = renderMarkdown
                                ),
                                paged = settings.editorPaginationEnabled
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
        val previewBitmaps = remember(image.uris) {
            image.uris.mapNotNull { uri ->
                context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }
        }
        ModalBottomSheet(onDismissRequest = { exportedImage = null }) {
            if (previewBitmaps.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    previewBitmaps.forEachIndexed { index, bitmap ->
                        Column(modifier = Modifier.width(220.dp)) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(360.dp)
                            )
                            Text(
                                text = "${index + 1} / ${previewBitmaps.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
            ListItem(
                headlineContent = { Text("保存到相册") },
                leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                modifier = Modifier.clickable {
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                image.uris.zip(image.fileNames).forEach { (uri, fileName) ->
                                    NoteImageExporter.saveImageToGallery(context.applicationContext, uri, fileName).getOrThrow()
                                }
                                image.fileNames.joinToString("、")
                            }
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
                    val shareIntent = if (image.uris.size > 1) {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "image/png"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(image.uris))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, image.primaryUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
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
                        if (isEditingText && !state.hideUndoRedo) {
                            IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                            }
                            IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做")
                            }
                        }
                        if (state.showMarathonButton) {
                            IconButton(
                                onClick = {
                                    if (state.marathonActive) viewModel.stopMarathon() else viewModel.startMarathon()
                                }
                            ) {
                                Icon(
                                    imageVector = if (state.marathonActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (state.marathonActive) "结束马拉松" else "开始马拉松"
                                )
                            }
                        }
                        if (!state.hideAiButton) {
                            IconButton(onClick = {
                                viewModel.dismissCompletion()
                                showAiMenu = true
                            }) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI 操作")
                            }
                        }
                        if (!isEditingText) {
                            IconButton(onClick = {
                                viewModel.dismissCompletion()
                                showShareMenu = true
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "分享")
                            }
                        }
                        if (!isEditingText && state.showKnowledgeButton) {
                            IconButton(onClick = {
                                viewModel.dismissCompletion()
                                onOpenKnowledgeScope(state.noteId)
                            }) {
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
                        IconButton(onClick = {
                            viewModel.dismissCompletion()
                            onOpenSettings()
                        }) {
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
                if (state.marathonActive) {
                    LinearProgressIndicator(
                        progress = { state.marathonProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                }
                if (settings.editorPaginationEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${(currentEditorPage + 1).coerceAtLeast(1)}/${totalEditorPages.coerceAtLeast(1)} 页",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    jumpPageText = (currentEditorPage + 1).toString()
                                    showJumpPageDialog = true
                                }
                        )
                    }
                }
                Text(
                    text = if (canShowGhostText) {
                        "${state.wordCount} 字，使用工具栏确认补全"
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
                        showCompletionActions = canShowGhostText,
                        onAcceptCompletion = viewModel::acceptCompletion,
                        onRetryCompletion = viewModel::retryCompletion,
                        onDismissCompletion = viewModel::dismissCompletion,
                        aiCompletionEnabled = !state.disableManualAiCompletion,
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
                .then(if (settings.editorPaginationEnabled) Modifier else Modifier.verticalScroll(contentScrollState))
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
                modifier = if (settings.editorPaginationEnabled) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                },
                textSizeSp = settings.editorTextSizeSp,
                lineHeightSp = editorLineHeightSp,
                letterSpacingSp = editorLetterSpacingSp,
                fontFamily = editorFontFamily,
                previewRange = state.completion.previewRange,
                pagedMode = settings.editorPaginationEnabled,
                animatePageTransitions = settings.editorPaginationEnabled,
                freezePaginationHeight = compactPagedEditing,
                allowCurrentPageVerticalScroll = compactPagedEditing,
                currentPage = currentEditorPage,
                onCurrentPageChange = { currentEditorPage = it },
                onPageCountChange = { totalEditorPages = it.coerceAtLeast(1) },
                renderMarkdown = renderMarkdown,
                onFocusChanged = { bodyFocused = it }
            )
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

private fun UserSettings.toEditorFontFamily(context: android.content.Context): FontFamily {
    if (editorFontPreset == EditorFontPreset.Custom && customEditorFontUri.isNotBlank()) {
        runCatching {
            context.contentResolver.openFileDescriptor(android.net.Uri.parse(customEditorFontUri), "r")?.use { descriptor ->
                FontFamily(Typeface.Builder(descriptor.fileDescriptor).build())
            }
        }.getOrNull()?.let { return it }
    }
    return editorFontPreset.toFallbackFontFamily()
}

private fun EditorFontPreset.toFallbackFontFamily(): FontFamily {
    return when (this) {
        EditorFontPreset.System -> FontFamily.Default
        EditorFontPreset.Sans -> FontFamily.SansSerif
        EditorFontPreset.Serif -> FontFamily.Serif
        EditorFontPreset.Monospace -> FontFamily.Monospace
        EditorFontPreset.Cursive -> FontFamily.Cursive
        EditorFontPreset.Custom -> FontFamily.Default
    }
}
