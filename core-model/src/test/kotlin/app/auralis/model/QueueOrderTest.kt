package app.auralis.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QueueOrderTest {
    @Test fun shuffleVisitsEverySongOnceAndRetracesPrevious() {
        val order = QueueOrder(Random(42)); order.reset(10, 4, true)
        val visited = mutableListOf(4)
        while (true) visited += order.next(visited.last(), false) ?: break
        assertEquals(10, visited.size); assertEquals((0..9).toSet(), visited.toSet())
        assertEquals(4, order.next(visited.last(), true))
        for (i in 1 until visited.size) assertEquals(visited[i - 1], order.previous(visited[i], false))
        assertEquals(visited.last(), order.previous(4, true))
    }
    @Test fun emptySingleAndSequentialQueuesStayValid() {
        val order = QueueOrder(Random(2)); order.reset(0, 0, false)
        assertNull(order.next(0, true)); assertNull(order.previous(0, true))
        order.reset(1, 0, true); assertEquals(listOf(0), order.indices())
        assertNull(order.next(0, false)); assertEquals(0, order.next(0, true))
        order.reset(3, 1, false); assertEquals(listOf(0, 1, 2), order.indices())
        assertEquals(2, order.next(1, false)); assertEquals(0, order.previous(1, false))
    }
    @Test fun malformedIndicesCannotEnterTraversalOrJumpToAnotherSong() {
        val order = QueueOrder(Random(3))
        for (current in listOf(-5, 8)) {
            order.reset(4, current, true)
            assertEquals((0..3).toSet(), order.indices().toSet()); assertEquals(4, order.indices().size)
            assertNull(order.next(current, true)); assertNull(order.previous(current, true))
        }
        order.reset(-1, 100, true); assertTrue(order.indices().isEmpty())
    }
    @Test fun resizingAndTogglingShuffleKeepsCurrentAtTheStartOfShuffledTraversal() {
        val order = QueueOrder(Random(7))
        for (size in 1..20) for (current in 0 until size) {
            order.reset(size, current, true)
            assertEquals(current, order.indices().first()); assertEquals(size, order.indices().distinct().size)
            order.reset(size, current, false); assertEquals((0 until size).toList(), order.indices())
        }
    }
}
