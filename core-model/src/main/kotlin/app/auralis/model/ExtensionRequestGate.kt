package app.auralis.model

/** Admission happens on Binder threads before posting any work to the UI looper. */
class ExtensionRequestGate(private val clock: () -> Long) {
    private val timestamps = ArrayDeque<Long>()
    private var busy = false
    @Synchronized fun acquire(): Boolean {
        val now = clock()
        while (timestamps.isNotEmpty() && now - timestamps.first() >= 60_000) timestamps.removeFirst()
        if (busy || timestamps.size >= 10) return false
        busy = true; timestamps.addLast(now); return true
    }
    @Synchronized fun release() { busy = false }
}
