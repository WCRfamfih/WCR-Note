package com.example.ainote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun GhostTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    ghostText: String?,
    textSizeSp: Int,
    onAcceptGhostText: () -> Unit,
    onDismissGhostText: () -> Unit,
    onRetryGhostText: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val parentTextToolbar = LocalTextToolbar.current
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = colorScheme.onSurface,
        fontSize = textSizeSp.sp
    )
    val ghostStyle = textStyle.copy(color = colorScheme.onSurfaceVariant.copy(alpha = 0.48f))
    val visualTransformation = remember(textStyle, colorScheme) {
        markdownVisualTransformation(textStyle, colorScheme)
    }
    var cursorRect by remember { mutableStateOf(Rect.Zero) }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    var controlsSize by remember { mutableStateOf(IntSize.Zero) }

    val plainCopyToolbar = remember(parentTextToolbar, clipboardManager, value) {
        PlainMarkdownCopyToolbar(parentTextToolbar, clipboardManager) {
            value.selectedTextWithoutMarkdown()
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides plainCopyToolbar) {
        Box(
            modifier = modifier
                .background(colorScheme.background)
                .onSizeChanged { editorSize = it }
                .padding(16.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                textStyle = textStyle,
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(colorScheme.primary),
                onTextLayout = { layoutResult ->
                    val transformedCursor = markdownTransformedOffset(
                        inputText = value.text,
                        originalOffset = value.selection.start
                    )
                    cursorRect = layoutResult.getCursorRect(transformedCursor)
                },
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

            if (!ghostText.isNullOrBlank()) {
                val horizontalPaddingPx = with(density) { 32.dp.roundToPx() }
                val editorWidth = max(editorSize.width - horizontalPaddingPx, 0)
                val cursorRight = cursorRect.right.roundToInt()
                val cursorLeft = cursorRect.left.roundToInt()
                val cursorTop = cursorRect.top.roundToInt()
                val cursorBottom = cursorRect.bottom.roundToInt()
                val minInlineWidth = 96
                val inlineRemainingWidth = editorWidth - cursorRight
                val wrapGhostToNextLine = inlineRemainingWidth < minInlineWidth
                val ghostX = if (wrapGhostToNextLine) 0 else cursorRight.coerceIn(0, editorWidth)
                val ghostY = if (wrapGhostToNextLine) cursorBottom + 4 else cursorTop
                val ghostMaxWidthPx = if (wrapGhostToNextLine) editorWidth else max(inlineRemainingWidth, minInlineWidth)
                val ghostMaxWidth = with(density) { ghostMaxWidthPx.toDp() }
                val controlsX = min(max(cursorLeft, 0), max(editorWidth - controlsSize.width, 0))
                val controlsY = if (wrapGhostToNextLine) {
                    ghostY + (textSizeSp * 1.8f).roundToInt()
                } else {
                    cursorBottom + 12
                }
                Text(
                    text = ghostText,
                    style = ghostStyle,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = ghostX,
                                y = ghostY
                            )
                        }
                        .widthIn(max = ghostMaxWidth)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAcceptGhostText
                        )
                )
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    shape = MaterialTheme.shapes.large,
                    color = colorScheme.surface,
                    modifier = Modifier
                        .offset { IntOffset(x = controlsX, y = controlsY) }
                        .onSizeChanged { controlsSize = it }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                        IconButton(onClick = onAcceptGhostText) {
                            Icon(Icons.Default.Check, contentDescription = "\u63a5\u53d7\u8865\u5168")
                        }
                        IconButton(onClick = onDismissGhostText) {
                            Icon(Icons.Default.Close, contentDescription = "\u5ffd\u7565\u8865\u5168")
                        }
                        IconButton(onClick = onRetryGhostText) {
                            Icon(Icons.Default.Refresh, contentDescription = "\u91cd\u8bd5\u8865\u5168")
                        }
                    }
                }
            }
        }
    }
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

