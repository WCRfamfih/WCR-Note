package com.example.ainote.data.repository

import com.example.ainote.data.local.NoteDao
import com.example.ainote.data.local.NoteEntity
import com.example.ainote.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(private val dao: NoteDao) {
    fun observeNotes(): Flow<List<Note>> = dao.observeNotes().map { notes -> notes.map { it.toDomain() } }

    fun searchNotes(query: String, folderName: String? = null): Flow<List<Note>> {
        return dao.searchNotes(query.trim(), folderName?.trim()?.ifBlank { "" }).map { notes -> notes.map { it.toDomain() } }
    }

    fun observeNote(id: Long): Flow<Note?> = dao.observeNote(id).map { it?.toDomain() }

    suspend fun createNote(folderName: String = ""): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            NoteEntity(
                title = "",
                content = "",
                createdAt = now,
                updatedAt = now,
                folderName = folderName.trim()
            )
        )
    }

    suspend fun saveNote(id: Long, title: String, content: String, createdAt: Long, pinned: Boolean) {
        dao.update(
            NoteEntity(
                id = id,
                title = title.ifBlank { extractTitle(content) },
                content = content,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis(),
                folderName = dao.getFolderName(id).orEmpty(),
                pinned = pinned
            )
        )
    }

    suspend fun deleteNote(id: Long) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun deleteFolder(folderName: String) {
        dao.clearFolder(folderName.trim(), System.currentTimeMillis())
    }

    private fun NoteEntity.toDomain(): Note = Note(
        id = id,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        folderName = folderName,
        pinned = pinned
    )

    private fun extractTitle(content: String): String {
        return content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(24).orEmpty()
    }
}
