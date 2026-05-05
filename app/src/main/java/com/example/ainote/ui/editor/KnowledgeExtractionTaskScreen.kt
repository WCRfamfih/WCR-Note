package com.example.ainote.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.domain.model.KnowledgeTargetSummary
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeExtractionTaskScreen(
    noteId: Long,
    initialMaterial: String,
    noteRepository: NoteRepository,
    aiRepository: AiRepository,
    settingsDataStore: SettingsDataStore,
    onOpenKnowledge: (Long) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: KnowledgeExtractionTaskViewModel = viewModel(
        factory = KnowledgeExtractionTaskViewModel.Factory(
            noteId = noteId,
            initialMaterial = initialMaterial,
            noteRepository = noteRepository,
            aiRepository = aiRepository
        )
    )
    val state by viewModel.uiState.collectAsState()
    val availableFolders by settingsDataStore.knowledgeFolders
        .map { stored -> (stored + state.recentTargets.map { it.folderName }).map(String::trim).filter(String::isNotBlank).distinct() }
        .collectAsState(initial = emptyList())
    var materialExpanded by remember { mutableStateOf(false) }
    var draftExpanded by remember { mutableStateOf(true) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var selectedFolderName by remember { mutableStateOf("") }

    if (showSearchSheet) {
        ModalBottomSheet(onDismissRequest = { showSearchSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("\u641c\u7d22\u77e5\u8bc6") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                if (state.searchResults.isEmpty()) {
                    Text(
                        text = if (state.searchQuery.isBlank()) "\u8f93\u5165\u5173\u952e\u5b57\u540e\u641c\u7d22\u5355\u4e2a\u77e5\u8bc6\u5361\u7247\u3002" else "\u6ca1\u6709\u627e\u5230\u5339\u914d\u7684\u77e5\u8bc6\u3002",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(state.searchResults, key = { it.id }) { target ->
                            KnowledgeTargetRow(
                                target = target,
                                selected = state.selectedTarget?.id == target.id,
                                onClick = {
                                    viewModel.selectTarget(target)
                                    showSearchSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u63d0\u53d6/\u66f4\u65b0\u5230\u77e5\u8bc6\u5e93") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.selectedTarget?.let { "\u5f53\u524d\u5c06\u66f4\u65b0\uff1a${it.title}" } ?: "\u672a\u9009\u62e9\u77e5\u8bc6\uff0c\u786e\u8ba4\u540e\u5c06\u521b\u5efa\u65b0\u77e5\u8bc6",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = { if (!state.completed) showSearchSheet = true },
                        enabled = !state.completed,
                        label = { Text("\u641c\u7d22\u77e5\u8bc6") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                    if (state.selectedTarget != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { viewModel.selectTarget(null) },
                            enabled = !state.completed
                        ) {
                            Text("\u6e05\u9664")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.instruction,
                    onValueChange = viewModel::updateInstruction,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.completed && !state.sending,
                    label = { Text("\u6307\u4ee4") },
                    placeholder = { Text("\u4f8b\u5982\uff1a\u63d0\u70bc\u4e3a\u4e00\u6761\u77ed\u77e5\u8bc6\uff0c\u7a81\u51fa\u7ed3\u8bba\u548c\u524d\u63d0") },
                    minLines = 3,
                    maxLines = 5
                )
                if (state.selectedTarget == null && availableFolders.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "\u521b\u5efa\u540e\u79fb\u5165\u6587\u4ef6\u5939",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFolderName.isBlank(),
                            onClick = { selectedFolderName = "" },
                            enabled = !state.completed && !state.sending,
                            label = { Text("\u672a\u5206\u7c7b") }
                        )
                        availableFolders.forEach { folder ->
                            FilterChip(
                                selected = selectedFolderName == folder,
                                onClick = { selectedFolderName = folder },
                                enabled = !state.completed && !state.sending,
                                label = { Text(folder, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.sending) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    TextButton(
                        onClick = viewModel::send,
                        enabled = !state.completed && !state.sending && state.instruction.trim().isNotBlank() && state.draft == null
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Text("\u53d1\u9001", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
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
                MessageCard(
                    title = "\u4efb\u52a1\u8bf4\u660e",
                    body = "\u8f93\u5165\u6307\u4ee4\u540e\uff0c\u7cfb\u7edf\u4f1a\u57fa\u4e8e\u5f53\u524d\u6587\u6863\u6750\u6599\u751f\u6210\u4e00\u6761\u77e5\u8bc6\u8349\u6848\uff0c\u7136\u540e\u7531\u4f60\u786e\u8ba4\u521b\u5efa\u6216\u8986\u76d6\u3002"
                )
            }
            item {
                ExpandableTextCard(
                    title = "\u5f85\u53d1\u9001\u6587\u672c\u6750\u6599",
                    text = state.sourceMaterial,
                    expanded = materialExpanded,
                    onToggle = { materialExpanded = !materialExpanded }
                )
            }
            if (state.recentTargets.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("\u6700\u8fd1\u77e5\u8bc6", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.recentTargets.forEach { target ->
                                    FilterChip(
                                        selected = state.selectedTarget?.id == target.id,
                                        onClick = { viewModel.selectTarget(if (state.selectedTarget?.id == target.id) null else target) },
                                        enabled = !state.completed,
                                        label = { Text(target.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            state.statusMessage?.let { message ->
                item {
                    MessageCard(title = "\u72b6\u6001", body = message)
                }
            }
            state.errorMessage?.let { message ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("\u9519\u8bef", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(6.dp))
                            Text(message, color = MaterialTheme.colorScheme.error)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = viewModel::dismissError) {
                                    Text("\u5173\u95ed")
                                }
                            }
                        }
                    }
                }
            }
            state.draft?.let { draft ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (draft.targetKnowledgeId == null) "\u65b0\u77e5\u8bc6\u9884\u89c8" else "\u66f4\u65b0\u77e5\u8bc6\u9884\u89c8",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = draft.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { draftExpanded = !draftExpanded }) {
                                    Icon(
                                        imageVector = if (draftExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (draftExpanded) "\u6536\u8d77" else "\u5c55\u5f00"
                                    )
                                }
                            }
                            if (draftExpanded) {
                                Spacer(Modifier.height(8.dp))
                                Text(draft.content, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = viewModel::retry,
                                    enabled = !state.completed && !state.sending
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Text("\u91cd\u8bd5", modifier = Modifier.padding(start = 6.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = { viewModel.confirm(selectedFolderName) },
                                    enabled = !state.completed && !state.sending
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Text(
                                        if (draft.targetKnowledgeId == null) "\u786e\u8ba4\u521b\u5efa" else "\u786e\u8ba4\u66f4\u65b0",
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }
                            val completedKnowledgeId = state.completedKnowledgeId
                            if (state.completed && completedKnowledgeId != null) {
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { onOpenKnowledge(completedKnowledgeId) }) {
                                        Text("\u6253\u5f00\u5bf9\u5e94\u77e5\u8bc6")
                                    }
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

@Composable
private fun ExpandableTextCard(
    title: String,
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "\u6536\u8d77" else "\u5c55\u5f00"
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = text.ifBlank { "\u6682\u65e0\u6750\u6599" },
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MessageCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun KnowledgeTargetRow(
    target: KnowledgeTargetSummary,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(target.title) },
        supportingContent = {
            Text(target.folderName.ifBlank { "\u672a\u5206\u7c7b" })
        },
        trailingContent = {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null)
            }
        }
    )
}
