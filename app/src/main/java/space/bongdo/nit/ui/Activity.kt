package space.bongdo.nit.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import space.bongdo.nit.data.Part

private val VERBS = mapOf(
    "bash" to "Выполняет",
    "shell" to "Выполняет",
    "read" to "Читает",
    "write" to "Пишет",
    "edit" to "Правит",
    "multiedit" to "Правит",
    "patch" to "Патчит",
    "grep" to "Ищет",
    "glob" to "Ищет",
    "list" to "Смотрит",
    "ls" to "Смотрит",
    "task" to "Задача",
    "webfetch" to "Загружает",
    "websearch" to "Ищет",
    "todowrite" to "План",
    "todoread" to "План",
    "skill" to "Навык",
    "question" to "Спрашивает",
)

fun isActivity(part: Part): Boolean =
    part.type == "reasoning" || (part.type == "tool" && part.name != "question")

fun oneLine(value: String): String = value.replace(Regex("\\s+"), " ").trim()

private fun record(input: JsonElement?): JsonObject? = when (input) {
    is JsonObject -> input
    is JsonPrimitive -> input.contentOrNull
        ?.let { value -> runCatching { Json.parseToJsonElement(value) as? JsonObject }.getOrNull() }
    else -> null
}

private fun field(input: JsonElement?, key: String): String {
    val objectValue = record(input) ?: return ""
    val value = objectValue[key] as? JsonPrimitive ?: return ""
    return oneLine(value.contentOrNull.orEmpty())
}

private fun fallbackInput(input: JsonElement?): String = when (input) {
    is JsonPrimitive -> oneLine(input.contentOrNull.orEmpty()).take(100)
    is JsonObject -> oneLine(input.toString()).take(100)
    else -> ""
}

private fun shortPath(path: String, directory: String?): String {
    val root = directory?.trimEnd('/').orEmpty()
    return if (root.isNotEmpty() && path.startsWith("$root/")) path.removePrefix("$root/") else path
}

fun toolSummary(part: Part, directory: String? = null): Pair<String, String> {
    val input = part.state?.input
    val command = field(input, "command")
    val file = field(input, "filePath").ifEmpty { field(input, "file_path") }.ifEmpty { field(input, "path") }
    val pattern = field(input, "pattern")
    val query = field(input, "query").ifEmpty { field(input, "search") }
    val url = field(input, "url")
    val fallback = field(input, "description").ifEmpty { field(input, "prompt") }
        .ifEmpty { field(input, "name") }.ifEmpty { field(input, "skill") }
    val target = when {
        command.isNotEmpty() -> command
        pattern.isNotEmpty() && file.isNotEmpty() -> "$pattern in ${shortPath(file, directory)}"
        pattern.isNotEmpty() -> pattern
        file.isNotEmpty() -> shortPath(file, directory)
        query.isNotEmpty() -> query
        url.isNotEmpty() -> url
        fallback.isNotEmpty() -> fallback
        else -> fallbackInput(input)
    }
    return (VERBS[part.name] ?: part.name) to target
}

fun activityLine(part: Part, directory: String? = null): String {
    if (part.type == "reasoning") {
        val preview = thinkingPreview(part.text)
        return if (preview.isBlank()) "Думает" else "Думает · $preview"
    }
    val (verb, target) = toolSummary(part, directory)
    return if (target.isBlank()) verb else "$verb $target"
}

fun thinkingPreview(text: String, max: Int = 90): String {
    val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    return if (line.length > max) "${line.take(max - 1)}…" else line
}

fun durationLabel(start: Long, end: Long): String {
    val total = ((end - start).coerceAtLeast(0)) / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = (total % 60).toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$seconds"
    } else {
        "$minutes:$seconds"
    }
}
