package com.example.ainote.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.ainote.data.debug.AiDebugLogStore
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.domain.model.NoteContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DocumentBackupRepository(
    context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    private val resolver = context.contentResolver

    suspend fun backupNote(
        id: Long,
        title: String,
        content: String,
        updatedAt: Long,
        contentType: NoteContentType
    ) = withContext(Dispatchers.IO) {
        val directoryUri = settingsDataStore.settings.first().documentDirectoryUri
        if (directoryUri.isBlank()) return@withContext
        runCatching {
            val treeUri = Uri.parse(directoryUri)
            val targetTree = if (contentType == NoteContentType.Knowledge) {
                findOrCreateDirectory(treeUri, KNOWLEDGE_DIRECTORY)
            } else {
                treeUri
            }
            val fileName = if (contentType == NoteContentType.Knowledge) "knowledge-$id.txt" else "note-$id.md"
            val documentUri = findOrCreateFile(targetTree, fileName, contentType)
            resolver.openOutputStream(documentUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                if (contentType == NoteContentType.Knowledge) {
                    writer.write(title.ifBlank { "\u672a\u547d\u540d\u77e5\u8bc6" })
                } else {
                    writer.write("# ")
                    writer.write(title.ifBlank { "\u672a\u547d\u540d\u7b14\u8bb0" })
                }
                writer.write("\n\n")
                writer.write(content)
                writer.write("\n\n<!-- updatedAt=$updatedAt -->\n")
            } ?: error("Cannot open output stream for $fileName")
        }.onFailure { error ->
            AiDebugLogStore.add("Document backup failed", error.message.orEmpty())
        }
    }

    suspend fun readBackups(directoryUri: String): List<DocumentBackupNote> = withContext(Dispatchers.IO) {
        if (directoryUri.isBlank()) return@withContext emptyList()
        runCatching {
            val treeUri = Uri.parse(directoryUri)
            val notes = listFiles(treeUri)
                .filter { it.displayName.startsWith("note-") && it.displayName.endsWith(".md") }
                .mapNotNull { file ->
                    val id = file.displayName.removePrefix("note-").removeSuffix(".md").toLongOrNull()
                        ?: return@mapNotNull null
                    val raw = resolver.openInputStream(file.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: return@mapNotNull null
                    parseBackupNote(id, raw)
                }
            val knowledge = findDirectory(treeUri, KNOWLEDGE_DIRECTORY)?.let { knowledgeTree ->
                listFiles(knowledgeTree)
                    .filter { it.displayName.startsWith("knowledge-") && it.displayName.endsWith(".txt") }
                    .mapNotNull { file ->
                        val id = file.displayName.removePrefix("knowledge-").removeSuffix(".txt").toLongOrNull()
                            ?: return@mapNotNull null
                        val raw = resolver.openInputStream(file.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?: return@mapNotNull null
                        parseBackupKnowledge(id, raw)
                    }
            }.orEmpty()
            notes + knowledge
        }.onFailure { error ->
            AiDebugLogStore.add("Document import failed", error.message.orEmpty())
        }.getOrDefault(emptyList())
    }

    private fun findOrCreateFile(treeUri: Uri, fileName: String, contentType: NoteContentType): Uri {
        findFile(treeUri, fileName)?.let { return it }
        val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
        val mimeType = if (contentType == NoteContentType.Knowledge) "text/plain" else "text/markdown"
        return DocumentsContract.createDocument(resolver, parentUri, mimeType, fileName)
            ?: error("Cannot create $fileName")
    }

    private fun findOrCreateDirectory(treeUri: Uri, directoryName: String): Uri {
        findDirectory(treeUri, directoryName)?.let { return it }
        val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
        val documentUri = DocumentsContract.createDocument(
            resolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            directoryName
        ) ?: error("Cannot create $directoryName")
        return DocumentsContract.buildTreeDocumentUri(treeUri.authority, DocumentsContract.getDocumentId(documentUri))
    }

    private fun findFile(treeUri: Uri, fileName: String): Uri? {
        return listFiles(treeUri).firstOrNull { it.displayName == fileName }?.uri
    }

    private fun findDirectory(treeUri: Uri, directoryName: String): Uri? {
        val documentUri = listFiles(treeUri).firstOrNull { it.displayName == directoryName }?.uri ?: return null
        return DocumentsContract.buildTreeDocumentUri(treeUri.authority, DocumentsContract.getDocumentId(documentUri))
    }

    private fun listFiles(treeUri: Uri): List<DocumentFileInfo> {
        val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        val files = mutableListOf<DocumentFileInfo>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                files += DocumentFileInfo(
                    displayName = cursor.getString(nameIndex),
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                )
            }
        }
        return files
    }

    private fun parseBackupNote(id: Long, raw: String): DocumentBackupNote {
        val withoutMetadata = raw.replace(Regex("\\n*<!--\\s*updatedAt=\\d+\\s*-->\\s*$"), "").trimEnd()
        val lines = withoutMetadata.lines()
        val title = lines.firstOrNull()
            ?.takeIf { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            .orEmpty()
        val content = if (lines.firstOrNull()?.startsWith("# ") == true) {
            lines.drop(1).dropWhile { it.isBlank() }.joinToString("\n")
        } else {
            withoutMetadata
        }
        return DocumentBackupNote(
            id = id,
            title = title.ifBlank { content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(24).orEmpty() },
            content = content,
            contentType = NoteContentType.Note
        )
    }

    private fun parseBackupKnowledge(id: Long, raw: String): DocumentBackupNote {
        val withoutMetadata = raw.replace(Regex("\\n*<!--\\s*updatedAt=\\d+\\s*-->\\s*$"), "").trimEnd()
        val lines = withoutMetadata.lines()
        val title = lines.firstOrNull()?.trim().orEmpty()
        val content = lines.drop(1).dropWhile { it.isBlank() }.joinToString("\n")
        return DocumentBackupNote(
            id = id,
            title = title.ifBlank { content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(24).orEmpty() },
            content = content,
            contentType = NoteContentType.Knowledge
        )
    }

    private companion object {
        const val KNOWLEDGE_DIRECTORY = "knowledge"
    }
}

data class DocumentBackupNote(
    val id: Long,
    val title: String,
    val content: String,
    val contentType: NoteContentType = NoteContentType.Note
)

private data class DocumentFileInfo(
    val displayName: String,
    val uri: Uri
)
