package org.trustweave.trust

import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.withTestClaimContexts
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.TrustPath
import org.trustweave.trust.types.VerifierIdentity
import org.trustweave.trust.types.getOrThrowDid
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Release-blocking smoke test: BARE `TrustWeave.quickStart()` (no factories supplied) must
 * work end to end. Regression guard for the bug where `quickStart()` configured
 * `trust { provider("inMemory") }` but no default TrustRegistryFactory existed, so every
 * bare quick start threw "TrustRegistry factory is required" at build time.
 */
class QuickStartSmokeTest {

    @Test
    fun `bare quickStart builds and supports issue + verify`() = runBlocking<Unit> {
        // No factories(...), no custom KMS — exactly what the documentation advertises.
        val trustWeave = TrustWeave.quickStart()
        try {
            val issuerDid = trustWeave.createDid().getOrThrowDid()
            val holderDid = trustWeave.createDid().getOrThrowDid()

            val credential = trustWeave.issue {
                credential {
                    type("QuickStartSmokeCredential")
                    issuer(issuerDid.value)
                    subject {
                        id(holderDid.value)
                        "name" to "Alice"
                    }
                    issued(Clock.System.now())
                }
                signedBy(issuerDid)
                withTestClaimContexts()
            }.getOrThrow()

            val result = trustWeave.verify(credential)
            assertIs<VerificationResult.Valid>(
                result,
                "quickStart-issued credential must verify; got: $result"
            )
        } finally {
            trustWeave.close()
        }
    }

    @Test
    fun `bare quickStart wires a usable in-memory trust registry`() = runBlocking<Unit> {
        val trustWeave = TrustWeave.quickStart()
        try {
            val anchorDid = "did:key:zQuickStartSmokeAnchor"
            var added = false
            var trusted = false
            val notConfigured = trustWeave.trust {
                added = addAnchor(anchorDid) {
                    credentialTypes("QuickStartSmokeCredential")
                    description("Smoke-test anchor")
                }
                trusted = isTrusted(anchorDid, "QuickStartSmokeCredential")
            }

            assertNull(notConfigured, "trust { provider(\"inMemory\") } must be configured by quickStart")
            assertTrue(added, "Anchor should be added to the default in-memory registry")
            assertTrue(trusted, "Anchor should be trusted for its registered credential type")

            // Self-path resolves; unrelated DIDs do not.
            val anchor = IssuerIdentity(org.trustweave.did.identifiers.Did(anchorDid))
            val verifier = VerifierIdentity(org.trustweave.did.identifiers.Did(anchorDid))
            assertIs<TrustPath.Verified>(trustWeave.findTrustPath(verifier, anchor))
        } finally {
            trustWeave.close()
        }
    }
}
