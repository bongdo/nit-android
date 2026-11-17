package space.bongdo.nit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import space.bongdo.nit.data.parseServer

class ServerAddressTest {
    @Test
    fun addsHttpsToBareHost() {
        val parsed = parseServer("bongdo.space/nit/")
        assertEquals("https://bongdo.space", parsed?.origin)
        assertEquals("/nit", parsed?.basePath)
        assertEquals("https://bongdo.space/nit/api/health", parsed?.api("/api/health"))
    }

    @Test
    fun keepsExplicitSchemeAndPort() {
        val parsed = parseServer("http://127.0.0.1:7793/nit")
        assertEquals("http://127.0.0.1:7793", parsed?.origin)
        assertEquals("/nit", parsed?.basePath)
    }

    @Test
    fun rejectsEmptyAndGarbage() {
        assertNull(parseServer("   "))
        assertNull(parseServer("https://"))
    }
}
