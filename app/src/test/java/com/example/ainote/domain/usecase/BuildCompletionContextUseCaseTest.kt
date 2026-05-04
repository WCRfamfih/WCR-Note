package com.example.ainote.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildCompletionContextUseCaseTest {
    private val useCase = BuildCompletionContextUseCase()

    @Test
    fun `build material prefers selected text`() {
        val content = "alpha\nbeta\ngamma"

        val material = useCase.buildMaterial(
            content = content,
            selectionStart = 6,
            selectionEnd = 10,
            useFullNoteContext = false,
            beforeLineCount = 1,
            afterLineCount = 1
        )

        assertEquals("beta", material)
    }

    @Test
    fun `build material uses bounded context when no selection`() {
        val content = "one\ntwo\nthree\nfour"

        val material = useCase.buildMaterial(
            content = content,
            selectionStart = 5,
            selectionEnd = 5,
            useFullNoteContext = false,
            beforeLineCount = 1,
            afterLineCount = 1
        )

        assertEquals("one\ntwo\nthree", material)
    }

    @Test
    fun `build material uses full content when configured`() {
        val content = "one\ntwo\nthree"

        val material = useCase.buildMaterial(
            content = content,
            selectionStart = 4,
            selectionEnd = 4,
            useFullNoteContext = true
        )

        assertEquals(content, material)
    }
}
