package org.trustweave.trust

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.revocation.RevocationManagers
import org.trustweave.did.identifiers.Did
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.trust.InMemoryTrustRegistry
import org.trustweave.trust.dsl.TrustWeaveConfig
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.KeyIdResult
import org.trustweave.trust.types.RevocationResult
import org.trustweave.trust.types.TrustOperationResult
import org.trustweave.trust.types.getOrThrow
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/** Sealed-result variants of the facade operations whose originals return Result/null/Boolean. */
class FacadeResultsTest {
    private lateinit var trustWeave: TrustWeave

    @BeforeEach
    fun setup() =
        runBlocking {
            val kms = InMemoryKeyManagementService()
            trustWeave =
                TrustWeave.build {
                    keys {
                        custom(kms)
                        signer { data, keyId ->
                            when (val r = kms.sign(KeyId(keyId), data)) {
                                is SignResult.Success -> r.signature
                                else -> throw IllegalStateException("Signing failed: $r")
                            }
                        }
                    }
                    did { method("key") { algorithm("Ed25519") } }
                }
        }

    private fun withRegistry(): TrustWeave =
        trustWeave.configuration.let { c ->
            TrustWeave.from(
                TrustWeaveConfig(
                    name = c.name,
                    kms = c.kms,
                    didRegistry = c.didRegistry,
                    blockchainRegistry = c.blockchainRegistry,
                    credentialConfig = c.credentialConfig,
                    credentialService = c.credentialService,
                    kmsService = c.kmsService,
                    trustRegistry = InMemoryTrustRegistry(),
                ),
            )
        }

    @Test
    fun `getKeyIdResult returns Success for a created DID`() =
        runBlocking {
            val created = trustWeave.createDid { method("key") }
            val did = assertIs<DidCreationResult.Success>(created).did
            val result = trustWeave.getKeyIdResult(did)
            assertIs<KeyIdResult.Success>(result)
            assertNotNull(result.getOrThrow())
            @Suppress("DEPRECATION")
            assertEquals(trustWeave.getKeyId(did).getOrThrow(), result.keyId)
        }

    @Test
    fun `getKeyIdResult returns Failure instead of throwing for an unresolvable DID`() =
        runBlocking<Unit> {
            val result = trustWeave.getKeyIdResult(Did("did:nonexistent:abc"))
            val failure = assertIs<KeyIdResult.Failure>(result)
            assertNotNull(failure.reason)
            assertFailsWith<IllegalStateException> { result.getOrThrow() }
        }

    @Test
    fun `trustResult reports NotConfigured without a registry and Completed with one`() =
        runBlocking {
            var ran = false
            assertIs<TrustOperationResult.NotConfigured>(trustWeave.trustResult { ran = true })
            assertEquals(false, ran, "block must not run without a registry")

            val withRegistry = withRegistry()
            assertEquals(TrustOperationResult.Completed, withRegistry.trustResult { ran = true })
            assertEquals(true, ran)
        }

    @Test
    fun `trustResult maps a throwing block to Failure`() =
        runBlocking {
            val withRegistry = withRegistry()
            val result = withRegistry.trustResult { error("boom") }
            val failure = assertIs<TrustOperationResult.Failure>(result)
            assertEquals("boom", failure.reason)
        }

    @Test
    fun `revokeResult reports NotConfigured InvalidRequest and the manager's answer`() =
        runBlocking<Unit> {
            assertIs<RevocationResult.NotConfigured>(
                trustWeave.revokeResult(10.seconds) {
                    credential("c")
                    statusList("l")
                },
            )

            val configured = TrustWeave.from(trustWeave.configuration.copy(revocationManager = RevocationManagers.default()))
            assertIs<RevocationResult.InvalidRequest>(configured.revokeResult(10.seconds) { credential("only-credential") })

            val outcome =
                configured.revokeResult(10.seconds) {
                    credential("c")
                    statusList("unknown-list")
                }
            // The default manager answers false or throws for an unknown list; either must be a variant, never an exception.
            assertIs<RevocationResult>(outcome)
        }
}
