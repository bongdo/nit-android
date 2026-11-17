package space.bongdo.nit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.delay
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.Part
import space.bongdo.nit.data.UserFile

fun displayMessages(messages: List<Message>, running: Boolean): List<Message> {
    val out = mutableListOf<Message>()
    var index = 0
    while (index < messages.size) {
        val message = messages[index]
        if (message.type == "assistant") {
            val group = mutableListOf(message)
            var next = index + 1
            while (next < messages.size && messages[next].type == "assistant") {
                group += messages[next]
                next += 1
            }
            val merged = message.copy(
                content = group.flatMap { it.content.orEmpty() },
                error = group.lastOrNull { it.error != null }?.error,
                cost = group.sumOf { it.cost ?: 0.0 },
                tokens = group.lastOrNull { it.tokens != null }?.tokens,
                agent = group.firstOrNull { !it.agent.isNullOrBlank() }?.agent,
                time = group.first().time,
            )
            val visible = merged.content.orEmpty().any { part ->
                (part.type == "text" && part.text.isNotBlank()) ||
                    (part.type == "tool" && part.name == "question" && questionRecords(part).any { it.answers.isNotEmpty() })
            } || merged.error != null
            val last = running && next == messages.size
            if (visible || last) out += merged
            index = next
        } else if (message.type == "user") {
            out += message
            index += 1
        } else {
            index += 1
        }
    }
    return out
}

@Composable
fun MessageItem(
    message: Message,
    running: Boolean,
    onOpenHtml: (String, String) -> Unit,
    onOpenArtifact: (String, String) -> Unit,
    onOpenFile: (String, String) -> Unit,
) {
    when (message.type) {
        "user" -> UserMessage(message, onOpenFile)
        "assistant" -> AssistantMessage(message, running, onOpenHtml, onOpenArtifact, onOpenFile)
        else -> Unit
    }
}

@Composable
private fun UserMessage(message: Message, onOpenFile: (String, String) -> Unit) {
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
                    SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                message.files?.forEach { file ->
                    Spacer(Modifier.height(6.dp))
                    FileRow(file, onOpenFile)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp, end = 4.dp)) {
            if (message.delivery == "steer" || message.delivery == "queue") {
                Text(
                    if (message.delivery == "steer") "в текущий ход · " else "в очередь · ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                )
            }
            Text(
                shortTime(message.time.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun FileRow(file: UserFile, onOpenFile: (String, String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .clickable {
                val uri = file.uri ?: return@clickable
                onOpenFile(uri, file.name ?: "Файл")
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            fileIconFor(file.name),
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
private fun AssistantMessage(
    message: Message,
    running: Boolean,
    onOpenHtml: (String, String) -> Unit,
    onOpenArtifact: (String, String) -> Unit,
    onOpenFile: (String, String) -> Unit,
) {
    val parts = message.content.orEmpty()
    val questions = parts.filter { it.type == "tool" && it.name == "question" }
    val textParts = parts.filter { it.type == "text" && it.text.isNotBlank() }
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
            Text("NIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (!message.agent.isNullOrBlank()) {
                Text(
                    "  ${message.agent.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (running && textParts.isNotEmpty()) {
                Text(
                    "  работает",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
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

        val answeredQuestion = questions.any { part -> questionRecords(part).any { it.answers.isNotEmpty() } }
        if (textParts.isEmpty() && !answeredQuestion && message.error == null && running) {
            TypingLine()
        }

        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                textParts.forEach { part: Part ->
                    MarkdownText(
                        text = part.text,
                        onOpenHtml = { source, title -> onOpenHtml(source, title) },
                        onOpenArtifact = onOpenArtifact,
                        onOpenFile = onOpenFile,
                    )
                }
            }
        }

        questions.forEach { part ->
            val answered = questionRecords(part).filter { it.answers.isNotEmpty() }
            if (answered.isNotEmpty()) QuestionCard(answered)
        }

        message.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            ErrorCard(error.message ?: error.name ?: "не получилось")
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
                    modifier = Modifier.size(15.dp).clickableNoRipple(::copy),
                )
            }
        }
    }
}

@Composable
fun TypingLine() {
    Text(
        "печатает…",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
