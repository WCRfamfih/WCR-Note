package com.example.ainote.data.settings

enum class ThemeMode(val label: String) {
    System("\u8ddf\u968f\u7cfb\u7edf"),
    Light("\u4eae\u8272"),
    Dark("\u6df1\u8272");

    companion object {
        fun from(value: String): ThemeMode = entries.firstOrNull { it.name == value } ?: System
    }
}
