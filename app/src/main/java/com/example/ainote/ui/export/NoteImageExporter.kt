package com.example.ainote.ui.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.core.graphics.createBitmap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NoteImageExporter {
    fun createShareImage(
        context: Context,
        title: String,
        content: String,
        backgroundColor: Int,
        textColor: Int
    ): Result<ExportedNoteImage> = runCatching {
        val fileName = imageFileName()
        val bitmap = renderNoteBitmap(
            title = title.ifBlank { "\u672a\u547d\u540d\u7b14\u8bb0" },
            content = content,
            backgroundColor = backgroundColor,
            textColor = textColor
        )
        val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(directory, fileName)
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "\u56fe\u7247\u5199\u5165\u5931\u8d25" }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        ExportedNoteImage(uri = uri, fileName = fileName)
    }

    fun saveNoteImage(
        context: Context,
        title: String,
        content: String,
        backgroundColor: Int,
        textColor: Int
    ): Result<String> = runCatching {
        val bitmap = renderNoteBitmap(
            title = title.ifBlank { "\u672a\u547d\u540d\u7b14\u8bb0" },
            content = content,
            backgroundColor = backgroundColor,
            textColor = textColor
        )
        val fileName = imageFileName()
        saveBitmapToGallery(context, bitmap, fileName)
        fileName
    }

    fun saveImageToGallery(
        context: Context,
        imageUri: Uri,
        fileName: String
    ): Result<String> = runCatching {
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        } ?: error("\u65e0\u6cd5\u8bfb\u53d6\u56fe\u7247")
        saveBitmapToGallery(context, bitmap, fileName)
        fileName
    }

    private fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Note")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("\u65e0\u6cd5\u521b\u5efa\u56fe\u7247\u6587\u4ef6")
        resolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "\u56fe\u7247\u5199\u5165\u5931\u8d25" }
        } ?: error("\u65e0\u6cd5\u6253\u5f00\u56fe\u7247\u8f93\u51fa\u6d41")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    private fun imageFileName(): String =
        "note-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}.png"

    private fun renderNoteBitmap(
        title: String,
        content: String,
        backgroundColor: Int,
        textColor: Int
    ): Bitmap {
        val width = 1440
        val horizontalPadding = 96
        val topPadding = 96
        val bottomPadding = 96
        val bodyWidth = width - horizontalPadding * 2
        val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val titleLayout = staticLayout(SpannableStringBuilder(title), titlePaint, bodyWidth, 1.18f)
        val bodyLayout = staticLayout(markdownSpannable(content), bodyPaint, bodyWidth, 1.32f)
        val height = (topPadding + titleLayout.height + 48 + bodyLayout.height + bottomPadding)
            .coerceAtLeast(720)
            .coerceAtMost(24000)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)
        canvas.save()
        canvas.translate(horizontalPadding.toFloat(), topPadding.toFloat())
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height + 48f)
        bodyLayout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    private fun staticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        spacingMultiplier: Float
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacingMultiplier)
            .setIncludePad(true)
            .build()
    }

    private fun markdownSpannable(input: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        input.split('\n').forEachIndexed { lineIndex, rawLine ->
            if (lineIndex > 0) builder.append('\n')
            val heading = Regex("^(#{1,3})\\s+").find(rawLine)
            val headingLevel = heading?.groupValues?.get(1)?.length
            val line = if (heading == null) rawLine else rawLine.drop(heading.value.length)
            val lineStart = builder.length
            appendInlineMarkdown(line, builder)
            val lineEnd = builder.length
            if (headingLevel != null && lineEnd > lineStart) {
                val scale = when (headingLevel) {
                    1 -> 1.45f
                    2 -> 1.25f
                    else -> 1.12f
                }
                builder.setSpan(RelativeSizeSpan(scale), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return builder
    }

    private fun appendInlineMarkdown(source: String, builder: SpannableStringBuilder) {
        val boldStarts = ArrayDeque<Int>()
        val italicStarts = ArrayDeque<Int>()
        val strikeStarts = ArrayDeque<Int>()
        val underlineStarts = ArrayDeque<Int>()
        var index = 0
        while (index < source.length) {
            when {
                source.startsWith("**", index) -> {
                    toggleSpanStart(boldStarts, builder) { StyleSpan(Typeface.BOLD) }
                    index += 2
                }
                source.startsWith("~~", index) -> {
                    toggleSpanStart(strikeStarts, builder) { StrikethroughSpan() }
                    index += 2
                }
                source.startsWith("<u>", index) -> {
                    underlineStarts.addLast(builder.length)
                    index += 3
                }
                source.startsWith("</u>", index) -> {
                    closeSpan(underlineStarts, builder, UnderlineSpan())
                    index += 4
                }
                source[index] == '*' -> {
                    toggleSpanStart(italicStarts, builder) { StyleSpan(Typeface.ITALIC) }
                    index += 1
                }
                else -> {
                    builder.append(source[index])
                    index += 1
                }
            }
        }
    }

    private fun toggleSpanStart(
        starts: ArrayDeque<Int>,
        builder: SpannableStringBuilder,
        spanFactory: () -> Any
    ) {
        if (starts.isEmpty()) {
            starts.addLast(builder.length)
        } else {
            closeSpan(starts, builder, spanFactory())
        }
    }

    private fun closeSpan(
        starts: ArrayDeque<Int>,
        builder: SpannableStringBuilder,
        span: Any
    ) {
        val start = starts.removeLastOrNull() ?: return
        val end = builder.length
        if (end > start) {
            builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

data class ExportedNoteImage(
    val uri: Uri,
    val fileName: String
)
