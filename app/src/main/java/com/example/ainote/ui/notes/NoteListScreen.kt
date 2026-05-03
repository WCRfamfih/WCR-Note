package com.example.ainote.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    repository: NoteRepository,
    settingsDataStore: SettingsDataStore,
    onOpenNote: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel: NoteListViewModel = viewModel(
        factory = NoteListViewModel.Factory(repository, settingsDataStore)
    )
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    var showFolderManager by remember { mutableStateOf(false) }
    var actionNote by remember { mutableStateOf<Note?>(null) }
    var moveNote by remember { mutableStateOf<Note?>(null) }
    var deleteNote by remember { mutableStateOf<Note?>(null) }

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
            onDismiss = { moveNote = null },
            onMove = { folderName ->
                viewModel.moveNoteToFolder(note.id, folderName)
                moveNote = null
            }
        )
    }

    deleteNote?.let { note ->
        ConfirmDialog(
            title = "删除笔记",
            text = "确定删除「${note.displayTitle}」吗？",
            confirmText = "删除",
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
                        Icon(Icons.Default.Folder, contentDescription = "文件夹")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createNote(onOpenNote) }) {
                Icon(Icons.Default.Add, contentDescription = "新建笔记")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text("笔记", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("搜索笔记") },
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
                    text = if (query.isBlank()) "这里还没有笔记。" else "没有找到相关笔记。",
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
            title = "新建文件夹",
            initialName = "",
            confirmText = "创建",
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                onCreateFolder(it)
                showCreateDialog = false
            }
        )
    }

    editingFolder?.let { folder ->
        FolderNameDialog(
            title = "重命名文件夹",
            initialName = folder.label,
            confirmText = "保存",
            onDismiss = { editingFolder = null },
            onConfirm = {
                onRenameFolder(folder.name.orEmpty(), it)
                editingFolder = null
            }
        )
    }

    deletingFolder?.let { folder ->
        ConfirmDialog(
            title = "删除文件夹",
            text = "确定删除「${folder.label}」吗？文件夹内笔记会移入未分类。",
            confirmText = "删除",
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
                title = { Text("文件夹") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val selected = folders.firstOrNull { it.name == activeFolderName && it.canEdit }
                    if (selected != null) {
                        IconButton(onClick = { deletingFolder = selected }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除文件夹")
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
                    onClick = {
                        activeFolderName = folder.name?.takeIf { folder.canEdit }
                    },
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
                        Text("新建文件夹")
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
                    Icon(Icons.Default.Edit, contentDescription = "重命名文件夹")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除文件夹")
                }
            } else if (active) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(note: Note, onOpen: () -> Unit, onLongPress: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = note.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = note.summary.ifBlank { "空白笔记" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
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
        ActionItem(Icons.Default.FileCopy, "复制", onCopy)
        ActionItem(Icons.AutoMirrored.Filled.DriveFileMove, "移动到文件夹", onMove)
        ActionItem(Icons.Default.Delete, "删除", onDelete)
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("取消")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
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
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动笔记") },
        text = {
            Column {
                Text("选择「${note.displayTitle}」的新文件夹")
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
                Text("取消")
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
                placeholder = { Text("文件夹名称") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
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
                Text("取消")
            }
        }
    )
}

private fun formatTime(timeMillis: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
}
