package com.example.ainote.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AiActionResultCard(
    actionLabel: String,
    text: String,
    primaryActionLabel: String,
    onAccept: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("\u0041\u0049 $actionLabel", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "\u5173\u95ed\u7ed3\u679c")
                }
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text("\u590d\u5236", modifier = Modifier.padding(start = 6.dp))
                }
                Row(modifier = Modifier.weight(1f)) {}
                TextButton(onClick = onAccept) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Text(primaryActionLabel, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
