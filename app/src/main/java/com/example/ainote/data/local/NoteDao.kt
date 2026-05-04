package com.example.ainote.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE notes SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("UPDATE notes SET folderName = '', updatedAt = :updatedAt WHERE folderName = :folderName AND contentType = :contentType")
    suspend fun clearFolder(folderName: String, contentType: String, updatedAt: Long)

    @Query("UPDATE notes SET folderName = :newName, updatedAt = :updatedAt WHERE folderName = :oldName AND contentType = :contentType")
    suspend fun renameFolder(oldName: String, newName: String, contentType: String, updatedAt: Long)

    @Query("UPDATE notes SET folderName = :folderName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveNote(id: Long, folderName: String, updatedAt: Long)

    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted = 0 AND contentType = :contentType ORDER BY pinned DESC, updatedAt DESC")
    fun observeNotesByType(contentType: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id AND deleted = 0 LIMIT 1")
    fun observeNote(id: Long): Flow<NoteEntity?>

    @Query("SELECT folderName FROM notes WHERE id = :id LIMIT 1")
    suspend fun getFolderName(id: Long): String?

    @Query("SELECT * FROM notes WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getNote(id: Long): NoteEntity?

    @Query("""
        SELECT * FROM notes
        WHERE deleted = 0
        AND contentType = :contentType
        AND (:query = '' OR title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        AND (:folderName IS NULL OR folderName = :folderName)
        ORDER BY pinned DESC, updatedAt DESC
    """)
    fun searchNotes(query: String, folderName: String?, contentType: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted = 0 AND contentType = :contentType AND title != '' ORDER BY updatedAt DESC")
    suspend fun getNotesByType(contentType: String): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnowledgeScope(scope: NoteKnowledgeScopeEntity)

    @Query("SELECT * FROM note_knowledge_scopes WHERE noteId = :noteId LIMIT 1")
    suspend fun getKnowledgeScope(noteId: Long): NoteKnowledgeScopeEntity?

    @Query("SELECT * FROM note_knowledge_scopes WHERE noteId = :noteId LIMIT 1")
    fun observeKnowledgeScope(noteId: Long): Flow<NoteKnowledgeScopeEntity?>

    @Query("DELETE FROM note_knowledge_scopes WHERE noteId = :noteId")
    suspend fun deleteKnowledgeScope(noteId: Long)
}
