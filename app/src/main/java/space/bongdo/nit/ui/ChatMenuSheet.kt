package space.bongdo.nit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMenuSheet(
    hasChildren: Boolean,
    hasParent: Boolean,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 26.dp)) {
            Text("Чат", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    MenuRow(Icons.Outlined.Layers, "Список чатов") { onAction("chats") }
                    MenuRow(Icons.Outlined.Add, "Новый чат") { onAction("new") }
                    MenuRow(Icons.Outlined.FolderOpen, "Другая папка") { onAction("dir") }
                    MenuRow(Icons.Outlined.Settings, "Настройки") { onAction("settings") }
                    MenuRow(Icons.Outlined.Palette, "Тема") { onAction("theme") }
                    MenuRow(Icons.Outlined.Compress, "Сжать контекст") { onAction("compact") }
                }
                Column(Modifier.weight(1f)) {
                    MenuRow(Icons.Outlined.Undo, "Откатить") { onAction("revert") }
                    MenuRow(Icons.Outlined.ForkRight, "Форк") { onAction("fork") }
                    MenuRow(Icons.Outlined.ForkRight, "Форк здесь") { onAction("fork-here") }
                    MenuRow(Icons.Outlined.Refresh, "Повторить") { onAction("retry") }
                    if (hasChildren) MenuRow(Icons.Outlined.AccountTree, "Субагенты") { onAction("subagents") }
                    if (hasParent) MenuRow(Icons.Outlined.ArrowBack, "К родителю") { onAction("parent") }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
