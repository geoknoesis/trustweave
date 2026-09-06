package org.trustweave.did.registrar.server

import org.trustweave.did.registrar.server.dto.ErrorResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class RegistrarErrorResponseTest {
    @Test
    fun `errors never expose provider messages and cancellation propagates`() {
        for (code in listOf("INTERNAL_ERROR", "INVALID_REQUEST", null)) {
            val response = ErrorResponse.fromException(IllegalStateException("password=secret private-host"), code)
            assertFalse(response.error.contains("secret"))
            assertEquals(response.error, response.didState?.reason)
            assertEquals(code, response.errorCode)
        }
        val cancelled = java.util.concurrent.CancellationException("cancelled")
        assertSame(
            cancelled,
            assertFailsWith<java.util.concurrent.CancellationException> {
                ErrorResponse.fromException(cancelled, "INTERNAL_ERROR")
            },
        )
    }
}
