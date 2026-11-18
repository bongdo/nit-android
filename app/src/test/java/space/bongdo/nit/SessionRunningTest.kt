package space.bongdo.nit

import org.junit.Assert.assertEquals
import org.junit.Test
import space.bongdo.nit.data.Session
import space.bongdo.nit.data.SessionTime
import space.bongdo.nit.data.mergeRunning
import space.bongdo.nit.data.running

class SessionRunningTest {
    private fun session(id: String, busy: Long? = null, idle: Long? = null) =
        Session(id = id, time = SessionTime(created = 1, updated = 2, busy = busy, idle = idle))

    @Test
    fun busyWithoutIdleMeansRunning() {
        assertEquals(true, session("s1", busy = 5).running())
        assertEquals(false, session("s1", busy = 5, idle = 6).running())
        assertEquals(false, session("s1").running())
    }

    @Test
    fun mergeKeepsNewerStreamEvents() {
        val previous = setOf("s1", "gone")
        val merged = mergeRunning(
            previous = previous,
            sessions = listOf(session("s1", busy = 1, idle = 2), session("s2")),
            eventsAt = mapOf("s1" to 200L),
            requestedAt = 100L,
        )
        assertEquals(setOf("s1", "gone"), merged)
    }

    @Test
    fun mergeAppliesTheSnapshotWhenNoNewerEventArrived() {
        val merged = mergeRunning(
            previous = setOf("s1"),
            sessions = listOf(session("s1", busy = 1, idle = 2), session("s2", busy = 3)),
            eventsAt = mapOf("s1" to 50L),
            requestedAt = 100L,
        )
        assertEquals(setOf("s2"), merged)
    }
}
