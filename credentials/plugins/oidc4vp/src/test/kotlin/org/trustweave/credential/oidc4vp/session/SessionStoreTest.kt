package org.trustweave.credential.oidc4vp.session

import kotlinx.coroutines.runBlocking
import org.trustweave.core.util.SessionCapacityExceededException
import org.trustweave.credential.oidc4vp.models.AuthorizationRequest
import org.trustweave.credential.oidc4vp.models.PermissionRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SessionStoreTest {
    @Test
    fun `expired requests disappear and capacity never evicts an active request`() =
        runBlocking<Unit> {
            var now = Instant.EPOCH
            val clock =
                object : Clock() {
                    override fun instant(): Instant = now

                    override fun getZone(): ZoneId = ZoneOffset.UTC

                    override fun withZone(zone: ZoneId): Clock = this
                }
            val store = InMemorySessionStore(capacity = 1, ttlMillis = 1000, clock = clock)
            val request = PermissionRequest("first", AuthorizationRequest(nonce = "secret"))
            store.put("first", request)
            assertFailsWith<SessionCapacityExceededException> { store.put("second", request.copy(requestId = "second")) }
            assertEquals(request, store.get("first"))
            now = now.plusMillis(1000)
            assertNull(store.get("first"))
            store.put("second", request.copy(requestId = "second"))
            store.remove("second")
            assertNull(store.get("second"))
        }
}
