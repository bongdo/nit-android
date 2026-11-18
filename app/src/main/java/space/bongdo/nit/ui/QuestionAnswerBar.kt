package space.bongdo.nit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import space.bongdo.nit.data.FormInfo

fun FormInfo.isQuestionForm(): Boolean {
    val kind = (metadata as? kotlinx.serialization.json.JsonObject)
        ?.get("kind") as? kotlinx.serialization.json.JsonPrimitive
    if (kind?.content != "question") return false
    val visible = fields.filterNot { it.hidden == true }
    return visible.isNotEmpty() && visible.all { it.type == "string" || it.type.isBlank() }
}

@Composable
fun QuestionAnswerBar(
    form: FormInfo,
    onReply: (FormInfo, JsonObject) -> Unit,
    onCancel: (FormInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fields = form.fields.filterNot { it.hidden == true }
    val values = remember(form.id) { mutableStateMapOf<String, String>() }
    var step by remember(form.id) { mutableIntStateOf(0) }
    val current = fields.getOrNull(step.coerceIn(0, (fields.size - 1).coerceAtLeast(0)))
    if (current == null) return

    fun submit() {
        val answer = buildJsonObject {
            fields.forEach { field ->
                val value = values[field.key].orEmpty().trim()
                if (value.isNotEmpty()) put(field.key, JsonPrimitive(value))
            }
        }
        onReply(form, answer)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        form.title.ifBlank { current.title ?: "Question" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (fields.size > 1) "Question ${step + 1} of ${fields.size} · agent is waiting" else "Agent is waiting for you",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onCancel(form) }) { Text("Cancel") }
            }
            Spacer(Modifier.height(4.dp))
            Text(current.title ?: current.key, style = MaterialTheme.typography.bodyMedium)
            current.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!current.options.isNullOrEmpty()) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    current.options!!.forEach { option ->
                        val optionValue = option.value.ifBlank { option.label }
                        val selected = values[current.key] == optionValue
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { values[current.key] = optionValue }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                )
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (selected) "●" else "○",
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(option.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            option.description?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 9.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = values[current.key].orEmpty(),
                    onValueChange = { values[current.key] = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                if (values[current.key].orEmpty().isBlank()) {
                    Text(
                        current.placeholder ?: "Your answer…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (step > 0) {
                    TextButton(onClick = { step -= 1 }) { Text("Back") }
                }
                val filled = values[current.key].orEmpty().isNotBlank()
                if (step < fields.size - 1) {
                    TextButton(
                        onClick = { step += 1 },
                        enabled = current.required != true || filled,
                    ) { Text("Next") }
                } else {
                    TextButton(
                        onClick = { submit() },
                        enabled = current.required != true || filled,
                    ) { Text("Answer") }
                }
            }
        }
    }
}
