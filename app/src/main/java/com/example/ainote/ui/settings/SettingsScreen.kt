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
import androidx.compose.material.icons.filled.Add
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
import com.example.ainote.data.settings.UserSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dataStore: SettingsDataStore,
    aiRepository: AiRepository,
    noteRepository: NoteRepository,
    onOpenAiSettings: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onOpenKnowledgeSettings: () -> Unit,
    onOpenExperimentalSettings: () -> Unit,
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
                Text("最近调整", style = MaterialTheme.typography.titleSmall)
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
            SettingsEntry("显示设置", "主题、强调色、字号、间距、字体与分页。", onOpenDisplaySettings)
            Spacer(Modifier.height(12.dp))
            SettingsEntry("知识库设置", "知识引用与单次发送知识上限。", onOpenKnowledgeSettings)
            Spacer(Modifier.height(12.dp))
            SettingsEntry("实验性内容", "试验中的编辑能力，默认全部关闭。", onOpenExperimentalSettings)
            Spacer(Modifier.height(12.dp))
            SettingsEntry("AI 调试日志", "查看当前会话中的 AI 请求、响应和错误。", onOpenAiDebugLog)

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
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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
    return fromProvider ?: Uri.decode(uri.lastPathSegment ?: "").substringAfterLast('/').ifBlank { uri.toString() }
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
                title = { Text("显示设置") },
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
            Text("颜色主题", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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
            Text("强调色预设", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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
            Text("亮度：${"%.2f".format(settings.accentBrightnessOffset)}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.accentBrightnessOffset,
                onValueChange = viewModel::updateAccentBrightnessOffset,
                valueRange = -0.25f..0.25f,
                steps = 24
            )
            Spacer(Modifier.height(12.dp))
            Text("饱和度：${"%.2f".format(settings.accentSaturationFactor)}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.accentSaturationFactor,
                onValueChange = viewModel::updateAccentSaturationFactor,
                valueRange = 0.5f..1.5f,
                steps = 19
            )

            Spacer(Modifier.height(20.dp))
            Text("文字大小：${settings.editorTextSizeSp} sp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorTextSizeSp.toFloat(),
                onValueChange = { viewModel.updateEditorTextSizeSp(it.toInt()) },
                valueRange = 14f..28f,
                steps = 13
            )
            Spacer(Modifier.height(12.dp))
            Text("行间距：${settings.editorLineSpacingPercent}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorLineSpacingPercent.toFloat(),
                onValueChange = { viewModel.updateEditorLineSpacingPercent(it.toInt()) },
                valueRange = 100f..220f,
                steps = 11
            )
            Spacer(Modifier.height(12.dp))
            Text("字间距：${settings.editorLetterSpacingTenthSp / 10f} sp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorLetterSpacingTenthSp.toFloat(),
                onValueChange = { viewModel.updateEditorLetterSpacingTenthSp(it.toInt()) },
                valueRange = 0f..12f,
                steps = 11
            )
            Spacer(Modifier.height(12.dp))
            SettingSwitch(
                title = "开启分页",
                description = "单篇文档按页显示，内容写满后自动续到下一页，并在底部显示页码。",
                checked = settings.editorPaginationEnabled,
                onCheckedChange = viewModel::updateEditorPaginationEnabled
            )

            Spacer(Modifier.height(20.dp))
            Text("字体", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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
                    Text("自定义字体", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = settings.customEditorFontLabel.ifBlank { "未选择字体文件。可通过系统文件选择器导入 .ttf / .otf 等字体。" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { fontPicker.launch(arrayOf("font/*", "application/octet-stream", "*/*")) }) {
                            Text(if (settings.customEditorFontUri.isBlank()) "选择字体" else "更换字体")
                        }
                        if (settings.customEditorFontUri.isNotBlank()) {
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Button(onClick = viewModel::clearCustomEditorFont) {
                                Text("清除字体")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingSwitch(
                title = "显示 Markdown 标记",
                description = "编辑时显示原始 Markdown 标记，并关闭渲染效果。",
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
                supportingText = { Text("超过这个上限时，会先弹窗确认是否全部发送。") },
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

    var selectedPresetId by remember(settings.aiServicePresets) {
        mutableStateOf(settings.aiServicePresets.firstOrNull()?.id.orEmpty())
    }
    val selectedPreset = settings.aiServicePresets.firstOrNull { it.id == selectedPresetId }
        ?: settings.aiServicePresets.firstOrNull()
        ?: settings.legacyPreset()
    if (selectedPresetId != selectedPreset.id) {
        selectedPresetId = selectedPreset.id
    }

    var label by remember(selectedPreset.id, selectedPreset.label) { mutableStateOf(selectedPreset.label) }
    var provider by remember(selectedPreset.id, selectedPreset.provider) { mutableStateOf(selectedPreset.provider) }
    var baseUrl by remember(selectedPreset.id, selectedPreset.baseUrl) { mutableStateOf(selectedPreset.baseUrl) }
    var model by remember(selectedPreset.id, selectedPreset.model) { mutableStateOf(selectedPreset.model) }
    var apiKey by remember(selectedPreset.id, selectedPreset.apiKey) { mutableStateOf(selectedPreset.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    fun saveSelectedPreset() {
        viewModel.updateAiServicePreset(
            selectedPreset.copy(
                label = label,
                provider = provider,
                baseUrl = baseUrl,
                model = model,
                apiKey = apiKey
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val nextId = "preset_${System.currentTimeMillis()}"
                            viewModel.addAiServicePreset(
                                AiServicePreset(
                                    id = nextId,
                                    label = "新预设",
                                    provider = "OpenAI",
                                    baseUrl = "https://api.openai.com/v1/chat/completions",
                                    model = "gpt-4o-mini"
                                )
                            )
                            selectedPresetId = nextId
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新增预设")
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
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                settings.aiServicePresets.forEach { preset ->
                    FilterChip(
                        selected = preset.id == selectedPresetId,
                        onClick = {
                            saveSelectedPreset()
                            selectedPresetId = preset.id
                        },
                        label = { Text(preset.label.ifBlank { preset.id }) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = ::saveSelectedPreset) {
                    Text("保存当前预设")
                }
                if (settings.aiServicePresets.size > 1) {
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Button(onClick = { viewModel.removeAiServicePreset(selectedPreset.id) }) {
                        Text("删除当前")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("服务商模板", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                AiProviderPreset.All.forEach { preset ->
                    ProviderPresetChip(
                        preset = preset,
                        selected = provider.equals(preset.provider, ignoreCase = true) &&
                            baseUrl == preset.baseUrl &&
                            model == preset.model,
                        onClick = {
                            label = preset.label
                            provider = preset.provider
                            baseUrl = preset.baseUrl
                            model = preset.model
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("预设名称") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Provider") },
                supportingText = { Text("填 Fake 使用本地模拟；其他 Provider 走 OpenAI 兼容接口。") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Base URL") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
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
            Button(
                onClick = {
                    saveSelectedPreset()
                    viewModel.testConnection(selectedPreset.id)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("测试 API 连接")
            }
            testStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(20.dp))
            Text("用途绑定", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            PresetUsageSelector("自动补全", settings.aiServicePresets, settings.autoCompletionPresetId, viewModel::updateAutoCompletionPresetId)
            PresetUsageSelector("手动补全", settings.aiServicePresets, settings.manualCompletionPresetId, viewModel::updateManualCompletionPresetId)
            PresetUsageSelector("AI 工具", settings.aiServicePresets, settings.aiToolPresetId, viewModel::updateAiToolPresetId)

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
            Text("自动补全触发延迟：${"%.1f".format(settings.completionDelayMs / 1000f)} s", style = MaterialTheme.typography.titleSmall)
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
                    text = "隐私说明：使用真实 API 时，应用会将上下文文本发送给当前服务商，API Key 仅保存在本地设备。",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalSettingsScreen(
    dataStore: SettingsDataStore,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(dataStore, AiRepository(dataStore)))
    val settings by viewModel.settings.collectAsState()
    var durationText by remember(settings.marathonDurationMinutes) {
        mutableStateOf(formatFloatSetting(settings.marathonDurationMinutes))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("实验性内容") },
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
            SettingSwitch(
                title = "马拉松模式",
                description = "默认关闭。开启后，编辑器可进入限时只追加正文的写作模式。",
                checked = settings.experimentalMarathonEnabled,
                onCheckedChange = viewModel::updateExperimentalMarathonEnabled
            )
            if (settings.experimentalMarathonEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { value ->
                        val filtered = filterSingleFloatInput(value)
                        durationText = filtered
                        filtered.toFloatOrNull()
                            ?.takeIf { it > 0f }
                            ?.let(viewModel::updateMarathonDurationMinutes)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设定时间（分钟）") },
                    supportingText = { Text("仅允许正数，可输入小数。") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                SettingSwitch(
                    title = "是否禁用AI功能",
                    description = "进行中隐藏右上角 AI 操作，并让工具栏手动 AI 补全变灰不可用。",
                    checked = settings.marathonDisableAi,
                    onCheckedChange = viewModel::updateMarathonDisableAi
                )
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
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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
    settings: UserSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (key) {
                EditableSettingKeys.AutoCompletionEnabled -> SettingSwitch("自动补全", "停止输入后显示一条建议续写。", settings.autoCompletionEnabled, viewModel::updateAutoCompletionEnabled)
                EditableSettingKeys.PreferChineseAutoCompletion -> SettingSwitch("优先中文自动补全", "仅在光标前文本包含中文时请求自动补全。", settings.preferChineseAutoCompletion, viewModel::updatePreferChineseAutoCompletion)
                EditableSettingKeys.SkipBlankLineAutoCompletion -> SettingSwitch("跳过空白行", "当前行为空时不触发自动补全。", settings.skipBlankLineAutoCompletion, viewModel::updateSkipBlankLineAutoCompletion)
                EditableSettingKeys.AutoCompleteOnlyOnContentChange -> SettingSwitch("仅内容变化时触发", "仅移动光标时不触发自动补全。", settings.autoCompleteOnlyOnContentChange, viewModel::updateAutoCompleteOnlyOnContentChange)
                EditableSettingKeys.UseFullNoteContext -> SettingSwitch("允许整篇上下文", "关闭时仅发送光标附近的上下文窗口。", settings.useFullNoteContext, viewModel::updateUseFullNoteContext)
                EditableSettingKeys.ShowCompletionErrorToast -> SettingSwitch("显示 AI 错误提示", "补全失败或返回无效内容时显示提示。", settings.showCompletionErrorToast, viewModel::updateShowCompletionErrorToast)
                EditableSettingKeys.KnowledgeBaseEnabled -> SettingSwitch("启用知识库引用", "允许将识别到的知识卡片一并发送到 AI 请求中。", settings.knowledgeBaseEnabled, viewModel::updateKnowledgeBaseEnabled)
                EditableSettingKeys.ShowMarkdownMarkers -> SettingSwitch("显示 Markdown 标记", "编辑时显示原始 Markdown 标记，并关闭渲染效果。", settings.showMarkdownMarkers, viewModel::updateShowMarkdownMarkers)
                EditableSettingKeys.EditorPaginationEnabled -> SettingSwitch("开启分页", "单篇文档按页显示，内容写满后自动续到下一页。", settings.editorPaginationEnabled, viewModel::updateEditorPaginationEnabled)
                EditableSettingKeys.EditorTextSizeSp -> RecentSlider("文字大小", "${settings.editorTextSizeSp} sp", settings.editorTextSizeSp.toFloat(), { viewModel.updateEditorTextSizeSp(it.toInt()) }, 14f..28f, 13)
                EditableSettingKeys.EditorLineSpacingPercent -> RecentSlider("行间距", "${settings.editorLineSpacingPercent}%", settings.editorLineSpacingPercent.toFloat(), { viewModel.updateEditorLineSpacingPercent(it.toInt()) }, 100f..220f, 11)
                EditableSettingKeys.EditorLetterSpacingTenthSp -> RecentSlider("字间距", "${settings.editorLetterSpacingTenthSp / 10f} sp", settings.editorLetterSpacingTenthSp.toFloat(), { viewModel.updateEditorLetterSpacingTenthSp(it.toInt()) }, 0f..12f, 11)
                EditableSettingKeys.AccentBrightnessOffset -> RecentSlider("强调色亮度", "${"%.2f".format(settings.accentBrightnessOffset)}", settings.accentBrightnessOffset, viewModel::updateAccentBrightnessOffset, -0.25f..0.25f, 24)
                EditableSettingKeys.AccentSaturationFactor -> RecentSlider("强调色饱和度", "${"%.2f".format(settings.accentSaturationFactor)}", settings.accentSaturationFactor, viewModel::updateAccentSaturationFactor, 0.5f..1.5f, 19)
                EditableSettingKeys.CompletionDelayMs -> RecentSlider("自动补全触发延迟", "${"%.1f".format(settings.completionDelayMs / 1000f)} s", settings.completionDelayMs / 1000f, { viewModel.updateCompletionDelayMs((it * 1000).toLong()) }, 0f..5f, 49)
                EditableSettingKeys.MaxCompletionLength -> RecentSlider("最大补全长度", "${settings.maxCompletionLength} 字", settings.maxCompletionLength.toFloat(), { viewModel.updateMaxCompletionLength(it.toInt()) }, 10f..80f, 6)
                EditableSettingKeys.CompletionBeforeLineCount -> RecentSlider("光标前行数", settings.completionBeforeLineCount.toString(), settings.completionBeforeLineCount.toFloat(), { viewModel.updateCompletionBeforeLineCount(it.toInt()) }, 0f..20f, 19)
                EditableSettingKeys.CompletionAfterLineCount -> RecentSlider("光标后行数", settings.completionAfterLineCount.toString(), settings.completionAfterLineCount.toFloat(), { viewModel.updateCompletionAfterLineCount(it.toInt()) }, 0f..20f, 19)
                EditableSettingKeys.KnowledgeSendLimit -> RecentNumberField("单次发送知识上限", settings.knowledgeSendLimit.toString()) {
                    it.toIntOrNull()?.let(viewModel::updateKnowledgeSendLimit)
                }
                EditableSettingKeys.ExperimentalMarathonEnabled -> SettingSwitch("马拉松模式", "进入编辑马拉松后，只允许继续追加正文内容。", settings.experimentalMarathonEnabled, viewModel::updateExperimentalMarathonEnabled)
                EditableSettingKeys.MarathonDurationMinutes -> RecentFloatField("马拉松时长（分钟）", formatFloatSetting(settings.marathonDurationMinutes)) {
                    it.toFloatOrNull()?.takeIf { value -> value > 0f }?.let(viewModel::updateMarathonDurationMinutes)
                }
                EditableSettingKeys.MarathonDisableAi -> SettingSwitch("马拉松时禁用 AI", "进行中隐藏 AI 操作，并禁用手动 AI 补全。", settings.marathonDisableAi, viewModel::updateMarathonDisableAi)
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

@Composable
private fun RecentFloatField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            val filtered = filterSingleFloatInput(next)
            text = filtered
            onValueChange(filtered)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        singleLine = true
    )
}

private fun filterSingleFloatInput(value: String): String {
    val builder = StringBuilder()
    var hasDot = false
    value.forEachIndexed { index, char ->
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !hasDot -> {
                if (builder.isEmpty() && index == 0) builder.append('0')
                builder.append('.')
                hasDot = true
            }
        }
    }
    return builder.toString()
}

private fun formatFloatSetting(value: Float): String {
    val rounded = String.format(java.util.Locale.US, "%.2f", value)
    return rounded.trimEnd('0').trimEnd('.')
}
