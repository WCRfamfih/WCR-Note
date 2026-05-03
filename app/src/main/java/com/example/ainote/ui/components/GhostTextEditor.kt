package com.example.ainote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun GhostTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    ghostText: String?,
    onAcceptGhostText: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface)
    val ghostStyle = textStyle.copy(color = colorScheme.onSurfaceVariant.copy(alpha = 0.48f))
    var cursorRect by remember { mutableStateOf(Rect.Zero) }
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .background(colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(16.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
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
            Text(
                text = ghostText,
                style = ghostStyle,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = cursorRect.right.roundToInt(),
                            y = cursorRect.top.roundToInt() - scrollState.value
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAcceptGhostText
                    )
            )
        }
    }
}
