package com.example.ainote.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun GhostTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textSizeSp: Int,
    lineHeightSp: Float,
    letterSpacingSp: Float,
    fontFamily: FontFamily,
    previewRange: TextRange? = null,
    pagedMode: Boolean = false,
    animatePageTransitions: Boolean = true,
    freezePaginationHeight: Boolean = false,
    allowCurrentPageVerticalScroll: Boolean = false,
    currentPage: Int = 0,
    onCurrentPageChange: (Int) -> Unit = {},
    onPageCountChange: (Int) -> Unit = {},
    renderMarkdown: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val parentTextToolbar = LocalTextToolbar.current
    val density = LocalDensity.current
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = colorScheme.onSurface,
        fontSize = textSizeSp.sp,
        lineHeight = lineHeightSp.sp,
        letterSpacing = letterSpacingSp.sp,
        fontFamily = fontFamily
    )
    val ghostStyle = textStyle.copy(color = colorScheme.onSurfaceVariant.copy(alpha = 0.48f))
    val previewStyle = remember(ghostStyle.color) { SpanStyle(color = ghostStyle.color) }
    val textMeasurer = rememberTextMeasurer()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var paginationHeightPx by remember { mutableIntStateOf(0) }
    var horizontalDragOffsetPx by remember { mutableFloatStateOf(0f) }
    val pageSlideOffsetPx = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val pageScrollState = rememberScrollState()
    val editorPresentation = remember(value.text, textStyle, renderMarkdown, previewRange, previewStyle) {
        buildEditorPresentation(
            text = value.text,
            textStyle = textStyle,
            renderMarkdown = renderMarkdown,
            previewRange = previewRange,
            previewStyle = previewStyle
        )
    }
    val pageRanges = remember(
        pagedMode,
        freezePaginationHeight,
        editorPresentation,
        viewportWidthPx,
        viewportHeightPx,
        paginationHeightPx,
        textStyle
    ) {
        val effectiveHeightPx = when {
            freezePaginationHeight && paginationHeightPx > 0 -> paginationHeightPx
            else -> viewportHeightPx
        }
        if (!pagedMode || viewportWidthPx <= 0 || effectiveHeightPx <= 0) {
            listOf(0 to value.text.length)
        } else {
            computePageRanges(
                presentation = editorPresentation,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                widthPx = viewportWidthPx,
                heightPx = effectiveHeightPx
            )
        }
    }
    val pageCount = pageRanges.size.coerceAtLeast(1)
    val safePage = currentPage.coerceIn(0, pageCount - 1)
    val settlePage: () -> Unit = remember(safePage, pageCount, viewportWidthPx, horizontalDragOffsetPx) {
        {
            val threshold = viewportWidthPx * 0.22f
            val targetPage = when {
                horizontalDragOffsetPx <= -threshold && safePage < pageCount - 1 -> safePage + 1
                horizontalDragOffsetPx >= threshold && safePage > 0 -> safePage - 1
                else -> safePage
            }
            val targetOffset = when {
                targetPage > safePage -> -viewportWidthPx.toFloat()
                targetPage < safePage -> viewportWidthPx.toFloat()
                else -> 0f
            }
            coroutineScope.launch {
                pageSlideOffsetPx.animateTo(
                    targetValue = targetOffset,
                    animationSpec = tween(durationMillis = if (targetPage == safePage) 180 else 220)
                )
                if (targetPage != safePage) {
                    onCurrentPageChange(targetPage)
                }
                horizontalDragOffsetPx = 0f
                pageSlideOffsetPx.snapTo(0f)
            }
        }
    }
    val dragState = rememberDraggableState { delta ->
        val minOffset = if (safePage < pageCount - 1) -viewportWidthPx.toFloat() else 0f
        val maxOffset = if (safePage > 0) viewportWidthPx.toFloat() else 0f
        horizontalDragOffsetPx = (horizontalDragOffsetPx + delta).coerceIn(minOffset, maxOffset)
    }
    val activeRange = pageRanges.getOrElse(safePage) { 0 to value.text.length }
    val localPreviewRange = previewRange?.let { range ->
        val start = maxOf(range.min, activeRange.first)
        val end = minOf(range.max, activeRange.second)
        if (start < end) TextRange(start - activeRange.first, end - activeRange.first) else null
    }
    val pageValue = remember(value, activeRange) {
        val localSelection = TextRange(
            start = (value.selection.start - activeRange.first).coerceIn(0, activeRange.second - activeRange.first),
            end = (value.selection.end - activeRange.first).coerceIn(0, activeRange.second - activeRange.first)
        )
        value.copy(
            text = value.text.substring(activeRange.first, activeRange.second),
            selection = localSelection
        )
    }
    val visualTransformation = remember(textStyle, renderMarkdown, localPreviewRange, previewStyle) {
        editorVisualTransformation(
            textStyle = textStyle,
            renderMarkdown = renderMarkdown,
            previewRange = localPreviewRange,
            previewStyle = previewStyle
        )
    }

    LaunchedEffect(pageCount) {
        onPageCountChange(pageCount)
        if (safePage != currentPage) {
            onCurrentPageChange(safePage)
        }
    }

    LaunchedEffect(value.selection, pageRanges, pagedMode) {
        if (!pagedMode) return@LaunchedEffect
        val cursorPage = pageIndexForOffset(value.selection.start, pageRanges)
        if (cursorPage != safePage) onCurrentPageChange(cursorPage)
    }

    LaunchedEffect(safePage, allowCurrentPageVerticalScroll) {
        if (allowCurrentPageVerticalScroll) {
            pageScrollState.scrollTo(0)
        }
    }

    LaunchedEffect(horizontalDragOffsetPx) {
        pageSlideOffsetPx.snapTo(horizontalDragOffsetPx)
    }

    val plainCopyToolbar = remember(parentTextToolbar, clipboardManager, value, renderMarkdown) {
        PlainMarkdownCopyToolbar(parentTextToolbar, clipboardManager) {
            if (renderMarkdown) value.selectedTextWithoutMarkdown() else null
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides plainCopyToolbar) {
        Box(
            modifier = modifier
                .background(colorScheme.background)
                .clipToBounds()
                .onSizeChanged {
                    val horizontalPaddingPx = with(density) { 16.dp.roundToPx() } * 2
                    val verticalPaddingPx = with(density) { 16.dp.roundToPx() } * 2
                    viewportWidthPx = (it.width - horizontalPaddingPx).coerceAtLeast(1)
                    viewportHeightPx = (it.height - verticalPaddingPx).coerceAtLeast(1)
                    if (!freezePaginationHeight || paginationHeightPx == 0) {
                        paginationHeightPx = viewportHeightPx
                    }
                }
                .draggable(
                    enabled = pagedMode,
                    orientation = Orientation.Horizontal,
                    state = dragState,
                    onDragStopped = { settlePage() }
                )
                .padding(16.dp)
        ) {
            fun pageValueFor(range: Pair<Int, Int>): TextFieldValue {
                val pageLength = range.second - range.first
                val localSelection = TextRange(
                    start = (value.selection.start - range.first).coerceIn(0, pageLength),
                    end = (value.selection.end - range.first).coerceIn(0, pageLength)
                )
                return value.copy(
                    text = value.text.substring(range.first, range.second),
                    selection = localSelection
                )
            }

            @Composable
            fun PageField(
                pageIndex: Int,
                horizontalOffsetPx: Float,
                enableVerticalScroll: Boolean
            ) {
                val range = pageRanges.getOrElse(pageIndex) { 0 to value.text.length }
                val pageFieldValue = remember(value, range) { pageValueFor(range) }
                val pagePreviewRange = previewRange?.let { rangeValue ->
                    val start = maxOf(rangeValue.min, range.first)
                    val end = minOf(rangeValue.max, range.second)
                    if (start < end) TextRange(start - range.first, end - range.first) else null
                }
                val transformation = remember(textStyle, renderMarkdown, pagePreviewRange, previewStyle) {
                    editorVisualTransformation(
                        textStyle = textStyle,
                        renderMarkdown = renderMarkdown,
                        previewRange = pagePreviewRange,
                        previewStyle = previewStyle
                    )
                }
                val editorModifier = Modifier
                    .fillMaxWidth()
                    .then(if (enableVerticalScroll) Modifier.verticalScroll(pageScrollState) else Modifier.fillMaxSize())
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .graphicsLayer { translationX = horizontalOffsetPx }

                BasicTextField(
                    value = pageFieldValue,
                    onValueChange = { localValue ->
                        val nextText = value.text.replaceRange(range.first, range.second, localValue.text)
                        val nextSelection = TextRange(
                            start = (range.first + localValue.selection.start).coerceIn(0, nextText.length),
                            end = (range.first + localValue.selection.end).coerceIn(0, nextText.length)
                        )
                        onValueChange(localValue.copy(text = nextText, selection = nextSelection))
                    },
                    modifier = editorModifier,
                    textStyle = textStyle,
                    visualTransformation = transformation,
                    cursorBrush = SolidColor(colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (value.text.isBlank()) {
                                Text(
                                    text = "\u5f00\u59cb\u5199\u70b9\u4ec0\u4e48...",
                                    style = ghostStyle
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            if (pagedMode && animatePageTransitions) {
                val currentOffset = pageSlideOffsetPx.value
                Box(modifier = Modifier.fillMaxSize()) {
                    PageField(
                        pageIndex = safePage,
                        horizontalOffsetPx = currentOffset,
                        enableVerticalScroll = allowCurrentPageVerticalScroll
                    )
                    when {
                        currentOffset < 0f && safePage < pageCount - 1 -> {
                            PageField(
                                pageIndex = safePage + 1,
                                horizontalOffsetPx = viewportWidthPx + currentOffset,
                                enableVerticalScroll = false
                            )
                        }
                        currentOffset > 0f && safePage > 0 -> {
                            PageField(
                                pageIndex = safePage - 1,
                                horizontalOffsetPx = -viewportWidthPx + currentOffset,
                                enableVerticalScroll = false
                            )
                        }
                    }
                }
            } else {
                PageField(
                    pageIndex = safePage,
                    horizontalOffsetPx = 0f,
                    enableVerticalScroll = allowCurrentPageVerticalScroll
                )
            }
        }
    }
}

private fun buildEditorPresentation(
    text: String,
    textStyle: TextStyle,
    renderMarkdown: Boolean,
    previewRange: TextRange?,
    previewStyle: SpanStyle
): EditorPresentation {
    if (!renderMarkdown) {
        val builder = AnnotatedString.Builder(text)
        previewRange
            ?.takeIf { !it.collapsed }
            ?.let { range ->
                val start = range.min.coerceIn(0, text.length)
                val end = range.max.coerceIn(start, text.length)
                if (start < end) builder.addStyle(previewStyle, start, end)
            }
        return EditorPresentation(
            annotatedString = builder.toAnnotatedString(),
            offsetMapping = OffsetMapping.Identity,
            originalLength = text.length
        )
    }
    val baseStyle = SpanStyle(
        color = textStyle.color,
        fontSize = textStyle.fontSize,
        fontWeight = textStyle.fontWeight,
        fontStyle = textStyle.fontStyle,
        textDecoration = textStyle.textDecoration,
        fontFamily = textStyle.fontFamily
    )
    val headingSize = textStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 18.sp
    val presentation = markdownPresentation(text, baseStyle, headingSize)
    val builder = AnnotatedString.Builder(presentation.annotatedString)
    previewRange
        ?.takeIf { !it.collapsed }
        ?.let { range ->
            val start = presentation.offsetMapping.originalToTransformed(range.min)
            val end = presentation.offsetMapping.originalToTransformed(range.max)
            if (start < end) builder.addStyle(previewStyle, start, end)
        }
    return EditorPresentation(
        annotatedString = builder.toAnnotatedString(),
        offsetMapping = presentation.offsetMapping,
        originalLength = text.length
    )
}

private fun computePageRanges(
    presentation: EditorPresentation,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: TextStyle,
    widthPx: Int,
    heightPx: Int
): List<Pair<Int, Int>> {
    val measured = textMeasurer.measure(
        text = presentation.annotatedString,
        style = textStyle,
        constraints = Constraints(maxWidth = widthPx)
    )
    if (measured.lineCount == 0) {
        return listOf(0 to 0)
    }
    val ranges = mutableListOf<Pair<Int, Int>>()
    var startLine = 0
    while (startLine < measured.lineCount) {
        val pageTop = measured.getLineTop(startLine)
        var endLine = startLine
        while (endLine < measured.lineCount) {
            val bottom = measured.getLineBottom(endLine)
            if (bottom - pageTop > heightPx && endLine > startLine) break
            if (bottom - pageTop > heightPx) break
            endLine++
        }
        val lastLine = if (endLine == startLine) startLine else endLine - 1
        val transformedStart = measured.getLineStart(startLine)
        val transformedEnd = measured.getLineEnd(lastLine, visibleEnd = true)
        val originalStart = presentation.offsetMapping.transformedToOriginal(transformedStart)
        val originalEnd = presentation.offsetMapping.transformedToOriginal(transformedEnd)
        val safeRange = originalStart.coerceAtMost(originalEnd) to originalEnd.coerceAtLeast(originalStart)
        if (ranges.lastOrNull() != safeRange) {
            ranges += safeRange
        }
        startLine = lastLine + 1
    }
    return ranges.ifEmpty { listOf(0 to presentation.originalLength) }
}

private fun pageIndexForOffset(
    offset: Int,
    pageRanges: List<Pair<Int, Int>>
): Int {
    val safeOffset = offset.coerceAtLeast(0)
    pageRanges.forEachIndexed { index, range ->
        val start = range.first
        val end = range.second
        val isLast = index == pageRanges.lastIndex
        if (safeOffset in start until end || (isLast && safeOffset == end)) {
            return index
        }
    }
    return (pageRanges.size - 1).coerceAtLeast(0)
}

fun markdownAnnotatedString(
    inputText: String,
    textStyle: TextStyle,
    colorScheme: ColorScheme
): AnnotatedString {
    val baseStyle = SpanStyle(
        color = textStyle.color,
        fontSize = textStyle.fontSize,
        fontWeight = textStyle.fontWeight,
        fontStyle = textStyle.fontStyle,
        textDecoration = textStyle.textDecoration,
        fontFamily = textStyle.fontFamily
    )
    val headingSize = textStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 18.sp
    return markdownPresentation(inputText, baseStyle, headingSize).annotatedString
}

fun stripMarkdownMarkers(inputText: String): String {
    return markdownPresentation(
        inputText = inputText,
        baseStyle = SpanStyle(),
        headingSize = 18.sp
    ).plainText
}

fun normalizeMarkdownMarkers(inputText: String): String {
    var current = inputText
    repeat(4) {
        val withoutEmpty = current
            .replace(Regex("\\*\\*\\s*\\*\\*"), "")
            .replace(Regex("~~\\s*~~"), "")
            .replace(Regex("<u>\\s*</u>"), "")
            .replace(Regex("(?<!\\*)\\*(?!\\*)\\s*(?<!\\*)\\*(?!\\*)"), "")
            .replace(Regex("(?m)^#{1,3}\\s*(?=\\n|$)"), "")
        val protectedMarkers = markdownMarkerMask(withoutEmpty)
        val cleaned = StringBuilder()
        var index = 0
        while (index < withoutEmpty.length) {
            val markerLength = unprotectedMarkerLength(withoutEmpty, index, protectedMarkers)
            if (markerLength > 0) {
                index += markerLength
            } else {
                cleaned.append(withoutEmpty[index])
                index++
            }
        }
        val next = cleaned.toString()
        if (next == current) return current
        current = next
    }
    return current
}

private fun markdownMarkerMask(inputText: String): BooleanArray {
    val hiddenRanges = mutableListOf<IntRange>()
    val effects = mutableListOf<MarkdownEffect>()
    collectHeadingSpans(inputText, effects, hiddenRanges, 18.sp)
    collectWrapperSpans(inputText, effects, hiddenRanges, Regex("\\*\\*(.+?)\\*\\*"), MarkdownEffectKind.Bold)
    collectWrapperSpans(inputText, effects, hiddenRanges, Regex("~~(.+?)~~"), MarkdownEffectKind.Strike)
    collectWrapperSpans(inputText, effects, hiddenRanges, Regex("<u>(.+?)</u>"), MarkdownEffectKind.Underline)
    collectWrapperSpans(inputText, effects, hiddenRanges, Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), MarkdownEffectKind.Italic)
    val mask = BooleanArray(inputText.length)
    hiddenRanges.forEach { range ->
        val start = range.first.coerceIn(0, inputText.length)
        val end = (range.last + 1).coerceIn(start, inputText.length)
        for (index in start until end) {
            mask[index] = true
        }
    }
    return mask
}

private fun unprotectedMarkerLength(text: String, index: Int, protectedMarkers: BooleanArray): Int {
    fun isProtected(start: Int, length: Int): Boolean {
        return start + length <= protectedMarkers.size && (start until start + length).all { protectedMarkers[it] }
    }
    val remaining = text.length - index
    if (remaining >= 4 && text.startsWith("</u>", index) && !isProtected(index, 4)) return 4
    if (remaining >= 3 && text.startsWith("<u>", index) && !isProtected(index, 3)) return 3
    if (remaining >= 2 && (text.startsWith("**", index) || text.startsWith("~~", index)) && !isProtected(index, 2)) return 2
    if (text[index] == '*' && !isProtected(index, 1)) return 1
    if (text[index] == '#' && isLineStartMarker(text, index) && !isProtected(index, 1)) return 1
    return 0
}

private fun isLineStartMarker(text: String, index: Int): Boolean {
    if (index > 0 && text[index - 1] != '\n') return false
    var cursor = index
    var count = 0
    while (cursor < text.length && text[cursor] == '#' && count < 3) {
        cursor++
        count++
    }
    return count > 0 && text.getOrNull(cursor)?.isWhitespace() == true
}

private fun markdownPresentation(
    inputText: String,
    baseStyle: SpanStyle,
    headingSize: TextUnit
): MarkdownPresentation {
    val effects = mutableListOf<MarkdownEffect>()
    val hiddenRanges = mutableListOf<IntRange>()
    collectHeadingSpans(inputText, effects, hiddenRanges, headingSize)
    collectWrapperSpans(
        inputText,
        effects,
        hiddenRanges,
        regex = Regex("\\*\\*(.+?)\\*\\*"),
        effect = MarkdownEffectKind.Bold
    )
    collectWrapperSpans(
        inputText,
        effects,
        hiddenRanges,
        regex = Regex("~~(.+?)~~"),
        effect = MarkdownEffectKind.Strike
    )
    collectWrapperSpans(
        inputText,
        effects,
        hiddenRanges,
        regex = Regex("<u>(.+?)</u>"),
        effect = MarkdownEffectKind.Underline
    )
    collectWrapperSpans(
        inputText,
        effects,
        hiddenRanges,
        regex = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"),
        effect = MarkdownEffectKind.Italic
    )

    val hidden = BooleanArray(inputText.length)
    hiddenRanges.forEach { range ->
        val start = range.first.coerceIn(0, inputText.length)
        val end = (range.last + 1).coerceIn(start, inputText.length)
        for (index in start until end) {
            hidden[index] = true
        }
    }

    val originalToTransformed = IntArray(inputText.length + 1)
    val transformedToOriginal = mutableListOf<Int>()
    val plainBuilder = StringBuilder()
    var transformedOffset = 0
    inputText.forEachIndexed { index, char ->
        originalToTransformed[index] = transformedOffset
        if (!hidden[index]) {
            transformedToOriginal += index
            plainBuilder.append(char)
            transformedOffset++
        }
    }
    originalToTransformed[inputText.length] = transformedOffset
    transformedToOriginal += inputText.length

    val builder = AnnotatedString.Builder(plainBuilder.toString())
    buildMergedStyleRuns(inputText.length, effects, originalToTransformed, baseStyle).forEach { run ->
        builder.addStyle(run.style, run.start, run.end)
    }

    return MarkdownPresentation(
        annotatedString = builder.toAnnotatedString(),
        plainText = plainBuilder.toString(),
        offsetMapping = MarkdownOffsetMapping(originalToTransformed, transformedToOriginal.toIntArray())
    )
}

private fun editorVisualTransformation(
    textStyle: TextStyle,
    renderMarkdown: Boolean,
    previewRange: TextRange?,
    previewStyle: SpanStyle
): VisualTransformation = VisualTransformation { text ->
    val baseStyle = SpanStyle(
        color = textStyle.color,
        fontSize = textStyle.fontSize,
        fontWeight = textStyle.fontWeight,
        fontStyle = textStyle.fontStyle,
        textDecoration = textStyle.textDecoration,
        fontFamily = textStyle.fontFamily
    )
    if (!renderMarkdown) {
        val builder = AnnotatedString.Builder(text.text)
        previewRange
            ?.takeIf { !it.collapsed }
            ?.let { range ->
                val start = range.min.coerceIn(0, text.text.length)
                val end = range.max.coerceIn(start, text.text.length)
                if (start < end) builder.addStyle(previewStyle, start, end)
            }
        return@VisualTransformation TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
    val headingSize = textStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 18.sp
    val presentation = markdownPresentation(text.text, baseStyle, headingSize)
    val builder = AnnotatedString.Builder(presentation.annotatedString)
    previewRange
        ?.takeIf { !it.collapsed }
        ?.let { range ->
            val start = presentation.offsetMapping.originalToTransformed(range.min)
            val end = presentation.offsetMapping.originalToTransformed(range.max)
            if (start < end) builder.addStyle(previewStyle, start, end)
        }
    TransformedText(builder.toAnnotatedString(), presentation.offsetMapping)
}

private fun collectHeadingSpans(
    text: String,
    effects: MutableList<MarkdownEffect>,
    hiddenRanges: MutableList<IntRange>,
    headingSize: TextUnit
) {
    Regex("^(#{1,3})\\s+.*$", RegexOption.MULTILINE).findAll(text).forEach { match ->
        val level = match.groupValues[1].length
        val markerEnd = match.range.first + level + 1
        val size = when (level) {
            1 -> headingSize * 1.45f
            2 -> headingSize * 1.25f
            else -> headingSize * 1.1f
        }
        hiddenRanges += match.range.first until markerEnd
        effects += MarkdownEffect(
            start = markerEnd,
            end = match.range.last + 1,
            kind = MarkdownEffectKind.Heading(size)
        )
    }
}

private fun collectWrapperSpans(
    text: String,
    effects: MutableList<MarkdownEffect>,
    hiddenRanges: MutableList<IntRange>,
    regex: Regex,
    effect: MarkdownEffectKind
) {
    regex.findAll(text).forEach { matchResult ->
        val innerGroup = matchResult.groups[1] ?: return@forEach
        val openMarkerStart = matchResult.range.first
        val innerStart = innerGroup.range.first
        val innerEnd = innerGroup.range.last + 1
        val closeMarkerEnd = matchResult.range.last + 1
        hiddenRanges += openMarkerStart until innerStart
        hiddenRanges += innerEnd until closeMarkerEnd
        effects += MarkdownEffect(innerStart, innerEnd, effect)
    }
}

private fun buildMergedStyleRuns(
    inputLength: Int,
    effects: List<MarkdownEffect>,
    originalToTransformed: IntArray,
    baseStyle: SpanStyle
): List<MarkdownStyleRun> {
    if (inputLength == 0) return emptyList()
    val runs = mutableListOf<MarkdownStyleRun>()
    var runStart: Int? = null
    var runStyle: SpanStyle? = null
    for (index in 0 until inputLength) {
        val transformedStart = originalToTransformed[index]
        val transformedEnd = originalToTransformed[index + 1]
        if (transformedStart == transformedEnd) continue
        val style = effects
            .filter { index in it.start until it.end }
            .fold(baseStyle) { acc, effect -> acc.mergeEffect(effect.kind) }
        if (runStyle != null && runStyle == style) continue
        val start = runStart
        val previousStyle = runStyle
        if (start != null && previousStyle != null && start < transformedStart) {
            runs += MarkdownStyleRun(start, transformedStart, previousStyle)
        }
        runStart = transformedStart
        runStyle = style
    }
    val start = runStart
    val style = runStyle
    val end = originalToTransformed[inputLength]
    if (start != null && style != null && start < end) {
        runs += MarkdownStyleRun(start, end, style)
    }
    return runs
}

private fun SpanStyle.mergeEffect(effect: MarkdownEffectKind): SpanStyle {
    return when (effect) {
        MarkdownEffectKind.Bold -> copy(fontWeight = FontWeight.Bold)
        MarkdownEffectKind.Italic -> copy(fontStyle = FontStyle.Italic)
        MarkdownEffectKind.Strike -> copy(textDecoration = mergeDecoration(TextDecoration.LineThrough))
        MarkdownEffectKind.Underline -> copy(textDecoration = mergeDecoration(TextDecoration.Underline))
        is MarkdownEffectKind.Heading -> copy(fontSize = effect.fontSize, fontWeight = FontWeight.SemiBold)
    }
}

private fun SpanStyle.mergeDecoration(decoration: TextDecoration): TextDecoration {
    val current = textDecoration
    return when {
        current == null || current == TextDecoration.None -> decoration
        current == decoration -> decoration
        else -> TextDecoration.combine(listOf(current, decoration))
    }
}

private fun TextFieldValue.selectedTextWithoutMarkdown(): String? {
    if (selection.collapsed) return null
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    return stripMarkdownMarkers(text.substring(start, end))
}

private class PlainMarkdownCopyToolbar(
    private val parent: TextToolbar,
    private val clipboardManager: ClipboardManager,
    private val selectedPlainText: () -> String?
) : TextToolbar {
    override val status: TextToolbarStatus
        get() = parent.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        parent.showMenu(
            rect = rect,
            onCopyRequested = onCopyRequested?.let {
                {
                    selectedPlainText()?.let { plainText ->
                        clipboardManager.setText(AnnotatedString(plainText))
                    } ?: onCopyRequested()
                }
            },
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested
        )
    }

    override fun hide() {
        parent.hide()
    }
}

private data class MarkdownEffect(
    val start: Int,
    val end: Int,
    val kind: MarkdownEffectKind
)

private sealed class MarkdownEffectKind {
    data object Bold : MarkdownEffectKind()
    data object Italic : MarkdownEffectKind()
    data object Strike : MarkdownEffectKind()
    data object Underline : MarkdownEffectKind()
    data class Heading(val fontSize: TextUnit) : MarkdownEffectKind()
}

private data class MarkdownStyleRun(
    val start: Int,
    val end: Int,
    val style: SpanStyle
)

private data class MarkdownPresentation(
    val annotatedString: AnnotatedString,
    val plainText: String,
    val offsetMapping: OffsetMapping
)

private data class EditorPresentation(
    val annotatedString: AnnotatedString,
    val offsetMapping: OffsetMapping,
    val originalLength: Int
)

private class MarkdownOffsetMapping(
    private val originalToTransformed: IntArray,
    private val transformedToOriginal: IntArray
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        return originalToTransformed[offset.coerceIn(0, originalToTransformed.lastIndex)]
    }

    override fun transformedToOriginal(offset: Int): Int {
        return transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
    }
}
