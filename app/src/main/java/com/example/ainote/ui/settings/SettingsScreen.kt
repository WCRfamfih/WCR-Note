package com.example.ainote.ui.settings

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.ainote.data.settings.SettingsDataStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dataStore: SettingsDataStore,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(dataStore))
    val settings by viewModel.settings.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
            OutlinedTextField(
                value = settings.apiProvider,
                onValueChange = viewModel::updateApiProvider,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Provider") },
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
                            contentDescription = if (showApiKey) "隐藏 API Key" else "显示 API Key"
                        )
                    }
                },
                singleLine = true
            )
            Spacer(Modifier.height(20.dp))
            SettingSwitch(
                title = "自动补全",
                description = "停止输入后显示一条候选补全，接受后才写入正文。",
                checked = settings.autoCompletionEnabled,
                onCheckedChange = viewModel::updateAutoCompletionEnabled
            )
            SettingSwitch(
                title = "允许整篇上下文",
                description = "关闭时只发送光标附近内容。",
                checked = settings.useFullNoteContext,
                onCheckedChange = viewModel::updateUseFullNoteContext
            )
            Spacer(Modifier.height(20.dp))
            Text("补全延迟：${settings.completionDelayMs} ms", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.completionDelayMs.toFloat(),
                onValueChange = { viewModel.updateCompletionDelayMs(it.toLong()) },
                valueRange = 300f..1500f,
                steps = 11
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
                    text = "隐私说明：启用 AI 功能后，应用会将光标附近文本发送到所选 AI 服务商。第一版使用 Fake 服务，不会联网；接入真实 API 后请只配置可信服务商。API Key 保存在本地设备。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
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
