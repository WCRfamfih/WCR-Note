package com.example.ainote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ainote.data.settings.SettingsDataStore
import com.example.ainote.data.settings.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val dataStore: SettingsDataStore) : ViewModel() {
    val settings: StateFlow<UserSettings> = dataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun updateApiProvider(value: String) = launch { dataStore.updateApiProvider(value) }
    fun updateApiKey(value: String) = launch { dataStore.updateApiKey(value) }
    fun updateAutoCompletionEnabled(value: Boolean) = launch { dataStore.updateAutoCompletionEnabled(value) }
    fun updateCompletionDelayMs(value: Long) = launch { dataStore.updateCompletionDelayMs(value) }
    fun updateMaxCompletionLength(value: Int) = launch { dataStore.updateMaxCompletionLength(value) }
    fun updateUseFullNoteContext(value: Boolean) = launch { dataStore.updateUseFullNoteContext(value) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(private val dataStore: SettingsDataStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(dataStore) as T
        }
    }
}
