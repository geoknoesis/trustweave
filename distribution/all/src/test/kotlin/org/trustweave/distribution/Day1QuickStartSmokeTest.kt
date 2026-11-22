package org.trustweave.distribution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.credential.jsonld.JsonLdContexts
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.quickStart
import org.trustweave.trust.types.getOrThrowDid
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Day-1 QuickStart smoke test verifying that consumers depending ONLY on `distribution:all`
 * (with NO `:testkit` dependency on the classpath) can create DIDs, issue a Verifiable
 * Credential, and verify it end-to-end using both `TrustWeave.quickStart()` and explicit
 * `TrustWeave.build { ... }`.
 */
class Day1QuickStartSmokeTest {
    companion object {
        private const val QUICKSTART_CONTEXT_URL = "https://trustweave.example/contexts/day1-quickstart/v1"

        private val contextRegistered: Boolean =
            run {
                JsonLdContexts.register(
                    QUICKSTART_CONTEXT_URL,
                    """
                    {
                      "@context": {
                        "@vocab": "https://trustweave.example/vocab/day1-quickstart#"
                      }
                    }
                    """.trimIndent(),
                )
                true
            }
    }

    @Test
    fun `distribution-all classpath does not include testkit`() {
        assertFailsWith<ClassNotFoundException> {
            Class.forName("org.trustweave.testkit.credential.InMemoryWallet")
        }
    }

    @Test
    fun `TrustWeave quickStart creates DIDs, issues a VC, and verifies it end-to-end`() =
        runBlocking<Unit> {
            assertTrue(contextRegistered)
            val trustWeave = TrustWeave.quickStart()
            try {
                assertEndToEndDidIssueAndVerify(trustWeave)
            } finally {
                trustWeave.closeAsync()
            }
        }

    @Test
    fun `TrustWeave build with KmsProviders IN_MEMORY and did key creates DIDs, issues a VC, and verifies it`() =
        runBlocking<Unit> {
            assertTrue(contextRegistered)
            val trustWeave =
                TrustWeave.build {
                    keys {
                        provider(KmsProviders.IN_MEMORY)
                    }
                    did {
                        method(KEY) {}
                    }
                }
            try {
                assertEndToEndDidIssueAndVerify(trustWeave)
            } finally {
                trustWeave.closeAsync()
            }
        }

    private suspend fun assertEndToEndDidIssueAndVerify(trustWeave: TrustWeave) {
        val issuerDid = trustWeave.createDid().getOrThrowDid()
        val holderDid = trustWeave.createDid().getOrThrowDid()

        assertTrue(issuerDid.value.startsWith("did:key:"), "Expected did:key issuer DID, got: ${issuerDid.value}")
        assertTrue(holderDid.value.startsWith("did:key:"), "Expected did:key holder DID, got: ${holderDid.value}")

        val credential =
            trustWeave
                .issue {
                    credential {
                        type("QuickStartCredential")
                        issuer(issuerDid.value)
                        subject {
                            id(holderDid.value)
                            "name" to "Alice"
                            "role" to "Developer"
                        }
                        issued(Clock.System.now())
                    }
                    signedBy(issuerDid)
                    additionalOption(JsonLdContexts.CONTEXTS_PROOF_OPTION, listOf(QUICKSTART_CONTEXT_URL))
                }.getOrThrow()

        val verification = trustWeave.verify(credential)
        assertIs<VerificationResult.Valid>(
            verification,
            "Issued credential must verify end-to-end using distribution:all; got: $verification",
        )
    }
}