private fun markdownTransformedOffset(inputText: String, originalOffset: Int): Int {
    return markdownPresentation(
        inputText = inputText,
        baseStyle = SpanStyle(),
        headingSize = 18.sp
    ).offsetMapping.originalToTransformed(originalOffset)
}

private fun markdownPresentation(
    inputText: String,
    baseStyle: SpanStyle,
    headingSize: TextUnit
): MarkdownPresentation {
    val spans = mutableListOf<MarkdownSpan>()
    val hiddenRanges = mutableListOf<IntRange>()
    collectHeadingSpans(inputText, spans, hiddenRanges, baseStyle, headingSize)
    collectWrapperSpans(
        inputText,
        spans,
        hiddenRanges,
        regex = Regex("\\*\\*(.+?)\\*\\*"),
        style = baseStyle.copy(fontWeight = FontWeight.Bold)
    )
    collectWrapperSpans(
        inputText,
        spans,
        hiddenRanges,
        regex = Regex("~~(.+?)~~"),
        style = baseStyle.copy(textDecoration = TextDecoration.LineThrough)
    )
    collectWrapperSpans(
        inputText,
        spans,
        hiddenRanges,
        regex = Regex("<u>(.+?)</u>"),
        style = baseStyle.copy(textDecoration = TextDecoration.Underline)
    )
    collectWrapperSpans(
        inputText,
        spans,
        hiddenRanges,
        regex = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"),
        style = baseStyle.copy(fontStyle = FontStyle.Italic)
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
    spans.forEach { span ->
        val start = originalToTransformed[span.start.coerceIn(0, inputText.length)]
        val end = originalToTransformed[span.end.coerceIn(0, inputText.length)]
        if (start < end) {
            builder.addStyle(span.style, start, end)
        }
    }

    return MarkdownPresentation(
        annotatedString = builder.toAnnotatedString(),
        plainText = plainBuilder.toString(),
        offsetMapping = MarkdownOffsetMapping(originalToTransformed, transformedToOriginal.toIntArray())
    )
}

private fun markdownVisualTransformation(
    textStyle: TextStyle,
    colorScheme: ColorScheme
): VisualTransformation = VisualTransformation { text ->
    val baseStyle = SpanStyle(
        color = textStyle.color,
        fontSize = textStyle.fontSize,
        fontWeight = textStyle.fontWeight,
        fontStyle = textStyle.fontStyle,
        textDecoration = textStyle.textDecoration,
        fontFamily = textStyle.fontFamily
    )
    val headingSize = textStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 18.sp
    val presentation = markdownPresentation(text.text, baseStyle, headingSize)
    TransformedText(presentation.annotatedString, presentation.offsetMapping)
}

private fun collectHeadingSpans(
    text: String,
    spans: MutableList<MarkdownSpan>,
    hiddenRanges: MutableList<IntRange>,
    baseStyle: SpanStyle,
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
        spans += MarkdownSpan(
            start = markerEnd,
            end = match.range.last + 1,
            style = baseStyle.copy(fontSize = size, fontWeight = FontWeight.SemiBold)
        )
    }
}

private fun collectWrapperSpans(
    text: String,
    spans: MutableList<MarkdownSpan>,
    hiddenRanges: MutableList<IntRange>,
    regex: Regex,
    style: SpanStyle
) {
    regex.findAll(text).forEach { matchResult ->
        val innerGroup = matchResult.groups[1] ?: return@forEach
        val openMarkerStart = matchResult.range.first
        val innerStart = innerGroup.range.first
        val innerEnd = innerGroup.range.last + 1
        val closeMarkerEnd = matchResult.range.last + 1
        hiddenRanges += openMarkerStart until innerStart
        hiddenRanges += innerEnd until closeMarkerEnd
        spans += MarkdownSpan(innerStart, innerEnd, style)
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

private data class MarkdownSpan(
    val start: Int,
    val end: Int,
    val style: SpanStyle
)

private data class MarkdownPresentation(
    val annotatedString: AnnotatedString,
    val plainText: String,
    val offsetMapping: OffsetMapping
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
