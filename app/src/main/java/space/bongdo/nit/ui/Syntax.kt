package space.bongdo.nit.ui

enum class SyntaxKind {
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,
    PATH,
    URL,
    PUNCTUATION,
    BRACKET1,
    BRACKET2,
    BRACKET3,
    BRACKET4,
    BRACKET5,
    BRACKET6,
}

data class SyntaxSpan(val start: Int, val end: Int, val kind: SyntaxKind)

private val KEYWORDS: Set<String> = """
    abstract alignas alignof and_eq asm assert async auto await base bitand bitor bool box
    break byte case catch chan char class compl concept const consteval constexpr constinit
    const_cast constructor continue crate co_await co_return co_yield debugger decimal def
    defer defined del delete descending didSet do double dynamic dynamic_cast elif else
    elseif enum ensure equals esac event except explicit export extends extern fallthrough
    false fi fileprivate final finally float fn for foreach friend from func function get
    global go goto group guard if implements implicit import in inline inout instanceof int
    interface internal into is join keyof lambda lazy let local lock long loop map match mod
    module move mut mutating namespace native never new next nil none nonlocal not not_eq
    null nullptr object on operator or or_eq out override package params partial pass print
    private protected protocol pub public raise range readonly record redo ref reinterpret_cast
    remove repeat required rescue retry rethrows return sealed select self set short sizeof
    static static_assert static_cast struct subscript super switch synchronized table template
    then this thread_local throw throws trait true try type typealias typeid typedef typename
    typeof union unknown unsafe unsigned until unset use using value var virtual void volatile
    wchar_t weak when where while willSet with xor xor_eq yield
    bitstruct comptime discard errdefer fault foreach_r mixin nextcase orelse par proc tvar
    unreachable within limit
""".trim().split(Regex("\\s+")).toSet()

fun tokenizeSyntax(text: String): List<SyntaxSpan> {
    val spans = ArrayList<SyntaxSpan>()
    val n = text.length
    var depth = 0
    var at = 0
    var noBlockClose = false
    var noHtmlClose = false

    while (at < n) {
        val char = text[at]
        if (char.isWhitespace()) {
            at++
            continue
        }

        val urlEnd = matchUrl(text, at)
        if (urlEnd > at) {
            spans.add(SyntaxSpan(at, urlEnd, SyntaxKind.URL))
            at = urlEnd
            continue
        }

        var commentEnd = -1
        if (!noBlockClose && text.startsWith("/*", at)) {
            val close = text.indexOf("*/", at + 2)
            if (close < 0) noBlockClose = true else commentEnd = close + 2
        } else if (!noHtmlClose && text.startsWith("<!--", at)) {
            val close = text.indexOf("-->", at + 4)
            if (close < 0) noHtmlClose = true else commentEnd = close + 3
        }
        if (commentEnd < 0) commentEnd = matchInlineComment(text, at)
        if (commentEnd > at) {
            spans.add(SyntaxSpan(at, commentEnd, SyntaxKind.COMMENT))
            at = commentEnd
            continue
        }

        val stringEnd = matchString(text, at)
        if (stringEnd > at) {
            spans.add(SyntaxSpan(at, stringEnd, SyntaxKind.STRING))
            at = stringEnd
            continue
        }

        if (at == 0 || !isWordChar(text[at - 1])) {
            val pathEnd = matchPath(text, at)
            if (pathEnd > at) {
                spans.add(SyntaxSpan(at, pathEnd, SyntaxKind.PATH))
                at = pathEnd
                continue
            }
        }

        val numberEnd = matchNumber(text, at)
        if (numberEnd > at) {
            spans.add(SyntaxSpan(at, numberEnd, SyntaxKind.NUMBER))
            at = numberEnd
            continue
        }

        val wordEnd = matchWord(text, at)
        if (wordEnd > at) {
            if (isKeyword(text.substring(at, wordEnd))) spans.add(SyntaxSpan(at, wordEnd, SyntaxKind.KEYWORD))
            at = wordEnd
            continue
        }

        if (char == '(' || char == '[' || char == '{') {
            spans.add(SyntaxSpan(at, at + 1, bracket(depth)))
            depth++
            at++
            continue
        }

        if (char == ')' || char == ']' || char == '}') {
            depth = maxOf(0, depth - 1)
            spans.add(SyntaxSpan(at, at + 1, bracket(depth)))
            at++
            continue
        }

        spans.add(SyntaxSpan(at, at + 1, SyntaxKind.PUNCTUATION))
        at++
    }

    return mergePunctuation(spans)
}

