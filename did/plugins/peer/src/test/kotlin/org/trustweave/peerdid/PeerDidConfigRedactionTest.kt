package org.trustweave.peerdid

import kotlin.test.Test
import kotlin.test.assertFalse

class PeerDidConfigRedactionTest {
    @Test
    fun `toString never prints additional property values`() {
        assertFalse("S3CRET" in PeerDidConfig(additionalProperties = mapOf("token" to "S3CRET")).toString())
    }
}
