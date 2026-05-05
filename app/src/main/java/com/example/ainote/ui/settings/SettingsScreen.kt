package com.example.ainote.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ainote.data.debug.AiDebugLogStore
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.AccentColorPreset
import com.example.ainote.data.settings.AiProviderPreset
import com.example.ainote.data.settings.AiServicePreset
import com.example.ainote.data.settings.EditableSettingKeys
import com.example.ainote.data.settings.EditorFontPreset
import com.example.ainote.data.settings.NoteSortDirection
import com.example.ainote.data.settings.NoteSortField
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dataStore: SettingsDataStore,
    aiRepository: AiRepository,
    noteRepository: NoteRepository,
    onOpenAiSettings: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onOpenKnowledgeSettings: () -> Unit,
    onOpenAiDebugLog: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(dataStore, aiRepository, noteRepository))
    val settings by viewModel.settings.collectAsState()
    val documentStatus by viewModel.documentStatus.collectAsState()
    val context = LocalContext.current
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        viewModel.updateDocumentDirectoryUri(uri.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val recentKeys = settings.recentEditableSettingKeys.filter(viewModel::isQuickEditableSetting)
            if (recentKeys.isNotEmpty()) {
                Text("????", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                recentKeys.forEach { key ->
                    RecentSettingCard(
                        key = key,
                        settings = settings,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(10.dp))
            }
            SettingsEntry("AI 设置", "服务商、预设、自动补全和上下文规则。", onOpenAiSettings)
            Spacer(Modifier.height(12.dp))
            SettingsEntry("显示设置", "主题、强调色、字号、间距和字体。", onOpenDisplaySettings)
            Spacer(Modifier.height(12.dp))
            SettingsEntry("知识库设置", "知识引用与单次请求知识数量上限。", onOpenKnowledgeSettings)
            Spacer(Modifier.height(12.dp))
            SettingsEntry("AI 调试日志", "查看本次会话中的请求、响应和错误。", onOpenAiDebugLog)
            Spacer(Modifier.height(20.dp))

            Text("文档备份目录", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = settings.documentDirectoryUri.takeIf { it.isNotBlank() }?.let(::displayDirectoryUri)
                            ?: "未选择目录。设置后，笔记会同步导出为文件。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { directoryPicker.launch(null) }) {
                            Text(if (settings.documentDirectoryUri.isBlank()) "选择目录" else "更换目录")
                        }
                        if (settings.documentDirectoryUri.isNotBlank()) {
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Button(onClick = { viewModel.updateDocumentDirectoryUri("") }) {
                                Text("清除")
                            }
                        }
                    }
                    documentStatus?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("笔记排序", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                NoteSortField.entries.forEach { field ->
                    FilterChip(
                        selected = settings.noteSortField == field,
                        onClick = { viewModel.updateNoteSortField(field) },
                        label = { Text(field.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                NoteSortDirection.entries.forEach { direction ->
                    FilterChip(
                        selected = settings.noteSortDirection == direction,
                        onClick = { viewModel.updateNoteSortDirection(direction) },
                        label = { Text(direction.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
    }
}

private fun displayDirectoryUri(uri: String): String {
    return Uri.decode(uri).substringAfterLast('/').ifBlank { uri }
}

private fun resolveDocumentName(context: android.content.Context, uri: Uri): String {
    val fromProvider = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
    return fromProvider
        ?: Uri.decode(uri.lastPathSegment ?: "").substringAfterLast('/').ifBlank { uri.toString() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(
    dataStore: SettingsDataStore,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(dataStore, AiRepository(dataStore)))
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModel.updateCustomEditorFont(uri.toString(), resolveDocumentName(context, uri))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("????") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "??")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("????", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.updateThemeMode(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("?????", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                AccentColorPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.accentColorPreset == preset,
                        onClick = { viewModel.updateAccentColorPreset(preset) },
                        label = { Text(preset.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("???${"%.2f".format(settings.accentBrightnessOffset)}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.accentBrightnessOffset,
                onValueChange = viewModel::updateAccentBrightnessOffset,
                valueRange = -0.25f..0.25f,
                steps = 24
            )
            Spacer(Modifier.height(12.dp))
            Text("????${"%.2f".format(settings.accentSaturationFactor)}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.accentSaturationFactor,
                onValueChange = viewModel::updateAccentSaturationFactor,
                valueRange = 0.5f..1.5f,
                steps = 19
            )
            Spacer(Modifier.height(20.dp))
            Text("?????${settings.editorTextSizeSp} sp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorTextSizeSp.toFloat(),
                onValueChange = { viewModel.updateEditorTextSizeSp(it.toInt()) },
                valueRange = 14f..28f,
                steps = 13
            )
            Spacer(Modifier.height(12.dp))
            Text("????${settings.editorLineSpacingPercent}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorLineSpacingPercent.toFloat(),
                onValueChange = { viewModel.updateEditorLineSpacingPercent(it.toInt()) },
                valueRange = 100f..220f,
                steps = 11
            )
            Spacer(Modifier.height(12.dp))
            Text("????${settings.editorLetterSpacingTenthSp / 10f} sp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorLetterSpacingTenthSp.toFloat(),
                onValueChange = { viewModel.updateEditorLetterSpacingTenthSp(it.toInt()) },
                valueRange = 0f..12f,
                steps = 11
            )
            Spacer(Modifier.height(12.dp))
            SettingSwitch(
                title = "????",
                description = "???????????????????????????????",
                checked = settings.editorPaginationEnabled,
                onCheckedChange = viewModel::updateEditorPaginationEnabled
            )
            Spacer(Modifier.height(20.dp))
            Text("??", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                EditorFontPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.editorFontPreset == preset,
                        onClick = {
                            if (preset == EditorFontPreset.Custom && settings.customEditorFontUri.isBlank()) {
                                fontPicker.launch(arrayOf("font/*", "application/octet-stream", "*/*"))
                            } else {
                                viewModel.updateEditorFontPreset(preset)
                            }
                        },
                        label = { Text(preset.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("?????", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = settings.customEditorFontLabel.ifBlank { "???????????????????? .ttf / .otf ????" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { fontPicker.launch(arrayOf("font/*", "application/octet-stream", "*/*")) }) {
                            Text(if (settings.customEditorFontUri.isBlank()) "????" else "????")
                        }
                        if (settings.customEditorFontUri.isNotBlank()) {
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Button(onClick = viewModel::clearCustomEditorFont) {
                                Text("????")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingSwitch(
                title = "?? Markdown ??",
                description = "??????? Markdown ???????????",
                checked = settings.showMarkdownMarkers,
                onCheckedChange = viewModel::updateShowMarkdownMarkers
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeSettingsScreen(

    dataStore: SettingsDataStore,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(dataStore, AiRepository(dataStore)))
    val settings by viewModel.settings.collectAsState()
    var limitText by remember(settings.knowledgeSendLimit) { mutableStateOf(settings.knowledgeSendLimit.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            SettingSwitch(
                title = "启用知识库引用",
                description = "允许将识别到的知识卡片一并发送到 AI 请求中。",
                checked = settings.knowledgeBaseEnabled,
                onCheckedChange = viewModel::updateKnowledgeBaseEnabled
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = limitText,
                onValueChange = { value ->
                    val digits = value.filter(Char::isDigit)
                    limitText = digits
                    digits.toIntOrNull()?.let(viewModel::updateKnowledgeSendLimit)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("单次发送知识上限") },
                supportingText = {
                    Text("如果识别到的知识超过这个数量，编辑器会先弹窗确认是否发送全部。")
                },
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    dataStore: SettingsDataStore,
    aiRepository: AiRepository,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(dataStore, aiRepository))
    val settings by viewModel.settings.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }
    var selectedPresetId by remember { mutableStateOf(settings.aiToolPresetId) }
    val presets = settings.aiServicePresets.ifEmpty {
        listOf(
            AiServicePreset(
                id = "fake",
                label = "Fake",
                provider = "Fake",
                baseUrl = "https://api.openai.com/v1/chat/completions",
                model = "gpt-4o-mini"
            )
        )
    }
    LaunchedEffect(presets, selectedPresetId) {
        if (presets.none { it.id == selectedPresetId }) selectedPresetId = presets.first().id
    }
    val selectedPreset = presets.firstOrNull { it.id == selectedPresetId } ?: presets.first()

    fun updateSelectedPreset(update: AiServicePreset.() -> AiServicePreset) {
        viewModel.updateAiServicePreset(selectedPreset.update())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("服务预设", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = preset.id == selectedPreset.id,
                        onClick = { selectedPresetId = preset.id },
                        label = { Text(preset.label.ifBlank { preset.provider.ifBlank { preset.id } }) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val id = "custom_${System.currentTimeMillis()}"
                        selectedPresetId = id
                        viewModel.addAiServicePreset(
                            AiServicePreset(
                                id = id,
                                label = "新预设",
                                provider = "OpenAI",
                                baseUrl = "https://api.openai.com/v1/chat/completions",
                                model = "gpt-4o-mini"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("新增预设")
                }
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = { viewModel.removeAiServicePreset(selectedPreset.id) },
                    enabled = presets.size > 1,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("删除当前")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("服务商模板", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                AiProviderPreset.All.forEach { preset ->
                    ProviderPresetChip(
                        preset = preset,
                        selected = selectedPreset.provider.equals(preset.provider, ignoreCase = true) &&
                            selectedPreset.baseUrl == preset.baseUrl &&
                            selectedPreset.model == preset.model,
                        onClick = {
                            updateSelectedPreset {
                                copy(
                                    label = preset.label,
                                    provider = preset.provider,
                                    baseUrl = preset.baseUrl,
                                    model = preset.model
                                )
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = selectedPreset.label,
                onValueChange = { value -> updateSelectedPreset { copy(label = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("预设名称") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = selectedPreset.provider,
                onValueChange = { value -> updateSelectedPreset { copy(provider = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Provider") },
                supportingText = { Text("填写 Fake 使用本地模拟；其他 Provider 走 OpenAI 兼容接口。") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = selectedPreset.baseUrl,
                onValueChange = { value -> updateSelectedPreset { copy(baseUrl = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Base URL") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = selectedPreset.model,
                onValueChange = { value -> updateSelectedPreset { copy(model = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = selectedPreset.apiKey,
                onValueChange = { value -> updateSelectedPreset { copy(apiKey = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "隐藏 API Key" else "显示 API Key"
                        )
                    }
                },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.testConnection(selectedPreset.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("测试 API 连接")
            }
            testStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            Text("用途绑定", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            PresetUsageSelector("自动补全", presets, settings.autoCompletionPresetId, viewModel::updateAutoCompletionPresetId)
            PresetUsageSelector("手动补全", presets, settings.manualCompletionPresetId, viewModel::updateManualCompletionPresetId)
            PresetUsageSelector("AI 工具", presets, settings.aiToolPresetId, viewModel::updateAiToolPresetId)
            Spacer(Modifier.height(20.dp))
            SettingSwitch(
                title = "自动补全",
                description = "停止输入后显示一条建议续写。",
                checked = settings.autoCompletionEnabled,
                onCheckedChange = viewModel::updateAutoCompletionEnabled
            )
            if (settings.autoCompletionEnabled) {
                SettingSwitch(
                    title = "优先中文自动补全",
                    description = "仅在光标前文本包含中文时请求自动补全。",
                    checked = settings.preferChineseAutoCompletion,
                    onCheckedChange = viewModel::updatePreferChineseAutoCompletion
                )
                SettingSwitch(
                    title = "跳过空白行",
                    description = "当前行为空时不触发自动补全。",
                    checked = settings.skipBlankLineAutoCompletion,
                    onCheckedChange = viewModel::updateSkipBlankLineAutoCompletion
                )
                SettingSwitch(
                    title = "仅内容变化时触发",
                    description = "仅移动光标时不触发自动补全。",
                    checked = settings.autoCompleteOnlyOnContentChange,
                    onCheckedChange = viewModel::updateAutoCompleteOnlyOnContentChange
                )
            }
            SettingSwitch(
                title = "显示 AI 错误提示",
                description = "补全失败或返回无效内容时显示提示。",
                checked = settings.showCompletionErrorToast,
                onCheckedChange = viewModel::updateShowCompletionErrorToast
            )
            SettingSwitch(
                title = "允许整篇上下文",
                description = "关闭时仅发送光标附近的上下文窗口。",
                checked = settings.useFullNoteContext,
                onCheckedChange = viewModel::updateUseFullNoteContext
            )
            if (!settings.useFullNoteContext) {
                Spacer(Modifier.height(12.dp))
                Text("光标前行数：${settings.completionBeforeLineCount}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = settings.completionBeforeLineCount.toFloat(),
                    onValueChange = { viewModel.updateCompletionBeforeLineCount(it.toInt()) },
                    valueRange = 0f..20f,
                    steps = 19
                )
                Text("光标后行数：${settings.completionAfterLineCount}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = settings.completionAfterLineCount.toFloat(),
                    onValueChange = { viewModel.updateCompletionAfterLineCount(it.toInt()) },
                    valueRange = 0f..20f,
                    steps = 19
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "自动补全触发延迟：${"%.1f".format(settings.completionDelayMs / 1000f)} s",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = settings.completionDelayMs / 1000f,
                onValueChange = { viewModel.updateCompletionDelayMs((it * 1000).toLong()) },
                valueRange = 0f..5f,
                steps = 49
            )
            Text("最大补全长度：${settings.maxCompletionLength} 字", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.maxCompletionLength.toFloat(),
                onValueChange = { viewModel.updateMaxCompletionLength(it.toInt()) },
                valueRange = 10f..80f,
                steps = 6
            )
            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "隐私说明：使用真实 API 时，应用会将附近文本或选中文本发送给当前服务商，API Key 仅保存在本地设备。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDebugLogScreen(onBack: () -> Unit) {
    val entries by AiDebugLogStore.entries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 调试日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Button(onClick = AiDebugLogStore::clear) {
                        Text("清空")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (entries.isEmpty()) {
                Text("暂无日志。本页仅显示当前应用会话内的 AI 调用记录。")
            } else {
                SelectionContainer {
                    Column {
                        entries.forEach { entry ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("${entry.time}  ${entry.title}", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(8.dp))
                                    Text(entry.detail, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        )
    }
}

@Composable
private fun PresetUsageSelector(
    title: String,
    presets: List<AiServicePreset>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = preset.id == selectedId,
                onClick = { onSelect(preset.id) },
                label = { Text(preset.label.ifBlank { preset.provider.ifBlank { preset.id } }) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ProviderPresetChip(
    preset: AiProviderPreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(preset.label) },
        modifier = Modifier.padding(end = 8.dp)
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RecentSettingCard(
    key: String,
    settings: com.example.ainote.data.settings.UserSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (key) {
                EditableSettingKeys.AutoCompletionEnabled -> SettingSwitch("????", "??????????????", settings.autoCompletionEnabled, viewModel::updateAutoCompletionEnabled)
                EditableSettingKeys.PreferChineseAutoCompletion -> SettingSwitch("????????", "???????????????????", settings.preferChineseAutoCompletion, viewModel::updatePreferChineseAutoCompletion)
                EditableSettingKeys.SkipBlankLineAutoCompletion -> SettingSwitch("?????", "??????????????", settings.skipBlankLineAutoCompletion, viewModel::updateSkipBlankLineAutoCompletion)
                EditableSettingKeys.AutoCompleteOnlyOnContentChange -> SettingSwitch("????????", "??????????????", settings.autoCompleteOnlyOnContentChange, viewModel::updateAutoCompleteOnlyOnContentChange)
                EditableSettingKeys.UseFullNoteContext -> SettingSwitch("???????", "?????????????????", settings.useFullNoteContext, viewModel::updateUseFullNoteContext)
                EditableSettingKeys.ShowCompletionErrorToast -> SettingSwitch("?? AI ????", "?????????????????", settings.showCompletionErrorToast, viewModel::updateShowCompletionErrorToast)
                EditableSettingKeys.KnowledgeBaseEnabled -> SettingSwitch("???????", "???????????????? AI ????", settings.knowledgeBaseEnabled, viewModel::updateKnowledgeBaseEnabled)
                EditableSettingKeys.ShowMarkdownMarkers -> SettingSwitch("?? Markdown ??", "??????? Markdown ???????????", settings.showMarkdownMarkers, viewModel::updateShowMarkdownMarkers)
                EditableSettingKeys.EditorPaginationEnabled -> SettingSwitch("????", "??????????????????????", settings.editorPaginationEnabled, viewModel::updateEditorPaginationEnabled)
                EditableSettingKeys.EditorTextSizeSp -> RecentSlider("????", "${settings.editorTextSizeSp} sp", settings.editorTextSizeSp.toFloat(), { viewModel.updateEditorTextSizeSp(it.toInt()) }, 14f..28f, 13)
                EditableSettingKeys.EditorLineSpacingPercent -> RecentSlider("???", "${settings.editorLineSpacingPercent}%", settings.editorLineSpacingPercent.toFloat(), { viewModel.updateEditorLineSpacingPercent(it.toInt()) }, 100f..220f, 11)
                EditableSettingKeys.EditorLetterSpacingTenthSp -> RecentSlider("???", "${settings.editorLetterSpacingTenthSp / 10f} sp", settings.editorLetterSpacingTenthSp.toFloat(), { viewModel.updateEditorLetterSpacingTenthSp(it.toInt()) }, 0f..12f, 11)
                EditableSettingKeys.AccentBrightnessOffset -> RecentSlider("?????", "${"%.2f".format(settings.accentBrightnessOffset)}", settings.accentBrightnessOffset, viewModel::updateAccentBrightnessOffset, -0.25f..0.25f, 24)
                EditableSettingKeys.AccentSaturationFactor -> RecentSlider("??????", "${"%.2f".format(settings.accentSaturationFactor)}", settings.accentSaturationFactor, viewModel::updateAccentSaturationFactor, 0.5f..1.5f, 19)
                EditableSettingKeys.CompletionDelayMs -> RecentSlider("????????", "${"%.1f".format(settings.completionDelayMs / 1000f)} s", settings.completionDelayMs / 1000f, { viewModel.updateCompletionDelayMs((it * 1000).toLong()) }, 0f..5f, 49)
                EditableSettingKeys.MaxCompletionLength -> RecentSlider("??????", "${settings.maxCompletionLength} ?", settings.maxCompletionLength.toFloat(), { viewModel.updateMaxCompletionLength(it.toInt()) }, 10f..80f, 6)
                EditableSettingKeys.CompletionBeforeLineCount -> RecentSlider("?????", settings.completionBeforeLineCount.toString(), settings.completionBeforeLineCount.toFloat(), { viewModel.updateCompletionBeforeLineCount(it.toInt()) }, 0f..20f, 19)
                EditableSettingKeys.CompletionAfterLineCount -> RecentSlider("?????", settings.completionAfterLineCount.toString(), settings.completionAfterLineCount.toFloat(), { viewModel.updateCompletionAfterLineCount(it.toInt()) }, 0f..20f, 19)
                EditableSettingKeys.KnowledgeSendLimit -> RecentNumberField("????????", settings.knowledgeSendLimit.toString()) { digits ->
                    digits.toIntOrNull()?.let(viewModel::updateKnowledgeSendLimit)
                }
            }
        }
    }
}

@Composable
private fun RecentSlider(

    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Text("$title：$valueLabel", style = MaterialTheme.typography.titleSmall)
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
}

@Composable
private fun RecentNumberField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            val digits = next.filter(Char::isDigit)
            text = digits
            onValueChange(digits)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        singleLine = true
    )
}
