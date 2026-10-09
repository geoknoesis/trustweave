package org.trustweave.credential.proof.internal.engines

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.internal.DefaultCredentialService
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.revocation.RevocationManagers
import org.trustweave.credential.revocation.StatusUpdate
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.credential.spi.status.CredentialStatusChecker
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService

/** The revocation check must use the SIGNED `vc.credentialStatus`, never the unsigned envelope copy. */
class SdJwtSignedStatusTest {
    private val kms = InMemoryKeyManagementService()
    private val didMethod = DidKeyMockMethod(kms)
    private val issuer: DidDocument = runBlocking { didMethod.createDid() }
    private val holder: DidDocument = runBlocking { didMethod.createDid() }
    private val resolver =
        object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
        }
    private val listId = StatusListId("urn:uuid:signed-status-list")
    private val status =
        CredentialStatus(
            id = StatusListId("urn:uuid:signed-status-list#7"),
            type = "BitstringStatusListEntry",
            statusPurpose = StatusPurpose.REVOCATION,
            statusListIndex = "7",
            statusListCredential = listId,
        )

    private val revokedChecker =
        object : CredentialStatusChecker {
            override suspend fun checkStatus(credential: VerifiableCredential): CredentialStatusCheckResult =
                if (credential.credentialStatus?.statusListIndex == "7") {
                    CredentialStatusCheckResult.Revoked("revoked")
                } else {
                    CredentialStatusCheckResult.Valid
                }
        }

    private fun engine(withChecker: Boolean = true) =
        SdJwtProofEngine(
            ProofEngineConfig(
                properties =
                    buildMap {
                        put("kms", kms)
                        if (withChecker) put("statusChecker", revokedChecker)
                    },
                didResolver = resolver,
            ),
        )

    private suspend fun issueWithStatus(engine: SdJwtProofEngine): VerifiableCredential =
        engine.issue(
            IssuanceRequest(
                format = ProofSuiteId.SD_JWT_VC,
                issuer = Issuer.IriIssuer(Iri(issuer.id.value)),
                issuerKeyId = issuer.verificationMethod.first().id,
                credentialSubject = CredentialSubject(id = Iri(holder.id.value), claims = mapOf("name" to JsonPrimitive("Alice"))),
                type = listOf(CredentialType.fromString("VerifiableCredential")),
                credentialStatus = status,
            ),
        )

    @Test
    fun `an untouched credential with a revoked signed status is revoked`() =
        runBlocking<Unit> {
            val engine = engine()
            engine.verify(issueWithStatus(engine), VerificationOptions()).shouldBeInstanceOf<VerificationResult.Invalid.Revoked>()
        }

    @Test
    fun `stripping the envelope credentialStatus does not hide the signed status`() =
        runBlocking<Unit> {
            val engine = engine()
            val stripped = issueWithStatus(engine).copy(credentialStatus = null)
            engine.verify(stripped, VerificationOptions()).shouldBeInstanceOf<VerificationResult.Invalid.Revoked>()
        }

    @Test
    fun `an envelope credentialStatus that differs from the signed one is rejected`() =
        runBlocking<Unit> {
            val engine = engine()
            val swapped = issueWithStatus(engine).let { it.copy(credentialStatus = status.copy(statusListIndex = "8")) }
            engine.verify(swapped, VerificationOptions()).shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        }

    @Test
    fun `service gating uses the signed status when the envelope status is stripped`() =
        runBlocking<Unit> {
            val manager = RevocationManagers.default()
            val list = manager.createStatusList(issuer.id.value, StatusPurpose.REVOCATION, 128, customId = "signed-status-list")
            manager.updateStatusListBatch(list, listOf(StatusUpdate(index = 7, revoked = true)))
            val signedStatus = status.copy(id = list, statusListCredential = list)
            val noChecker = engine(withChecker = false)
            val service =
                DefaultCredentialService(
                    engines = mapOf(ProofSuiteId.SD_JWT_VC to noChecker),
                    didResolver = resolver,
                    revocationManager = manager,
                )
            val issued =
                noChecker.issue(
                    IssuanceRequest(
                        format = ProofSuiteId.SD_JWT_VC,
                        issuer = Issuer.IriIssuer(Iri(issuer.id.value)),
                        issuerKeyId = issuer.verificationMethod.first().id,
                        credentialSubject = CredentialSubject(id = Iri(holder.id.value), claims = mapOf("name" to JsonPrimitive("Alice"))),
                        type = listOf(CredentialType.fromString("VerifiableCredential")),
                        credentialStatus = signedStatus,
                    ),
                )
            service
                .verify(issued.copy(credentialStatus = null), null, VerificationOptions())
                .shouldBeInstanceOf<VerificationResult.Invalid.Revoked>()
        }
}
