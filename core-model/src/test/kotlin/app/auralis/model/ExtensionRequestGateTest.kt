package app.auralis.model

import org.junit.Assert.*
import org.junit.Test

class ExtensionRequestGateTest {
    @Test fun aFloodCannotQueueUnboundedMainThreadWork() {
        val gate = ExtensionRequestGate { 0L }
        assertTrue(gate.acquire())
        repeat(10_000) { assertFalse(gate.acquire()) }
        gate.release(); assertTrue(gate.acquire())
    }
    @Test fun completingRequestsDoesNotResetTheRateBudget() {
        var now = 0L; val gate = ExtensionRequestGate { now }
        repeat(10) { assertTrue(gate.acquire()); gate.release() }
        assertFalse(gate.acquire())
        now = 60_000; assertTrue(gate.acquire())
    }
}
