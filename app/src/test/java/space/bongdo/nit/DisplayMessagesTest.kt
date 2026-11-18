package space.bongdo.nit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.MessageTime
import space.bongdo.nit.data.Part
import space.bongdo.nit.data.ToolState
import space.bongdo.nit.ui.displayMessages

class DisplayMessagesTest {
    private fun assistant(id: String, text: String, tool: Boolean = false): Message {
        val parts = mutableListOf<Part>()
        if (text.isNotEmpty()) parts += Part(type = "text", text = text)
        if (tool) parts += Part(type = "tool", id = "t-$id", name = "shell", state = space.bongdo.nit.data.ToolState())
        return Message(id = id, type = "assistant", time = MessageTime(created = 1), content = parts)
    }

    @Test
    fun mergesConsecutiveAssistantMessages() {
        val display = displayMessages(
            listOf(assistant("a1", "one"), assistant("a2", "two"), assistant("a3", "three")),
            running = false,
        )
        assertEquals(1, display.size)
        assertEquals("onetwothree", display[0].content.orEmpty().joinToString("") { it.text })
    }

    @Test
    fun keepsStepsOnlyAssistantWhileIdle() {
        val display = displayMessages(listOf(assistant("a1", "", tool = true)), running = false)
        assertEquals(1, display.size)
        assertEquals(1, display[0].shownSteps)
    }

    @Test
    fun keepsEmptyLastAssistantWhileRunning() {
        val display = displayMessages(listOf(assistant("a1", "", tool = true)), running = true)
        assertEquals(1, display.size)
    }

    @Test
    fun keepsCompactionAndShellRows() {
        val compact = Message(id = "c1", type = "compaction", time = MessageTime(created = 1), summary = "old")
        val shell = Message(id = "s1", type = "shell", time = MessageTime(created = 2), command = "ls")
        val display = displayMessages(listOf(assistant("a1", "hi"), compact, shell), running = false)
        assertEquals(listOf("a1", "c1", "s1"), display.map { it.id })
    }

    @Test
    fun keepsFailedIdleButDropsEmptyIdle() {
        val failed = Message(
            id = "i1",
            type = "idle",
            time = MessageTime(created = 1),
            error = space.bongdo.nit.data.MessageError(message = "boom"),
        )
        val empty = Message(id = "i2", type = "idle", time = MessageTime(created = 2))
        val display = displayMessages(listOf(assistant("a1", "hi"), failed, empty), running = false)
        assertEquals(2, display.size)
        assertEquals("i1", display[1].id)
    }

    @Test
    fun keepsUserAndGroupsFollowingAssistant() {
        val user = Message(id = "u1", type = "user", time = MessageTime(created = 1), text = "hi")
        val display = displayMessages(
            listOf(user, assistant("a1", "", tool = true), assistant("a2", "answer")),
            running = false,
        )
        assertEquals(2, display.size)
        assertEquals("user", display[0].type)
        assertEquals("assistant", display[1].type)
        assertEquals("answer", display[1].content!!.first { it.type == "text" }.text)
    }

    @Test
    fun sumsMarkersAndLoadedActivity() {
        val light = assistant("a1", "one").copy(steps = 3, tools = 2)
        val full = Message(
            id = "a2",
            type = "assistant",
            time = MessageTime(created = 2),
            content = listOf(
                Part(type = "reasoning", text = "hm"),
                Part(type = "tool", id = "t1", name = "read", state = ToolState(status = "completed")),
                Part(type = "text", text = "done"),
            ),
        )
        val display = displayMessages(listOf(light, full), running = false, details = mapOf("a2" to full))
        assertEquals(1, display.size)
        assertEquals(5, display[0].shownSteps)
        assertEquals(3, display[0].shownTools)
        assertEquals(listOf("a1", "a2"), display[0].sourceIDs)
    }

    @Test
    fun loadedDetailsReplaceMarkerCount() {
        val light = assistant("a1", "").copy(steps = 2, tools = 2)
        val full = Message(
            id = "a1",
            type = "assistant",
            time = MessageTime(created = 1),
            content = listOf(
                Part(type = "reasoning", text = "thinking"),
                Part(type = "text", text = "answer"),
            ),
        )
        val display = displayMessages(listOf(light), running = false, details = mapOf("a1" to full))
        assertEquals(1, display[0].shownSteps)
        assertEquals(0, display[0].shownTools)
        assertEquals("answer", display[0].content!!.last { it.type == "text" }.text)
    }
}
