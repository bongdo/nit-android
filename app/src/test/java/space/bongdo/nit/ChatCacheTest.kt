package space.bongdo.nit

import org.junit.Assert.assertEquals
import org.junit.Test
import space.bongdo.nit.data.CachedBoot
import space.bongdo.nit.data.CachedChat
import space.bongdo.nit.data.ChatCache
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.MessageTime
import space.bongdo.nit.data.Part
import space.bongdo.nit.data.Session
import space.bongdo.nit.data.SessionTime
import space.bongdo.nit.data.ToolState

class ChatCacheTest {
    private fun assistant(vararg parts: Part) =
        Message(id = "a1", type = "assistant", time = MessageTime(created = 1), content = parts.toList())

    @Test
    fun keepsOnlyWhatTheUserSees() {
        val reasoning = Part(type = "reasoning", text = "thinking")
        val read = Part(type = "tool", id = "t1", name = "read", state = ToolState(status = "completed"))
        val question = Part(type = "tool", id = "q1", name = "question", state = ToolState(status = "completed"))
        val text = Part(type = "text", text = "answer")
        val light = ChatCache.lighten(listOf(assistant(reasoning, read, question, text))).single()

        assertEquals(listOf("question", "text"), light.content.orEmpty().map {
            if (it.type == "tool") it.name else it.type
        })
        assertEquals(2, light.steps)
        assertEquals(1, light.tools)
        assertEquals(null, light.edited)
    }

    @Test
    fun trimKeepsRecentChatsAndTheTail() {
        val sessions = (0 until 30).map { index ->
            Session(
                id = "s$index",
                time = SessionTime(created = 1, updated = index.toLong()),
            )
        }
        val chats = sessions.associate { session ->
            session.id to CachedChat(
                messages = (0 until 70).map {
                    Message(id = "${session.id}-$it", type = "user", text = "m$it")
                },
                updatedAt = session.time.updated,
            )
        } + ("orphan" to CachedChat())
        val boot = CachedBoot(server = "https://x", sessions = sessions, chats = chats)
        val trimmed = ChatCache.trim(boot)

        assertEquals(ChatCache.MAX_CHATS, trimmed.chats.size)
        assertEquals(true, "s29" in trimmed.chats)
        assertEquals(false, "s4" in trimmed.chats)
        assertEquals(ChatCache.MAX_MESSAGES, trimmed.chats.getValue("s29").messages.size)
        assertEquals("s29-69", trimmed.chats.getValue("s29").messages.last().id)
        assertEquals(false, "orphan" in trimmed.chats)
    }
}
