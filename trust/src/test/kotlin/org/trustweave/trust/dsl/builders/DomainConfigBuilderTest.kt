package org.trustweave.trust.dsl.builders

import org.trustweave.trust.domain.DomainId
import org.trustweave.trust.domain.treasury.KmsKeyRef
import org.trustweave.trust.domain.treasury.SpendPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DomainConfigBuilderTest {
    private fun DomainConfigBuilder.valid() {
        domainId("edu")
        payerDid("did:web:payer.example")
        chainAccount("eip155:137") {
            keyRef = KmsKeyRef("aws", "polygon-key")
            address = "0xabc"
        }
    }

    @Test
    fun `a complete domain definition builds with the default spend policy`() {
        val config = DomainConfigBuilder().apply { valid() }.build()

        assertEquals(DomainId("edu"), config.domainId)
        assertEquals("did:web:payer.example", config.payerDid)
        assertSame(SpendPolicy.DEFAULT, config.spendPolicy)
        val account = config.accounts.single()
        assertEquals(DomainChainAccountConfig("eip155:137", "0xabc", KmsKeyRef("aws", "polygon-key")), account)
    }

    @Test
    fun `typed domain ids, several chains and a custom policy are kept`() {
        val policy = SpendPolicy(requireSponsorAllowList = true, sponsorAllowList = setOf("did:web:sponsor"))
        val config =
            DomainConfigBuilder()
                .apply {
                    domainId(DomainId("typed"))
                    payerDid("did:web:p")
                    chainAccount("eip155:1") {
                        keyRef = KmsKeyRef("k", "1")
                        address = "0x1"
                    }
                    chainAccount("eip155:2") {
                        keyRef = KmsKeyRef("k", "2")
                        address = "0x2"
                    }
                    spendPolicy(policy)
                }.build()

        assertEquals(DomainId("typed"), config.domainId)
        assertEquals(listOf("eip155:1", "eip155:2"), config.accounts.map { it.chainId })
        assertSame(policy, config.spendPolicy)
    }

    @Test
    fun `the last domain id wins when it is set twice`() {
        val config =
            DomainConfigBuilder()
                .apply {
                    valid()
                    domainId("override")
                }.build()

        assertEquals("override", config.domainId.value)
    }

    @Test
    fun `each missing required piece is reported by name`() {
        val noDomain = DomainConfigBuilder().apply { payerDid("did:web:p") }
        assertTrue(assertFailsWith<IllegalArgumentException> { noDomain.build() }.message!!.contains("domainId(...) } is required"))

        val noPayer = DomainConfigBuilder().apply { domainId("edu") }
        assertTrue(assertFailsWith<IllegalArgumentException> { noPayer.build() }.message!!.contains("payerDid(...) } is required"))

        val noAccounts =
            DomainConfigBuilder().apply {
                domainId("edu")
                payerDid("did:web:p")
            }
        assertTrue(assertFailsWith<IllegalArgumentException> { noAccounts.build() }.message!!.contains("at least one chainAccount"))
    }

    @Test
    fun `a chain account needs both a key reference and an address`() {
        val noKey =
            assertFailsWith<IllegalArgumentException> {
                DomainConfigBuilder().apply {
                    chainAccount("eip155:1") { address = "0x1" }
                }
            }
        assertEquals("chainAccount(eip155:1) { keyRef = ... } is required", noKey.message)

        val noAddress =
            assertFailsWith<IllegalArgumentException> {
                DomainConfigBuilder().apply {
                    chainAccount("eip155:1") { keyRef = KmsKeyRef("k", "1") }
                }
            }
        assertEquals("chainAccount(eip155:1) { address = ... } is required", noAddress.message)
    }

    @Test
    fun `blank identifiers are rejected by the value types`() {
        assertFailsWith<IllegalArgumentException> { DomainConfigBuilder().domainId(" ") }
        assertFailsWith<IllegalArgumentException> { KmsKeyRef("", "key") }
        assertFailsWith<IllegalArgumentException> { KmsKeyRef("kms", " ") }
    }
}
