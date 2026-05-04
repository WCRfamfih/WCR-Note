package com.example.ainote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeMatchingTest {
    @Test
    fun `title aliases split trim and dedupe`() {
        val note = Note(
            id = 1L,
            title = " 苹果 / Apple / fruit / Apple / ",
            content = "",
            createdAt = 0L,
            updatedAt = 0L,
            contentType = NoteContentType.Knowledge
        )

        assertEquals(listOf("苹果", "Apple", "fruit"), note.titleAliases())
    }

    @Test
    fun `knowledge scope requires folder and card enabled`() {
        val scope = NoteKnowledgeScope(
            enabledFolderNames = setOf("Tech", ""),
            disabledFolderNames = setOf("Archive"),
            enabledKnowledgeIds = setOf(1L, 2L, 3L),
            disabledKnowledgeIds = setOf(3L)
        )

        val enabledNote = Note(
            id = 1L,
            title = "Kotlin",
            content = "",
            createdAt = 0L,
            updatedAt = 0L,
            folderName = "Tech",
            contentType = NoteContentType.Knowledge
        )
        val disabledByFolder = enabledNote.copy(id = 2L, folderName = "Archive")
        val disabledByCard = enabledNote.copy(id = 3L)
        val enabledUncategorized = enabledNote.copy(id = 2L, folderName = "")

        assertTrue(scope.allows(enabledNote))
        assertFalse(scope.allows(disabledByFolder))
        assertFalse(scope.allows(disabledByCard))
        assertTrue(scope.allows(enabledUncategorized))
    }
}
