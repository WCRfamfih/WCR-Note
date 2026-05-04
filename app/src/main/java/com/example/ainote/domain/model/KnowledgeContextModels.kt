package com.example.ainote.domain.model

data class KnowledgeContextResolution(
    val matches: List<Note> = emptyList(),
    val limitedMatches: List<Note> = emptyList(),
    val limit: Int = 0,
    val overflow: Boolean = false,
    val relatedKnowledge: String = ""
)
