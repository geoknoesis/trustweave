package org.trustweave.trust

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.RevocationManagers
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.dsl.createTestCredentialService
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.withTestClaimContexts
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.DidResult
import org.trustweave.trust.types.getOrThrowDid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Facade-level tests that operation timeouts honour the sealed-result contract:
 * an operation exceeding its timeout must return the operation's Failure type,
 * never leak a raw [kotlinx.coroutines.TimeoutCancellationException] to callers.
 *
 * Real cancellation (parent coroutine cancelled) must still propagate.
 */
class TimeoutFailureMappingTest {

    /**
     * Wraps a [DidMethod] and delays every operation, so facade timeouts fire
     * deterministically before the underlying operation completes.
     */
    private class SlowDidMethod(
        private val delegate: DidMethod,
        private val slowFor: Duration,
        private val started: CompletableDeferred<Unit>? = null
    ) : DidMethod by delegate {
        override suspend fun createDid(options: DidCreationOptions): DidDocument {
            started?.complete(Unit)
            delay(slowFor)
            return delegate.createDid(options)
        }

        override suspend fun resolveDid(did: Did): DidResolutionResult {
            started?.complete(Unit)
            delay(slowFor)
            return delegate.resolveDid(did)
        }

        override suspend fun updateDid(
            did: Did,
            updater: (DidDocument) -> DidDocument
        ): DidDocument {
            started?.complete(Unit)
            delay(slowFor)
            return delegate.updateDid(did, updater)
        }
    }

    private val shortTimeout = 200.milliseconds
    private val slowFor = 30.seconds