private fun bracket(depth: Int): SyntaxKind = when (depth % 6) {
    0 -> SyntaxKind.BRACKET1
    1 -> SyntaxKind.BRACKET2
    2 -> SyntaxKind.BRACKET3
    3 -> SyntaxKind.BRACKET4
    4 -> SyntaxKind.BRACKET5
    else -> SyntaxKind.BRACKET6
}

private fun mergePunctuation(spans: List<SyntaxSpan>): List<SyntaxSpan> {
    val merged = ArrayList<SyntaxSpan>(spans.size)
    for (span in spans) {
        val last = merged.lastOrNull()
        if (last != null && last.kind == SyntaxKind.PUNCTUATION &&
            span.kind == SyntaxKind.PUNCTUATION && last.end == span.start
        ) {
            merged[merged.lastIndex] = last.copy(end = span.end)
            continue
        }
        merged.add(span)
    }
    return merged
}

private fun isKeyword(word: String): Boolean =
    KEYWORDS.contains(word) || (word == word.uppercase() && KEYWORDS.contains(word.lowercase()))

private fun isWordChar(c: Char): Boolean =
    c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'

private fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

private fun isDigit(c: Char): Boolean = c in '0'..'9'

private fun isLineBreak(c: Char): Boolean = c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029'

private fun isPathChar(c: Char): Boolean = isWordChar(c) || c == '-' || c == '.'

private fun isSegmentChar(c: Char): Boolean = isPathChar(c) || c == '{' || c == '}'

private fun lineEnd(text: String, from: Int): Int {
    val newline = text.indexOf('\n', from)
    return if (newline < 0) text.length else newline
}

private fun matchUrl(text: String, at: Int): Int {
    if (!isAsciiLetter(text[at])) return -1
    var i = at + 1
    while (i < text.length && (isWordChar(text[i]) || text[i] == '+' || text[i] == '.' || text[i] == '-')) i++
    if (!text.startsWith("://", i)) return -1
    i += 3
    val bodyStart = i
    while (i < text.length) {
        val c = text[i]
        if (c.isWhitespace() || c == '<' || c == '>' || c == '"' || c == '\'') break
        i++
    }
    return if (i > bodyStart) i else -1
}

private fun matchInlineComment(text: String, at: Int): Int {
    if (text.startsWith("#!", at)) return lineEnd(text, at)
    if (text.startsWith("//", at)) return lineEnd(text, at)
    if (text[at] == '#') {
        if (at + 1 < text.length && (text[at + 1] == ' ' || text[at + 1] == '\t')) return lineEnd(text, at)
        if (at + 1 == text.length || text[at + 1] == '\n') return at + 1
    }
    if (text.startsWith("--", at)) {
        if (at + 2 < text.length && (text[at + 2] == ' ' || text[at + 2] == '\t')) return lineEnd(text, at)
        if (at + 2 == text.length || text[at + 2] == '\n') return at + 2
    }
    return -1
}

private fun matchString(text: String, at: Int): Int {
    val quote = text[at]
    if (quote != '"' && quote != '\'' && quote != '`') return -1
    var i = at + 1
    while (i < text.length) {
        val c = text[i]
        if (c == '\\') {
            if (i + 1 >= text.length || isLineBreak(text[i + 1])) return -1
            i += 2
            continue
        }
        if (c == quote) return i + 1
        if (c == '\n' && quote != '`') return -1
        i++
    }
    return -1
}

private fun matchPath(text: String, at: Int): Int {
    if (text[at] != '/') return -1
    var i = at + 1
    if (i >= text.length || !isAsciiLetter(text[i])) return -1
    i++
    while (i < text.length && isPathChar(text[i])) i++
    var end = i
    while (i < text.length && text[i] == '/') {
        val segmentStart = i + 1
        var j = segmentStart
        while (j < text.length && isSegmentChar(text[j])) j++
        if (j == segmentStart) break
        i = j
        end = j
    }
    if (i < text.length && text[i] == '/') end = i + 1
    return end
}

private fun matchNumber(text: String, at: Int): Int {
    if (!isDigit(text[at])) return -1
    var i = at + 1
    while (i < text.length && (isDigit(text[i]) || text[i] == '_')) i++
    if (i + 1 < text.length && text[i] == '.' && (isDigit(text[i + 1]) || text[i + 1] == '_')) {
        i += 2
        while (i < text.length && (isDigit(text[i]) || text[i] == '_')) i++
    }
    return i
}

private fun matchWord(text: String, at: Int): Int {
    val c = text[at]
    if (!isAsciiLetter(c) && c != '_') return -1
    var i = at + 1
    while (i < text.length && isWordChar(text[i])) i++
    return i
}
