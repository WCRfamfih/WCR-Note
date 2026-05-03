package com.example.ainote.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.ainote.data.debug.AiDebugLogStore
import com.example.ainote.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DocumentBackupRepository(
    context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    private val resolver = context.contentResolver

    suspend fun backupNote(id: Long, title: String, content: String, updatedAt: Long) = withContext(Dispatchers.IO) {
        val directoryUri = settingsDataStore.settings.first().documentDirectoryUri
        if (directoryUri.isBlank()) return@withContext
        runCatching {
            val treeUri = Uri.parse(directoryUri)
            val fileName = "note-$id.md"
            val documentUri = findOrCreateFile(treeUri, fileName)
            resolver.openOutputStream(documentUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write("# ")
                writer.write(title.ifBlank { "未命名笔记" })
                writer.write("\n\n")
                writer.write(content)
                writer.write("\n\n<!-- updatedAt=$updatedAt -->\n")
            } ?: error("Cannot open output stream for $fileName")
        }.onFailure { error ->
            AiDebugLogStore.add("Document backup failed", error.message.orEmpty())
        }
    }

    private fun findOrCreateFile(treeUri: Uri, fileName: String): Uri {
        findFile(treeUri, fileName)?.let { return it }
        val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
        return DocumentsContract.createDocument(resolver, parentUri, "text/markdown", fileName)
            ?: error("Cannot create $fileName")
    }

    private fun findFile(treeUri: Uri, fileName: String): Uri? {
        val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == fileName) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                }
            }
        }
        return null
    }
}
