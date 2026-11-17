package space.bongdo.nit.ui

data class HtmlArtifact(val path: String = "", val source: String = "", val title: String = "")
data class RichSegment(val text: String = "", val artifacts: List<HtmlArtifact> = emptyList())

private val marker = Regex("""\[\[html:([^\u005D|\r\n]+?)(?:\|([^\u005D\r\n]*))?]]""")
private val htmlStart = Regex("""^<(?:!doctype\s+html|html[\s>]|[a-z][\w-]*[\s>/])""", RegexOption.IGNORE_CASE)
private val htmlEnd = Regex("""</[a-z][\w:-]*>\s*$""", RegexOption.IGNORE_CASE)

private fun withoutCode(text: String): String = text
    .replace(Regex("""(`{3,}|~{3,})[\s\S]*?\1"""), " ")
    .replace(Regex("""`[^`\n]+`"""), " ")

fun looksLikeHtml(text: String): Boolean {
    val clean = withoutCode(text)
    return Regex("""<(?:h[1-6]|p|div|table|tr|td|ul|ol|li|section|article|header|footer|main|nav|form|button|style|svg|canvas|video|audio|figure|details)\b""", RegexOption.IGNORE_CASE)
        .containsMatchIn(clean) || clean.contains("<!doctype html", ignoreCase = true) || clean.contains("<html", ignoreCase = true)
}

fun isPureHtml(text: String): Boolean {
    val value = text.trim()
    if (!looksLikeHtml(value)) return false
    if (!htmlStart.containsMatchIn(value)) return false
    return htmlEnd.containsMatchIn(value)
}

fun richSegments(text: String): List<RichSegment> {
    if (isPureHtml(text)) return listOf(RichSegment(artifacts = listOf(HtmlArtifact(source = text))))
    val safe = text.replace(Regex("""(`{3,}|~{3,})[\s\S]*?\1""")) { " ".repeat(it.value.length) }
    val inserts = marker.findAll(safe).mapNotNull { match ->
        val path = match.groupValues[1].trim().removePrefix("./")
        if (!validArtifactPath(path)) return@mapNotNull null
        match.range to HtmlArtifact(path = path, title = match.groupValues[2].trim())
    }.toList()
    if (inserts.isEmpty()) return listOf(RichSegment(text = text))
    val out = mutableListOf<RichSegment>()
    var cursor = 0
    for ((range, artifact) in inserts) {
        if (range.first < cursor) continue
        out += RichSegment(text = text.substring(cursor, range.first))
        out += RichSegment(artifacts = listOf(artifact))
        cursor = range.last + 1
    }
    out += RichSegment(text = text.substring(cursor))
    return out
}

private fun validArtifactPath(path: String): Boolean {
    if (path.isBlank() || path.length > 300) return false
    if (path.startsWith("/") || path.contains('\\')) return false
    if (!Regex("""^[A-Za-z0-9._/-]+$""").matches(path)) return false
    if (!Regex("""\.html?$""", RegexOption.IGNORE_CASE).containsMatchIn(path)) return false
    return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}

data class FileTarget(val path: String)

fun fileTarget(raw: String): FileTarget? {
    val text = raw.trim()
    if (text.isEmpty() || text.any(Char::isWhitespace)) return null
    if (Regex("""^[a-z][a-z0-9+.-]*://""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
    if (text.startsWith("~") || text.contains("../..")) return null
    var path = text
    val lines = Regex("""^(.*):(\d{1,6})(?:-(\d{1,6}))?$""").find(text)
    if (lines != null) path = lines.groupValues[1]
    if (path.endsWith("/")) return null
    val parts = path.split('/').filter { it.isNotBlank() && it != "." }
    if (parts.isEmpty() || parts.any { it == ".." || !Regex("""^[A-Za-z0-9_.@+-]+$""").matches(it) }) return null
    if (parts.size == 1) {
        if (!Regex("""\.(md|markdown|txt|ts|tsx|js|jsx|mjs|cjs|go|py|json|jsonl|ya?ml|toml|css|scss|html?|sh|bash|zsh|sql|rs|java|kt|kts|c|h|cpp|hpp|cs|rb|php|swift|xml|ini|conf|lock|mod|sum|env|svg|c3|zig|nim|str|dart|nix|lua|hs|ex|exs|erl|clj|scala|sol|vue|svelte)$""", RegexOption.IGNORE_CASE).containsMatchIn(path)) return null
    }
    return FileTarget(path)
}

fun resolvePath(path: String, directory: String?): String {
    if (path.startsWith("/")) return path
    val root = directory?.takeIf { it.startsWith("/") } ?: return path
    val parts = "$root/$path".split('/')
    val clean = mutableListOf<String>()
    for (part in parts) {
        when (part) {
            "", "." -> Unit
            ".." -> if (clean.isNotEmpty()) clean.removeAt(clean.size - 1)
            else -> clean += part
        }
    }
    return "/" + clean.joinToString("/")
}
