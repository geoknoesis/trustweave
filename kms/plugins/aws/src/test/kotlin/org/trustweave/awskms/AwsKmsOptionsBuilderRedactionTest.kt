package org.trustweave.awskms

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class AwsKmsOptionsBuilderRedactionTest {
    @Test
    fun `toString never prints credentials`() {
        val b = AwsKmsOptionsBuilder()
        b.region = "us-east-1"
        b.accessKeyId = "AKIATOPSECRET"
        b.secretAccessKey = "TOPSECRET"
        b.sessionToken = "TOPSECRET"
        assertFalse(b.toString().contains("TOPSECRET"), b.toString())
    }
}
