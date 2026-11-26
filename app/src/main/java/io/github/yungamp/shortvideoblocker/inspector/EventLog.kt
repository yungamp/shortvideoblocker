package io.github.yungamp.shortvideoblocker.inspector

/** Thread-safe ring buffer of recent accessibility events plus per-type counters. */
class EventLog(private val capacity: Int = 400) {
    private val buffer = ArrayDeque<EventRecord>(capacity)
    private val counts = HashMap<String, Int>()

    @Synchronized
    fun add(record: EventRecord) {
        if (buffer.size >= capacity) buffer.removeFirst()
        buffer.addLast(record)
        val key = if (record.pkg != null) "${record.type} ${record.pkg}" else record.type
        counts[key] = (counts[key] ?: 0) + 1
    }

    @Synchronized
    fun snapshot(): List<EventRecord> = buffer.toList()

    @Synchronized
    fun counts(): Map<String, Int> = counts.toMap()

    @Synchronized
    fun clear() {
        buffer.clear()
        counts.clear()
    }
}
