package org.trustweave.credential.internal

import kotlinx.coroutines.runBlocking
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.CredentialServices
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.RevocationManagers
import org.trustweave.credential.revocation.RevocationStatus
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An unverified credential must not make this service call out: the revocation manager may fetch a
 * status list over HTTP and a trust evaluator may query a registry, both at the credential's
 * (attacker-chosen) direction.
 */
class VerifyProofBeforeLookupsTest {
    private val kms = InMemoryKeyManagementService()
    private val didMethod = DidKeyMockMethod(kms)
    private val issuer = runBlocking { didMethod.createDid() }

    private val revocationLookups = AtomicInteger()
    private val trustLookups = AtomicInteger()

    private val recordingRevocation: CredentialRevocationManager =
        object : CredentialRevocationManager by RevocationManagers.default() {
            override suspend fun checkRevocationStatus(credential: VerifiableCredential): RevocationStatus {
                revocationLookups.incrementAndGet()
                return RevocationStatus(revoked = false)
            }
        }

    private val recordingTrust =
        object : TrustEvaluator {
            override suspend fun isTrusted(issuer: Did): Boolean {
                trustLookups.incrementAndGet()
                return true
            }
        }

    private val service =
        CredentialServices.createCredentialService(
            kms = kms,
            didResolver =
                object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                },
            formats = listOf(ProofSuiteId.VC_LD),
            revocationManager = recordingRevocation,
        )

    private val status =
        CredentialStatus(
            id = StatusListId("https://attacker.example/status/1"),
            type = "BitstringStatusListEntry",
            statusPurpose = StatusPurpose.REVOCATION,
            statusListIndex = "1",
            statusListCredential = StatusListId("https://attacker.example/status/1"),
        )

    private fun issued(): VerifiableCredential =
        runBlocking {
            val result =
                service.issue(
                    IssuanceRequest(
                        format = ProofSuiteId.VC_LD,
                        issuer = Issuer.IriIssuer(Iri(issuer.id.value)),
                        issuerKeyId = issuer.verificationMethod.first().id,
                        credentialSubject = CredentialSubject(id = Iri("did:example:holder"), claims = emptyMap()),
                        type = listOf(CredentialType.fromString("VerifiableCredential")),
                    ),
                )
            (result as IssuanceResult.Success).credential
        }

    @Test
    fun `a credential with a broken proof triggers no revocation or trust lookup`() =
        runBlocking {
            // Adding a status entry after signing invalidates the proof and points the lookup at a host
            // of the caller's choosing.
            val tampered = issued().copy(credentialStatus = status)
            val result = service.verify(tampered, recordingTrust, VerificationOptions())
            assertTrue(result is VerificationResult.Invalid, "tampered credential must not verify: $result")
            assertEquals(0, revocationLookups.get(), "no status-list fetch for an unverified credential")
            assertEquals(0, trustLookups.get(), "no trust lookup for an unverified credential")
        }

    @Test
    fun `a verified credential is still checked for revocation and trust`() =
        runBlocking {
            val result = service.verify(issued(), recordingTrust, VerificationOptions())
            assertTrue(result is VerificationResult.Valid, "$result")
            assertEquals(1, trustLookups.get())
        }
}
