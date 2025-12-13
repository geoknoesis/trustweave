package com.trustweave.trust.dsl

import com.trustweave.credential.CredentialService
import com.trustweave.credential.credentialService
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.requests.IssuanceRequest
import com.trustweave.credential.requests.PresentationRequest
import com.trustweave.credential.requests.VerificationOptions
import com.trustweave.credential.results.IssuanceResult
import com.trustweave.credential.results.VerificationResult
import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.CredentialType
import com.trustweave.did.identifiers.Did
import com.trustweave.did.resolver.DidResolver
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.results.SignResult
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.datetime.Clock

/**
 * Simple test helper to create a CredentialService for tests.
 * 
 * Creates a real CredentialService with KMS and DID resolver configured.
 * This ensures credentials are actually signed with proofs.
 */
fun createTestCredentialService(
    kms: KeyManagementService? = null,
    didResolver: DidResolver? = null
): CredentialService {
    val actualKms = kms ?: InMemoryKeyManagementService()
    val actualDidResolver = didResolver ?: DidResolver { did ->
        // Default resolver that always succeeds (for simple tests)
        com.trustweave.did.resolver.DidResolutionResult.Success(
            document = com.trustweave.did.model.DidDocument(
                id = did,
                verificationMethod = emptyList(),
                authentication = emptyList(),
                assertionMethod = emptyList()
            )
        )
    }
    
    // Create signer function from KMS
    val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
        when (val result = actualKms.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
            is SignResult.Success -> result.signature
            else -> throw IllegalStateException("Signing failed: $result")
        }
    }
    
    // Create real CredentialService with signer configured
    return credentialService(
        didResolver = actualDidResolver,
        signer = signer
    )
}

/**
 * Legacy mock implementation - removed.
 * Use createTestCredentialService(kms, didResolver) for real credential signing instead.
 */
private fun createMockCredentialService(): CredentialService {
    return object : CredentialService {
        override suspend fun issue(request: IssuanceRequest): IssuanceResult {
            return IssuanceResult.Success(
                VerifiableCredential(
                    id = request.id?.let { com.trustweave.credential.identifiers.CredentialId(it.value) },
                    type = request.type,
                    issuer = request.issuer,
                    credentialSubject = request.credentialSubject,
                    issuanceDate = request.issuedAt,
                    expirationDate = request.validUntil,
                    credentialStatus = request.credentialStatus,
                    credentialSchema = request.credentialSchema,
                    evidence = request.evidence,
                    proof = null // Proof would be added by proof engine
                )
            )
        }

        override suspend fun verify(
            credential: VerifiableCredential,
            trustPolicy: com.trustweave.credential.trust.TrustPolicy?,
            options: VerificationOptions
        ): VerificationResult {
            return VerificationResult.Valid(
                credential = credential,
                issuerIri = credential.issuer.id,
                subjectIri = credential.credentialSubject.id,
                issuedAt = credential.issuanceDate,
                expiresAt = credential.expirationDate
            )
        }

        override suspend fun createPresentation(
            credentials: List<VerifiableCredential>,
            request: PresentationRequest
        ): VerifiablePresentation {
            val holder = credentials.firstOrNull()?.credentialSubject?.id
                ?: Did("did:key:holder")
            
            return VerifiablePresentation(
                type = listOf(CredentialType.fromString("VerifiablePresentation")),
                verifiableCredential = credentials,
                holder = holder,
                proof = null,
                challenge = request.proofOptions?.challenge,
                domain = request.proofOptions?.domain
            )
        }

        override suspend fun verifyPresentation(
            presentation: VerifiablePresentation,
            trustPolicy: com.trustweave.credential.trust.TrustPolicy?,
            options: VerificationOptions
        ): VerificationResult {
            val credential = presentation.verifiableCredential.firstOrNull()
                ?: VerifiableCredential(
                    type = listOf(CredentialType.VerifiableCredential),
                    issuer = Issuer.fromDid(Did("did:key:issuer")),
                    credentialSubject = CredentialSubject.fromDid(Did("did:key:subject")),
                    issuanceDate = Clock.System.now()
                )
            return VerificationResult.Valid(
                credential = credential,
                issuerIri = credential.issuer.id,
                subjectIri = credential.credentialSubject.id,
                issuedAt = credential.issuanceDate,
                expiresAt = credential.expirationDate
            )
        }

        override suspend fun status(credential: VerifiableCredential): com.trustweave.credential.results.CredentialStatusInfo {
            return com.trustweave.credential.results.CredentialStatusInfo(
                valid = true,
                revoked = false,
                expired = false,
                notYetValid = false
            )
        }

        override fun supports(format: ProofSuiteId): Boolean {
            return format == ProofSuiteId.VC_LD
        }

        override fun supportedFormats(): List<ProofSuiteId> {
            return listOf(ProofSuiteId.VC_LD)
        }

        override fun supportsCapability(
            format: ProofSuiteId,
            capability: com.trustweave.credential.spi.proof.ProofEngineCapabilities.() -> Boolean
        ): Boolean {
            return format == ProofSuiteId.VC_LD
        }
    }
}

