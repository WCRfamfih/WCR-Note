package com.example.ainote.ui.settings

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.settings.AiProviderPreset
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dataStore: SettingsDataStore,
    aiRepository: AiRepository,
    onOpenAiSettings: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(dataStore, aiRepository))
    val settings by viewModel.settings.collectAsState()

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
            Text("\u6587\u5b57\u5927\u5c0f\uff1a${settings.editorTextSizeSp} sp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.editorTextSizeSp.toFloat(),
                onValueChange = { viewModel.updateEditorTextSizeSp(it.toInt()) },
                valueRange = 14f..28f,
                steps = 13
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
                AiProviderPreset.All.forEach { preset ->
                    ProviderPresetChip(
                        preset = preset,
                        selected = settings.apiProvider.equals(preset.provider, ignoreCase = true) &&
                            settings.apiBaseUrl == preset.baseUrl &&
                            settings.apiModel == preset.model,
                        onClick = { viewModel.applyProviderPreset(preset) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = settings.apiProvider,
                onValueChange = viewModel::updateApiProvider,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Provider") },
                supportingText = { Text("\u586b Fake \u4f7f\u7528\u672c\u5730\u6a21\u62df\uff1b\u586b OpenAI \u6216\u5176\u4ed6\u540d\u79f0\u5219\u4f7f\u7528 OpenAI-compatible HTTP API\u3002") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = settings.apiBaseUrl,
                onValueChange = viewModel::updateApiBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Base URL") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = settings.apiModel,
                onValueChange = viewModel::updateApiModel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = viewModel::updateApiKey,
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
            Button(onClick = viewModel::testConnection, modifier = Modifier.fillMaxWidth()) {
                Text("\u6d4b\u8bd5 API \u8fde\u63a5")
            }
            testStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            SettingSwitch(
                title = "\u81ea\u52a8\u8865\u5168",
                description = "\u505c\u6b62\u8f93\u5165\u540e\u663e\u793a\u4e00\u6761\u5019\u9009\u8865\u5168\uff0c\u63a5\u53d7\u540e\u624d\u5199\u5165\u6b63\u6587\u3002",
                checked = settings.autoCompletionEnabled,
                onCheckedChange = viewModel::updateAutoCompletionEnabled
            )
            SettingSwitch(
                title = "\u4f18\u5148\u4e2d\u6587\u6587\u672c\u81ea\u52a8\u8865\u5168",
                description = "\u5f00\u542f\u65f6\u53ea\u5728\u5149\u6807\u524d\u5305\u542b\u4e2d\u6587\u65f6\u81ea\u52a8\u8865\u5168\uff1b\u5173\u95ed\u540e\u5176\u4ed6\u8bed\u8a00\u4e5f\u80fd\u53c2\u4e0e\u3002",
                checked = settings.preferChineseAutoCompletion,
                onCheckedChange = viewModel::updatePreferChineseAutoCompletion
            )
            SettingSwitch(
                title = "\u5141\u8bb8\u6574\u7bc7\u4e0a\u4e0b\u6587",
                description = "\u5173\u95ed\u65f6\u53ea\u53d1\u9001\u5149\u6807\u9644\u8fd1\u5185\u5bb9\u3002",
                checked = settings.useFullNoteContext,
                onCheckedChange = viewModel::updateUseFullNoteContext
            )
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
