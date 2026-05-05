package com.example.ainote.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

class CoverImageStorage(
    private val context: Context
) {
    private val coverDirectory: File by lazy {
        File(context.filesDir, "covers").apply { mkdirs() }
    }

    fun importToLocalStorage(sourceUri: String): String {
        val uri = Uri.parse(sourceUri)
        val extension = resolveExtension(uri)
        val targetFile = File(coverDirectory, "${UUID.randomUUID()}$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取封面图片。")
        return Uri.fromFile(targetFile).toString()
    }

    private fun resolveExtension(uri: Uri): String {
        val mimeExtension = context.contentResolver.getType(uri)
            ?.substringAfterLast('/', "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { ".$it" }
        if (mimeExtension != null) return mimeExtension
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty()
        val suffix = displayName.substringAfterLast('.', "").trim()
        return if (suffix.isBlank() || suffix == displayName) ".img" else ".$suffix"
    }
}
