package com.example.ainote.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteKnowledgeScopeScreen(
    noteId: Long,
    noteRepository: NoteRepository,
    settingsDataStore: SettingsDataStore,
    onBack: () -> Unit
) {
    val viewModel: NoteKnowledgeScopeViewModel = viewModel(
        factory = NoteKnowledgeScopeViewModel.Factory(noteId, noteRepository, settingsDataStore)
    )
    val state by viewModel.uiState.collectAsState()
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u672c\u7b14\u8bb0\u77e5\u8bc6\u8bc6\u522b") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "\u8fd9\u91cc\u7684\u5f00\u5173\u53ea\u5f71\u54cd\u5f53\u524d\u7b14\u8bb0\u7684\u77e5\u8bc6\u81ea\u52a8\u8bc6\u522b\uff0c\u4e0d\u4f1a\u6539\u53d8\u5168\u5c40\u77e5\u8bc6\u5e93\u5185\u5bb9\u3002",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "\u5df2\u542f\u7528\u6587\u4ef6\u5939 ${state.enabledFolderCount}/${state.totalFolderCount}  ·  \u5df2\u751f\u6548\u5361\u7247 ${state.enabledKnowledgeCount}/${state.totalKnowledgeCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(state.folders, key = { it.folderName }) { folder ->
                val expanded = expandedFolders[folder.folderName] ?: false
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedFolders[folder.folderName] = !expanded }
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "\u5361\u7247 ${folder.cards.count { it.effectiveEnabled }}/${folder.cards.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = folder.enabled,
                                onCheckedChange = { viewModel.toggleFolder(folder.folderName, it) }
                            )
                            IconButton(onClick = { expandedFolders[folder.folderName] = !expanded }) {
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (expanded) "\u6536\u8d77" else "\u5c55\u5f00"
                                )
                            }
                        }
                        if (expanded) {
                            if (folder.cards.isEmpty()) {
                                Text(
                                    text = "\u8fd9\u4e2a\u6587\u4ef6\u5939\u8fd8\u6ca1\u6709\u77e5\u8bc6\u5361\u7247\u3002",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                folder.cards.forEach { card ->
                                    ListItem(
                                        headlineContent = { Text(card.title) },
                                        supportingContent = {
                                            val message = if (folder.enabled) {
                                                if (card.enabled) "\u5f53\u524d\u4f1a\u53c2\u4e0e\u8bc6\u522b" else "\u5df2\u5355\u72ec\u5173\u95ed"
                                            } else {
                                                "\u6240\u5728\u6587\u4ef6\u5939\u5df2\u5173\u95ed\uff0c\u5355\u5361\u5f00\u5173\u6682\u4e0d\u751f\u6548"
                                            }
                                            Text(message)
                                        },
                                        trailingContent = {
                                            Switch(
                                                checked = card.enabled,
                                                onCheckedChange = { viewModel.toggleKnowledge(folder.folderName, card.id, it) }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
