package com.example.ainote.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.domain.model.Note
import com.example.ainote.domain.model.NoteContentType
import com.example.ainote.ui.components.markdownAnnotatedString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    repository: NoteRepository,
    settingsDataStore: SettingsDataStore,
    contentType: NoteContentType = NoteContentType.Note,
    onOpenNote: (Long) -> Unit,
    onOpenNotes: () -> Unit = {},
    onOpenKnowledge: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val viewModel: NoteListViewModel = viewModel(
        factory = NoteListViewModel.Factory(repository, settingsDataStore, contentType)
    )
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    var showFolderManager by remember { mutableStateOf(false) }
    var actionNote by remember { mutableStateOf<Note?>(null) }
    var moveNote by remember { mutableStateOf<Note?>(null) }
    var deleteNote by remember { mutableStateOf<Note?>(null) }

    val isKnowledge = contentType == NoteContentType.Knowledge
    val itemLabel = if (isKnowledge) "\u77e5\u8bc6" else "\u7b14\u8bb0"

    if (showFolderManager) {
        FolderManagerScreen(
            folders = uiState.folders,
            selectedFolder = uiState.selectedFolder,
            onCreateFolder = viewModel::createFolder,
            onRenameFolder = viewModel::renameFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onBack = { showFolderManager = false }
        )
        return
    }

    actionNote?.let { note ->
        NoteActionSheet(
            note = note,
            itemLabel = itemLabel,
            onDismiss = { actionNote = null },
            onCopy = {
                viewModel.copyNote(note.id)
                actionNote = null
            },
            onMove = {
                moveNote = note
                actionNote = null
            },
            onDelete = {
                deleteNote = note
                actionNote = null
            }
        )
    }

    moveNote?.let { note ->
        MoveNoteDialog(
            note = note,
            folders = uiState.folders,
            itemLabel = itemLabel,
            onDismiss = { moveNote = null },
            onMove = { folderName ->
                viewModel.moveNoteToFolder(note.id, folderName)
                moveNote = null
            }
        )
    }

    deleteNote?.let { note ->
        ConfirmDialog(
            title = "\u5220\u9664$itemLabel",
            text = "\u786e\u5b9a\u5220\u9664\u300c${note.displayTitle}\u300d\u5417\uff1f",
            confirmText = "\u5220\u9664",
            onDismiss = { deleteNote = null },
            onConfirm = {
                viewModel.deleteNote(note.id)
                deleteNote = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { showFolderManager = true }) {
                        Icon(Icons.Default.Folder, contentDescription = "\u6587\u4ef6\u5939")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "\u8bbe\u7f6e")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createNote(onOpenNote) }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !isKnowledge,
                    onClick = onOpenNotes,
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text("\u7b14\u8bb0") }
                )
                NavigationBarItem(
                    selected = isKnowledge,
                    onClick = onOpenKnowledge,
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("\u77e5\u8bc6\u5e93") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = if (isKnowledge) "\u77e5\u8bc6\u5e93" else "\u7b14\u8bb0",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(if (isKnowledge) "\u641c\u7d22\u77e5\u8bc6" else "\u641c\u7d22\u7b14\u8bb0") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.folders, key = { it.name ?: "__all__" }) { folder ->
                    FilterChip(
                        selected = uiState.selectedFolder == folder.name,
                        onClick = { viewModel.selectFolder(folder.name) },
                        label = { Text(folder.label) },
                        leadingIcon = if (uiState.selectedFolder == folder.name) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (uiState.notes.isEmpty()) {
                Text(
                    text = if (query.isBlank()) {
                        if (isKnowledge) "\u8fd9\u91cc\u8fd8\u6ca1\u6709\u77e5\u8bc6\u3002" else "\u8fd9\u91cc\u8fd8\u6ca1\u6709\u7b14\u8bb0\u3002"
                    } else {
                        if (isKnowledge) "\u6ca1\u6709\u627e\u5230\u76f8\u5173\u77e5\u8bc6\u3002" else "\u6ca1\u6709\u627e\u5230\u76f8\u5173\u7b14\u8bb0\u3002"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            contentType = contentType,
                            onOpen = { onOpenNote(note.id) },
                            onLongPress = { actionNote = note }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderManagerScreen(
    folders: List<FolderSummary>,
    selectedFolder: String?,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var activeFolderName by remember(selectedFolder) { mutableStateOf(selectedFolder?.takeIf { it.isNotBlank() }) }
    var editingFolder by remember { mutableStateOf<FolderSummary?>(null) }
    var deletingFolder by remember { mutableStateOf<FolderSummary?>(null) }

    if (showCreateDialog) {
        FolderNameDialog(
            title = "\u65b0\u5efa\u6587\u4ef6\u5939",
            initialName = "",
            confirmText = "\u521b\u5efa",
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                onCreateFolder(it)
                showCreateDialog = false
            }
        )
    }

    editingFolder?.let { folder ->
        FolderNameDialog(
            title = "\u91cd\u547d\u540d\u6587\u4ef6\u5939",
            initialName = folder.label,
            confirmText = "\u4fdd\u5b58",
            onDismiss = { editingFolder = null },
            onConfirm = {
                onRenameFolder(folder.name.orEmpty(), it)
                editingFolder = null
            }
        )
    }

    deletingFolder?.let { folder ->
        ConfirmDialog(
            title = "\u5220\u9664\u6587\u4ef6\u5939",
            text = "\u786e\u5b9a\u5220\u9664\u300c${folder.label}\u300d\u5417\uff1f\u5185\u5bb9\u4f1a\u79fb\u5165\u672a\u5206\u7c7b\u3002",
            confirmText = "\u5220\u9664",
            onDismiss = { deletingFolder = null },
            onConfirm = {
                onDeleteFolder(folder.name.orEmpty())
                deletingFolder = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u6587\u4ef6\u5939") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                },
                actions = {
                    val selected = folders.firstOrNull { it.name == activeFolderName && it.canEdit }
                    if (selected != null) {
                        IconButton(onClick = { deletingFolder = selected }) {
                            Icon(Icons.Default.Delete, contentDescription = "\u5220\u9664\u6587\u4ef6\u5939")
                        }
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
            items(folders, key = { it.name ?: "__all__" }) { folder ->
                val active = folder.name == activeFolderName && folder.canEdit
                FolderRow(
                    folder = folder,
                    selected = folder.name == selectedFolder,
                    active = active,
                    onClick = { activeFolderName = folder.name?.takeIf { folder.canEdit } },
                    onRename = if (active) { { editingFolder = folder } } else null,
                    onDelete = if (active) { { deletingFolder = folder } } else null
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text("\u65b0\u5efa\u6587\u4ef6\u5939")
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: FolderSummary,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = folder.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = folder.count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onDelete != null) {
                IconButton(onClick = onRename ?: {}) {
                    Icon(Icons.Default.Edit, contentDescription = "\u91cd\u547d\u540d\u6587\u4ef6\u5939")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "\u5220\u9664\u6587\u4ef6\u5939")
                }
            } else if (active) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    contentType: NoteContentType,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val isKnowledge = contentType == NoteContentType.Knowledge
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = note.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isKnowledge) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (note.isGlobalKnowledge) {
                            Text(
                                text = "全局",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        Text(
                            text = "\u25c6",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (isKnowledge) {
                Text(
                    text = note.summary.ifBlank { "\u7a7a\u767d\u77e5\u8bc6" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = markdownAnnotatedString(
                        note.summary.ifBlank { "\u7a7a\u767d\u7b14\u8bb0" },
                        MaterialTheme.typography.bodyMedium,
                        MaterialTheme.colorScheme
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = formatTime(note.updatedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteActionSheet(
    note: Note,
    itemLabel: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = note.displayTitle,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        ActionItem(Icons.Default.FileCopy, "\u590d\u5236", onCopy)
        ActionItem(Icons.AutoMirrored.Filled.DriveFileMove, "\u79fb\u52a8\u5230\u6587\u4ef6\u5939", onMove)
        ActionItem(Icons.Default.Delete, "\u5220\u9664$itemLabel", onDelete)
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("\u53d6\u6d88")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(text) }
    )
}

@Composable
private fun MoveNoteDialog(
    note: Note,
    folders: List<FolderSummary>,
    itemLabel: String,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u79fb\u52a8$itemLabel") },
        text = {
            Column {
                Text("\u9009\u62e9\u300c${note.displayTitle}\u300d\u7684\u65b0\u6587\u4ef6\u5939")
                Spacer(Modifier.height(12.dp))
                folders.filter { it.name != null }.forEach { folder ->
                    ListItem(
                        modifier = Modifier.clickable { onMove(folder.name) },
                        headlineContent = { Text(folder.label) },
                        trailingContent = { Text(folder.count.toString()) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("\u53d6\u6d88")
            }
        }
    )
}

@Composable
private fun FolderNameDialog(
    title: String,
    initialName: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("\u6587\u4ef6\u5939\u540d\u79f0") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("\u53d6\u6d88")
            }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("\u53d6\u6d88")
            }
        }
    )
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
