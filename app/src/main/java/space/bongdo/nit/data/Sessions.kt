package space.bongdo.nit.data

fun Session.running(): Boolean {
    val busy = time.busy ?: return false
    val idle = time.idle
    return idle == null || busy > idle
}

fun mergeRunning(
    previous: Set<String>,
    sessions: List<Session>,
    eventsAt: Map<String, Long>,
    requestedAt: Long,
): Set<String> {
    val next = previous.filterTo(mutableSetOf()) { id -> sessions.none { it.id == id } }
    for (session in sessions) {
        if ((eventsAt[session.id] ?: 0L) > requestedAt) {
            if (session.id in previous) next += session.id
            continue
        }
        if (session.running()) next += session.id else next -= session.id
    }
    return next
}
