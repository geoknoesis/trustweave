package org.trustweave.anchor.algorand

import org.junit.jupiter.api.Test
import org.trustweave.core.exception.ConfigException
import java.util.Base64
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * A sponsor that was configured but cannot be loaded is a deployment fault, not an absent sponsor.
 *
 * Collapsing the two lets a typo in a mnemonic present itself as `SponsorNotAllowed` — the operator
 * sees an authorization decision and goes looking for a policy problem that does not exist.
 */
class ConfigSponsorRegistryTest {
    private val sponsorDid = "did:example:sponsor"
    private val secretMnemonic = "abandon abandon abandon not actually a valid algorand mnemonic"

    @Test
    fun `an unconfigured sponsor resolves to null`() {
        val registry = ConfigSponsorRegistry(emptyMap())

        assertNull(registry.resolve(sponsorDid))
    }

    @Test
    fun `a configured sponsor whose mnemonic cannot be parsed is a configuration error`() {
        val registry = ConfigSponsorRegistry(mapOf("sponsor.$sponsorDid.mnemonic" to secretMnemonic))

        assertFailsWith<ConfigException> { registry.resolve(sponsorDid) }
    }

    @Test
    fun `a configured sponsor whose private key cannot be decoded is a configuration error`() {
        val registry = ConfigSponsorRegistry(mapOf("sponsor.$sponsorDid.privateKey" to "!!!not-base64!!!"))

        assertFailsWith<ConfigException> { registry.resolve(sponsorDid) }
    }

    @Test
    fun `the configuration error does not echo the sponsor key material`() {
        val registry = ConfigSponsorRegistry(mapOf("sponsor.$sponsorDid.mnemonic" to secretMnemonic))

        val failure = assertFailsWith<ConfigException> { registry.resolve(sponsorDid) }

        assertFalse(
            failure.message.contains(secretMnemonic),
            "A sponsor mnemonic must never appear in an exception message: ${failure.message}",
        )
        assertFalse(
            failure.context.values.any { it.toString().contains(secretMnemonic) },
            "A sponsor mnemonic must never appear in exception context: ${failure.context}",
        )
    }

    @Test
    fun `a well-formed private key still resolves`() {
        val seed = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val registry = ConfigSponsorRegistry(mapOf("sponsor.$sponsorDid.privateKey" to seed))

        val entry = registry.resolve(sponsorDid)

        kotlin.test.assertNotNull(entry, "A valid sponsor key must still produce an entry")
    }
}
