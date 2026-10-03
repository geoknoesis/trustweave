package org.trustweave.plcdid

import kotlin.test.Test
import kotlin.test.assertFalse

class PlcDidConfigRedactionTest {
    @Test
    fun `toString never prints additional property values`() {
        assertFalse("S3CRET" in PlcDidConfig(additionalProperties = mapOf("token" to "S3CRET")).toString())
    }
}
