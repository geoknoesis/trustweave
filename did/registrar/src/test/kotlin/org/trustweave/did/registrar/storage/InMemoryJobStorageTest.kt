package org.trustweave.did.registrar.storage

import org.junit.jupiter.api.Test
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.DidState
import org.trustweave.did.registrar.model.OperationState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class InMemoryJobStorageTest {
    private fun resp(state: OperationState) = DidRegistrationResponse(jobId = null, didState = DidState(state = state, did = "did:test:x"))

    private val finished get() = resp(OperationState.FINISHED)
    private val waiting get() = resp(OperationState.WAIT)

    @Test
    fun `default constructor still works`() {
        val s = InMemoryJobStorage()
        s.store("a", finished)
        assertNotNull(s.get("a"))
        assertTrue(s.exists("a"))
        assertTrue(s.remove("a"))
        assertFalse(s.exists("a"))
    }

    @Test
    fun `entry count is capped and finished jobs are evicted before unfinished ones`() {
        val s = InMemoryJobStorage(maxEntries = 5)
        s.store("pending", waiting)
        repeat(100) { s.store("job-$it", finished) }
        assertTrue(s.size() <= 5)
        assertNotNull(s.get("pending"), "an unfinished job must outlive finished ones")
        assertNotNull(s.get("job-99"))
        assertNull(s.get("job-0"))
    }

    @Test
    fun `finished jobs expire after ttl and unfinished after their own ttl`() {
        var now = 0L
        val s = InMemoryJobStorage(finishedJobTtl = 10.minutes, unfinishedJobTtl = 60.minutes, nowMillis = { now })
        s.store("f", finished)
        s.store("w", waiting)
        now = 11 * 60_000L
        assertNull(s.get("f"))
        assertFalse(s.exists("f"))
        assertNotNull(s.get("w"))
        now = 61 * 60_000L
        assertNull(s.get("w"))
        assertEquals(0, s.size())
    }
}
