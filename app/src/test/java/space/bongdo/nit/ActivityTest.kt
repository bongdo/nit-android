package space.bongdo.nit

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import space.bongdo.nit.data.Part
import space.bongdo.nit.data.ToolState
import space.bongdo.nit.ui.activityLine
import space.bongdo.nit.ui.durationLabel
import space.bongdo.nit.ui.oneLine
import space.bongdo.nit.ui.thinkingPreview
import space.bongdo.nit.ui.toolSummary

class ActivityTest {
    private fun tool(name: String, input: kotlinx.serialization.json.JsonElement?): Part =
        Part(type = "tool", id = "t", name = name, state = ToolState(status = "running", input = input))

    @Test
    fun summarisesShellAndFileTools() {
        val shell = tool("bash", buildJsonObject { put("command", "  ls \n  -la ") })
        assertEquals("Выполняет" to "ls -la", toolSummary(shell))

        val read = tool("read", buildJsonObject { put("filePath", "/work/src/Main.kt") })
        assertEquals("Читает" to "src/Main.kt", toolSummary(read, "/work"))

        val search = tool("grep", buildJsonObject { put("pattern", "TODO"); put("path", "/work/a.kt") })
        assertEquals("Ищет" to "TODO in a.kt", toolSummary(search, "/work"))
    }

    @Test
    fun activityLineDescribesThinkingAndTools() {
        val reasoning = Part(type = "reasoning", text = "\n  checking the diff  \nmore")
        assertEquals("Думает · checking the diff", activityLine(reasoning))

        val shell = tool("bash", buildJsonObject { put("command", "make") })
        assertEquals("Выполняет make", activityLine(shell))
    }

    @Test
    fun previewsAndDurations() {
        assertEquals("first", thinkingPreview("\n first\n line \n"))
        assertEquals("0:42", durationLabel(1_000, 43_000))
        assertEquals("1:12", durationLabel(0, 72_000))
        assertEquals("1:02:03", durationLabel(0, 3_723_000))
        assertEquals("one line", oneLine(" one \n\t line "))
    }
}
