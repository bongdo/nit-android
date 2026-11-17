package space.bongdo.nit

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.bongdo.nit.data.NitApi
import space.bongdo.nit.data.PersistentCookieJar
import space.bongdo.nit.data.UserFile
import space.bongdo.nit.data.parseServer
import java.io.File

class ApiContractTest {
    private val server = MockWebServer()
    private var stored: String? = null
    private val jar = PersistentCookieJar(read = { stored }, write = { stored = it })

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun readsEveryEnvelopeTheScreensNeed() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/login") -> MockResponse()
                        .setResponseCode(303)
                        .addHeader("Location", "/nit/")
                        .addHeader("Set-Cookie", "gate_session=abc; Path=/; HttpOnly")
                        .addHeader("Set-Cookie", "nit_gate=def; Path=/; HttpOnly; Max-Age=604800")
                    path.endsWith("/api/health") -> json("""{"ok":true}""")
                    path.endsWith("/api/sessions") -> json(
                        """{"data":[{"id":"ses_1","title":"T","location":{"directory":"/work"},"time":{"created":1,"updated":2}}],"cursor":{"next":null}}""",
                    )
                    path.endsWith("/api/sessions/ses_1") -> json(
                        """{"data":{"id":"ses_1","title":"T","location":{"directory":"/work"},"time":{"created":1,"updated":2}}}""",
                    )
                    path.contains("/messages") -> json(
                        """{"data":[{"id":"msg_1","type":"assistant","time":{"created":1},"content":[{"type":"text","text":"hi"}]}],"previous":null}""",
                    )
                    path.endsWith("/permissions") -> json(
                        """{"data":[{"id":"perm_1","sessionID":"ses_1","action":"edit","resources":["/a"]}]}""",
                    )
                    path.endsWith("/form") -> json(
                        """{"data":[{"id":"form_1","sessionID":"ses_1","title":"Q","fields":[{"key":"x","type":"text","title":"X"}]}]}""",
                    )
                    path.contains("/prompt") -> json("""{"data":{"id":"msg_user"}}""")
                    path.endsWith("/api/projects") -> json("""[{"id":"p","canonical":"/work"}]""")
                    path.contains("/api/fs") -> json("""{"path":"/work","parent":"/","dirs":["a"]}""")
                    path.contains("/api/models") -> json(
                        """{"data":[{"id":"m","modelID":"m","providerID":"p","name":"M","status":"active","enabled":true,"variants":[],"limit":{"context":1000}}]}""",
                    )
                    path.endsWith("/api/agents") -> json("""{"data":[{"id":"build","name":"Build"}]}""")
                    path.contains("/upload") -> json("""{"path":"/work/out/f.png","name":"f.png","size":3}""")
                    path.endsWith("/api/ui") -> json("""{"hidden":["ses_9"],"showHidden":false}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val api = NitApi(jar)
        api.useServer(parseServer(server.url("/nit").toString())!!)

        api.login("bongdo", "secret", "")
        assertTrue(api.health())
        assertEquals("T", api.sessions()[0].title)
        assertEquals("/work", api.session("ses_1").location.directory)
        assertEquals("hi", api.messages("ses_1").data[0].content!![0].text)
        assertEquals("edit", api.permissions("ses_1")[0].action)
        assertEquals("Q", api.forms("ses_1")[0].title)
        assertEquals("/work", api.projects()[0].canonical)
        assertEquals("a", api.folders("/work").dirs[0])
        assertEquals(1000L, api.models("/work")[0].limit?.context)
        assertEquals("build", api.agents()[0].id)
        assertEquals("ses_9", api.hiddenSessions().first())

        val temp = File.createTempFile("nit-test-", ".png")
        temp.writeBytes(byteArrayOf(1, 2, 3))
        val uploaded = api.upload(temp, "f.png", "/work", "image/png")
        temp.delete()
        assertEquals("/work/out/f.png", uploaded.path)

        val promptID = api.prompt(
            "ses_1",
            "look",
            listOf(UserFile(uri = "file:///work/out/f.png", name = "f.png")),
        )
        assertEquals("msg_user", promptID)
        while (true) {
            val request = server.takeRequest()
            if (request.path?.contains("/prompt") == true) {
                val promptBody = request.body.readUtf8()
                assertTrue(promptBody.contains("file:///work/out/f.png"))
                assertTrue(!promptBody.contains("bytes"))
                break
            }
        }
    }
}
