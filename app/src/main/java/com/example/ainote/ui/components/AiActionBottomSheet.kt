package com.example.ainote.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.WorkspacePremium
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
                text = "\u0041\u0049 \u64cd\u4f5c",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            AiActionItem(
                icon = Icons.Default.AutoAwesome,
                title = "\u7ee7\u7eed\u5199",
                subtitle = "\u6839\u636e\u5f53\u524d\u7b14\u8bb0\u7eed\u5199\u4e00\u5c0f\u6bb5",
                onClick = { onActionClick(AiActionType.ContinueWriting) }
            )
            AiActionItem(
                icon = Icons.Default.OpenInFull,
                title = "\u6269\u5199",
                subtitle = "\u5c06\u9009\u4e2d\u5185\u5bb9\u6216\u5f53\u524d\u5185\u5bb9\u5199\u5f97\u66f4\u5b8c\u6574",
                onClick = { onActionClick(AiActionType.Expand) }
            )
            AiActionItem(
                icon = Icons.Default.WorkspacePremium,
                title = "\u6539\u5f97\u66f4\u6b63\u5f0f",
                subtitle = "\u4f18\u5148\u6539\u5199\u9009\u4e2d\u6587\u672c",
                onClick = { onActionClick(AiActionType.Formal) }
            )
            AiActionItem(
                icon = Icons.Default.Compress,
                title = "\u6539\u5f97\u66f4\u7b80\u6d01",
                subtitle = "\u538b\u7f29\u8868\u8ff0\uff0c\u4fdd\u7559\u6838\u5fc3\u610f\u601d",
                onClick = { onActionClick(AiActionType.Concise) }
            )
            AiActionItem(
                icon = Icons.Default.Checklist,
                title = "\u6574\u7406\u6210\u5f85\u529e",
                subtitle = "\u8f6c\u6210\u53ef\u52fe\u9009\u7684\u4efb\u52a1\u5217\u8868",
                onClick = { onActionClick(AiActionType.Todo) }
            )
            AiActionItem(
                icon = Icons.AutoMirrored.Filled.Article,
                title = "\u603b\u7ed3",
                subtitle = "\u63d0\u70bc\u5f53\u524d\u7b14\u8bb0\u7684\u4e3b\u8981\u5185\u5bb9",
                onClick = { onActionClick(AiActionType.Summarize) }
            )
            AiActionItem(
                icon = Icons.Default.Title,
                title = "\u751f\u6210\u6807\u9898",
                subtitle = "\u6839\u636e\u6b63\u6587\u751f\u6210\u4e00\u4e2a\u77ed\u6807\u9898",
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
