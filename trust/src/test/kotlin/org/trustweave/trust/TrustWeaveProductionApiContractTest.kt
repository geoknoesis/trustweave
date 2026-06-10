package org.trustweave.trust

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.dsl.createTestCredentialService
import org.trustweave.trust.dsl.withTestClaimContexts
import org.trustweave.trust.dsl.credential.issueBatch
import org.trustweave.trust.dsl.credential.presentationResult
import org.trustweave.trust.dsl.credential.verifyBatch
import org.trustweave.trust.dsl.TrustWeaveConfig
import org.trustweave.trust.types.PresentationResult
import org.trustweave.trust.types.getOrThrow
import org.trustweave.trust.types.getOrThrowDid
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract tests for production-oriented API behavior (sealed results, adapter-not-ready propagation).
 */
class TrustWeaveProductionApiContractTest {

    @Test
    fun `issue returns AdapterNotReady when credential service is null`() = runBlocking {
        val tw = trustWeaveWithoutCredentialService()
        val result = tw.issue { }
        assertIs<IssuanceResult.Failure.AdapterNotReady>(result)
        assertTrue(result.errors.any { it.contains("Credential", ignoreCase = true) })
    }

    @Test
    fun `presentationResult returns AdapterNotReady when credential service is null`() = runBlocking {
        val tw = trustWeaveWithoutCredentialService()
        val result = tw.presentationResult { }
        assertIs<PresentationResult.Failure.AdapterNotReady>(result)
        assertTrue(result.errors.any { it.contains("Credential", ignoreCase = true) })
    }

    @Test
    fun presentationResultGetOrThrowThrowsInvalidStateWhenAdapterNotReady() = runBlocking {
        val tw = trustWeaveWithoutCredentialService()
        val ex = assertFailsWith<TrustWeaveException.InvalidState> {
            tw.presentationResult { }.getOrThrow()
        }
        assertEquals("PRESENTATION_ADAPTER_NOT_READY", ex.code)
    }

    @Test
    fun `presentationResult returns InvalidRequest when holder missing`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            when (val result = kms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                is SignResult.Success -> result.signature
                else -> error("Signing failed: $result")
            }
        }
        val tempTw = TrustWeave.build {
            keys {
                custom(kms)
                signer(signer)
            }
            did { method("key") { algorithm("Ed25519") } }
        }
        val didResolver = DidResolver { did -> tempTw.configuration.didRegistry.resolve(did.value) }
        val cs = createTestCredentialService(kms = kms, didResolver = didResolver)
        val tw = TrustWeave.build {
            keys {
                custom(kms)
                signer(signer)
            }
            did { method("key") { algorithm("Ed25519") } }
            credentialService(cs)
        }
        val issuer = tw.createDid { method("key"); algorithm("Ed25519") }.getOrThrowDid()
        val issued = tw.issue {
            credential {
                type("ContractCredential")
                issuer(issuer)
                subject {
                    id(org.trustweave.did.identifiers.Did("did:key:sub"))
                    "x" to "y"
                }
            }
            signedBy(issuer)
            withTestClaimContexts() // Define ad-hoc test claims in the credential @context
        }
        val credential = assertIs<IssuanceResult.Success>(issued).credential

        val pr = tw.presentationResult {
            credentials(credential)
        }
        val inv = assertIs<PresentationResult.Failure.InvalidRequest>(pr)
        assertTrue(inv.errors.any { it.contains("Holder", ignoreCase = true) })
    }

    @Test
    fun `issueBatch emits AdapterNotReady for each request when service missing`() = runBlocking {
        val tw = trustWeaveWithoutCredentialService()
        val results = tw.issueBatch {
            requests = listOf({ }, { })
            maxConcurrency = 2
        }.toList()
        assertEquals(2, results.size)
        results.forEach { assertIs<IssuanceResult.Failure.AdapterNotReady>(it) }
    }

    @Test
    fun `verifyBatch emits AdapterNotReady for each credential when service missing`() = runBlocking {
        val tw = trustWeaveWithoutCredentialService()
        val c = kotlinx.datetime.Clock.System.now()
        val minimal = org.trustweave.credential.model.vc.VerifiableCredential(
            type = listOf(org.trustweave.credential.model.CredentialType.VerifiableCredential),
            issuer = org.trustweave.credential.model.vc.Issuer.IriIssuer(
                org.trustweave.core.identifiers.Iri("did:key:batch-a"),
            ),
            issuanceDate = c,
            credentialSubject = org.trustweave.credential.model.vc.CredentialSubject.fromDid(
                org.trustweave.did.identifiers.Did("did:key:batch-b"),
            ),
        )
        val results = tw.verifyBatch {
            credentials = listOf(minimal, minimal)
            maxConcurrency = 2
        }.toList()
        assertEquals(2, results.size)
        results.forEach { r ->
            assertIs<VerificationResult.Invalid.AdapterNotReady>(r)
        }
    }

    private fun trustWeaveWithoutCredentialService(): TrustWeave {
        val config = TrustWeaveConfig(
            name = "no-credential-service",
            kms = InMemoryKeyManagementService(),
            didRegistry = DidMethodRegistry(),
            blockchainRegistry = BlockchainAnchorRegistry(),
            credentialConfig = TrustWeaveConfig.CredentialConfig(),
            credentialService = null,
        )
        return TrustWeave(config)
    }
}
