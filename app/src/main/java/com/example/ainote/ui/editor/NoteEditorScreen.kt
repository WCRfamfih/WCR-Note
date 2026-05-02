package com.example.ainote.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.ui.components.AiCompletionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long,
    noteRepository: NoteRepository,
    aiRepository: AiRepository,
    settingsDataStore: SettingsDataStore,
    onBack: () -> Unit
) {
    val viewModel: NoteEditorViewModel = viewModel(
        factory = NoteEditorViewModel.Factory(noteId, noteRepository, aiRepository, settingsDataStore)
    )
    val state by viewModel.uiState.collectAsState()

    BackHandler {
        viewModel.saveNow(onBack)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑笔记") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveNow(onBack) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.requestCompletionNow() }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI 补全")
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
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("标题") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = state.content,
                onValueChange = { value: TextFieldValue -> viewModel.updateContent(value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("开始写点什么...") }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${state.wordCount} 字，自动保存",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (state.completion.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.completion.suggestion?.let { suggestion ->
                AiCompletionCard(
                    text = suggestion,
                    onAccept = viewModel::acceptCompletion,
                    onDismiss = viewModel::dismissCompletion
                )
            }
        }
    }
}
