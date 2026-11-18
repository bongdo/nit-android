package space.bongdo.nit

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.bongdo.nit.data.NitApi
import space.bongdo.nit.data.PersistentCookieJar
import space.bongdo.nit.data.UnauthorizedException
import space.bongdo.nit.data.parseServer

class NitApiTest {
    private val server = MockWebServer()
    private var stored: String? = null
    private val jar = PersistentCookieJar(read = { stored }, write = { stored = it })

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loginPostsGateFormAndKeepsSession() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(303)
                .addHeader("Location", "/nit/")
                .addHeader("Set-Cookie", "gate_session=abc; Path=/; HttpOnly")
                .addHeader("Set-Cookie", "nit_gate=def; Path=/; HttpOnly; Max-Age=604800"),
        )
        server.start()
        val api = NitApi(jar)
        api.useServer(parseServer(server.url("/nit").toString())!!)
        api.login("bongdo", "secret", "")

        val request = server.takeRequest()
        assertEquals("/login", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=bongdo"))
        assertTrue(body.contains("password=secret"))
        assertTrue(body.contains("next=%2Fnit%2F"))
        assertTrue(api.hasSession())
        assertTrue(stored!!.contains("gate_session"))
    }

    @Test
    fun unauthorizedThrows() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"no"}"""))
        server.start()
        val api = NitApi(jar)
        api.useServer(parseServer(server.url("/nit").toString())!!)
        var thrown = false
        try {
            api.sessions()
        } catch (_: UnauthorizedException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun parsesSessionsPage() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"id":"ses_1","title":"Hello","location":{"directory":"/work"},"time":{"created":1,"updated":2}}]}""",
            ),
        )
        server.start()
        val api = NitApi(jar)
        api.useServer(parseServer(server.url("/nit").toString())!!)
        val sessions = api.sessions()
        assertEquals(1, sessions.size)
        assertEquals("Hello", sessions[0].title)
        assertEquals("/work", sessions[0].location.directory)
    }
}

class EventStreamTest {
    @Test
    fun retryDelayJittersAroundTheBaseAndStaysBounded() {
        assertEquals(350L, space.bongdo.nit.data.EventStream.retryDelay(0, 0.0))
        assertEquals(1_300L, space.bongdo.nit.data.EventStream.retryDelay(1, 1.0))
        assertEquals(8_000L, space.bongdo.nit.data.EventStream.retryDelay(10, 1.0))
        for (attempt in 0..10) {
            val delay = space.bongdo.nit.data.EventStream.retryDelay(attempt, 0.5)
            assertTrue(delay in 100L..8_000L)
        }
    }

    @Test
    fun readsSseEventAndSessionID() = kotlinx.coroutines.runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"session.text.delta\",\"created\":7,\"data\":{\"sessionID\":\"ses_1\",\"assistantMessageID\":\"msg_1\",\"delta\":\"hi\"}}\n\n"),
        )
        server.start()
        val event = kotlinx.coroutines.withTimeout(5_000) {
            space.bongdo.nit.data.EventStream.open(okhttp3.OkHttpClient(), server.url("/api/events").toString())
                .first { it.type == "session.text.delta" }
        }
        server.shutdown()
        assertEquals("session.text.delta", event.type)
        assertEquals("ses_1", space.bongdo.nit.data.StreamReducer.sessionID(event))
    }
}
