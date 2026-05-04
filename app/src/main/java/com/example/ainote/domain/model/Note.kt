package com.example.ainote.domain.model

data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val folderName: String = "",
    val contentType: NoteContentType = NoteContentType.Note,
    val isGlobalKnowledge: Boolean = false,
    val pinned: Boolean = false
) {
    val displayTitle: String
        get() = title.ifBlank {
            content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(24) ?: "\u672a\u547d\u540d"
        }

    val summary: String
        get() = content.replace('\n', ' ').trim().take(80)
}
