package com.example.ainote.data.settings

enum class NoteSortField(val label: String) {
    Time("时间"),
    Name("名称");

    companion object {
        fun from(value: String): NoteSortField = entries.firstOrNull { it.name == value } ?: Time
    }
}

enum class NoteSortDirection(val label: String) {
    Descending("倒序"),
    Ascending("升序");

    companion object {
        fun from(value: String): NoteSortDirection = entries.firstOrNull { it.name == value } ?: Descending
    }
}
