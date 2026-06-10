package org.trustweave.wallet.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [WalletCreationOptions], in particular that secret values never
 * appear in its `toString()` output.
 */
class WalletCreationOptionsTest {

    @Test
    fun toStringMustNotLeakEncryptionKey() {
        val secret = "aes-256-key-material-EXAMPLE"
        val options = WalletCreationOptions(
            label = "my-wallet",
            encryptionKey = secret
        )

        val rendered = options.toString()

        assertFalse(rendered.contains(secret), "toString() must not contain the encryption key")
        assertTrue(rendered.contains("encryptionKey=***"), "Encryption key should be redacted")
    }

    @Test
    fun toStringMustNotLeakAdditionalPropertyValues() {
        val dbPassword = "p4ssw0rd-very-secret"
        val apiToken = "tok_EXAMPLE_1234567890"
        val options = WalletCreationOptions(
            label = "my-wallet",
            additionalProperties = mapOf(
                "dbPassword" to dbPassword,
                "apiToken" to apiToken,
                "poolSize" to 10
            )
        )

        val rendered = options.toString()

        assertFalse(rendered.contains(dbPassword), "toString() must not contain property values")
        assertFalse(rendered.contains(apiToken), "toString() must not contain property values")
        assertFalse(rendered.contains("10"), "Even non-string property values must be redacted")
        // Keys remain visible for debugging, values are masked.
        assertTrue(rendered.contains("dbPassword=***"))
        assertTrue(rendered.contains("apiToken=***"))
        assertTrue(rendered.contains("poolSize=***"))
    }

    @Test
    fun toStringKeepsNonSensitiveFieldsReadable() {
        val options = WalletCreationOptions(
            label = "my-wallet",
            storagePath = "/data/wallets",
            enableOrganization = true,
            enablePresentation = true
        )

        val rendered = options.toString()

        assertTrue(rendered.contains("label=my-wallet"))
        assertTrue(rendered.contains("storagePath=/data/wallets"))
        assertTrue(rendered.contains("enableOrganization=true"))
        assertTrue(rendered.contains("enablePresentation=true"))
        assertTrue(rendered.contains("encryptionKey=null"))
    }

    @Test
    fun builderRedactionAppliesToBuiltOptions() {
        val secret = "builder-secret-value"
        val options = walletCreationOptions {
            label = "built"
            encryptionKey = secret
            property("password", secret)
        }

        val rendered = options.toString()

        assertFalse(rendered.contains(secret), "Options built via DSL must also redact secrets")
        assertTrue(rendered.contains("password=***"))
        // The actual values must remain accessible to wallet factories.
        assertEquals(secret, options.encryptionKey)
        assertEquals(secret, options.additionalProperties["password"])
    }

    @Test
    fun dataClassEqualityStillUsesRealValues() {
        val a = WalletCreationOptions(encryptionKey = "k1", additionalProperties = mapOf("p" to "v"))
        val b = WalletCreationOptions(encryptionKey = "k1", additionalProperties = mapOf("p" to "v"))
        val c = WalletCreationOptions(encryptionKey = "k2", additionalProperties = mapOf("p" to "v"))

        assertEquals(a, b)
        assertFalse(a == c)
    }
}
