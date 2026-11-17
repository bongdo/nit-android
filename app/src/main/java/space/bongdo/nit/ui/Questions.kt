package space.bongdo.nit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import space.bongdo.nit.data.Part

data class QuestionOption(val label: String, val description: String? = null)
data class QuestionRecord(
    val header: String? = null,
    val question: String = "",
    val options: List<QuestionOption> = emptyList(),
    val answers: List<String> = emptyList(),
)

private val looseJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun questionRecords(part: Part): List<QuestionRecord> {
    val questions = parseQuestions(part.state?.input)
    if (questions.isEmpty()) return emptyList()
    val fallback = part.state?.content
        ?.mapNotNull { it.text }
        ?.filter { it.isNotBlank() }
        ?.joinToString("\n")
        .orEmpty()
    val answers = parseAnswers(part.state?.metadata, questions, fallback)
    return questions.mapIndexed { index, item -> item.copy(answers = answers.getOrNull(index).orEmpty()) }
}

private fun parseQuestions(input: JsonElement?): List<QuestionRecord> {
    val value = input ?: return emptyList()
    val items = questionItems(value)
    return items.mapNotNull { normalizeQuestion(it) }
}

private fun questionItems(value: JsonElement): List<JsonElement> {
    if (value is JsonPrimitive) {
        val text = value.contentOrNull.orEmpty().trim()
        if (text.startsWith("{") || text.startsWith("[")) {
            val parsed = runCatching { looseJson.parseToJsonElement(text) }.getOrNull()
            if (parsed != null) return questionItems(parsed)
        }
        return if (text.isBlank()) emptyList() else listOf(value)
    }
    if (value is JsonArray) return value.flatMap { questionItems(it) }
    if (value !is JsonObject) return emptyList()
    value["questions"]?.let { return questionItems(it) }
    if (value.containsKey("question") || value.containsKey("options")) return listOf(value)
    return emptyList()
}

private fun normalizeQuestion(value: JsonElement): QuestionRecord? {
    if (value is JsonPrimitive) {
        val text = value.contentOrNull.orEmpty().trim()
        return if (text.isBlank()) null else QuestionRecord(question = text)
    }
    if (value !is JsonObject) return null
    val question = (value["question"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
    val options = normalizeOptions(value["options"])
    if (question.isBlank() && options.isEmpty()) return null
    val header = (value["header"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    return QuestionRecord(header = header, question = question.ifBlank { "Вопрос" }, options = options)
}

private fun normalizeOptions(value: JsonElement?): List<QuestionOption> {
    val array = value as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> item.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let { QuestionOption(it) }
            is JsonObject -> {
                val label = (item["label"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
                if (label.isBlank()) null else QuestionOption(
                    label = label,
                    description = (item["description"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
                )
            }
            else -> null
        }
    }
}

private fun parseAnswers(metadata: JsonElement?, questions: List<QuestionRecord>, fallback: String): List<List<String>> {
    val value = (metadata as? JsonObject)?.get("answers")
    val groups = when (value) {
        is JsonArray -> {
            if (value.all { it is JsonPrimitive }) {
                val flat = value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
                if (questions.size > 1) flat.map { listOf(it) } else listOf(flat)
            } else {
                value.map { stringsOf(it) }
            }
        }
        is JsonObject -> value.values.map { stringsOf(it) }
        else -> emptyList()
    }
    if (groups.any { it.isNotEmpty() }) {
        return questions.mapIndexed { index, _ -> groups.getOrNull(index).orEmpty() }
    }
    return questions.mapIndexed { index, _ -> if (index == 0 && fallback.isNotBlank()) listOf(fallback) else emptyList() }
}

private fun stringsOf(value: JsonElement): List<String> = when (value) {
    is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
    else -> emptyList()
}

@Composable
fun QuestionCard(questions: List<QuestionRecord>, modifier: Modifier = Modifier) {
    if (questions.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        questions.forEach { item ->
            Column {
                item.header?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(item.question, style = MaterialTheme.typography.bodyMedium)
                if (item.answers.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.85f)
                                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 4.dp, topStart = 14.dp, topEnd = 14.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                        ) {
                            Column {
                                Box(
                                    Modifier
                                        .padding(bottom = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 1.dp, vertical = 0.dp),
                                ) {
                                    Text(
                                        "  ассистент  ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    item.answers.joinToString(", "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                )
                            }
                        }
                    }
                } else if (item.options.isNotEmpty()) {
                    Column(Modifier.padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        item.options.forEach { option ->
                            Row {
                                Text("○", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    buildString {
                                        append(option.label)
                                        option.description?.let { append(" — $it") }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
