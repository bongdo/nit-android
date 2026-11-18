package space.bongdo.nit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.MessageTime
import space.bongdo.nit.data.Part
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
    fun dropsCompletedAssistantWithoutVisibleContent() {
        val display = displayMessages(listOf(assistant("a1", "", tool = true)), running = false)
        assertTrue(display.isEmpty())
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
}
