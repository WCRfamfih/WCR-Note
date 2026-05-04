package com.example.ainote.data.repository

import com.example.ainote.data.local.NoteDao
import com.example.ainote.data.local.NoteEntity
import com.example.ainote.data.local.NoteKnowledgeScopeEntity
import com.example.ainote.domain.model.KnowledgeScopeSummary
import com.example.ainote.domain.model.KnowledgeTargetSummary
import com.example.ainote.domain.model.Note
import com.example.ainote.domain.model.NoteContentType
import com.example.ainote.domain.model.NoteKnowledgeScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
                contentType = contentType.storageValue,
                isGlobalKnowledge = false
            )
        )
    }

    suspend fun saveNote(
        id: Long,
        title: String,
        content: String,
        createdAt: Long,
        pinned: Boolean,
        contentType: NoteContentType,
        isGlobalKnowledge: Boolean = false
    ) {
        val updatedAt = System.currentTimeMillis()
        val resolvedTitle = title.ifBlank { extractTitle(content) }
        val existing = dao.getNote(id)
        val resolvedContentType = existing?.contentType?.let(NoteContentType::from) ?: contentType
        val entity = NoteEntity(
            id = id,
            title = resolvedTitle,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            folderName = existing?.folderName ?: dao.getFolderName(id).orEmpty(),
            contentType = resolvedContentType.storageValue,
            isGlobalKnowledge = if (resolvedContentType == NoteContentType.Knowledge) isGlobalKnowledge else false,
            pinned = pinned
        )
        if (existing == null) {
            dao.insert(entity)
        } else {
            dao.update(
                entity
            )
        }
        backupRepository?.backupNote(id, resolvedTitle, content, updatedAt, resolvedContentType)
    }

    suspend fun deleteNote(id: Long) {
        dao.softDelete(id, System.currentTimeMillis())
        dao.deleteKnowledgeScope(id)
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
                    contentType = backup.contentType.storageValue,
                    isGlobalKnowledge = false
                )
            )
        }
        return backups.size
    }

    suspend fun getKnowledgeEntries(): List<Note> {
        return dao.getNotesByType(NoteContentType.Knowledge.storageValue).map { it.toDomain() }
    }

    fun observeKnowledgeEntries(): Flow<List<Note>> {
        return observeNotes(NoteContentType.Knowledge)
    }

    suspend fun getKnowledgeEntry(id: Long): Note? {
        val note = dao.getNote(id) ?: return null
        return note.toDomain().takeIf { it.contentType == NoteContentType.Knowledge }
    }

    fun observeRecentKnowledgeEntries(limit: Int = 8): Flow<List<Note>> {
        return observeKnowledgeEntries().map { notes ->
            notes.sortedByDescending { it.updatedAt }.take(limit)
        }
    }

    fun searchKnowledgeEntries(query: String): Flow<List<Note>> {
        return searchNotes(query = query, contentType = NoteContentType.Knowledge)
    }

    suspend fun createKnowledge(title: String, content: String, isGlobalKnowledge: Boolean = false): Long {
        val id = createNote(contentType = NoteContentType.Knowledge)
        saveNote(
            id = id,
            title = title,
            content = content,
            createdAt = System.currentTimeMillis(),
            pinned = false,
            contentType = NoteContentType.Knowledge,
            isGlobalKnowledge = isGlobalKnowledge
        )
        return id
    }

    suspend fun overwriteKnowledge(id: Long, title: String, content: String) {
        val existing = getKnowledgeEntry(id) ?: return
        saveNote(
            id = id,
            title = title,
            content = content,
            createdAt = existing.createdAt,
            pinned = existing.pinned,
            contentType = NoteContentType.Knowledge,
            isGlobalKnowledge = existing.isGlobalKnowledge
        )
    }

    suspend fun getNoteKnowledgeScope(noteId: Long): NoteKnowledgeScope? {
        return dao.getKnowledgeScope(noteId)?.toDomain()
    }

    fun observeNoteKnowledgeScope(noteId: Long): Flow<NoteKnowledgeScope?> {
        return dao.observeKnowledgeScope(noteId).map { it?.toDomain() }
    }

    suspend fun saveNoteKnowledgeScope(noteId: Long, scope: NoteKnowledgeScope?) {
        if (scope == null || !scope.hasCustomConfiguration) {
            dao.deleteKnowledgeScope(noteId)
            return
        }
        dao.upsertKnowledgeScope(NoteKnowledgeScopeEntity.fromDomain(noteId, scope))
    }

    suspend fun getEffectiveKnowledgeEntries(noteId: Long?): List<Note> {
        val entries = getKnowledgeEntries()
        if (noteId == null) return entries
        val scope = getNoteKnowledgeScope(noteId) ?: return entries
        return entries.filter(scope::allows)
    }

    fun observeKnowledgeScopeSummary(noteId: Long): Flow<KnowledgeScopeSummary> {
        return combine(
            observeKnowledgeEntries(),
            observeNoteKnowledgeScope(noteId)
        ) { entries, scope ->
            val distinctFolders = entries.map { it.folderName }.distinct()
            if (scope == null) {
                KnowledgeScopeSummary(
                    enabledFolderCount = distinctFolders.size,
                    totalFolderCount = distinctFolders.size,
                    enabledKnowledgeCount = entries.size,
                    totalKnowledgeCount = entries.size
                )
            } else {
                val enabledKnowledge = entries.count(scope::allows)
                KnowledgeScopeSummary(
                    enabledFolderCount = distinctFolders.count { folder ->
                        folder in scope.enabledFolderNames && folder !in scope.disabledFolderNames
                    },
                    totalFolderCount = distinctFolders.size,
                    enabledKnowledgeCount = enabledKnowledge,
                    totalKnowledgeCount = entries.size
                )
            }
        }
    }

    private fun NoteEntity.toDomain(): Note = Note(
        id = id,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        folderName = folderName,
        contentType = NoteContentType.from(contentType),
        isGlobalKnowledge = isGlobalKnowledge,
        pinned = pinned
    )

    private fun extractTitle(content: String): String {
        return content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(24).orEmpty()
    }

    fun Note.toKnowledgeTargetSummary(): KnowledgeTargetSummary {
        return KnowledgeTargetSummary(
            id = id,
            title = displayTitle,
            folderName = folderName,
            updatedAt = updatedAt
        )
    }
}
