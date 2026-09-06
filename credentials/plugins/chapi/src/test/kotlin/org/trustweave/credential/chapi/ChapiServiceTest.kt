package org.trustweave.credential.chapi

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChapiServiceTest {
    @Test
    fun `message generation does not accumulate pending sessions`() =
        runBlocking<Unit> {
            val service = ChapiService()
            val ids = mutableSetOf<String>()
            repeat(1001) {
                val request = service.createProofRequest("did:key:verifier", emptyMap(), emptyMap())
                assertTrue(ids.add(request.requestId))
                assertEquals("did:key:verifier", request.chapiMessage["verifier"]?.jsonPrimitive?.content)
            }
        }
}
