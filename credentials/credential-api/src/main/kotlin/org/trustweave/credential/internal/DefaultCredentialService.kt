package org.trustweave.credential.internal

import org.trustweave.credential.*
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.vc.getFormatId
import org.trustweave.credential.results.CredentialStatusInfo
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.credential.trust.TrustPolicy
// ProofEngineUtils is imported for DID resolution
import org.trustweave.credential.proof.internal.engines.ProofEngineUtils
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Default implementation of CredentialService.
 * 
 * Delegates format-specific operations to registered proof engines.
 */
internal class DefaultCredentialService(
    private val engines: Map<ProofSuiteId, ProofEngine>,
    private val didResolver: DidResolver,
    private val schemaRegistry: SchemaRegistry? = null,
    private val revocationManager: CredentialRevocationManager? = null
) : CredentialService {
    
    // Json instance configured with Instant serialization for ISO 8601 formatting
    private val json = Json {
        serializersModule = SerializationModule.default
    }
    
    override suspend fun issue(request: IssuanceRequest): IssuanceResult {
        // Input validation
        try {
            request.id?.let { InputValidation.validateCredentialId(it) }
            InputValidation.validateIri(request.issuer.id)
            InputValidation.validateIri(request.credentialSubject.id)
            request.credentialSchema?.id?.value?.let { InputValidation.validateSchemaId(it) }
            request.issuerKeyId?.value?.let { InputValidation.validateVerificationMethodId(it) }
            
            // Validate claims count
            val claimsCount = request.credentialSubject.claims.size
            if (claimsCount > SecurityConstants.MAX_CLAIMS_PER_CREDENTIAL) {
                return IssuanceResult.Failure.InvalidRequest(
                    field = "credentialSubject.claims",
                    reason = "Credential exceeds maximum claims count of " +
                            "${SecurityConstants.MAX_CLAIMS_PER_CREDENTIAL}: $claimsCount claims"
                )
            }
        } catch (e: IllegalArgumentException) {
            return IssuanceResult.Failure.InvalidRequest(
                field = "request",
                reason = "Input validation failed: ${e.message}"
            )
        }
        
        // Validate engine availability
        ErrorHandling.validateEngineAvailability(request.format, engines)?.let { return it }
        
        val engine = engines[request.format]!! // Safe because validateEngineAvailability ensures it exists
        
        // Handle issuance with centralized error handling
        return ErrorHandling.handleIssuanceErrors(request.format) {
            engine.issue(request)
        }
    }
    
    override suspend fun verify(
        credential: VerifiableCredential,
        trustPolicy: TrustPolicy?,
        options: VerificationOptions
    ): VerificationResult {
        // Input validation for security and stability
        try {
            InputValidation.validateCredentialStructure(credential)
        } catch (e: IllegalArgumentException) {
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Credential input validation failed: ${e.message}",
                errors = listOf("Invalid credential structure: ${e.message}")
            )
        }
        
        // Validate VC context
        CredentialValidation.validateContext(credential)?.let { return it }
        
        // Validate proof exists
        CredentialValidation.validateProofExists(credential)?.let { return it }
        
        // Get format from proof
        val proofSuiteId = credential.proof?.getFormatId()
            ?: return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Credential has no proof",
                errors = listOf("VerifiableCredential must have a proof for verification")
            )
        
        val engine = engines[proofSuiteId]
            ?: return VerificationResult.Invalid.UnsupportedFormat(
                credential = credential,
                format = proofSuiteId,
                errors = listOf(
                    "Format ${proofSuiteId.value} is not supported. " +
                    "Supported formats: ${engines.keys.map { it.value }}"
                )
            )
        
        if (!engine.isReady()) {
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Proof engine for format ${proofSuiteId.value} is not ready",
                errors = listOf("Proof engine not initialized")
            )
        }
        
        // Perform temporal validity checks (format-agnostic)
        val now = Clock.System.now()
        CredentialValidation.validateNotBefore(credential, options, now)?.let { return it }
        CredentialValidation.validateExpiration(credential, options, now)?.let { return it }
        
        // Schema validation (format-agnostic)
        CredentialValidation.validateSchema(credential, options, schemaRegistry)?.let { return it }
        
        // Revocation check (format-agnostic) with proper warning collection
        val revocationWarnings = mutableListOf<String>()
        if (options.checkRevocation) {
            val (revocationFailure, warnings) = RevocationChecker.checkRevocationStatus(
                credential = credential,
                revocationManager = revocationManager,
                policy = options.revocationFailurePolicy
            )
            revocationFailure?.let { return it }
            revocationWarnings.addAll(warnings)
        }
        
        // Trust policy check (format-agnostic)
        CredentialValidation.validateTrust(credential, trustPolicy)?.let { return it }
        
        // Delegate to proof engine for format-specific verification
        val engineResult = engine.verify(credential, options)
        
        // Add revocation warnings to the result if verification succeeded
        return when (engineResult) {
            is VerificationResult.Valid -> {
                if (revocationWarnings.isNotEmpty()) {
                    engineResult.copy(warnings = engineResult.warnings + revocationWarnings)
                } else {
                    engineResult
                }
            }
            is VerificationResult.Invalid -> engineResult
        }
    }
    
    override suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation {
        require(credentials.isNotEmpty()) { "At least one credential is required" }
        
        // Input validation
        if (credentials.size > SecurityConstants.MAX_CREDENTIALS_PER_PRESENTATION) {
            throw IllegalArgumentException(
                "Presentation exceeds maximum credentials count of " +
                "${SecurityConstants.MAX_CREDENTIALS_PER_PRESENTATION}: ${credentials.size} credentials"
            )
        }
        
        // Validate each credential structure
        credentials.forEach { credential ->
            try {
                InputValidation.validateCredentialStructure(credential)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Invalid credential in presentation: ${e.message}",
                    e
                )
            }
        }
        
        // For now, use the first credential's format engine
        // Multi-format presentations can be handled in the future
        val credential = credentials.first()
        val proofSuiteId = credential.proof?.getFormatId()
            ?: throw IllegalArgumentException("Credential has no proof")
        
        val engine = engines[proofSuiteId]
            ?: throw IllegalArgumentException("Unsupported format: ${proofSuiteId.value}")
        
        if (!engine.capabilities.presentation) {
            throw UnsupportedOperationException(
                "Format ${proofSuiteId.value} does not support presentations"
            )
        }
        
        return engine.createPresentation(credentials, request)
    }
    
    override suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        trustPolicy: TrustPolicy?,
        options: VerificationOptions
    ): VerificationResult {
        // Input validation for security and stability
        try {
            InputValidation.validatePresentationStructure(presentation)
        } catch (e: IllegalArgumentException) {
            // Create a minimal error credential for the InvalidProof result
            val errorCredential = VerifiableCredential(
                type = listOf(CredentialType.fromString("VerifiableCredential")),
                issuer = Issuer.IriIssuer(Iri("did:error:invalid-presentation")),
                issuanceDate = Clock.System.now(),
                credentialSubject = CredentialSubject(
                    id = Iri("did:error:invalid-presentation"),
                    claims = emptyMap()
                )
            )
            return VerificationResult.Invalid.InvalidProof(
                credential = errorCredential,
                reason = "Presentation input validation failed: ${e.message}",
                errors = listOf("Invalid presentation structure: ${e.message}")
            )
        }
        
        // Check that presentation has credentials
        if (presentation.verifiableCredential.isEmpty()) {
            // Create a minimal error credential for the InvalidProof result
            // We can't throw here because we need to return a VerificationResult
            val errorCredential = VerifiableCredential(
                type = listOf(CredentialType.fromString("VerifiableCredential")),
                issuer = Issuer.IriIssuer(Iri("did:error:empty-presentation")),
                issuanceDate = Clock.System.now(),
                credentialSubject = CredentialSubject(
                    id = Iri("did:error:empty-presentation"),
                    claims = emptyMap()
                )
            )
            return VerificationResult.Invalid.InvalidProof(
                credential = errorCredential,
                reason = "Presentation must contain at least one credential",
                errors = listOf("VerifiablePresentation must contain at least one VerifiableCredential")
            )
        }
        
        // Verify each credential in the presentation
        val credentialResults = presentation.verifiableCredential.map { credential ->
            verify(credential, trustPolicy, options)
        }
        
        // If any credential is invalid, return failure
        val firstInvalidCredential = credentialResults.firstOrNull { it is VerificationResult.Invalid }
        if (firstInvalidCredential != null) {
            return firstInvalidCredential
        }
        
        // Verify challenge if required
        PresentationVerification.verifyChallenge(presentation, options)?.let { return it }
        
        // Verify domain if required
        PresentationVerification.verifyDomain(presentation, options)?.let { return it }
        
        // Verify presentation proof if verification is enabled
        if (options.verifyPresentationProof) {
            if (presentation.proof == null) {
                // Presentation proof is required when verification is enabled
                return VerificationResult.Invalid.InvalidProof(
                    credential = presentation.verifiableCredential.first(),
                    reason = "Presentation proof is required but missing",
                    errors = listOf(
                        "VerifiablePresentation must have a proof when presentation proof verification is enabled"
                    )
                )
            } else {
                // Verify presentation proof using the appropriate proof engine
                val proofFormat = presentation.proof.getFormatId()
                PresentationVerification.verifyProofFormatSupported(proofFormat, engines, presentation)?.let { return it }
                val engine = engines[proofFormat]!!
                
                // For presentation proof verification, we need to verify the proof signature
                // This is similar to credential verification but for the presentation itself
                // Since proof engines don't have a direct "verifyPresentationProof" method,
                // we'll create a minimal credential-like structure for verification
                // Note: This is a simplified approach - full implementation would verify
                // the presentation document itself, not a credential
                
                // For VC-LD presentations, we can verify the LinkedDataProof directly
                if (presentation.proof is org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof) {
                    val proof = presentation.proof as org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
                    
                    // Verify the proof signature
                    val holderIri = presentation.holder
                    if (holderIri.isDid) {
                        val verificationMethod = PresentationVerification.resolvePresentationProofVerificationMethod(
                            holderIri = holderIri,
                            verificationMethodId = proof.verificationMethod,
                            didResolver = didResolver
                        )
                        
                        if (verificationMethod == null) {
                            return VerificationResult.Invalid.InvalidIssuer(
                                credential = presentation.verifiableCredential.first(),
                                issuerIri = holderIri,
                                reason = "Could not resolve holder DID or get verification key",
                                errors = listOf("Failed to resolve holder: ${holderIri.value}")
                            )
                        }
                        
                        // Build presentation document without proof for canonicalization
                        val vpDocument = buildPresentationDocumentWithoutProof(presentation)
                        val canonical = canonicalizePresentationDocument(vpDocument)
                        
                        // Verify signature
                        val isValid = PresentationVerification.verifyPresentationSignature(
                            canonical = canonical,
                            proofValue = proof.proofValue,
                            verificationMethod = verificationMethod,
                            proofType = proof.type
                        )
                        
                        if (!isValid) {
                            return VerificationResult.Invalid.InvalidProof(
                                credential = presentation.verifiableCredential.first(),
                                reason = "Presentation proof signature verification failed",
                                errors = listOf("Invalid signature on VerifiablePresentation proof")
                            )
                        }
                    }
                } else if (presentation.proof is org.trustweave.credential.model.vc.CredentialProof.SdJwtVcProof) {
                    // SD-JWT-VC presentation proofs would be verified differently
                    // For now, we'll return a warning that SD-JWT-VC presentation proof verification
                    // is not fully implemented
                    return VerificationResult.Valid(
                        credential = presentation.verifiableCredential.first(),
                        issuerIri = presentation.verifiableCredential.first().issuer.id,
                        subjectIri = presentation.holder,
                        issuedAt = Clock.System.now(),
                        expiresAt = presentation.expirationDate,
                        warnings = listOf(
                            "SD-JWT-VC presentation proof verification is not fully implemented. " +
                            "Only credential proofs were verified."
                        )
                    )
                }
            }
        }
        
        // All checks passed - return success
        val firstValidResult = credentialResults.firstOrNull { it is VerificationResult.Valid }
            as? VerificationResult.Valid
        
        return VerificationResult.Valid(
            credential = presentation.verifiableCredential.first(),
            issuerIri = firstValidResult?.issuerIri ?: presentation.verifiableCredential.first().issuer.id,
            subjectIri = presentation.holder,
            issuedAt = firstValidResult?.issuedAt ?: Clock.System.now(),
            expiresAt = presentation.expirationDate ?: firstValidResult?.expiresAt,
            warnings = credentialResults.flatMap { it.allWarnings }
        )
    }
    
    // Helper methods for presentation verification
    
    private fun buildPresentationDocumentWithoutProof(presentation: VerifiablePresentation): kotlinx.serialization.json.JsonObject {
        return kotlinx.serialization.json.buildJsonObject {
            put("@context", kotlinx.serialization.json.buildJsonArray {
                presentation.context.forEach { add(it) }
            })
            presentation.id?.let { put("id", it.value) }
            put("type", kotlinx.serialization.json.buildJsonArray {
                presentation.type.forEach { add(it.value) }
            })
            put("holder", presentation.holder.value)
            put("verifiableCredential", kotlinx.serialization.json.buildJsonArray {
                presentation.verifiableCredential.forEach { credential ->
                    // For presentation proof verification, include credential IDs
                    // The actual credential proofs are verified separately above
                    add(kotlinx.serialization.json.buildJsonObject {
                        credential.id?.let { put("id", it.value) }
                        put("type", buildJsonArray {
                            credential.type.forEach { add(it.value) }
                        })
                    })
                }
            })
            presentation.expirationDate?.let { put("expirationDate", it.toString()) }
            presentation.challenge?.let { put("challenge", it) }
            presentation.domain?.let { put("domain", it) }
            // Explicitly exclude proof for canonicalization
        }
    }
    
    /**
     * Canonicalize presentation document using JSON-LD.
     * 
     * Converts the presentation document to canonical N-Quads format for signature verification.
     * 
     * @param document The presentation document JSON (without proof)
     * @return Canonicalized document as string
     */
    private fun canonicalizePresentationDocument(document: kotlinx.serialization.json.JsonObject): String {
        // Use same canonicalization as VC-LD (JSON-LD)
        return JsonLdUtils.canonicalizeDocument(document, json)
    }
    
    
    override suspend fun status(credential: VerifiableCredential): CredentialStatusInfo {
        val now = Clock.System.now()
        
        val expired = credential.expirationDate != null && now > credential.expirationDate
        
        val revoked = if (credential.credentialStatus != null && revocationManager != null) {
            try {
                val revocationStatus = revocationManager.checkRevocationStatus(credential)
                revocationStatus.revoked || revocationStatus.suspended
            } catch (e: Exception) {
                false // If revocation check fails, assume not revoked
            }
        } else {
            false
        }
        
        return CredentialStatusInfo(
            valid = !expired && !revoked,
            revoked = revoked,
            expired = expired,
            notYetValid = false  // VC doesn't have validFrom, only expirationDate
        )
    }
    
    override fun supports(format: ProofSuiteId): Boolean {
        return engines.containsKey(format)
    }
    
    override fun supportedFormats(): List<ProofSuiteId> {
        return engines.keys.toList()
    }
    
    override fun supportsCapability(
        format: ProofSuiteId,
        capability: ProofEngineCapabilities.() -> Boolean
    ): Boolean {
        val engine = engines[format] ?: return false
        return capability(engine.capabilities)
    }
    
    
}
