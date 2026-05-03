package com.example.ainote.di

import android.content.Context
import com.example.ainote.data.local.NoteDatabase
import com.example.ainote.data.repository.AiRepository
import com.example.ainote.data.repository.DocumentBackupRepository
import com.example.ainote.data.repository.NoteRepository
import com.example.ainote.data.settings.SettingsDataStore

class AppContainer(context: Context) {
    private val database = NoteDatabase.getInstance(context)

    val settingsDataStore = SettingsDataStore(context)
    private val documentBackupRepository = DocumentBackupRepository(context, settingsDataStore)
    val noteRepository = NoteRepository(database.noteDao(), documentBackupRepository)
    val aiRepository = AiRepository(settingsDataStore)
}