    private lateinit var trustWeave: TrustWeave
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setup() = runBlocking {
        kms = InMemoryKeyManagementService()
        val kmsRef = kms
        trustWeave = TrustWeave.build {
            keys {
                custom(kmsRef)
                signer { data, keyId ->
                    when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
        }
    }

    /**
     * Replaces the registered "key" method with a slow wrapper. The facade reads
     * methods through its DID registry on every call, so post-build registration
     * takes effect immediately.
     */
    private fun installSlowKeyMethod(started: CompletableDeferred<Unit>? = null) {
        trustWeave.getDidRegistry().register(SlowDidMethod(DidKeyMockMethod(kms), slowFor, started))
    }

    @Test
    fun `createDid exceeding timeout returns Failure instead of throwing`() = runBlocking {
        installSlowKeyMethod()

        val result = trustWeave.createDid(timeout = shortTimeout) {
            method("key")
            algorithm("Ed25519")
        }

        val failure = assertIs<DidCreationResult.Failure.Other>(result)
        assertTrue(failure.reason.contains("timed out"), "Unexpected reason: ${failure.reason}")
    }

    @Test
    fun `resolveDid exceeding timeout returns Failure instead of throwing`() = runBlocking {
        val did = trustWeave.createDid { method("key"); algorithm("Ed25519") }.getOrThrowDid()
        installSlowKeyMethod()

        val result = trustWeave.resolveDid(did, timeout = shortTimeout)

        val failure = assertIs<DidResolutionResult.Failure.ResolutionError>(result)
        assertTrue(failure.reason.contains("timed out"), "Unexpected reason: ${failure.reason}")
    }

    @Test
    fun `updateDid exceeding timeout returns Failure instead of throwing`() = runBlocking {
        val did = trustWeave.createDid { method("key"); algorithm("Ed25519") }.getOrThrowDid()
        installSlowKeyMethod()

        val result = trustWeave.updateDid(timeout = shortTimeout) {
            did(did.value)
            removeKey("$did#none")
        }

        val failure = assertIs<DidResult.Failure.UpdateFailed>(result)
        assertTrue(failure.reason.contains("timed out"), "Unexpected reason: ${failure.reason}")
    }

    @Test
    fun `rotateKey exceeding timeout returns Failure instead of throwing`() = runBlocking {
        val did = trustWeave.createDid { method("key"); algorithm("Ed25519") }.getOrThrowDid()
        installSlowKeyMethod()

        val result = trustWeave.rotateKey(timeout = shortTimeout) {
            did(did.value)
            algorithm("Ed25519")
        }

        val failure = assertIs<DidResult.Failure.UpdateFailed>(result)
        assertTrue(failure.reason.contains("timed out"), "Unexpected reason: ${failure.reason}")
    }

    @Test
    fun `delegate exceeding timeout returns invalid result instead of throwing`() = runBlocking {
        val delegator = trustWeave.createDid { method("key"); algorithm("Ed25519") }.getOrThrowDid()
        val delegate = trustWeave.createDid { method("key"); algorithm("Ed25519") }.getOrThrowDid()
        installSlowKeyMethod()

        val result = trustWeave.delegate(timeout = shortTimeout) {
            from(delegator.value)
            to(delegate.value)
        }

        assertFalse(result.valid)
        assertTrue(
            result.errors.any { it.contains("timed out") },
            "Unexpected errors: ${result.errors}"
        )
    }

    @Test
    fun `issue exceeding timeout returns Failure instead of throwing`() = runBlocking {
        // Rebuild with a credential service so issuance is configured; signedBy(did)
        // without an explicit key id forces key extraction through the (slow) resolver.
        val kmsRef = kms
        val issuingTrustWeave = TrustWeave.build {
            keys {
                custom(kmsRef)
                signer { data, keyId ->
                    when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did {
                method("key") { algorithm("Ed25519") }
            }
            credentialService(createTestCredentialService(kms = kmsRef))
        }
        val issuerDid = issuingTrustWeave.createDid { method("key"); algorithm("Ed25519") }.getOrThrowDid()
        issuingTrustWeave.getDidRegistry().register(SlowDidMethod(DidKeyMockMethod(kms), slowFor))

        val result = issuingTrustWeave.issue(timeout = shortTimeout) {
            credential {
                type("TestCredential")
                issuer(issuerDid)
                subject {
                    id("did:key:subject")
                    "test" to "value"
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid) // key id auto-extraction goes through the slow resolver
            withTestClaimContexts()
        }

        val failure = assertIs<IssuanceResult.Failure.AdapterError>(result)
        assertTrue(failure.reason.contains("timed out"), "Unexpected reason: ${failure.reason}")
    }

    @Test
    fun `verify exceeding timeout returns Invalid instead of throwing`() = runBlocking {
        // The credential service resolves issuer DIDs through the facade's registry, so
        // installing a slow "key" method after issuance makes verification (and only
        // verification) exceed its timeout.
        val kmsRef = kms
        var facade: TrustWeave? = null
        val registryBackedResolver = DidResolver { did ->
            requireNotNull(facade).getDidRegistry().resolve(did.value)
        }
        val verifyingTrustWeave = TrustWeave.build {
            keys {
                custom(kmsRef)
                signer { data, keyId ->
                    when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did {
                method("key") { algorithm("Ed25519") }
            }
            credentialService(
                createTestCredentialService(kms = kmsRef, didResolver = registryBackedResolver)
            )
        }
        facade = verifyingTrustWeave
        val issuerDid = verifyingTrustWeave.createDid { method("key"); algorithm("Ed25519") }.getOrThrowDid()

        val credential = verifyingTrustWeave.issue {
            credential {
                type("TestCredential")
                issuer(issuerDid)
                subject {
                    id("did:key:subject")
                    "test" to "value"
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid)
            withTestClaimContexts()
        }.getOrThrow()

        // Issuance done — now every resolution is slow, so verification times out.
        verifyingTrustWeave.getDidRegistry().register(SlowDidMethod(DidKeyMockMethod(kms), slowFor))

        val result = verifyingTrustWeave.verify(credential, timeout = shortTimeout)

        val invalid = assertIs<VerificationResult.Invalid.InvalidProof>(result)
        assertEquals(credential, invalid.credential, "Timeout result must retain the request credential")
        assertTrue(invalid.reason.contains("timed out"), "Unexpected reason: ${invalid.reason}")
        assertTrue(invalid.errors.any { it.contains("timed out") }, "Unexpected errors: ${invalid.errors}")
    }

    /**
     * Delays [CredentialRevocationManager.revokeCredential] so revocation timeouts fire
     * deterministically; all other operations delegate to the default in-memory manager.
     */
    private class SlowRevocationManager(
        private val slowFor: Duration,
        private val started: CompletableDeferred<Unit>? = null,
        private val delegate: CredentialRevocationManager = RevocationManagers.default()
    ) : CredentialRevocationManager by delegate {
        override suspend fun revokeCredential(credentialId: String, statusListId: StatusListId): Boolean {
            started?.complete(Unit)
            delay(slowFor)
            return delegate.revokeCredential(credentialId, statusListId)
        }
    }

    @Test
    fun `revoke exceeding timeout throws OperationTimedOut instead of leaking TimeoutCancellationException`() =
        runBlocking<Unit> {
            // revoke() returns Boolean, which cannot express a timeout honestly (false would
            // silently misreport an unknown outcome as "not revoked"), so the contract is a
            // typed TrustWeaveException.OperationTimedOut instead.
            val slowTrustWeave = TrustWeave.from(
                trustWeave.configuration.copy(revocationManager = SlowRevocationManager(slowFor))
            )

            val exception = assertFailsWith<TrustWeaveException.OperationTimedOut> {
                slowTrustWeave.revoke(timeout = shortTimeout) {
                    credential("cred-timeout-test")
                    statusList("list-timeout-test")
                }
            }
            assertEquals("OPERATION_TIMED_OUT", exception.code)
            assertTrue(exception.message.contains("timed out"), "Unexpected message: ${exception.message}")
            assertIs<kotlinx.coroutines.TimeoutCancellationException>(exception.cause)
        }

    @Test
    fun `revoke cancellation still propagates and is not mapped to OperationTimedOut`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val slowTrustWeave = TrustWeave.from(
            trustWeave.configuration.copy(revocationManager = SlowRevocationManager(slowFor, started))
        )

        var completedNormally = false
        val job = launch {
            slowTrustWeave.revoke(timeout = 60.seconds) {
                credential("cred-cancel-test")
                statusList("list-cancel-test")
            }
            completedNormally = true
        }

        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled, "Job should be cancelled")
        assertFalse(completedNormally, "Cancellation must propagate, not be mapped to a result/exception")
    }

    @Test
    fun `real cancellation still propagates and is not mapped to a Failure`() = runBlocking {
        val did = trustWeave.createDid { method("key"); algorithm("Ed25519") }.getOrThrowDid()
        val started = CompletableDeferred<Unit>()
        installSlowKeyMethod(started)

        var completedNormally = false
        val job = launch {
            // Generous timeout: the only way this returns early is wrongly mapping
            // the parent cancellation below to a Failure result.
            trustWeave.resolveDid(did, timeout = 60.seconds)
            completedNormally = true
        }

        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled, "Job should be cancelled")
        assertFalse(completedNormally, "Cancellation must propagate, not be mapped to a result")
    }
}
