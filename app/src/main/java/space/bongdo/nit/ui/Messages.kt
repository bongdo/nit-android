package space.bongdo.nit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.Part
import space.bongdo.nit.data.UserFile

@Composable
fun MessageItem(message: Message, onOpenHtml: (String) -> Unit) {
    when (message.type) {
        "user" -> UserMessage(message)
        "assistant" -> AssistantMessage(message, onOpenHtml)
        else -> Unit
    }
}

@Composable
private fun UserMessage(message: Message) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Column {
                message.text?.takeIf { it.isNotBlank() }?.let {
                    SelectionContainer {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                message.files?.forEach { file ->
                    Spacer(Modifier.height(6.dp))
                    FileRow(file)
                }
            }
        }
        Text(
            shortTime(message.time.created),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 3.dp, end = 4.dp),
        )
    }
}

@Composable
private fun FileRow(file: UserFile) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            file.name ?: "файл",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if ((file.bytes ?: 0) > 0) {
            Text(
                byteLabel(file.bytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssistantMessage(message: Message, onOpenHtml: (String) -> Unit) {
    val tools = message.content?.filter { it.type == "tool" } ?: emptyList()
    val reasoning = message.content?.count { it.type == "reasoning" } ?: 0
    val textParts = message.content?.filter { it.type == "text" && it.text.isNotBlank() } ?: emptyList()
    var expanded by remember(message.id) { mutableStateOf(false) }
    var copied by remember(message.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    fun copy() {
        val full = textParts.joinToString("\n\n") { it.text }
        if (full.isBlank()) return
        clipboard.setText(AnnotatedString(full))
        copied = true
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_400)
            copied = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NIT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!message.agent.isNullOrBlank()) {
                Text(
                    "  ${message.agent.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                shortTime(message.time.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(5.dp))

        if (textParts.isEmpty() && message.error == null) {
            Text(
                if (tools.isEmpty()) "…" else "готовлю ответ…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                textParts.forEach { part: Part ->
                    MarkdownText(part.text, onOpenHtml = onOpenHtml)
                }
            }
        }

        message.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            ErrorCard(error.message ?: error.name ?: "не получилось")
        }

        if (tools.isNotEmpty() || reasoning > 0) {
            Spacer(Modifier.height(7.dp))
            StepsToggle(
                tools = tools.size,
                reasoning = reasoning,
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )
            if (expanded) {
                StepsList(tools)
            }
        }

        if (textParts.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if ((message.cost ?: 0.0) > 0) {
                    Text(
                        if (message.cost!! < 0.01) "<\$0.01" else String.format(java.util.Locale.ROOT, "~\$%.2f", message.cost),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = "копировать",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(15.dp)
                        .clickableNoRipple(::copy),
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun StepsToggle(tools: Int, reasoning: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
            .clickableNoRipple(onToggle)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        val pieces = buildList {
            if (tools > 0) add("$tools ${plural(tools, "шаг", "шага", "шагов")}")
            if (reasoning > 0) add("размышления")
        }
        Text(
            pieces.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepsList(tools: List<Part>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        tools.forEach { tool ->
            val status = tool.state?.status ?: ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tool.name.ifBlank { "tool" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (status) {
                        "completed" -> "готово"
                        "error" -> "ошибка"
                        "running" -> "работает"
                        else -> "…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (status) {
                        "error" -> MaterialTheme.colorScheme.error
                        "completed" -> Success
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            val detail = tool.state?.input?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            if (!detail.isNullOrBlank()) {
                Text(
                    detail.take(220),
                    style = CodeStyle.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
