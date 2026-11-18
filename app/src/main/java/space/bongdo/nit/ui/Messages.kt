package space.bongdo.nit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
        } else if (message.type == "user" || message.type == "compaction" ||
            message.type == "shell" || message.type == "skill" ||
            message.type == "system" ||
            (message.type == "synthetic" && !message.description.isNullOrBlank()) ||
            (message.type == "idle" && message.error?.message != null)
        ) {
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
    imageModel: (UserFile) -> Any?,
    onOpenHtml: (String, String) -> Unit,
    onOpenArtifact: (String, String) -> Unit,
    onOpenFile: (String, String) -> Unit,
) {
    when (message.type) {
        "user" -> UserMessage(message, imageModel, onOpenHtml, onOpenArtifact, onOpenFile)
        "assistant" -> AssistantMessage(message, running, onOpenHtml, onOpenArtifact, onOpenFile)
        "compaction" -> CompactionRow(message)
        "shell" -> ShellCard(message)
        "skill" -> NoteLine("Skill ${message.name.orEmpty()}".trim())
        "system", "synthetic" -> {
            val note = message.description?.takeIf { it.isNotBlank() }
                ?: if (message.type == "system") "Инструкции обновлены" else ""
            if (note.isNotBlank()) NoteLine(note)
        }
        "idle" -> ErrorCard(message.error?.message ?: "ход завершился с ошибкой")
        else -> Unit
    }
}

@Composable
private fun NoteLine(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun ShellCard(message: Message) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                message.command.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                message.status.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message.output?.output?.takeIf { it.isNotBlank() }?.let { output ->
            Spacer(Modifier.height(6.dp))
            Text(
                output,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 20,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UserMessage(
    message: Message,
    imageModel: (UserFile) -> Any?,
    onOpenHtml: (String, String) -> Unit,
    onOpenArtifact: (String, String) -> Unit,
    onOpenFile: (String, String) -> Unit,
) {
    val models = remember(message.files) { message.files.orEmpty().map { it to imageModel(it) } }
    val images = models.filter { it.second != null }
    val files = models.filter { it.second == null }.map { it.first }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Column {
                images.forEachIndexed { index, (file, model) ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    AsyncImage(
                        model = model,
                        contentDescription = file.name ?: "фото",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val uri = file.uri ?: return@clickable
                                onOpenFile(uri, file.name ?: "Фото")
                            },
                    )
                }
                message.text?.takeIf { it.isNotBlank() }?.let {
                    if (images.isNotEmpty()) Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        MarkdownText(
                            text = it,
                            onOpenHtml = onOpenHtml,
                            onOpenArtifact = onOpenArtifact,
                            onOpenFile = onOpenFile,
                        )
                    }
                }
                files.forEach { file ->
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
fun CompactionBusyLine() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text("Сжимаю контекст…", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CompactionRow(message: Message) {
    val busy = message.status == "running" || message.status == "pending"
    if (busy) {
        CompactionBusyLine()
        return
    }
    val summary = message.summary?.trim().orEmpty()
    var open by remember(message.id) { mutableStateOf(false) }
    val tokens = message.tokens?.input ?: 0
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(
                Modifier
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .then(if (summary.isNotBlank()) Modifier.clickable { open = !open } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text("Контекст сжат", style = MaterialTheme.typography.labelSmall)
                if (tokens > 0) {
                    Text(
                        " · ${formatTokens(tokens)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.isNotBlank()) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (open) "свернуть" else "показать",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = 6.dp, end = 6.dp)) {
                Text(
                    "КРАТКОЕ СОДЕРЖАНИЕ" + when (message.reason) {
                        "manual" -> " · ПО ЗАПРОСУ"
                        "auto" -> " · АВТОМАТИЧЕСКИ"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                SelectionContainer {
                    MarkdownText(
                        text = summary,
                        onOpenHtml = { _, _ -> },
                        onOpenArtifact = { _, _ -> },
                        onOpenFile = { _, _ -> },
                    )
                }
            }
        }
    }
}

private fun formatTokens(value: Long): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0)
    else -> value.toString()
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
