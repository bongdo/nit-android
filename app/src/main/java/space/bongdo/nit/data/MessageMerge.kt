package space.bongdo.nit.data

object MessageMerge {
    fun snapshot(
        snapshot: List<Message>,
        current: List<Message>,
        before: Map<String, Message>,
    ): List<Message> {
        val changed = current.filter { before[it.id] !== it }
        if (changed.isEmpty()) return snapshot
        val changedIDs = changed.mapTo(mutableSetOf()) { it.id }
        val base = snapshot.filter { !it.id.startsWith("local-") && it.id !in changedIDs }
        return base + changed
    }

    fun prepend(current: List<Message>, older: List<Message>): List<Message> {
        if (older.isEmpty()) return current
        val known = current.mapTo(mutableSetOf()) { it.id }
        val fresh = older.filter { it.id !in known }
        return if (fresh.isEmpty()) current else fresh + current
    }
}
