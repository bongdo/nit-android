package space.bongdo.nit

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.bongdo.nit.data.StreamEvent
import space.bongdo.nit.data.StreamReducer

class StreamReducerTest {
    private fun event(type: String, data: JsonObject): StreamEvent =
        StreamEvent(type = type, data = data, created = 1_000)

    @Test
    fun createsAndAppendsText() {
        var messages = emptyList<space.bongdo.nit.data.Message>()
        messages = StreamReducer.apply(
            messages,
            event("session.text.delta", buildJsonObject {
                put("sessionID", "ses_1")
                put("assistantMessageID", "msg_1")
                put("ordinal", 0)
                put("delta", "Hel")
            }),
        )
        messages = StreamReducer.apply(
            messages,
            event("session.text.delta", buildJsonObject {
                put("sessionID", "ses_1")
                put("assistantMessageID", "msg_1")
                put("ordinal", 0)
                put("delta", "lo")
            }),
        )
        assertEquals(1, messages.size)
        assertEquals("Hello", messages[0].content!![0].text)
    }

    @Test
    fun keepsReasoningAndTextApart() {
        var messages = emptyList<space.bongdo.nit.data.Message>()
        for ((type, delta) in listOf(
            "session.reasoning.delta" to "think",
            "session.text.delta" to "answer",
        )) {
            messages = StreamReducer.apply(
                messages,
                event(type, buildJsonObject {
                    put("assistantMessageID", "msg_1")
                    put("ordinal", 0)
                    put("delta", delta)
                }),
            )
        }
        assertEquals(listOf("reasoning", "text"), messages[0].content!!.map { it.type })
    }

    @Test
    fun tracksToolLifecycle() {
        var messages = emptyList<space.bongdo.nit.data.Message>()
        messages = StreamReducer.apply(
            messages,
            event("session.tool.input.started", buildJsonObject {
                put("assistantMessageID", "msg_1")
                put("id", "call_1")
                put("name", "shell")
            }),
        )
        messages = StreamReducer.apply(
            messages,
            event("session.tool.called", buildJsonObject {
                put("assistantMessageID", "msg_1")
                put("id", "call_1")
                put("name", "shell")
            }),
        )
        messages = StreamReducer.apply(
            messages,
            event("session.tool.success", buildJsonObject {
                put("assistantMessageID", "msg_1")
                put("id", "call_1")
                put("name", "shell")
            }),
        )
        val tool = messages[0].content!!.first { it.type == "tool" }
        assertEquals("completed", tool.state?.status)
    }

    @Test
    fun stepEndCarriesCostAndTokens() {
        var messages = emptyList<space.bongdo.nit.data.Message>()
        messages = StreamReducer.apply(
            messages,
            event("session.step.started", buildJsonObject {
                put("assistantMessageID", "msg_1")
            }),
        )
        messages = StreamReducer.apply(
            messages,
            event("session.step.ended", buildJsonObject {
                put("assistantMessageID", "msg_1")
                put("cost", 0.03)
                put("tokens", buildJsonObject { put("input", 1200) })
            }),
        )
        assertEquals(0.03, messages[0].cost!!, 0.0001)
        assertEquals(1200L, messages[0].tokens?.input)
    }

    @Test
    fun findsSessionID() {
        val event = event("session.idle", buildJsonObject { put("sessionID", "ses_1") })
        assertEquals("ses_1", StreamReducer.sessionID(event))
        assertTrue(StreamReducer.isMessageEvent("session.text.delta"))
    }
}
