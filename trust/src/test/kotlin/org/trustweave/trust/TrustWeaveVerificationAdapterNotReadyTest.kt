package org.trustweave.trust

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.identifiers.Did
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.dsl.TrustWeaveConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TrustWeaveVerificationAdapterNotReadyTest {

    @Test
    fun `verify credential overload returns AdapterNotReady with same credential when service missing`() =
        runBlocking {
            val trustWeave = trustWeaveWithoutCredentialService()
            val vc = sampleCredential(idSuffix = "explicit-verify")

            val result = trustWeave.verify(vc)
            val notReady = assertIs<VerificationResult.Invalid.AdapterNotReady>(result)
            assertEquals(vc, notReady.credential)
            assertTrue(notReady.errors.any { it.contains("CredentialService", ignoreCase = true) })
        }

    @Test
    fun `verify DSL returns AdapterNotReady with placeholder credential when service missing`() =
        runBlocking {
            val trustWeave = trustWeaveWithoutCredentialService()
            val vc = sampleCredential(idSuffix = "dsl-verify")

            val result = trustWeave.verify {
                credential(vc)
            }
            val notReady = assertIs<VerificationResult.Invalid.AdapterNotReady>(result)
            assertNotEquals(vc.issuer.id.value, notReady.credential.issuer.id.value)
            assertTrue(
                notReady.credential.issuer.id.value.contains("trustweave-configuration-placeholder"),
            )
            assertTrue(notReady.errors.isNotEmpty())
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

    private fun sampleCredential(idSuffix: String): VerifiableCredential =
        VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(Iri("did:key:test-issuer-$idSuffix")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:test-subject-$idSuffix")),
        )
}
