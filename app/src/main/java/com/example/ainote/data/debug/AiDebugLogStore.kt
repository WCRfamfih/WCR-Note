package com.example.ainote.data.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiDebugLogEntry(
    val id: Long,
    val time: String,
    val title: String,
    val detail: String
)

object AiDebugLogStore {
    private const val MaxEntries = 200
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val _entries = MutableStateFlow<List<AiDebugLogEntry>>(emptyList())
    val entries: StateFlow<List<AiDebugLogEntry>> = _entries

    fun add(title: String, detail: String) {
        val now = System.currentTimeMillis()
        val entry = AiDebugLogEntry(
            id = now,
            time = formatter.format(Date(now)),
            title = title,
            detail = detail.take(8_000)
        )
        _entries.update { current -> (listOf(entry) + current).take(MaxEntries) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
