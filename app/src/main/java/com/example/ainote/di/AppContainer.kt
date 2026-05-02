package com.example.ainote.di

import android.content.Context
import com.example.ainote.data.local.NoteDatabase
import com.example.ainote.data.remote.FakeAiCompletionService
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore

class AppContainer(context: Context) {
    private val database = NoteDatabase.getInstance(context)

    val noteRepository = NoteRepository(database.noteDao())
    val settingsDataStore = SettingsDataStore(context)
    val aiRepository = AiRepository(FakeAiCompletionService())
}
