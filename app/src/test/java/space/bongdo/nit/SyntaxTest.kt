package space.bongdo.nit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import space.bongdo.nit.ui.SyntaxKind
import space.bongdo.nit.ui.SyntaxSpan
import space.bongdo.nit.ui.tokenizeSyntax

class SyntaxTest {

    @Test
    fun `javascript snippet`() {
        val src = """const x = 42; if (x) { return "hi\n"; }"""
        assertEquals(
            listOf(
                "const" to SyntaxKind.KEYWORD,
                "=" to SyntaxKind.PUNCTUATION,
                "42" to SyntaxKind.NUMBER,
                ";" to SyntaxKind.PUNCTUATION,
                "if" to SyntaxKind.KEYWORD,
                "(" to SyntaxKind.BRACKET1,
                ")" to SyntaxKind.BRACKET1,
                "{" to SyntaxKind.BRACKET1,
                "return" to SyntaxKind.KEYWORD,
                "\"hi\\n\"" to SyntaxKind.STRING,
                ";" to SyntaxKind.PUNCTUATION,
                "}" to SyntaxKind.BRACKET1,
            ),
            tokens(src),
        )
    }

    @Test
    fun `go snippet with backtick string and line comment`() {
        val src = """func main() {
	// greet
	s := `raw
text`
	fmt.Println("hi", s)
}"""
        assertEquals(
            listOf(
                "func" to SyntaxKind.KEYWORD,
                "(" to SyntaxKind.BRACKET1,
                ")" to SyntaxKind.BRACKET1,
                "{" to SyntaxKind.BRACKET1,
                "// greet" to SyntaxKind.COMMENT,
                ":=" to SyntaxKind.PUNCTUATION,
                "`raw\ntext`" to SyntaxKind.STRING,
                "." to SyntaxKind.PUNCTUATION,
                "(" to SyntaxKind.BRACKET2,
                "\"hi\"" to SyntaxKind.STRING,
                "," to SyntaxKind.PUNCTUATION,
                ")" to SyntaxKind.BRACKET2,
                "}" to SyntaxKind.BRACKET1,
            ),
            tokens(src),
        )
    }

    @Test
    fun `python snippet`() {
        val src = """def greet(name):
    return f"hi {name}"  # tail"""
        assertEquals(
            listOf(
                "def" to SyntaxKind.KEYWORD,
                "(" to SyntaxKind.BRACKET1,
                ")" to SyntaxKind.BRACKET1,
                ":" to SyntaxKind.PUNCTUATION,
                "return" to SyntaxKind.KEYWORD,
                "\"hi {name}\"" to SyntaxKind.STRING,
                "# tail" to SyntaxKind.COMMENT,
            ),
            tokens(src),
        )
    }

    @Test
    fun `strings honor escapes`() {
        val src = """val s = "a\"b" and 'c\'d'"""
        assertEquals(
            listOf(
                "=" to SyntaxKind.PUNCTUATION,
                "\"a\\\"b\"" to SyntaxKind.STRING,
                "'c\\'d'" to SyntaxKind.STRING,
            ),
            tokens(src),
        )
    }

    @Test
    fun `unterminated string is not a string span`() {
        val src = """a "oops b"""
        val spans = assertStable(src)
        assertEquals(listOf("\"" to SyntaxKind.PUNCTUATION), tokens(src))
        assertFalse(spans.any { it.kind == SyntaxKind.STRING })
    }

    @Test
    fun `urls win over punctuation`() {
        val src = "see https://example.com/a?b=1 and ssh://host/x now"
        val spans = assertStable(src)
        assertEquals(
            listOf(
                "https://example.com/a?b=1" to SyntaxKind.URL,
                "ssh://host/x" to SyntaxKind.URL,
            ),
            tokens(src),
        )
        assertFalse(spans.any { it.kind == SyntaxKind.PATH })
    }

