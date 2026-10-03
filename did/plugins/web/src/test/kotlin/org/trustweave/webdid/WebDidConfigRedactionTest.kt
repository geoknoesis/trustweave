package org.trustweave.webdid

import kotlin.test.Test
import kotlin.test.assertFalse

class WebDidConfigRedactionTest {
    @Test
    fun `toString never prints additional property values`() {
        assertFalse("S3CRET" in WebDidConfig(additionalProperties = mapOf("token" to "S3CRET")).toString())
    }
}
