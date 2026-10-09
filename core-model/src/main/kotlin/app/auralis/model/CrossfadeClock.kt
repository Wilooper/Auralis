package app.auralis.model

class CrossfadeClock {
    private var durationMs = 0L
    private var elapsedMs = 0L
    val progress: Double get() = if (durationMs > 0) elapsedMs.toDouble() / durationMs else 0.0

    fun start(durationMs: Long) {
        require(durationMs > 0)
        this.durationMs = durationMs
        elapsedMs = 0
    }

    fun advance(deltaMs: Long, running: Boolean, incomingReady: Boolean): Boolean {
        require(deltaMs >= 0)
        if (durationMs == 0L || !running || !incomingReady) return false
        elapsedMs = (elapsedMs + deltaMs.coerceAtMost(durationMs)).coerceAtMost(durationMs)
        return elapsedMs == durationMs
    }

    fun cancel() { durationMs = 0; elapsedMs = 0 }
}
