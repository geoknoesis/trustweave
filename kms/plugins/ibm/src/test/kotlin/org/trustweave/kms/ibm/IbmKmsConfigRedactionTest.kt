package org.trustweave.kms.ibm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IbmKmsConfigRedactionTest {
    @Test
    fun `toString never contains credentials`() {
        val text = IbmKmsConfig(apiKey = "SECRET-API-KEY", instanceId = "instance-1").toString()

        assertFalse("SECRET-API-KEY" in text, text)
        assertTrue("<redacted>" in text, text)
        assertTrue("instance-1" in text, text)
    }
}
