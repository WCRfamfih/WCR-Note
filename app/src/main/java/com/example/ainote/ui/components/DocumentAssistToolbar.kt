package com.example.ainote.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.ainote.ui.editor.MarkdownFormatAction

@Composable
fun DocumentAssistToolbar(
    onAction: (MarkdownFormatAction) -> Unit,
    markdownToolsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            AiCompletionButton { onAction(MarkdownFormatAction.ManualCompletion) }
            ToolButton("<") { onAction(MarkdownFormatAction.Outdent) }
            ToolButton(">") { onAction(MarkdownFormatAction.Indent) }
            if (markdownToolsEnabled) {
                ToolButton("H1") { onAction(MarkdownFormatAction.Heading1) }
                ToolButton("H2") { onAction(MarkdownFormatAction.Heading2) }
                ToolButton("H3") { onAction(MarkdownFormatAction.Heading3) }
                ToolButton("B", fontWeight = FontWeight.Bold) { onAction(MarkdownFormatAction.Bold) }
                ToolButton("I", fontStyle = FontStyle.Italic) { onAction(MarkdownFormatAction.Italic) }
                ToolButton("S", textDecoration = TextDecoration.LineThrough) { onAction(MarkdownFormatAction.Strike) }
                ToolButton("U", textDecoration = TextDecoration.Underline) { onAction(MarkdownFormatAction.Underline) }
            }
        }
    }
}

@Composable
private fun AiCompletionButton(
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .padding(horizontal = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "手动 AI 补全"
        )
    }
}

@Composable
private fun ToolButton(
    text: String,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = text,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration
        )
    }
}
