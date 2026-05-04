package com.example.ainote.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val folderName: String = "",
    val contentType: String = "note",
    val pinned: Boolean = false,
    val deleted: Boolean = false
)
