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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = colorScheme.onSurface,
        fontSize = textSizeSp.sp
    )
    val ghostStyle = textStyle.copy(color = colorScheme.onSurfaceVariant.copy(alpha = 0.48f))
    var cursorRect by remember { mutableStateOf(Rect.Zero) }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    var controlsSize by remember { mutableStateOf(IntSize.Zero) }

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
            cursorBrush = SolidColor(colorScheme.primary),
            onTextLayout = { layoutResult ->
                cursorRect = layoutResult.getCursorRect(value.selection.start)
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
