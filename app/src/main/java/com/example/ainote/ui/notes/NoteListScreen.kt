package com.example.ainote.ui.notes

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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

data class CardOpenTransitionOrigin(
    val pivotXFraction: Float,
    val pivotYFraction: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    repository: NoteRepository,
    settingsDataStore: SettingsDataStore,
    contentType: NoteContentType = NoteContentType.Note,
    onOpenNote: (Long, CardOpenTransitionOrigin?) -> Unit,
    onOpenNotes: () -> Unit = {},
    onOpenKnowledge: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val viewModel: NoteListViewModel = viewModel(
        factory = NoteListViewModel.Factory(repository, settingsDataStore, contentType)
    )
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val context = LocalContext.current

    var showFolderManager by remember { mutableStateOf(false) }
    var actionNote by remember { mutableStateOf<Note?>(null) }
    var moveNote by remember { mutableStateOf<Note?>(null) }
    var deleteNote by remember { mutableStateOf<Note?>(null) }
    var pendingCoverNoteId by remember { mutableStateOf<Long?>(null) }

    val isKnowledge = contentType == NoteContentType.Knowledge
    val itemLabel = if (isKnowledge) "知识" else "笔记"

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val noteId = pendingCoverNoteId ?: return@rememberLauncherForActivityResult
        pendingCoverNoteId = null
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.updateNoteCover(noteId, uri.toString())
    }

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
            },
            onAddCover = {
                pendingCoverNoteId = note.id
                actionNote = null
                coverPicker.launch(arrayOf("image/*"))
            },
            onReplaceCover = {
                pendingCoverNoteId = note.id
                actionNote = null
                coverPicker.launch(arrayOf("image/*"))
            },
            onRemoveCover = {
                viewModel.updateNoteCover(note.id, null)
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
            title = "删除$itemLabel",
            text = "确定删除“${note.displayTitle}”吗？",
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
            FloatingActionButton(onClick = { viewModel.createNote { id -> onOpenNote(id, null) } }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !isKnowledge,
                    onClick = onOpenNotes,
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text("笔记") }
                )
                NavigationBarItem(
                    selected = isKnowledge,
                    onClick = onOpenKnowledge,
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("知识库") }
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
                text = if (isKnowledge) "知识库" else "笔记",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(if (isKnowledge) "搜索知识" else "搜索笔记") },
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
                        if (isKnowledge) "这里还没有知识。" else "这里还没有笔记。"
                    } else {
                        if (isKnowledge) "没有找到相关知识。" else "没有找到相关笔记。"
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
                            onOpen = { origin -> onOpenNote(note.id, origin) },
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
            text = "确定删除“${folder.label}”吗？内容会移入未分类。",
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
                    onClick = { activeFolderName = folder.name?.takeIf { folder.canEdit } },
                    onRename = if (active) ({ editingFolder = folder }) else null,
                    onDelete = if (active) ({ deletingFolder = folder }) else null
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
private fun NoteCard(
    note: Note,
    contentType: NoteContentType,
    onOpen: (CardOpenTransitionOrigin?) -> Unit,
    onLongPress: () -> Unit
) {
    val isKnowledge = contentType == NoteContentType.Knowledge
    val view = LocalView.current
    var transitionOrigin by remember { mutableStateOf<CardOpenTransitionOrigin?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val rootWidth = view.width.toFloat().coerceAtLeast(1f)
                val rootHeight = view.height.toFloat().coerceAtLeast(1f)
                transitionOrigin = CardOpenTransitionOrigin(
                    pivotXFraction = ((bounds.left + bounds.right) * 0.5f / rootWidth).coerceIn(0f, 1f),
                    pivotYFraction = ((bounds.top + bounds.bottom) * 0.5f / rootHeight).coerceIn(0f, 1f)
                )
            }
            .combinedClickable(onClick = { onOpen(transitionOrigin) }, onLongClick = onLongPress),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            if (note.coverImageUri.isNotBlank()) {
                CoverImage(note.coverImageUri)
            }
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
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text("全局") },
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                            Text(
                                text = "◆",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (note.coverImageUri.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    if (isKnowledge) {
                        Text(
                            text = note.summary.ifBlank { "空白知识" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = markdownAnnotatedString(
                                note.summary.ifBlank { "空白笔记" },
                                MaterialTheme.typography.bodyMedium,
                                MaterialTheme.colorScheme
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteActionSheet(
    note: Note,
    itemLabel: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onAddCover: () -> Unit,
    onReplaceCover: () -> Unit,
    onRemoveCover: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = note.displayTitle,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        ActionItem(Icons.Default.FileCopy, "复制", onCopy)
        ActionItem(Icons.AutoMirrored.Filled.DriveFileMove, "移动到文件夹", onMove)
        if (note.coverImageUri.isBlank()) {
            ActionItem(Icons.Default.Image, "添加封面", onAddCover)
        } else {
            ActionItem(Icons.Default.Image, "更换封面", onReplaceCover)
            ActionItem(Icons.Default.Delete, "移除封面", onRemoveCover)
        }
        ActionItem(Icons.Default.Delete, "删除$itemLabel", onDelete)
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
        title = { Text("移动$itemLabel") },
        text = {
            Column {
                Text("选择“${note.displayTitle}”的新文件夹")
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

@Composable
private fun CoverImage(uri: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }
    val image = bitmap
    val ratio = image?.let { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) } ?: (16f / 9f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = "封面",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
