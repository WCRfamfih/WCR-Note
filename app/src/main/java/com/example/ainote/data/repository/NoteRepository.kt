package com.example.ainote.data.repository

import com.example.ainote.data.local.NoteDao
import com.example.ainote.data.local.NoteEntity
import com.example.ainote.domain.model.Note
import com.example.ainote.domain.model.NoteContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(
    private val dao: NoteDao,
    private val backupRepository: DocumentBackupRepository? = null
) {
    fun observeNotes(contentType: NoteContentType = NoteContentType.Note): Flow<List<Note>> {
        return dao.observeNotesByType(contentType.storageValue).map { notes -> notes.map { it.toDomain() } }
    }

    fun searchNotes(
        query: String,
        folderName: String? = null,
        contentType: NoteContentType = NoteContentType.Note
    ): Flow<List<Note>> {
        return dao.searchNotes(query.trim(), folderName?.trim()?.ifBlank { "" }, contentType.storageValue)
            .map { notes -> notes.map { it.toDomain() } }
    }

    fun observeNote(id: Long): Flow<Note?> = dao.observeNote(id).map { it?.toDomain() }

    suspend fun createNote(folderName: String = "", contentType: NoteContentType = NoteContentType.Note): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            NoteEntity(
                title = "",
                content = "",
                createdAt = now,
                updatedAt = now,
                folderName = folderName.trim(),
                contentType = contentType.storageValue
            )
        )
    }

    suspend fun saveNote(id: Long, title: String, content: String, createdAt: Long, pinned: Boolean) {
        val updatedAt = System.currentTimeMillis()
        val resolvedTitle = title.ifBlank { extractTitle(content) }
        val existing = dao.getNote(id)
        val contentType = NoteContentType.from(existing?.contentType)
        dao.update(
            NoteEntity(
                id = id,
                title = resolvedTitle,
                content = content,
                createdAt = createdAt,
                updatedAt = updatedAt,
                folderName = existing?.folderName ?: dao.getFolderName(id).orEmpty(),
                contentType = contentType.storageValue,
                pinned = pinned
            )
        )
        backupRepository?.backupNote(id, resolvedTitle, content, updatedAt, contentType)
    }

    suspend fun deleteNote(id: Long) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun deleteFolder(folderName: String, contentType: NoteContentType = NoteContentType.Note) {
        dao.clearFolder(folderName.trim(), contentType.storageValue, System.currentTimeMillis())
    }

    suspend fun renameFolder(oldName: String, newName: String, contentType: NoteContentType = NoteContentType.Note) {
        val trimmedOldName = oldName.trim()
        val trimmedNewName = newName.trim()
        if (trimmedOldName.isBlank() || trimmedNewName.isBlank() || trimmedOldName == trimmedNewName) return
        dao.renameFolder(trimmedOldName, trimmedNewName, contentType.storageValue, System.currentTimeMillis())
    }

    suspend fun moveNoteToFolder(id: Long, folderName: String) {
        dao.moveNote(id, folderName.trim(), System.currentTimeMillis())
    }

    suspend fun copyNote(id: Long): Long? {
        val source = dao.getNote(id) ?: return null
        val now = System.currentTimeMillis()
        return dao.insert(
            source.copy(
                id = 0,
                title = if (source.title.isBlank()) "" else "${source.title} 副本",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun importBackupsFromDirectory(directoryUri: String): Int {
        val backupRepository = backupRepository ?: return 0
        val backups = backupRepository.readBackups(directoryUri)
        val now = System.currentTimeMillis()
        backups.forEach { backup ->
            dao.insert(
                NoteEntity(
                    id = backup.id,
                    title = backup.title.ifBlank { extractTitle(backup.content) },
                    content = backup.content,
                    createdAt = now,
                    updatedAt = now,
                    contentType = backup.contentType.storageValue
                )
            )
        }
        return backups.size
    }

    suspend fun getKnowledgeEntries(): List<Note> {
        return dao.getNotesByType(NoteContentType.Knowledge.storageValue).map { it.toDomain() }
    }

    private fun NoteEntity.toDomain(): Note = Note(
        id = id,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        folderName = folderName,
        contentType = NoteContentType.from(contentType),
        pinned = pinned
    )

    private fun extractTitle(content: String): String {
        return content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(24).orEmpty()
    }
}
