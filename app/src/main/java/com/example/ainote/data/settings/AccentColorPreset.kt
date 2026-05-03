package com.example.ainote.data.settings

import androidx.compose.ui.graphics.Color

enum class AccentColorPreset(
    val label: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
) {
    Violet("紫色", Color(0xFF7053B6), Color(0xFF6B5D7B), Color(0xFF82525F)),
    Blue("蓝色", Color(0xFF2F6FDB), Color(0xFF526070), Color(0xFF675A7A)),
    Green("绿色", Color(0xFF2F7D55), Color(0xFF52645A), Color(0xFF6A5D42)),
    Amber("琥珀", Color(0xFF8A6200), Color(0xFF6A5F4A), Color(0xFF70575E)),
    Rose("玫瑰", Color(0xFFB33D65), Color(0xFF755660), Color(0xFF6A5D42));

    companion object {
        fun from(value: String): AccentColorPreset = entries.firstOrNull { it.name == value } ?: Violet
    }
}
