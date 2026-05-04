package com.example.ainote.domain.model

fun Note.titleAliases(): List<String> {
    return title
        .split('/')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}
