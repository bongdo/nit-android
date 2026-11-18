package space.bongdo.nit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import space.bongdo.nit.data.Part

@Composable
fun StepsRow(
    summary: String,
    liveStats: String,
    running: Boolean,
    loading: Boolean,
    expanded: Boolean,
    hasErrors: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickableNoRipple(onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
            contentDescription = if (expanded) "Collapse steps" else "Show steps",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        if (running || loading) {
            Spacer(Modifier.width(5.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.3.dp,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            summary,
            style = MaterialTheme.typography.labelSmall,
            color = if (running) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (liveStats.isNotBlank()) {
            Text(
                liveStats,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
        if (hasErrors) {
            Spacer(Modifier.width(5.dp))
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = "errors in steps",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
fun ActivityLog(parts: List<Part>, running: Boolean, directory: String?) {
    Column(Modifier.fillMaxWidth().padding(start = 6.dp, top = 4.dp, bottom = 4.dp)) {
        parts.forEachIndexed { index, part ->
            if (part.type == "reasoning") {
                ReasoningRow(part, streaming = running && index == parts.lastIndex)
            } else if (part.type == "tool" && part.name != "question") {
                ToolRow(part, directory)
            }
        }
    }
}

@Composable
fun StepNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun ReasoningRow(part: Part, streaming: Boolean) {
    var open by remember(part.text.length) { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickableNoRipple { open = !open }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (open) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (streaming) "Thinking" else "Thought",
                style = MaterialTheme.typography.labelSmall,
                color = if (streaming) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                thinkingPreview(part.text),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (open) {
            Text(
                part.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 26.dp, end = 8.dp, bottom = 6.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun ToolRow(part: Part, directory: String?) {
    var open by remember(part.id) { mutableStateOf(false) }
    val status = part.state?.status.orEmpty()
    val (verb, target) = toolSummary(part, directory)
    val body = part.state?.input
    val content = part.state?.content.orEmpty()
    val error = part.state?.error?.message
    val inputText = when (body) {
        is JsonPrimitive -> body.contentOrNull.orEmpty()
        null -> ""
        else -> body.toString()
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickableNoRipple { open = !open }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (open) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            when (status) {
                "running", "streaming" -> CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.3.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                "completed" -> Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(13.dp),
                )
                "error" -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp),
                )
                else -> androidx.compose.foundation.layout.Box(
                    Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                verb,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (target.isNotBlank()) {
                Spacer(Modifier.width(5.dp))
                Text(
                    target,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (open) {
            Column(
                modifier = Modifier.padding(start = 26.dp, end = 8.dp, top = 2.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (inputText.isNotBlank() && inputText != "null") {
                    Text(
                        inputText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                content.forEach { item ->
                    val text = item.text ?: item.uri ?: item.url ?: item.name
                    if (!text.isNullOrBlank()) {
                        Text(
                            text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!error.isNullOrBlank()) {
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (inputText.isBlank() && content.isEmpty() && error.isNullOrBlank()) {
                    Text(
                        "no details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
