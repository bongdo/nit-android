package space.bongdo.nit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Layers
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.bongdo.nit.data.Session

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMenuSheet(
    hasParent: Boolean,
    children: List<Session>,
    runningIds: Set<String>,
    onDismiss: () -> Unit,
    onOpenSubagent: (Session) -> Unit,
    onAction: (String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 26.dp),
        ) {
            Text("Чат", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    MenuRow(Icons.Outlined.Layers, "Список чатов") { onAction("chats") }
                    MenuRow(Icons.Outlined.Add, "Новый чат") { onAction("new") }
                    MenuRow(Icons.Outlined.FolderOpen, "Другая папка") { onAction("dir") }
                    MenuRow(Icons.Outlined.Settings, "Настройки") { onAction("settings") }
                    MenuRow(Icons.Outlined.Palette, "Тема") { onAction("theme") }
                }
                Column(Modifier.weight(1f)) {
                    MenuRow(Icons.Outlined.Compress, "Сжать контекст") { onAction("compact") }
                    MenuRow(Icons.Outlined.Undo, "Откатить") { onAction("revert") }
                    MenuRow(Icons.Outlined.ForkRight, "Форк") { onAction("fork") }
                    MenuRow(Icons.Outlined.ForkRight, "Форк здесь") { onAction("fork-here") }
                    MenuRow(Icons.Outlined.Refresh, "Повторить") { onAction("retry") }
                    if (hasParent) MenuRow(Icons.Outlined.ArrowBack, "К родителю") { onAction("parent") }
                }
            }
            if (children.isNotEmpty()) {
                Text(
                    "СУБАГЕНТЫ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                )
                children.sortedByDescending { it.time.updated }.forEach { child ->
                    SubagentRow(
                        session = child,
                        running = child.id in runningIds,
                        onClick = { onOpenSubagent(child) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubagentRow(session: Session, running: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.title?.takeIf { it.isNotBlank() } ?: "Субагент",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (running) "работает · " else "") + shortTime(session.time.updated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
