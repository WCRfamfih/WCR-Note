package com.example.ainote.domain.model

data class KnowledgeExtractionRequest(
    val noteId: Long? = null,
    val material: String,
    val instruction: String,
    val targetKnowledgeId: Long? = null,
    val targetKnowledgeTitle: String? = null,
    val targetKnowledgeContent: String? = null
)

data class KnowledgeExtractionDraft(
    val title: String,
    val content: String,
    val targetKnowledgeId: Long? = null
)

data class KnowledgeTargetSummary(
    val id: Long,
    val title: String,
    val folderName: String,
    val updatedAt: Long
)

data class KnowledgeExtractionLaunchArgs(
    val noteId: Long,
    val contentType: NoteContentType,
    val material: String
)
