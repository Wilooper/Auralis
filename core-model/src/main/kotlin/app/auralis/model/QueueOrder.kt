package app.auralis.model

/** Shuffle affects traversal only, keeping the displayed queue and current song stable. */
class QueueOrder(private val random: kotlin.random.Random = kotlin.random.Random.Default) {
    private var order = emptyList<Int>()
    fun reset(size: Int, current: Int, shuffle: Boolean) {
        val first = current.coerceIn(0, (size - 1).coerceAtLeast(0))
        order = when {
            size <= 0 -> emptyList()
            shuffle -> listOf(first) + (0 until size).filter { it != first }.shuffled(random)
            else -> (0 until size).toList()
        }
    }
    fun indices(): List<Int> = order
    fun next(current: Int, repeatAll: Boolean): Int? {
        val at = order.indexOf(current)
        if (at < 0) return null
        return order.getOrNull(at + 1) ?: order.firstOrNull().takeIf { repeatAll }
    }
    fun previous(current: Int, repeatAll: Boolean): Int? {
        val at = order.indexOf(current)
        if (at < 0) return null
        return order.getOrNull(at - 1) ?: order.lastOrNull().takeIf { repeatAll }
    }
}
