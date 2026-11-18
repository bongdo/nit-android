package space.bongdo.nit

import org.junit.Assert.assertEquals
import org.junit.Test
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.MessageMerge
import space.bongdo.nit.data.MessageTime

class MessageMergeTest {
    private fun message(id: String, text: String) =
        Message(id = id, type = "assistant", time = MessageTime(created = 1), text = text)

    @Test
    fun keepsMessagesStreamedWhileTheSnapshotWasInFlight() {
        val first = message("m1", "old")
        val streaming = message("m2", "streamed")
        val before = mapOf("m1" to first, "m2" to streaming)
        val current = listOf(first, message("m2", "streamed more"))

        val merged = MessageMerge.snapshot(listOf(message("m1", "fresh")), current, before)

        assertEquals(listOf("m1", "m2"), merged.map { it.id })
        assertEquals("fresh", merged[0].text)
        assertEquals("streamed more", merged[1].text)
    }

    @Test
    fun prefersTheSnapshotWhenNothingChanged() {
        val current = listOf(message("m1", "streamed"))
        val merged = MessageMerge.snapshot(listOf(message("m1", "server")), current, current.associateBy { it.id })
        assertEquals("server", merged.single().text)
    }

    @Test
    fun prependDropsOverlappingMessages() {
        val existing = listOf(message("m2", "b"), message("m3", "c"))
        val older = listOf(message("m1", "a"), message("m2", "b"))
        assertEquals(listOf("m1", "m2", "m3"), MessageMerge.prepend(existing, older).map { it.id })
    }

    @Test
    fun prependReturnsTheSameListWhenNothingIsNew() {
        val existing = listOf(message("m1", "a"))
        assertEquals(existing, MessageMerge.prepend(existing, listOf(message("m1", "a"))))
    }
}
