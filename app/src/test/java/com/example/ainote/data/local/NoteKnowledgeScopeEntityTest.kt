package com.example.ainote.data.local

import com.example.ainote.domain.model.NoteKnowledgeScope
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteKnowledgeScopeEntityTest {
    @Test
    fun `entity round trip preserves uncategorized folder`() {
        val scope = NoteKnowledgeScope(
            enabledFolderNames = setOf("", "Tech"),
            disabledFolderNames = setOf("Archive"),
            enabledKnowledgeIds = setOf(1L, 2L),
            disabledKnowledgeIds = setOf(3L)
        )

        val entity = NoteKnowledgeScopeEntity.fromDomain(noteId = 9L, scope = scope)

        assertEquals(scope, entity.toDomain())
    }
}
