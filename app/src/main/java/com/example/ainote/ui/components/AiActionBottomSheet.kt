package com.example.ainote.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ainote.domain.model.AiActionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiActionBottomSheet(
    onDismiss: () -> Unit,
    onActionClick: (AiActionType) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "AI 操作",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            AiActionItem(
                icon = Icons.Default.AutoAwesome,
                title = "继续写",
                subtitle = "根据当前笔记续写一小段",
                onClick = { onActionClick(AiActionType.ContinueWriting) }
            )
            AiActionItem(
                icon = Icons.AutoMirrored.Filled.Article,
                title = "总结",
                subtitle = "提炼当前笔记的主要内容",
                onClick = { onActionClick(AiActionType.Summarize) }
            )
            AiActionItem(
                icon = Icons.Default.Title,
                title = "生成标题",
                subtitle = "根据正文生成一个短标题",
                onClick = { onActionClick(AiActionType.GenerateTitle) }
            )
        }
    }
}

@Composable
private fun AiActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) }
    )
}
