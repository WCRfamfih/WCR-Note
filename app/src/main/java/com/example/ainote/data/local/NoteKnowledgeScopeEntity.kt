package com.example.ainote.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ainote.domain.model.NoteKnowledgeScope

@Entity(tableName = "note_knowledge_scopes")
data class NoteKnowledgeScopeEntity(
    @PrimaryKey
    val noteId: Long,
    val enabledFolderNames: String = "",
    val disabledFolderNames: String = "",
    val enabledKnowledgeIds: String = "",
    val disabledKnowledgeIds: String = ""
) {
    fun toDomain(): NoteKnowledgeScope {
        return NoteKnowledgeScope(
            enabledFolderNames = enabledFolderNames.decodeStringSet(),
            disabledFolderNames = disabledFolderNames.decodeStringSet(),
            enabledKnowledgeIds = enabledKnowledgeIds.decodeLongSet(),
            disabledKnowledgeIds = disabledKnowledgeIds.decodeLongSet()
        )
    }

    companion object {
        fun fromDomain(noteId: Long, scope: NoteKnowledgeScope): NoteKnowledgeScopeEntity {
            return NoteKnowledgeScopeEntity(
                noteId = noteId,
                enabledFolderNames = scope.enabledFolderNames.encodeStringSet(),
                disabledFolderNames = scope.disabledFolderNames.encodeStringSet(),
                enabledKnowledgeIds = scope.enabledKnowledgeIds.encodeLongSet(),
                disabledKnowledgeIds = scope.disabledKnowledgeIds.encodeLongSet()
            )
        }
    }
}

private fun String.decodeStringSet(): Set<String> {
    return split('\n')
        .mapNotNull { raw ->
            when (val value = raw.trim()) {
                BLANK_FOLDER_TOKEN -> ""
                "" -> null
                else -> value
            }
        }
        .toSet()
}

private fun Set<String>.encodeStringSet(): String {
    return asSequence()
        .map { value -> if (value.isBlank()) BLANK_FOLDER_TOKEN else value.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
        .joinToString("\n")
}

private fun String.decodeLongSet(): Set<Long> {
    return split(',')
        .mapNotNull { it.trim().toLongOrNull() }
        .toSet()
}

private fun Set<Long>.encodeLongSet(): String {
    return asSequence()
        .distinct()
        .sorted()
        .joinToString(",")
}

private const val BLANK_FOLDER_TOKEN = "__UNCATEGORIZED__"