    @Test
    fun `paths need a word boundary before the slash`() {
        val src = "cat /etc/hosts /usr/local/bin/ and/or a/b"
        assertEquals(
            listOf(
                "/etc/hosts" to SyntaxKind.PATH,
                "/usr/local/bin/" to SyntaxKind.PATH,
                "/" to SyntaxKind.PUNCTUATION,
                "or" to SyntaxKind.KEYWORD,
                "/" to SyntaxKind.PUNCTUATION,
            ),
            tokens(src),
        )
        val trailing = "ls /a/b/c/"
        assertEquals(listOf("/a/b/c/" to SyntaxKind.PATH), tokens(trailing))
        assertEquals(
            listOf("/a/" to SyntaxKind.PATH, "/b" to SyntaxKind.PATH),
            tokens("/a//b"),
        )
    }

    @Test
    fun `comment flavours`() {
        val src = "/* block */ <!-- html --> #!/bin/sh\n// line\n# hash\n-- dash\n--\n#"
        assertEquals(
            listOf(
                "/* block */" to SyntaxKind.COMMENT,
                "<!-- html -->" to SyntaxKind.COMMENT,
                "#!/bin/sh" to SyntaxKind.COMMENT,
                "// line" to SyntaxKind.COMMENT,
                "# hash" to SyntaxKind.COMMENT,
                "-- dash" to SyntaxKind.COMMENT,
                "--" to SyntaxKind.COMMENT,
                "#" to SyntaxKind.COMMENT,
            ),
            tokens(src),
        )
    }

    @Test
    fun `comment bodies span newlines`() {
        assertEquals(
            listOf("/* a\nb */" to SyntaxKind.COMMENT),
            tokens("x /* a\nb */ y"),
        )
        assertEquals(
            listOf("<!-- a\nb -->" to SyntaxKind.COMMENT),
            tokens("x <!-- a\nb --> y"),
        )
    }

    @Test
    fun `unclosed block comment falls back to punctuation`() {
        val src = "/* nope\nmore"
        assertEquals(listOf("/*" to SyntaxKind.PUNCTUATION), tokens(src))
        assertStable(src)
    }

    @Test
    fun `hash without space is not a comment`() {
        assertEquals(
            listOf("#" to SyntaxKind.PUNCTUATION, "5" to SyntaxKind.NUMBER),
            tokens("#5"),
        )
        assertEquals(listOf("#-" to SyntaxKind.PUNCTUATION), tokens("#-"))
    }

    @Test
    fun `brackets cycle through six colours`() {
        val src = "( [ { ( [ { } ] ) } ] )"
        assertEquals(
            listOf(
                "(" to SyntaxKind.BRACKET1,
                "[" to SyntaxKind.BRACKET2,
                "{" to SyntaxKind.BRACKET3,
                "(" to SyntaxKind.BRACKET4,
                "[" to SyntaxKind.BRACKET5,
                "{" to SyntaxKind.BRACKET6,
                "}" to SyntaxKind.BRACKET6,
                "]" to SyntaxKind.BRACKET5,
                ")" to SyntaxKind.BRACKET4,
                "}" to SyntaxKind.BRACKET3,
                "]" to SyntaxKind.BRACKET2,
                ")" to SyntaxKind.BRACKET1,
            ),
            tokens(src),
        )
    }

    @Test
    fun `seventh bracket wraps to bracket1 and stray closers stay shallow`() {
        val deep = "(((((((x)))))))"
        val kinds = tokenizeSyntax(deep).map { it.kind }
        assertEquals(
            listOf(
                SyntaxKind.BRACKET1,
                SyntaxKind.BRACKET2,
                SyntaxKind.BRACKET3,
                SyntaxKind.BRACKET4,
                SyntaxKind.BRACKET5,
                SyntaxKind.BRACKET6,
                SyntaxKind.BRACKET1,
                SyntaxKind.BRACKET1,
                SyntaxKind.BRACKET6,
                SyntaxKind.BRACKET5,
                SyntaxKind.BRACKET4,
                SyntaxKind.BRACKET3,
                SyntaxKind.BRACKET2,
                SyntaxKind.BRACKET1,
            ),
            kinds,
        )
        assertEquals(listOf(")" to SyntaxKind.BRACKET1), tokens(")"))
    }

