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
import android.text.SpannableString
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
import com.example.ainote.data.settings.EditorFontPreset
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
        textColor: Int,
        style: NoteImageRenderStyle,
        paged: Boolean = false
    ): Result<ExportedNoteImage> = runCatching {
        val baseFileName = imageFileName().removeSuffix(".png")
        val bitmaps = renderNoteBitmaps(
            context = context,
            title = title.ifBlank { "\u672a\u547d\u540d\u7b14\u8bb0" },
            content = content,
            backgroundColor = backgroundColor,
            textColor = textColor,
            style = style,
            paged = paged
        )
        val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val pageDigits = bitmaps.size.toString().length.coerceAtLeast(2)
        val files = bitmaps.mapIndexed { index, bitmap ->
            val fileName = if (bitmaps.size == 1) {
                "$baseFileName.png"
            } else {
                val pageLabel = (index + 1).toString().padStart(pageDigits, '0')
                "$baseFileName-p$pageLabel.png"
            }
            val file = File(directory, fileName)
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "\u56fe\u7247\u5199\u5165\u5931\u8d25" }
            }
            file
        }
        ExportedNoteImage(
            uris = files.map { file -> FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) },
            fileNames = files.map(File::getName)
        )
    }

    fun saveNoteImage(
        context: Context,
        title: String,
        content: String,
        backgroundColor: Int,
        textColor: Int,
        style: NoteImageRenderStyle
    ): Result<String> = runCatching {
        val bitmap = renderNoteBitmaps(
            context = context,
            title = title.ifBlank { "\u672a\u547d\u540d\u7b14\u8bb0" },
            content = content,
            backgroundColor = backgroundColor,
            textColor = textColor,
            style = style,
            paged = false
        ).first()
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

    private fun renderNoteBitmaps(
        context: Context,
        title: String,
        content: String,
        backgroundColor: Int,
        textColor: Int,
        style: NoteImageRenderStyle,
        paged: Boolean
    ): List<Bitmap> {
        val width = 1440
        val horizontalPadding = 96
        val topPadding = 96
        val bottomPadding = 96
        val titleSpacing = 48
        val bodyWidth = width - horizontalPadding * 2
        val typeface = loadTypeface(context, style)
        val bodyTextSizePx = style.editorTextSizeSp * 2.22f
        val titleTextSizePx = bodyTextSizePx * 1.3f
        val bodyLineSpacingMultiplier = style.editorLineSpacingPercent / 100f
        val titleLineSpacingMultiplier = (style.editorLineSpacingPercent / 100f).coerceIn(1f, 1.5f)
        val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = titleTextSizePx
            this.typeface = typeface?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = style.editorLetterSpacingSp / textSize
        }
        val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = bodyTextSizePx
            this.typeface = typeface ?: Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = style.editorLetterSpacingSp / textSize
        }
        val titleLayout = staticLayout(SpannableStringBuilder(title), titlePaint, bodyWidth, titleLineSpacingMultiplier)
        val bodyText = if (style.renderMarkdown) markdownSpannable(content) else SpannableStringBuilder(content)
        val bodyLayout = staticLayout(bodyText, bodyPaint, bodyWidth, bodyLineSpacingMultiplier)
        if (!paged) {
            val height = (topPadding + titleLayout.height + titleSpacing + bodyLayout.height + bottomPadding)
                .coerceAtLeast(720)
                .coerceAtMost(24000)
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            canvas.drawColor(backgroundColor)
            canvas.save()
            canvas.translate(horizontalPadding.toFloat(), topPadding.toFloat())
            titleLayout.draw(canvas)
            canvas.translate(0f, titleLayout.height + titleSpacing.toFloat())
            bodyLayout.draw(canvas)
            canvas.restore()
            return listOf(bitmap)
        }
        val pageHeight = 1920
        val firstBodyViewport = (pageHeight - topPadding - bottomPadding - titleLayout.height - titleSpacing).coerceAtLeast(320)
        val bodyViewport = (pageHeight - topPadding - bottomPadding).coerceAtLeast(320)
        if (bodyLayout.lineCount == 0) {
            val bitmap = createBitmap(width, pageHeight)
            val canvas = Canvas(bitmap)
            canvas.drawColor(backgroundColor)
            canvas.save()
            canvas.translate(horizontalPadding.toFloat(), topPadding.toFloat())
            titleLayout.draw(canvas)
            canvas.restore()
            return listOf(bitmap)
        }
        val pageSlices = computePagedLineSlices(bodyLayout, firstBodyViewport, bodyViewport)
        return pageSlices.mapIndexed { pageIndex, pageSlice ->
            val bitmap = createBitmap(width, pageHeight)
            val canvas = Canvas(bitmap)
            canvas.drawColor(backgroundColor)
            canvas.save()
            canvas.translate(horizontalPadding.toFloat(), topPadding.toFloat())
            if (pageIndex == 0) {
                titleLayout.draw(canvas)
                canvas.translate(0f, titleLayout.height + titleSpacing.toFloat())
                drawLayoutPage(
                    canvas = canvas,
                    pageLayout = buildPageLayout(
                        sourceLayout = bodyLayout,
                        pageSlice = pageSlice,
                        paint = bodyPaint,
                        bodyWidth = bodyWidth,
                        spacingMultiplier = bodyLineSpacingMultiplier
                    )
                )
            } else {
                drawLayoutPage(
                    canvas = canvas,
                    pageLayout = buildPageLayout(
                        sourceLayout = bodyLayout,
                        pageSlice = pageSlice,
                        paint = bodyPaint,
                        bodyWidth = bodyWidth,
                        spacingMultiplier = bodyLineSpacingMultiplier
                    )
                )
            }
            canvas.restore()
            bitmap
        }
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

    private fun computePagedLineSlices(
        layout: StaticLayout,
        firstViewportHeight: Int,
        otherViewportHeight: Int
    ): List<PageSlice> {
        if (layout.lineCount == 0) return listOf(PageSlice(0, 0, 0, 0))
        val slices = mutableListOf<PageSlice>()
        var startLine = 0
        while (startLine < layout.lineCount) {
            val viewportHeight = if (slices.isEmpty()) firstViewportHeight else otherViewportHeight
            val pageTop = layout.getLineTop(startLine)
            var endExclusive = startLine + 1
            while (endExclusive < layout.lineCount) {
                val lineBottom = layout.getLineBottom(endExclusive - 1)
                if (lineBottom - pageTop > viewportHeight) break
                endExclusive++
            }
            slices += PageSlice(
                startLine = startLine,
                endLineExclusive = endExclusive,
                startOffset = layout.getLineStart(startLine),
                endOffset = layout.getLineEnd(endExclusive - 1)
            )
            startLine = endExclusive
        }
        return slices
    }

    private fun buildPageLayout(
        sourceLayout: StaticLayout,
        pageSlice: PageSlice,
        paint: TextPaint,
        bodyWidth: Int,
        spacingMultiplier: Float
    ): StaticLayout {
        val pageText = SpannableString(
            sourceLayout.text.subSequence(pageSlice.startOffset, pageSlice.endOffset)
        )
        return staticLayout(pageText, paint, bodyWidth, spacingMultiplier)
    }

    private fun drawLayoutPage(
        canvas: Canvas,
        pageLayout: StaticLayout
    ) {
        pageLayout.draw(canvas)
    }

    private fun loadTypeface(
        context: Context,
        style: NoteImageRenderStyle
    ): Typeface? {
        if (style.editorFontPreset == EditorFontPreset.Custom && style.customEditorFontUri.isNotBlank()) {
            runCatching {
                context.contentResolver.openFileDescriptor(Uri.parse(style.customEditorFontUri), "r")?.use { descriptor ->
                    Typeface.Builder(descriptor.fileDescriptor).build()
                }
            }.getOrNull()?.let { return it }
        }
        return when (style.editorFontPreset) {
            EditorFontPreset.System -> Typeface.DEFAULT
            EditorFontPreset.Sans -> Typeface.SANS_SERIF
            EditorFontPreset.Serif -> Typeface.SERIF
            EditorFontPreset.Monospace -> Typeface.MONOSPACE
            EditorFontPreset.Cursive -> Typeface.create("cursive", Typeface.NORMAL)
            EditorFontPreset.Custom -> Typeface.DEFAULT
        }
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

data class NoteImageRenderStyle(
    val editorTextSizeSp: Int,
    val editorLineSpacingPercent: Int,
    val editorLetterSpacingSp: Float,
    val editorFontPreset: EditorFontPreset,
    val customEditorFontUri: String,
    val renderMarkdown: Boolean
)

data class ExportedNoteImage(
    val uris: List<Uri>,
    val fileNames: List<String>
) {
    val primaryUri: Uri get() = uris.first()
    val primaryFileName: String get() = fileNames.first()
}

private data class PageSlice(
    val startLine: Int,
    val endLineExclusive: Int,
    val startOffset: Int,
    val endOffset: Int
)
