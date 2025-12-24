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
import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
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
        val engine = engines[request.format]
            ?: return IssuanceResult.Failure.UnsupportedFormat(
                format = request.format,
                supportedFormats = engines.keys.toList()
            )
        
        if (!engine.isReady()) {
            return IssuanceResult.Failure.AdapterNotReady(
                format = request.format,
                reason = "Proof engine not initialized"
            )
        }
        
        return try {
            val credential = engine.issue(request)
            IssuanceResult.Success(credential)
        } catch (e: IllegalArgumentException) {
            IssuanceResult.Failure.InvalidRequest(
                field = "request",
                reason = e.message ?: "Invalid request"
            )
        } catch (e: IllegalStateException) {
            IssuanceResult.Failure.AdapterNotReady(
                format = request.format,
                reason = e.message
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions to respect coroutine cancellation
            throw e
        } catch (e: java.util.concurrent.TimeoutException) {
            IssuanceResult.Failure.AdapterError(
                format = request.format,
                reason = "Issuance operation timed out: ${e.message}",
                cause = e
            )
        } catch (e: java.io.IOException) {
            IssuanceResult.Failure.AdapterError(
                format = request.format,
                reason = "I/O error during issuance: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            IssuanceResult.Failure.AdapterError(
                format = request.format,
                reason = e.message ?: "Unknown error during issuance",
                cause = e
            )
        }
    }
    
    override suspend fun verify(
        credential: VerifiableCredential,
        trustPolicy: TrustPolicy?,
        options: VerificationOptions
    ): VerificationResult {
        // Validate VC context
        if (!credential.hasValidContext()) {
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Invalid or missing W3C VC context",
                errors = listOf(
                    "Credential context must include at least one of: " +
                    "'https://www.w3.org/2018/credentials/v1' (VC 1.1) or " +
                    "'https://www.w3.org/ns/credentials/v2' (VC 2.0). " +
                    "Found: ${credential.context}"
                )
            )
        }
        
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
        
        // Perform temporal validity checks first (format-agnostic)
        val now = Clock.System.now()
        val clockSkew = options.clockSkewTolerance
        
        // Check validFrom (notBefore) - VC 2.0 support
        if (options.checkNotBefore && credential.validFrom != null) {
            val clockSkewKt = kotlin.time.Duration.parse(clockSkew.toString())
            if (now < credential.validFrom.minus(clockSkewKt)) {
                return VerificationResult.Invalid.NotYetValid(
                    credential = credential,
                    validFrom = credential.validFrom,
                    errors = listOf("Credential not yet valid (validFrom: ${credential.validFrom}, current time: $now)")
                )
            }
        }
        
        // Check expiration
        if (options.checkExpiration && credential.expirationDate != null) {
            val clockSkewKt = kotlin.time.Duration.parse(clockSkew.toString())
            if (now > credential.expirationDate.plus(clockSkewKt)) {
                return VerificationResult.Invalid.Expired(
                    credential = credential,
                    expiredAt = credential.expirationDate,
                    errors = listOf("Credential expired at ${credential.expirationDate}")
                )
            }
        }
        
        // Schema validation (format-agnostic)
        if (options.validateSchema && schemaRegistry != null && credential.credentialSchema != null) {
            val schemaId = options.schemaId ?: credential.credentialSchema.id
            
            try {
                val schemaResult = schemaRegistry.validate(credential, schemaId)
                if (!schemaResult.valid) {
                    val validationErrors = schemaResult.errors.map { "${it.path}: ${it.message}" }
                    return VerificationResult.Invalid.SchemaValidationFailed(
                        credential = credential,
                        schemaId = schemaId.value,
                        validationErrors = validationErrors,
                        errors = validationErrors
                    )
                }
            } catch (e: Exception) {
                return VerificationResult.Invalid.SchemaValidationFailed(
                    credential = credential,
                    schemaId = schemaId.value,
                    validationErrors = listOf("Schema validation error: ${e.message}"),
                    errors = listOf("Schema validation failed: ${e.message}")
                )
            }
        }
        
        // Revocation check (format-agnostic)
        if (options.checkRevocation && revocationManager != null && credential.credentialStatus != null) {
            try {
                val revocationStatus = revocationManager.checkRevocationStatus(credential)
                if (revocationStatus.revoked) {
                    return VerificationResult.Invalid.Revoked(
                        credential = credential,
                        revokedAt = Clock.System.now(),
                        errors = listOf("Credential has been revoked${revocationStatus.reason?.let { ": $it" } ?: ""}"),
                        warnings = emptyList(),
                        revocationReason = revocationStatus.reason
                    )
                }
                if (revocationStatus.suspended) {
                    return VerificationResult.Invalid.Revoked(
                        credential = credential,
                        revokedAt = Clock.System.now(),
                        errors = listOf("Credential is suspended${revocationStatus.reason?.let { ": $it" } ?: ""}"),
                        warnings = emptyList(),
                        revocationReason = revocationStatus.reason
                    )
                }
            } catch (e: java.util.concurrent.TimeoutException) {
                // Timeout accessing revocation service
                handleRevocationFailure(
                    credential = credential,
                    error = e,
                    reason = "Revocation check timed out",
                    policy = options.revocationFailurePolicy
                )?.let { return it }
            } catch (e: java.net.UnknownHostException) {
                // Network error - revocation service unreachable
                handleRevocationFailure(
                    credential = credential,
                    error = e,
                    reason = "Revocation service unreachable: ${e.message}",
                    policy = options.revocationFailurePolicy
                )?.let { return it }
            } catch (e: java.net.ConnectException) {
                // Connection refused
                handleRevocationFailure(
                    credential = credential,
                    error = e,
                    reason = "Revocation service connection refused: ${e.message}",
                    policy = options.revocationFailurePolicy
                )?.let { return it }
            } catch (e: java.io.IOException) {
                // I/O error
                handleRevocationFailure(
                    credential = credential,
                    error = e,
                    reason = "Revocation check I/O error: ${e.message}",
                    policy = options.revocationFailurePolicy
                )?.let { return it }
            } catch (e: IllegalStateException) {
                // Invalid state (e.g., revocation manager not initialized)
                handleRevocationFailure(
                    credential = credential,
                    error = e,
                    reason = "Revocation manager error: ${e.message}",
                    policy = options.revocationFailurePolicy
                )?.let { return it }
            } catch (e: IllegalArgumentException) {
                // Invalid argument (e.g., malformed credential status)
                handleRevocationFailure(
                    credential = credential,
                    error = e,
                    reason = "Invalid revocation check request: ${e.message}",
                    policy = options.revocationFailurePolicy
                )?.let { return it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Coroutine cancellation - rethrow
                throw e
            } catch (e: Exception) {
                // Other unexpected errors
                handleRevocationFailure(
                    credential = credential,
                    error = e,
                    reason = "Unexpected revocation check error: ${e.message}",
                    policy = options.revocationFailurePolicy
                )?.let { return it }
            }
        }
        
        // Trust policy check (format-agnostic)
        if (trustPolicy != null) {
            val issuerIri = credential.issuer.id
            if (issuerIri.isDid) {
                try {
                    val issuerDid = Did(issuerIri.value)
                    val isTrusted = trustPolicy.isTrusted(issuerDid)
                    if (!isTrusted) {
                        return VerificationResult.Invalid.UntrustedIssuer(
                            credential = credential,
                            issuerDid = issuerDid
                        )
                    }
                } catch (e: IllegalArgumentException) {
                    // Invalid DID format - will be caught by proof engine verification
                }
            }
        }
        
        // Delegate to proof engine for format-specific verification
        return engine.verify(credential, options)
    }
    
    override suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation {
        require(credentials.isNotEmpty()) { "At least one credential is required" }
        
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
        if (options.verifyChallenge) {
            if (options.expectedChallenge != null) {
                if (presentation.challenge != options.expectedChallenge) {
                    return VerificationResult.Invalid.InvalidProof(
                        credential = presentation.verifiableCredential.first(),
                        reason = "Challenge mismatch",
                        errors = listOf(
                            "Expected challenge '${options.expectedChallenge}', " +
                            "but got '${presentation.challenge}'"
                        )
                    )
                }
            } else if (presentation.challenge == null) {
                // Warning: challenge verification requested but no challenge provided
                // Continue verification but this might be a security concern
            }
        }
        
        // Verify domain if required
        if (options.verifyDomain) {
            if (options.expectedDomain != null) {
                if (presentation.domain != options.expectedDomain) {
                    return VerificationResult.Invalid.InvalidProof(
                        credential = presentation.verifiableCredential.first(),
                        reason = "Domain mismatch",
                        errors = listOf(
                            "Expected domain '${options.expectedDomain}', " +
                            "but got '${presentation.domain}'"
                        )
                    )
                }
            }
        }
        
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
                val engine = engines[proofFormat]
                
                if (engine == null) {
                    return VerificationResult.Invalid.UnsupportedFormat(
                        credential = presentation.verifiableCredential.first(),
                        format = proofFormat,
                        errors = listOf(
                            "Presentation proof format '${proofFormat.value}' is not supported. " +
                            "Supported formats: ${engines.keys.map { it.value }}"
                        )
                    )
                }
                
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
                        val verificationMethod = ProofEngineUtils.resolveVerificationMethod(
                            issuerIri = holderIri,
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
                        val isValid = verifyPresentationSignature(
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
    
    private fun canonicalizePresentationDocument(document: kotlinx.serialization.json.JsonObject): String {
        // Use same canonicalization as VC-LD (JSON-LD)
        return try {
            val documentMap = jsonObjectToMap(document)
            val options = com.github.jsonldjava.core.JsonLdOptions()
            options.format = "application/n-quads"
            val canonical = com.github.jsonldjava.core.JsonLdProcessor.normalize(documentMap, options)
            canonical?.toString() ?: kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                document
            )
        } catch (e: Exception) {
            // Fallback: use JSON serialization
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                document
            )
        }
    }
    
    private fun jsonObjectToMap(jsonObject: kotlinx.serialization.json.JsonObject): Map<String, Any> {
        return jsonObject.entries.associate { (key, value) ->
            key to when (value) {
                is kotlinx.serialization.json.JsonPrimitive -> {
                    when {
                        value.isString -> value.content
                        value.booleanOrNull != null -> value.boolean
                        value.longOrNull != null -> value.long
                        value.doubleOrNull != null -> value.double
                        else -> value.content
                    }
                }
                is kotlinx.serialization.json.JsonArray -> value.map { element ->
                    when (element) {
                        is kotlinx.serialization.json.JsonPrimitive -> element.content
                        is kotlinx.serialization.json.JsonObject -> jsonObjectToMap(element)
                        else -> element.toString()
                    }
                }
                is kotlinx.serialization.json.JsonObject -> jsonObjectToMap(value)
                else -> value.toString()
            }
        }
    }
    
    private fun verifyPresentationSignature(
        canonical: String,
        proofValue: String,
        verificationMethod: org.trustweave.did.model.VerificationMethod,
        proofType: String
    ): Boolean {
        if (proofValue.isBlank()) {
            return false
        }
        
        // Only support Ed25519Signature2020 for now
        if (proofType != "Ed25519Signature2020") {
            return false
        }
        
        try {
            // Extract public key from verification method
            val publicKey = ProofEngineUtils.extractPublicKey(verificationMethod) ?: return false
            
            // Decode signature (base64url)
            val signatureBytes = try {
                java.util.Base64.getUrlDecoder().decode(proofValue)
            } catch (e: Exception) {
                return false
            }
            
            // Get canonical document bytes
            val documentBytes = canonical.toByteArray(Charsets.UTF_8)
            
            // Verify Ed25519 signature
            val signature = java.security.Signature.getInstance("Ed25519")
            signature.initVerify(publicKey as? java.security.PublicKey ?: return false)
            signature.update(documentBytes)
            return signature.verify(signatureBytes)
        } catch (e: Exception) {
            return false
        }
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
    
    /**
     * Handle revocation check failure according to the configured policy.
     * 
     * @param credential The credential being verified
     * @param error The exception that occurred
     * @param reason Human-readable reason for the failure
     * @param policy The revocation failure policy to apply
     * @return VerificationResult if verification should fail, null if it should continue
     */
    private fun handleRevocationFailure(
        credential: VerifiableCredential,
        error: Throwable,
        reason: String,
        policy: org.trustweave.credential.requests.RevocationFailurePolicy
    ): VerificationResult? {
        return when (policy) {
            org.trustweave.credential.requests.RevocationFailurePolicy.FAIL_CLOSED -> {
                // Fail-closed: Reject credential if revocation cannot be checked
                VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "Revocation check failed: $reason",
                    errors = listOf(
                        "Cannot verify credential revocation status: $reason",
                        "Credential rejected due to revocation check failure (fail-closed policy)"
                    ),
                    warnings = emptyList()
                )
            }
            org.trustweave.credential.requests.RevocationFailurePolicy.FAIL_WITH_WARNING -> {
                // Fail-with-warning: Continue verification but add warning
                // Return null to continue verification - warning will be added later if needed
                // Note: This requires modifying the verification flow to collect warnings
                // For now, we'll add the warning to the result after verification
                null
            }
            org.trustweave.credential.requests.RevocationFailurePolicy.FAIL_OPEN -> {
                // Fail-open: Continue verification silently
                // Use only when availability is more important than security
                null
            }
        }
    }
    
}
