package org.trustweave.trust.dsl

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.did.model.DidDocument
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `credentials { autoAnchor(true) }` must actually anchor.
 *
 * The option was configurable and plumbed all the way into `TrustWeaveConfig`, but nothing ever
 * read it — `IssuanceBuilder` contained no anchoring code at all, so enabling it was a silent
 * no-op. Three existing tests configured it and passed, because they only asserted that a
 * credential came back; none asserted that anything reached a chain.
 *
 * Failure semantics follow the `withRevocation()` precedent in the same builder: an opt-in
 * side-effect that cannot be performed fails the issuance rather than being skipped quietly.
 * Silently not anchoring is the exact defect being fixed, so it must not be reintroduced as
 * "best effort".
 */
class AutoAnchorIssuanceTest {
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setUp() {
        kms = InMemoryKeyManagementService()
    }

    private suspend fun buildTrustWeave(
        autoAnchor: Boolean,
        defaultChain: String?,
        registerChain: String? = "algorand:testnet",
    ) = TrustWeave.build {
        val kmsRef = kms
        keys { custom(kmsRef) }
        did { method("key") {} }
        if (registerChain != null) {
            anchor { chain(registerChain) { inMemory() } }
        }
        credentials {
            defaultProofType(ProofType.Ed25519Signature2020)
            autoAnchor(autoAnchor)
            if (defaultChain != null) defaultChain(defaultChain)
        }
    }

    private suspend fun issue(tw: TrustWeave): IssuanceResult {
        val issuerKey: KeyHandle =
            when (val r = kms.generateKey("Ed25519", emptyMap())) {
                is GenerateKeyResult.Success -> r.keyHandle
                else -> throw IllegalStateException("key generation failed: $r")
            }
        val doc: DidDocument = DidKeyMockMethod(kms).createDid()
        return tw.issue {
            credential {
                type("PersonCredential")
                issuer(doc.id)
                subject { id("did:key:subject") }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = doc.id, keyId = issuerKey.id.value)
        }
    }

    private fun anchoredCount(tw: TrustWeave): Int =
        tw.configuration.anchorClients.values
            .filterIsInstance<InMemoryBlockchainAnchorClient>()
            .sumOf { it.size() }

    @Test
    fun `auto-anchor writes the issued credential to the configured chain`() =
        runBlocking<Unit> {
            val tw = buildTrustWeave(autoAnchor = true, defaultChain = "algorand:testnet")

            assertEquals(0, anchoredCount(tw), "nothing should be anchored before issuing")
            val result = issue(tw)

            assertTrue(result is IssuanceResult.Success, "issuance should succeed, got: $result")
            assertEquals(
                1,
                anchoredCount(tw),
                "autoAnchor(true) must anchor the issued credential exactly once",
            )
        }

    @Test
    fun `auto-anchor without a default chain fails closed`() =
        runBlocking<Unit> {
            val tw = buildTrustWeave(autoAnchor = true, defaultChain = null)

            val result = issue(tw)

            assertTrue(
                result is IssuanceResult.Failure,
                "autoAnchor with no chain configured must fail rather than issue un-anchored, got: $result",
            )
            assertEquals(0, anchoredCount(tw))
        }

    @Test
    fun `auto-anchor against an unregistered chain fails closed`() =
        runBlocking<Unit> {
            val tw =
                buildTrustWeave(
                    autoAnchor = true,
                    defaultChain = "eip155:99999",
                    registerChain = "algorand:testnet",
                )

            val result = issue(tw)

            assertTrue(
                result is IssuanceResult.Failure,
                "an unregistered chain must fail the issuance, not be skipped silently, got: $result",
            )
        }

    @Test
    fun `issuance without auto-anchor does not touch a chain`() =
        runBlocking<Unit> {
            val tw = buildTrustWeave(autoAnchor = false, defaultChain = "algorand:testnet")

            val result = issue(tw)

            assertTrue(result is IssuanceResult.Success, "issuance should succeed, got: $result")
            assertEquals(0, anchoredCount(tw), "autoAnchor(false) must not anchor")
        }
}
