package org.trustweave.googlekms

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleKmsConfigRedactionTest {
    @Test
    fun `toString never contains credentials`() {
        val text =
            GoogleKmsConfig(
                projectId = "project-1",
                location = "us",
                credentialsJson = """{"private_key":"SECRET-KEY"}""",
            ).toString()

        assertFalse("SECRET-KEY" in text, text)
        assertTrue("<redacted>" in text, text)
        assertTrue("project-1" in text, text)
    }
}
