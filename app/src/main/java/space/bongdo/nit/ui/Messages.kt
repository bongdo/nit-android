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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.CircularProgressIndicator
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
import space.bongdo.nit.data.EditedFile
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.Part
import space.bongdo.nit.data.UserFile

fun displayMessages(
    messages: List<Message>,
    running: Boolean,
    details: Map<String, Message> = emptyMap(),
): List<Message> {
    val view = if (details.isEmpty()) messages else messages.map { details[it.id] ?: it }
    val out = mutableListOf<Message>()
    var index = 0
    while (index < view.size) {
        val message = view[index]
        if (message.type == "assistant") {
            val group = mutableListOf(message)
            var next = index + 1
            while (next < view.size && view[next].type == "assistant") {
                group += view[next]
                next += 1
            }
            val source = messages.subList(index, next)
            val activity = group.flatMap { it.content.orEmpty() }.filter { isActivity(it) }
            val markers = source.filter { details[it.id] == null }
            val markerSteps = markers.sumOf { it.steps ?: 0 }
            val markerTools = markers.sumOf { it.tools ?: 0 }
            val markerErrors = markers.sumOf { it.errors ?: 0 }
            val shownSteps = activity.size + markerSteps
            val shownTools = activity.count { it.type == "tool" } + markerTools
            val shownErrors = activity.count { it.type == "tool" && it.state?.status == "error" } + markerErrors
            val first = group.first()
            val last = group.last()
            val end = last.time.completed ?: last.time.streamed ?: last.time.created
            val merged = first.copy(
                content = group.flatMap { it.content.orEmpty() },
                error = group.lastOrNull { it.error != null }?.error,
                cost = group.sumOf { it.cost ?: 0.0 },
                tokens = group.lastOrNull { it.tokens != null }?.tokens,
                agent = group.firstOrNull { !it.agent.isNullOrBlank() }?.agent,
                edited = group.flatMap { it.edited.orEmpty() }
                    .groupBy { it.path }
                    .map { (path, files) ->
                        EditedFile(
                            path = path,
                            additions = files.sumOf { it.additions ?: 0 },
                            deletions = files.sumOf { it.deletions ?: 0 },
                        )
                    },
                time = first.time.copy(completed = end),
                sourceIDs = source.map { it.id },
                shownSteps = shownSteps,
                shownTools = shownTools,
                shownErrors = shownErrors,
            )
            val visible = merged.content.orEmpty().any { part ->
                (part.type == "text" && part.text.isNotBlank()) ||
                    (part.type == "tool" && part.name == "question" && questionRecords(part).any { it.answers.isNotEmpty() })
            } || merged.error != null || shownSteps > 0
            val lastRunning = running && next == view.size
            if (visible || lastRunning) out += merged
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
    directory: String?,
    stepsLoading: Boolean,
    imageModel: (UserFile) -> Any?,
    onLoadSteps: (List<String>) -> Unit,
    onRevert: (Message) -> Unit,
    onFork: (Message) -> Unit,
    onOpenHtml: (String, String) -> Unit,
    onOpenArtifact: (String, String) -> Unit,
    onOpenFile: (String, String) -> Unit,
) {
    when (message.type) {
        "user" -> UserMessage(message, running, imageModel, onRevert, onFork, onOpenHtml, onOpenArtifact, onOpenFile)
        "assistant" -> AssistantMessage(
            message = message,
            running = running,
            directory = directory,
            stepsLoading = stepsLoading,
            onLoadSteps = onLoadSteps,
            onOpenHtml = onOpenHtml,
            onOpenArtifact = onOpenArtifact,
            onOpenFile = onOpenFile,
        )
        "compaction" -> CompactionRow(message)
        "shell" -> ShellCard(message)
        "skill" -> NoteLine("Skill ${message.name.orEmpty()}".trim())
        "system", "synthetic" -> {
            val note = message.description?.takeIf { it.isNotBlank() }
                ?: if (message.type == "system") "Instructions updated" else ""
            if (note.isNotBlank()) NoteLine(note)
        }
        "idle" -> ErrorCard(message.error?.message ?: "the run failed")
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
    running: Boolean,
    imageModel: (UserFile) -> Any?,
    onRevert: (Message) -> Unit,
    onFork: (Message) -> Unit,
    onOpenHtml: (String, String) -> Unit,
    onOpenArtifact: (String, String) -> Unit,
    onOpenFile: (String, String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }
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
                        contentDescription = file.name ?: "photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val uri = file.uri ?: return@clickable
                                onOpenFile(uri, file.name ?: "Photo")
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 2.dp),
        ) {
            if (message.delivery == "steer" || message.delivery == "queue") {
                Text(
                    if (message.delivery == "steer") "steered · " else "queued · ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                )
            }
            Text(
                shortTime(message.time.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(4.dp))
            UserAction(
                icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                label = "Copy",
                onClick = {
                    clipboard.setText(AnnotatedString(message.text.orEmpty()))
                    copied = true
                },
            )
            UserAction(
                icon = Icons.Outlined.Undo,
                label = if (running) "Stop and edit" else "Edit",
                accent = running,
                onClick = { onRevert(message) },
            )
            UserAction(
                icon = Icons.Outlined.ForkRight,
                label = "Fork before this message",
                onClick = { onFork(message) },
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
                onOpenFile(uri, file.name ?: "File")
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
            file.name ?: "file",
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
    directory: String?,
    stepsLoading: Boolean,
    onLoadSteps: (List<String>) -> Unit,
    onOpenHtml: (String, String) -> Unit,
    onOpenArtifact: (String, String) -> Unit,
    onOpenFile: (String, String) -> Unit,
) {
    val parts = message.content.orEmpty()
    val questions = parts.filter { it.type == "tool" && it.name == "question" }
    val textParts = parts.filter { it.type == "text" && it.text.isNotBlank() }
    val activity = parts.filter { isActivity(it) }
    val current = activity.lastOrNull()
    var stepsOpen by remember(message.id) { mutableStateOf(false) }
    var copied by remember(message.id) { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

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

    val duration = if (message.time.created > 0) {
        durationLabel(message.time.created, if (running) now else message.time.completed ?: now)
    } else {
        ""
    }
    val stats = buildList {
        if (message.shownSteps > 0) add("${message.shownSteps} steps")
        if (message.shownTools > 0) add("${message.shownTools} tools")
        if (message.shownErrors > 0) add("${message.shownErrors} errors")
        if (duration.isNotBlank()) add(duration)
    }.joinToString(" · ")
    val liveStats = buildList {
        if (message.shownSteps > 0) add("${message.shownSteps} steps")
        if (duration.isNotBlank()) add(duration)
    }.joinToString(" · ")
    val summary = when {
        running && current != null -> activityLine(current, directory)
        running && textParts.isEmpty() -> "Waiting for the first token…"
        running -> "Typing"
        else -> stats
    }
    val showSteps = running || message.shownSteps > 0
    val edited = message.edited.orEmpty()
    var filesOpen by remember(message.id) { mutableStateOf(false) }

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
            Spacer(Modifier.weight(1f))
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.4.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (duration.isNotBlank()) {
                    Text(
                        "  $duration",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else {
                Text(
                    shortTime(message.time.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(5.dp))

        val answeredQuestion = questions.any { part -> questionRecords(part).any { it.answers.isNotEmpty() } }
        if (running && showSteps) {
            StepsRow(
                summary = summary,
                liveStats = liveStats,
                running = true,
                loading = stepsLoading && activity.isEmpty(),
                expanded = stepsOpen,
                hasErrors = message.shownErrors > 0,
                onToggle = {
                    stepsOpen = !stepsOpen
                    if (stepsOpen) onLoadSteps(message.sourceIDs)
                },
            )
            if (stepsOpen) {
                when {
                    activity.isNotEmpty() -> ActivityLog(activity, true, directory)
                    stepsLoading -> StepNote("Loading steps…")
                    else -> StepNote("No steps were recorded for this turn.")
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        if (textParts.isEmpty() && activity.isEmpty() && !answeredQuestion && message.error == null && running) {
            Spacer(Modifier.height(4.dp))
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
            ErrorCard(error.message ?: error.name ?: "Something went wrong")
        }

        if (!running && (textParts.isNotEmpty() || showSteps || edited.isNotEmpty())) {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showSteps) {
                    FooterButton(
                        icon = Icons.Outlined.FormatListBulleted,
                        label = "Steps · ${message.shownSteps}",
                        onClick = {
                            stepsOpen = !stepsOpen
                            if (stepsOpen) onLoadSteps(message.sourceIDs)
                        },
                    )
                }
                if (textParts.isNotEmpty()) {
                    FooterButton(
                        icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        label = if (copied) "Copied" else "Copy",
                        onClick = ::copy,
                    )
                }
                Spacer(Modifier.weight(1f))
                if ((message.cost ?: 0.0) > 0) {
                    Text(
                        if (message.cost!! < 0.01) "<\$0.01" else String.format(java.util.Locale.ROOT, "~\$%.2f", message.cost),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            if (showSteps && stepsOpen) {
                when {
                    activity.isNotEmpty() -> ActivityLog(activity, false, directory)
                    stepsLoading -> StepNote("Loading steps…")
                    else -> StepNote("No steps were recorded for this turn.")
                }
            }
            if (edited.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                FooterButton(
                    icon = Icons.Outlined.Description,
                    label = "${edited.size} ${if (edited.size == 1) "file" else "files"}",
                    trailing = if (filesOpen) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    onClick = { filesOpen = !filesOpen },
                )
                if (filesOpen) {
                    edited.forEach { file ->
                        EditedFileRow(file, onOpenFile)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (accent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun FooterButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(2.dp))
            Icon(
                trailing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun EditedFileRow(file: EditedFile, onOpenFile: (String, String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                onOpenFile(file.path, file.path.substringAfterLast('/'))
            }
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            file.path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if ((file.additions ?: 0) > 0) {
            Text("+${file.additions}", style = MaterialTheme.typography.labelSmall, color = successColorOrTertiary())
        }
        if ((file.deletions ?: 0) > 0) {
            Spacer(Modifier.width(5.dp))
            Text("−${file.deletions}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun successColorOrTertiary(): androidx.compose.ui.graphics.Color {
    val success = LocalSuccess.current
    return if (success == androidx.compose.ui.graphics.Color.Unspecified) {
        MaterialTheme.colorScheme.tertiary
    } else {
        success
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
            Text("Compacting context…", style = MaterialTheme.typography.labelSmall)
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
                Text("Context compacted", style = MaterialTheme.typography.labelSmall)
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
                        contentDescription = if (open) "collapse" else "show",
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
                    "SUMMARY" + when (message.reason) {
                        "manual" -> " · ON REQUEST"
                        "auto" -> " · AUTOMATIC"
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
        "typing…",
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
