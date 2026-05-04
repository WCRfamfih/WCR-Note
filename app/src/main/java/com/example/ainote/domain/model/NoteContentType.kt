package com.example.ainote.domain.model

enum class NoteContentType(val storageValue: String, val label: String) {
    Note("note", "\u7b14\u8bb0"),
    Knowledge("knowledge", "\u77e5\u8bc6\u5e93");

    companion object {
        fun from(value: String?): NoteContentType {
            return entries.firstOrNull { it.storageValue == value } ?: Note
        }
    }
}
