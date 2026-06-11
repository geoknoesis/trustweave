package org.trustweave.awskms

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwsKmsConfigTest {

    @Test
    fun `toString must not leak secretAccessKey or sessionToken`() {
        val secret = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
        val token = "FwoGZXIvYXdzEXAMPLESESSIONTOKEN"

        val config = AwsKmsConfig.builder()
            .region("us-east-1")
            .accessKeyId("AKIAIOSFODNN7EXAMPLE")
            .secretAccessKey(secret)
            .sessionToken(token)
            .build()

        val rendered = config.toString()

        assertFalse(rendered.contains(secret), "toString() must not contain the secret access key")
        assertFalse(rendered.contains(token), "toString() must not contain the session token")
        assertTrue(rendered.contains("secretAccessKey=***"), "Secret access key should be redacted")
        assertTrue(rendered.contains("sessionToken=***"), "Session token should be redacted")
    }

    @Test
    fun `toString keeps non-sensitive fields readable`() {
        val config = AwsKmsConfig.builder()
            .region("eu-west-1")
            .accessKeyId("AKIAIOSFODNN7EXAMPLE")
            .secretAccessKey("super-secret-value")
            .endpointOverride("http://localhost:4566")
            .pendingWindowInDays(7)
            .cacheTtlSeconds(60)
            .build()

        val rendered = config.toString()

        assertTrue(rendered.contains("region=eu-west-1"))
        assertTrue(rendered.contains("accessKeyId=AKIAIOSFODNN7EXAMPLE"))
        assertTrue(rendered.contains("endpointOverride=http://localhost:4566"))
        assertTrue(rendered.contains("pendingWindowInDays=7"))
        assertTrue(rendered.contains("cacheTtlSeconds=60"))
    }

    @Test
    fun `toString renders null secrets as null not redaction marker`() {
        val config = AwsKmsConfig.builder()
            .region("us-east-1")
            .build()

        val rendered = config.toString()

        assertTrue(rendered.contains("secretAccessKey=null"))
        assertTrue(rendered.contains("sessionToken=null"))
    }

    @Test
    fun `fromMap defaults cacheTtlSeconds when absent instead of caching forever`() {
        val config = AwsKmsConfig.fromMap(mapOf(AwsKmsOptionKeys.REGION to "us-east-1"))

        assertEquals(
            AwsKmsConfig.DEFAULT_CACHE_TTL_SECONDS,
            config.cacheTtlSeconds,
            "Absent cacheTtlSeconds must fall back to the default TTL, not null (cache-forever)"
        )
    }

    @Test
    fun `fromMap honors an explicit cacheTtlSeconds value`() {
        val config = AwsKmsConfig.fromMap(
            mapOf(
                AwsKmsOptionKeys.REGION to "us-east-1",
                "cacheTtlSeconds" to 42
            )
        )

        assertEquals(42L, config.cacheTtlSeconds)
    }

    @Test
    fun `builder still allows explicit null TTL (cache forever) as programmatic opt-in`() {
        val config = AwsKmsConfig.builder()
            .region("us-east-1")
            .cacheTtlSeconds(null)
            .build()

        assertEquals(null, config.cacheTtlSeconds)
    }

    @Test
    fun `builder defaults cacheTtlSeconds to the shared default`() {
        val config = AwsKmsConfig.builder()
            .region("us-east-1")
            .build()

        assertEquals(AwsKmsConfig.DEFAULT_CACHE_TTL_SECONDS, config.cacheTtlSeconds)
    }

    @Test
    fun `copy preserves equality and redaction`() {
        val secret = "another-secret-value"
        val config = AwsKmsConfig(region = "us-east-1", secretAccessKey = secret)
        val copied = config.copy()

        assertEquals(config, copied)
        assertFalse(copied.toString().contains(secret), "Copied config toString() must also redact secrets")
        // The actual value must remain accessible for client construction.
        assertEquals(secret, copied.secretAccessKey)
    }
}
