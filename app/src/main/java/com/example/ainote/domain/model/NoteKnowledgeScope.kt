package com.example.ainote.domain.model

data class NoteKnowledgeScope(
    val enabledFolderNames: Set<String> = emptySet(),
    val disabledFolderNames: Set<String> = emptySet(),
    val enabledKnowledgeIds: Set<Long> = emptySet(),
    val disabledKnowledgeIds: Set<Long> = emptySet()
) {
    val hasCustomConfiguration: Boolean
        get() = enabledFolderNames.isNotEmpty() ||
            disabledFolderNames.isNotEmpty() ||
            enabledKnowledgeIds.isNotEmpty() ||
            disabledKnowledgeIds.isNotEmpty()

    fun allows(note: Note): Boolean {
        val folderEnabled = note.folderName in enabledFolderNames &&
            note.folderName !in disabledFolderNames
        val knowledgeEnabled = note.id in enabledKnowledgeIds &&
            note.id !in disabledKnowledgeIds
        return folderEnabled && knowledgeEnabled
    }
}

data class KnowledgeScopeSummary(
    val enabledFolderCount: Int = 0,
    val totalFolderCount: Int = 0,
    val enabledKnowledgeCount: Int = 0,
    val totalKnowledgeCount: Int = 0
) {
    val isConfigured: Boolean
        get() = totalFolderCount > 0 || totalKnowledgeCount > 0
}
