package space.bongdo.nit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.bongdo.nit.ui.HtmlArtifact
import space.bongdo.nit.ui.fileTarget
import space.bongdo.nit.ui.isPureHtml
import space.bongdo.nit.ui.resolvePath
import space.bongdo.nit.ui.richSegments

class RichTextTest {
    @Test
    fun findsPathWithLines() {
        assertEquals("src/main.kt", fileTarget("src/main.kt:10-12")?.path)
        assertNull(fileTarget("ordinary words"))
        assertNull(fileTarget("https://example.com/a.kt"))
    }

    @Test
    fun resolvesRelativePaths() {
        assertEquals("/work/src/a.kt", resolvePath("src/a.kt", "/work"))
        assertEquals("/abs/a.kt", resolvePath("/abs/a.kt", "/work"))
    }

    @Test
    fun pureHtmlBecomesArtifact() {
        assertTrue(isPureHtml("<div><h1>Hello</h1></div>"))
        val segments = richSegments("<div><h1>Hello</h1></div>")
        assertEquals(1, segments.size)
        assertTrue(segments[0].artifacts.isNotEmpty())
    }

    @Test
    fun markerSplitsText() {
        val segments = richSegments("before\n[[html:report.html|Report]]\nafter")
        assertEquals(3, segments.size)
        assertEquals("before\n", segments[0].text)
        assertEquals(HtmlArtifact(path = "report.html", title = "Report"), segments[1].artifacts.first())
        assertEquals("\nafter", segments[2].text)
    }

    @Test
    fun markerInsideFenceIsIgnored() {
        val segments = richSegments("```\n[[html:report.html]]\n```")
        assertEquals(1, segments.size)
        assertTrue(segments[0].artifacts.isEmpty())
    }
}
