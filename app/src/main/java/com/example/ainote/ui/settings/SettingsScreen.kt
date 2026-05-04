package com.example.ainote.ui.settings

import android.content.Intent
import android.net.Uri
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            SettingsEntry(
                title = "AI Settings",
                description = "Providers, presets, auto-completion, and context rules.",
                onClick = onOpenAiSettings
            )
            Spacer(Modifier.height(12.dp))
            SettingsEntry(
                title = "Display Settings",
                description = "Theme, accent, size, spacing, and font.",
                onClick = onOpenDisplaySettings
            )
            Spacer(Modifier.height(12.dp))
            SettingsEntry(
                title = "Knowledge Settings",
                description = "Knowledge injection and per-request knowledge limits.",
                onClick = onOpenKnowledgeSettings
            )
            Spacer(Modifier.height(12.dp))
            SettingsEntry(
                title = "AI Debug Log",
                description = "Inspect requests, responses, and errors from this session.",
                onClick = onOpenAiDebugLog
            )
            Spacer(Modifier.height(20.dp))

            Text("Document Backup Directory", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = settings.documentDirectoryUri.takeIf { it.isNotBlank() }?.let(::displayDirectoryUri)
                            ?: "No directory selected. If set, notes will also be written out as files.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { directoryPicker.launch(null) }) {
                            Text(if (settings.documentDirectoryUri.isBlank()) "Choose Directory" else "Change Directory")
                        }
                        if (settings.documentDirectoryUri.isNotBlank()) {
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Button(onClick = { viewModel.updateDocumentDirectoryUri("") }) {
                                Text("Clear")
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
            Text("Note Sorting", style = MaterialTheme.typography.titleSmall)
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
fun DisplaySettingsScreen(
    dataStore: SettingsDataStore,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(dataStore, AiRepository(dataStore)))
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Display Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text("Theme", style = MaterialTheme.typography.titleSmall)
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
            Text("Accent", style = MaterialTheme.typography.titleSmall)
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
            Text("Text Size: ${settings.editorTextSizeSp} sp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorTextSizeSp.toFloat(),
                onValueChange = { viewModel.updateEditorTextSizeSp(it.toInt()) },
                valueRange = 14f..28f,
                steps = 13
            )
            Spacer(Modifier.height(12.dp))
            Text("Line Spacing: ${settings.editorLineSpacingPercent}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorLineSpacingPercent.toFloat(),
                onValueChange = { viewModel.updateEditorLineSpacingPercent(it.toInt()) },
                valueRange = 100f..220f,
                steps = 11
            )
            Spacer(Modifier.height(12.dp))
            Text("Letter Spacing: ${settings.editorLetterSpacingTenthSp / 10f} sp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorLetterSpacingTenthSp.toFloat(),
                onValueChange = { viewModel.updateEditorLetterSpacingTenthSp(it.toInt()) },
                valueRange = 0f..12f,
                steps = 11
            )
            Spacer(Modifier.height(20.dp))
            Text("Font", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                EditorFontPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.editorFontPreset == preset,
                        onClick = { viewModel.updateEditorFontPreset(preset) },
                        label = { Text(preset.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingSwitch(
                title = "Show Markdown Markers",
                description = "Show raw Markdown markers and disable rendered Markdown while editing.",
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
                title = { Text("Knowledge Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                title = "Enable Knowledge Injection",
                description = "Allow matched knowledge cards to be included in AI requests.",
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
                label = { Text("Per-request Knowledge Limit") },
                supportingText = {
                    Text("If recognized knowledge exceeds this number, the editor will ask before sending all matches.")
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
                title = { Text("AI Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text("Service Presets", style = MaterialTheme.typography.titleSmall)
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
                                label = "New Preset",
                                provider = "OpenAI",
                                baseUrl = "https://api.openai.com/v1/chat/completions",
                                model = "gpt-4o-mini"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add Preset")
                }
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = { viewModel.removeAiServicePreset(selectedPreset.id) },
                    enabled = presets.size > 1,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete Current")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Provider Templates", style = MaterialTheme.typography.titleSmall)
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
                label = { Text("Preset Name") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = selectedPreset.provider,
                onValueChange = { value -> updateSelectedPreset { copy(provider = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Provider") },
                supportingText = { Text("Use Fake for local simulation. Any other provider uses an OpenAI-compatible HTTP API.") },
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
                            contentDescription = if (showApiKey) "Hide API Key" else "Show API Key"
                        )
                    }
                },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.testConnection(selectedPreset.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("Test API Connection")
            }
            testStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            Text("Usage Binding", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            PresetUsageSelector(
                title = "Auto-completion",
                presets = presets,
                selectedId = settings.autoCompletionPresetId,
                onSelect = viewModel::updateAutoCompletionPresetId
            )
            PresetUsageSelector(
                title = "Manual completion",
                presets = presets,
                selectedId = settings.manualCompletionPresetId,
                onSelect = viewModel::updateManualCompletionPresetId
            )
            PresetUsageSelector(
                title = "AI tools",
                presets = presets,
                selectedId = settings.aiToolPresetId,
                onSelect = viewModel::updateAiToolPresetId
            )
            Spacer(Modifier.height(20.dp))
            SettingSwitch(
                title = "Auto-completion",
                description = "Show one suggested continuation after typing stops.",
                checked = settings.autoCompletionEnabled,
                onCheckedChange = viewModel::updateAutoCompletionEnabled
            )
            if (settings.autoCompletionEnabled) {
                SettingSwitch(
                    title = "Prefer Chinese text for auto-completion",
                    description = "Only request auto-completion when the text before the cursor contains Chinese.",
                    checked = settings.preferChineseAutoCompletion,
                    onCheckedChange = viewModel::updatePreferChineseAutoCompletion
                )
                SettingSwitch(
                    title = "Skip blank lines",
                    description = "Do not auto-complete when the current line is blank.",
                    checked = settings.skipBlankLineAutoCompletion,
                    onCheckedChange = viewModel::updateSkipBlankLineAutoCompletion
                )
                SettingSwitch(
                    title = "Only after content changes",
                    description = "Moving the cursor alone will not trigger auto-completion.",
                    checked = settings.autoCompleteOnlyOnContentChange,
                    onCheckedChange = viewModel::updateAutoCompleteOnlyOnContentChange
                )
            }
            SettingSwitch(
                title = "Show AI error toasts",
                description = "Show a snackbar when completion fails or returns nothing useful.",
                checked = settings.showCompletionErrorToast,
                onCheckedChange = viewModel::updateShowCompletionErrorToast
            )
            SettingSwitch(
                title = "Allow full-note context",
                description = "If off, only a bounded window around the cursor is sent.",
                checked = settings.useFullNoteContext,
                onCheckedChange = viewModel::updateUseFullNoteContext
            )
            if (!settings.useFullNoteContext) {
                Spacer(Modifier.height(12.dp))
                Text("Lines before cursor: ${settings.completionBeforeLineCount}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = settings.completionBeforeLineCount.toFloat(),
                    onValueChange = { viewModel.updateCompletionBeforeLineCount(it.toInt()) },
                    valueRange = 0f..20f,
                    steps = 19
                )
                Text("Lines after cursor: ${settings.completionAfterLineCount}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = settings.completionAfterLineCount.toFloat(),
                    onValueChange = { viewModel.updateCompletionAfterLineCount(it.toInt()) },
                    valueRange = 0f..20f,
                    steps = 19
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("Completion delay: ${settings.completionDelayMs} ms", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.completionDelayMs.toFloat(),
                onValueChange = { viewModel.updateCompletionDelayMs(it.toLong()) },
                valueRange = 300f..1500f,
                steps = 11
            )
            Text("Max completion length: ${settings.maxCompletionLength} chars", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.maxCompletionLength.toFloat(),
                onValueChange = { viewModel.updateMaxCompletionLength(it.toInt()) },
                valueRange = 10f..80f,
                steps = 6
            )
            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Privacy note: when using a real API, the app sends nearby text or the selected text to the chosen provider. API keys are stored on the local device.",
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
                title = { Text("AI Debug Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = AiDebugLogStore::clear) {
                        Text("Clear")
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
                Text("No logs yet. This screen only shows AI calls from the current app session.")
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
