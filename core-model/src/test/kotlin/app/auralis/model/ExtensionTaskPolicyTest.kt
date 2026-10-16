package app.auralis.model

import org.junit.Assert.*
import org.junit.Test

class ExtensionTaskPolicyTest {
    private var now = 0L
    private val policy = ExtensionTaskPolicy({ now })
    @Test fun leavingAppRevokesOnlyForegroundWork() {
        policy.visibility(true)
        policy.begin("a", "foreground", false); policy.begin("b", "approved", true)
        assertEquals(listOf("a"), policy.visibility(false).map { it.task })
        assertFalse(policy.valid("a")); assertTrue(policy.valid("b"))
    }
    @Test fun hiddenTasksRequireExplicitBackgroundApproval() {
        assertThrows(IllegalStateException::class.java) { policy.begin("a", "ext", false) }
        assertTrue(policy.valid(policy.begin("b", "ext", true).task))
    }
    @Test fun capacityAndPerExtensionLimitCannotBeBypassed() {
        policy.visibility(true); policy.begin("a", "one", false)
        assertThrows(IllegalStateException::class.java) { policy.begin("duplicate", "one", false) }
        policy.begin("b", "two", false)
        assertThrows(IllegalStateException::class.java) { policy.begin("c", "three", true) }
        policy.finish("a"); assertEquals("c", policy.begin("c", "three", true).task)
    }
    @Test fun deadlinesAndRevocationInvalidateOutstandingCalls() {
        policy.visibility(true); policy.begin("a", "one", false)
        now = 30_000; assertFalse(policy.valid("a")); assertEquals(1, policy.expire().size)
        policy.begin("b", "two", true); policy.revoke("two"); assertFalse(policy.valid("b"))
    }
    @Test fun returningAfterSleepExpiresBackgroundLeasesBeforeNewWork() {
        policy.visibility(true); policy.begin("a", "one", true); policy.visibility(false)
        now = 60_000
        assertEquals(listOf("a"), policy.visibility(true).map { it.task })
        assertTrue(policy.snapshot().isEmpty()); assertFalse(policy.valid("a"))
        assertEquals("b", policy.begin("b", "one", false).task)
    }
}
