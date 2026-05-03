package com.example.ainote.domain.model

data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val folderName: String = "",
    val pinned: Boolean = false
) {
    val displayTitle: String
        get() = title.ifBlank {
            content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(24) ?: "未命名笔记"
        }

    val summary: String
        get() = content.replace('\n', ' ').trim().take(80)
}
