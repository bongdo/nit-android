package space.bongdo.nit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.bongdo.nit.data.MAX_RETRY_ATTEMPTS
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.MessageError
import space.bongdo.nit.data.MessageTime
import space.bongdo.nit.data.UserFile
import space.bongdo.nit.data.isRetryableFailure
import space.bongdo.nit.data.retryDelay
import space.bongdo.nit.data.retryPlan

class RetryTest {
    private fun user(id: String, text: String = "hi") =
        Message(id = id, type = "user", time = MessageTime(created = 1), text = text)

    private fun assistant(id: String) =
        Message(id = id, type = "assistant", time = MessageTime(created = 2))

    @Test
    fun retryableFailures() {
        assertTrue(isRetryableFailure(MessageError(status = 429)))
        assertTrue(isRetryableFailure(MessageError(status = 503)))
        assertTrue(isRetryableFailure(MessageError(status = 529)))
        assertTrue(isRetryableFailure(MessageError(message = "Provider overloaded")))
        assertTrue(isRetryableFailure(MessageError(message = "rate limit reached")))
        assertFalse(isRetryableFailure(MessageError(status = 400, message = "bad request")))
        assertFalse(isRetryableFailure(null))
    }

    @Test
    fun planUsesTheLastAdmittedUserMessage() {
        val messages = listOf(user("u1"), assistant("a1"), user("u2", "again"), assistant("a2"))
        val plan = retryPlan(messages, MessageError(status = 529), 1, "a2")
        assertEquals("u2", plan?.messageID)
        assertEquals("again", plan?.text)
    }

    @Test
    fun planSkipsOptimisticMessagesAndStopsAtTheAttemptLimit() {
        assertNull(retryPlan(listOf(user("local-1")), MessageError(status = 529), 1, null))
        assertNull(
            retryPlan(
                listOf(user("u1")),
                MessageError(status = 529),
                MAX_RETRY_ATTEMPTS + 1,
                null,
            ),
        )
        assertNull(retryPlan(listOf(user("u1")), MessageError(status = 400), 1, null))
    }

    @Test
    fun planKeepsFilesForTheResentPrompt() {
        val file = UserFile(uri = "file:///tmp/a.png", name = "a.png")
        val plan = retryPlan(
            listOf(user("u1").copy(files = listOf(file))),
            MessageError(status = 429),
            1,
            null,
        )
        assertEquals(listOf(file), plan?.files)
    }

    @Test
    fun delayBacksOffAndHonoursRetryAfter() {
        assertEquals(2_000L, retryDelay(1))
        assertEquals(4_000L, retryDelay(2))
        assertEquals(8_000L, retryDelay(3))
        assertEquals(30_000L, retryDelay(9))
        assertEquals(12_000L, retryDelay(1, 12_000))
        assertEquals(30_000L, retryDelay(1, 90_000))
    }
}