    @Test
    fun `numbers`() {
        val src = "x = 1_000 + 3.14 + 7._"
        assertEquals(
            listOf(
                "=" to SyntaxKind.PUNCTUATION,
                "1_000" to SyntaxKind.NUMBER,
                "+" to SyntaxKind.PUNCTUATION,
                "3.14" to SyntaxKind.NUMBER,
                "+" to SyntaxKind.PUNCTUATION,
                "7._" to SyntaxKind.NUMBER,
            ),
            tokens(src),
        )
        assertTrue(tokenizeSyntax("ab12").isEmpty())
    }

    @Test
    fun `keywords match case and uppercase variants`() {
        val src = "TRUE willSet FROM from select"
        assertEquals(
            listOf(
                "TRUE" to SyntaxKind.KEYWORD,
                "willSet" to SyntaxKind.KEYWORD,
                "FROM" to SyntaxKind.KEYWORD,
                "from" to SyntaxKind.KEYWORD,
                "select" to SyntaxKind.KEYWORD,
            ),
            tokens(src),
        )
        assertTrue(tokenizeSyntax("Selector Fromm").isEmpty())
    }

    @Test
    fun `adjacent punctuation merges`() {
        val src = "a:=b == c => d !== e;"
        assertEquals(
            listOf(
                ":=" to SyntaxKind.PUNCTUATION,
                "==" to SyntaxKind.PUNCTUATION,
                "=>" to SyntaxKind.PUNCTUATION,
                "!==" to SyntaxKind.PUNCTUATION,
                ";" to SyntaxKind.PUNCTUATION,
            ),
            tokens(src),
        )
    }

    @Test
    fun `spans are sorted and non overlapping`() {
        val src = """# title
func f(x: Int) = x + 1 // done
val s = "text" and /tmp/file.txt
see https://example.com/a
"""
        val spans = assertStable(src)
        assertTrue(spans.isNotEmpty())
        assertTrue(spans.any { it.kind == SyntaxKind.KEYWORD })
        assertTrue(spans.any { it.kind == SyntaxKind.URL })
        assertTrue(spans.any { it.kind == SyntaxKind.PATH })
        assertTrue(spans.any { it.kind == SyntaxKind.STRING })
        assertTrue(spans.any { it.kind == SyntaxKind.COMMENT })
        assertTrue(spans.any { it.kind == SyntaxKind.BRACKET1 })
    }

    @Test
    fun `long input smoke`() {
        val chunk = """func f(x) { return "v" + 42 } // c
"""
        val repeats = 4000
        val src = chunk.repeat(repeats).trimEnd()
        val perChunk = tokenizeSyntax(chunk).size
        val spans = assertStable(src)
        assertEquals(perChunk * repeats, spans.size)
        assertEquals(src.length, spans.last().end)
    }

    @Test
    fun `pathological unclosed comments stay linear`() {
        val src = "/*a".repeat(40000) + "<!--b".repeat(10000)
        val started = System.nanoTime()
        val spans = assertStable(src)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(spans.isNotEmpty())
        assertTrue("tokenize took ${elapsedMs}ms", elapsedMs < 2000)
    }

    private fun tokens(text: String): List<Pair<String, SyntaxKind>> =
        tokenizeSyntax(text).map { text.substring(it.start, it.end) to it.kind }

    private fun assertStable(text: String): List<SyntaxSpan> {
        val spans = tokenizeSyntax(text)
        var end = 0
        for (span in spans) {
            assertTrue("span $span overlaps or is empty", span.start >= end && span.end > span.start)
            assertTrue("span $span exceeds ${text.length} chars", span.end <= text.length)
            end = span.end
        }
        return spans
    }
}
