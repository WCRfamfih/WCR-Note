package com.example.ainote.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
                title = { Text("\u8bbe\u7f6e") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
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
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenAiSettings),
                    headlineContent = { Text("AI \u8bbe\u7f6e") },
                    supportingContent = { Text("\u670d\u52a1\u5546\u3001API Key\u3001\u81ea\u52a8\u8865\u5168\u548c\u9690\u79c1\u4e0a\u4e0b\u6587") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenAiDebugLog),
                    headlineContent = { Text("AI \u8c03\u8bd5\u65e5\u5fd7") },
                    supportingContent = { Text("\u67e5\u770b\u672c\u6b21\u542f\u52a8\u540e\u7684 API \u8c03\u7528\u3001\u8fd4\u56de\u548c\u9519\u8bef\u3002") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("\u989c\u8272\u4e3b\u9898", style = MaterialTheme.typography.titleSmall)
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
            Text("\u5f3a\u8c03\u8272\u9884\u8bbe", style = MaterialTheme.typography.titleSmall)
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
            Spacer(Modifier.height(20.dp))
            Text("\u6587\u5b57\u5927\u5c0f\uff1a${settings.editorTextSizeSp} sp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorTextSizeSp.toFloat(),
                onValueChange = { viewModel.updateEditorTextSizeSp(it.toInt()) },
                valueRange = 14f..28f,
                steps = 13
            )
            SettingSwitch(
                title = "\u663e\u793a Markdown \u6807\u8bb0",
                description = "\u8c03\u8bd5\u7528\u3002\u5f00\u542f\u540e\u663e\u793a\u5e76\u5141\u8bb8\u624b\u52a8\u7f16\u8f91\u7279\u6b8a\u5b57\u7b26\uff0c\u540c\u65f6\u5173\u95ed Markdown \u6548\u679c\u6e32\u67d3\u3002",
                checked = settings.showMarkdownMarkers,
                onCheckedChange = viewModel::updateShowMarkdownMarkers
            )
            Spacer(Modifier.height(20.dp))
            Text("\u6587\u6863\u4fdd\u5b58\u76ee\u5f55", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = settings.documentDirectoryUri.takeIf { it.isNotBlank() }?.let(::displayDirectoryUri)
                            ?: "\u672a\u6307\u5b9a\u3002\u6307\u5b9a\u540e\uff0c\u6bcf\u6b21\u4fdd\u5b58\u7b14\u8bb0\u90fd\u4f1a\u540c\u6b65\u5199\u51fa Markdown \u6587\u4ef6\u3002",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { directoryPicker.launch(null) }) {
                            Text(if (settings.documentDirectoryUri.isBlank()) "\u9009\u62e9\u76ee\u5f55" else "\u66f4\u6362\u76ee\u5f55")
                        }
                        if (settings.documentDirectoryUri.isNotBlank()) {
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Button(onClick = { viewModel.updateDocumentDirectoryUri("") }) {
                                Text("\u6e05\u9664")
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
            Text("\u7b14\u8bb0\u6392\u5e8f", style = MaterialTheme.typography.titleSmall)
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
                title = { Text("AI \u8bbe\u7f6e") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
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
            Text("\u670d\u52a1\u5546\u9884\u8bbe", style = MaterialTheme.typography.titleSmall)
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
                                label = "\u65b0\u9884\u8bbe",
                                provider = "OpenAI",
                                baseUrl = "https://api.openai.com/v1/chat/completions",
                                model = "gpt-4o-mini"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("\u65b0\u589e\u9884\u8bbe")
                }
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = { viewModel.removeAiServicePreset(selectedPreset.id) },
                    enabled = presets.size > 1,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("\u5220\u9664\u5f53\u524d")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("\u5957\u7528\u6a21\u677f", style = MaterialTheme.typography.titleSmall)
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
                label = { Text("\u9884\u8bbe\u540d\u79f0") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = selectedPreset.provider,
                onValueChange = { value -> updateSelectedPreset { copy(provider = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Provider") },
                supportingText = { Text("\u586b Fake \u4f7f\u7528\u672c\u5730\u6a21\u62df\uff1b\u586b OpenAI \u6216\u5176\u4ed6\u540d\u79f0\u5219\u4f7f\u7528 OpenAI-compatible HTTP API\u3002") },
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
                            contentDescription = if (showApiKey) "\u9690\u85cf API Key" else "\u663e\u793a API Key"
                        )
                    }
                },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.testConnection(selectedPreset.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("\u6d4b\u8bd5 API \u8fde\u63a5")
            }
            testStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            Text("\u7528\u9014\u7ed1\u5b9a", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            PresetUsageSelector(
                title = "\u81ea\u52a8\u8865\u5168\u4f7f\u7528",
                presets = presets,
                selectedId = settings.autoCompletionPresetId,
                onSelect = viewModel::updateAutoCompletionPresetId
            )
            PresetUsageSelector(
                title = "\u624b\u52a8\u8865\u5168\u4f7f\u7528",
                presets = presets,
                selectedId = settings.manualCompletionPresetId,
                onSelect = viewModel::updateManualCompletionPresetId
            )
            PresetUsageSelector(
                title = "\u53f3\u4e0a\u89d2 AI \u5de5\u5177\u4f7f\u7528",
                presets = presets,
                selectedId = settings.aiToolPresetId,
                onSelect = viewModel::updateAiToolPresetId
            )
            Spacer(Modifier.height(20.dp))
            SettingSwitch(
                title = "\u81ea\u52a8\u8865\u5168",
                description = "\u505c\u6b62\u8f93\u5165\u540e\u663e\u793a\u4e00\u6761\u5019\u9009\u8865\u5168\uff0c\u63a5\u53d7\u540e\u624d\u5199\u5165\u6b63\u6587\u3002",
                checked = settings.autoCompletionEnabled,
                onCheckedChange = viewModel::updateAutoCompletionEnabled
            )
            if (settings.autoCompletionEnabled) {
                SettingSwitch(
                    title = "\u4f18\u5148\u4e2d\u6587\u6587\u672c\u81ea\u52a8\u8865\u5168",
                    description = "\u5f00\u542f\u65f6\u53ea\u5728\u5149\u6807\u524d\u5305\u542b\u4e2d\u6587\u65f6\u81ea\u52a8\u8865\u5168\uff1b\u5173\u95ed\u540e\u5176\u4ed6\u8bed\u8a00\u4e5f\u80fd\u53c2\u4e0e\u3002",
                    checked = settings.preferChineseAutoCompletion,
                    onCheckedChange = viewModel::updatePreferChineseAutoCompletion
                )
                SettingSwitch(
                    title = "\u7a7a\u6587\u5b57\u884c\u4e0d\u89e6\u53d1\u81ea\u52a8\u8865\u5168",
                    description = "\u5f00\u542f\u540e\uff0c\u5149\u6807\u6240\u5728\u884c\u6ca1\u6709\u6587\u5b57\u65f6\u4e0d\u4f1a\u81ea\u52a8\u8bf7\u6c42\u8865\u5168\uff1b\u624b\u52a8\u8865\u5168\u4e0d\u53d7\u5f71\u54cd\u3002",
                    checked = settings.skipBlankLineAutoCompletion,
                    onCheckedChange = viewModel::updateSkipBlankLineAutoCompletion
                )
                SettingSwitch(
                    title = "\u4ec5\u5f53\u5185\u5bb9\u53d8\u5316\u65f6\u81ea\u52a8\u8865\u5168",
                    description = "\u5f00\u542f\u540e\uff0c\u79fb\u52a8\u5149\u6807\u4e0d\u4f1a\u81ea\u52a8\u8bf7\u6c42\u8865\u5168\uff1b\u53ea\u6709\u6b63\u6587\u5185\u5bb9\u53d8\u5316\u540e\u624d\u4f1a\u89e6\u53d1\u3002\u624b\u52a8\u8865\u5168\u4e0d\u53d7\u5f71\u54cd\u3002",
                    checked = settings.autoCompleteOnlyOnContentChange,
                    onCheckedChange = viewModel::updateAutoCompleteOnlyOnContentChange
                )
            }
            SettingSwitch(
                title = "\u663e\u793a AI \u8865\u5168\u9519\u8bef\u63d0\u793a",
                description = "\u5f00\u542f\u540e\uff0c\u8865\u5168\u5931\u8d25\u6216\u7a7a\u8fd4\u56de\u4f1a\u5728\u5e95\u90e8\u663e\u793a\u63d0\u793a\uff1b\u5173\u95ed\u540e\u4ec5\u8bb0\u5f55\u5230 AI \u8c03\u8bd5\u65e5\u5fd7\u3002",
                checked = settings.showCompletionErrorToast,
                onCheckedChange = viewModel::updateShowCompletionErrorToast
            )
            SettingSwitch(
                title = "\u5141\u8bb8\u6574\u7bc7\u4e0a\u4e0b\u6587",
                description = "\u5173\u95ed\u65f6\u53ea\u53d1\u9001\u5149\u6807\u9644\u8fd1\u5185\u5bb9\u3002",
                checked = settings.useFullNoteContext,
                onCheckedChange = viewModel::updateUseFullNoteContext
            )
            if (!settings.useFullNoteContext) {
                Spacer(Modifier.height(12.dp))
                Text("\u5141\u8bb8\u53d1\u9001\u7684\u524d\u6587\u884c\u6570\uff1a${settings.completionBeforeLineCount}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = settings.completionBeforeLineCount.toFloat(),
                    onValueChange = { viewModel.updateCompletionBeforeLineCount(it.toInt()) },
                    valueRange = 0f..20f,
                    steps = 19
                )
                Text("\u5141\u8bb8\u53d1\u9001\u7684\u4e0b\u6587\u884c\u6570\uff1a${settings.completionAfterLineCount}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = settings.completionAfterLineCount.toFloat(),
                    onValueChange = { viewModel.updateCompletionAfterLineCount(it.toInt()) },
                    valueRange = 0f..20f,
                    steps = 19
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("\u8865\u5168\u5ef6\u8fdf\uff1a${settings.completionDelayMs} ms", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.completionDelayMs.toFloat(),
                onValueChange = { viewModel.updateCompletionDelayMs(it.toLong()) },
                valueRange = 300f..1500f,
                steps = 11
            )
            Text("\u6700\u5927\u8865\u5168\u957f\u5ea6\uff1a${settings.maxCompletionLength} \u5b57", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.maxCompletionLength.toFloat(),
                onValueChange = { viewModel.updateMaxCompletionLength(it.toInt()) },
                valueRange = 10f..80f,
                steps = 6
            )
            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "\u9690\u79c1\u8bf4\u660e\uff1a\u542f\u7528\u771f\u5b9e API \u540e\uff0c\u5e94\u7528\u4f1a\u5c06\u5149\u6807\u9644\u8fd1\u6587\u672c\u6216\u4f60\u9009\u62e9\u7684\u5904\u7406\u5185\u5bb9\u53d1\u9001\u5230\u6240\u9009 AI \u670d\u52a1\u5546\u3002\u9ed8\u8ba4\u53ea\u53d1\u9001\u5149\u6807\u9644\u8fd1\u5185\u5bb9\uff0cAPI Key \u4fdd\u5b58\u5728\u672c\u5730\u8bbe\u5907\u3002",
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
                title = { Text("AI \u8c03\u8bd5\u65e5\u5fd7") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                },
                actions = {
                    Button(onClick = AiDebugLogStore::clear) {
                        Text("\u6e05\u7a7a")
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
                Text("\u6682\u65e0\u65e5\u5fd7\u3002\u672c\u9875\u9762\u53ea\u663e\u793a\u672c\u6b21\u542f\u52a8\u540e\u7684 AI \u8c03\u7528\u8bb0\u5f55\uff0c\u91cd\u542f\u5e94\u7528\u4f1a\u81ea\u52a8\u6e05\u7a7a\u3002")
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
