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
import org.trustweave.credential.trust.TrustEvaluator
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
import kotlin.time.toKotlinDuration

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
            request.credentialSubject.id?.let { InputValidation.validateIri(it) }
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
        trustEvaluator: TrustEvaluator?,
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
        
        // Get format from proof — at this point proof is non-null (validateProofExists passed),
        // so a null proofSuiteId means the proof type is present but unrecognised/unsupported.
        val proofSuiteId = credential.proof?.getFormatId()
            ?: return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Proof type is unrecognized or unsupported",
                errors = listOf("Proof format ID could not be determined from proof type")
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
        CredentialValidation.validateTrust(credential, trustEvaluator)?.let { return it }
        
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
        trustEvaluator: TrustEvaluator?,
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
            verify(credential, trustEvaluator, options)
        }
        
        // If any credential is invalid, return failure
        val firstInvalidCredential = credentialResults.firstOrNull { it is VerificationResult.Invalid }
        if (firstInvalidCredential != null) {
            return firstInvalidCredential
        }

        // Verify presentation proof FIRST (cryptographic integrity must be established before
        // checking holder binding or challenge, to prevent binding-bypass attacks).
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
                // Copy nullable property to a local — cross-module smart cast not possible.
                val presentationProof = presentation.proof!!
                // Verify presentation proof using the appropriate proof engine
                val proofFormat = presentationProof.getFormatId()
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
                            didResolver = didResolver,
                            declaredProofPurpose = proof.proofPurpose
                        )

                        if (verificationMethod == null) {
                            return VerificationResult.Invalid.InvalidProof(
                                credential = null,
                                reason = "Presentation proof verification failed: could not resolve holder DID, " +
                                    "get verification key for '${holderIri.value}', or the proof's verification " +
                                    "method/proofPurpose is not authorized for 'authentication'",
                                errors = listOf(
                                    "Failed to resolve holder '${holderIri.value}' or its verification method is " +
                                        "not authorized for the 'authentication' proof purpose"
                                )
                            )
                        }

                        // Build presentation document without proof for canonicalization
                        val vpDocument = buildPresentationDocumentWithoutProof(presentation)

                        // Verify signature (covers canonicalized proof options + document,
                        // per W3C Data Integrity)
                        val isValid = PresentationVerification.verifyPresentationSignature(
                            vpDocument = vpDocument,
                            proof = proof,
                            verificationMethod = verificationMethod
                        )

                        if (!isValid) {
                            return VerificationResult.Invalid.InvalidProof(
                                credential = presentation.verifiableCredential.first(),
                                reason = "Presentation proof signature verification failed",
                                errors = listOf("Invalid signature on VerifiablePresentation proof")
                            )
                        }

                        // F-01: Verify the proof's verificationMethod belongs to the declared holder.
                        // This prevents an attacker from supplying a valid proof signed by a
                        // different DID's key while keeping holder binding superficially intact.
                        // SEC-08: presentation.holder is non-nullable; the prior `!= null` guard
                        // was vacuous and has been removed.
                        if (options.enforceHolderBinding) {
                            val holderDid = presentation.holder.value
                            // F-08: exact DID equality of the pre-fragment part — a prefix
                            // check would let 'did:example:abcdef#key-1' satisfy holder
                            // 'did:example:abc'.
                            if (!PresentationVerification.verificationMethodBelongsToHolder(
                                    proof.verificationMethod, holderDid
                                )
                            ) {
                                return VerificationResult.Invalid.InvalidProof(
                                    credential = null,
                                    reason = "Presentation proof verificationMethod '${proof.verificationMethod}' " +
                                        "does not belong to holder '$holderDid'"
                                )
                            }
                        }
                    } else {
                        return VerificationResult.Invalid.InvalidProof(
                            credential = null,
                            reason = "Presentation holder '${holderIri.value}' is not a DID",
                            errors = listOf("Non-DID holder IRI cannot be verified: ${holderIri.value}")
                        )
                    }
                } else if (presentationProof is org.trustweave.credential.model.vc.CredentialProof.SdJwtVcProof) {
                    // SD-JWT-VC presentations carry holder binding as a Key Binding JWT
                    // (KB-JWT) appended to the compact SD-JWT. Verify its signature against
                    // the holder's DID keys, plus sd_hash/iat. The KB-JWT nonce/aud are
                    // checked against expectedChallenge/expectedDomain further below.
                    PresentationVerification.verifySdJwtKeyBinding(
                        presentation = presentation,
                        proof = presentationProof,
                        options = options,
                        didResolver = didResolver
                    )?.let { return it }
                } else {
                    val proofTypeName = presentation.proof!!::class.simpleName
                    return VerificationResult.Invalid.InvalidProof(
                        credential = presentation.verifiableCredential.first(),
                        reason = "Unsupported presentation proof type: $proofTypeName",
                        errors = listOf("Presentation proof type '$proofTypeName' is not supported for verification")
                    )
                }
            }
        }

        // cnf holder binding (RFC 7800 / SD-JWT VC) — enforced unconditionally, for EVERY
        // presentation proof format, whenever the (now verified) presentation embeds an
        // SD-JWT credential whose issuer-signed JWT carries `cnf`: the authenticated
        // presentation holder must be the cnf-designated DID. Without this, an attacker
        // could wrap a stolen cnf-bound credential in e.g. an LD-proof presentation signed
        // with their own key. The SD-JWT KB-JWT path additionally requires the KB-JWT to be
        // signed by the cnf DID (PresentationVerification.verifySdJwtKeyBinding). Only
        // legacy cnf-less credentials are exempt (weaker envelope-holder binding).
        if (options.verifyPresentationProof) {
            PresentationVerification.verifyCnfHolderBinding(presentation)?.let { return it }
        }

        // Guard: holder binding is meaningless without presentation proof verification — the
        // holder field can be trivially forged if the presentation signature is not checked.
        if (options.enforceHolderBinding && !options.verifyPresentationProof) {
            return VerificationResult.Invalid.InvalidProof(
                credential = null,
                reason = "Holder binding cannot be enforced without presentation proof verification",
                errors = listOf("verifyPresentationProof must be true when enforceHolderBinding is true")
            )
        }

        // Holder binding: presentation.holder must match each credential's credentialSubject.id
        if (options.enforceHolderBinding) {
            val holderIri = presentation.holder
            for (credential in presentation.verifiableCredential) {
                val subjectId = credential.credentialSubject.id
                if (subjectId == null || subjectId.value != holderIri.value) {
                    return VerificationResult.Invalid.InvalidProof(
                        credential = null,
                        reason = "Holder binding failed: credential subject ID does not match presentation holder",
                        errors = listOf("credentialSubject.id is missing or does not match holder DID")
                    )
                }
            }
        }

        // Guard: challenge/domain fields can be trivially forged when the presentation signature
        // was not verified.  Require proof verification before honouring either check.
        if ((options.verifyChallenge || options.verifyDomain) && !options.verifyPresentationProof) {
            return VerificationResult.Invalid.InvalidProof(
                credential = null,
                reason = "Challenge/domain verification requires presentation proof verification",
                errors = listOf("verifyPresentationProof must be true when verifyChallenge or verifyDomain is enabled")
            )
        }

        // Verify challenge if required
        PresentationVerification.verifyChallenge(presentation, options)?.let { return it }

        // Verify domain if required
        PresentationVerification.verifyDomain(presentation, options)?.let { return it }
        
        // All checks passed - return success
        val firstValidResult = credentialResults.firstOrNull { it is VerificationResult.Valid }
            as? VerificationResult.Valid

        // F-07: Warn when a presentation contains credentials from more than one issuer.
        // Only the first issuer is reflected in the top-level result field, which may mislead
        // relying parties that inspect only that field.
        val distinctIssuers = credentialResults
            .filterIsInstance<VerificationResult.Valid>()
            .mapNotNull { it.issuerIri }
            .distinct()
        val multiIssuerWarnings = if (distinctIssuers.size > 1) {
            listOf(
                "Presentation contains credentials from ${distinctIssuers.size} distinct issuers: " +
                    "$distinctIssuers. Only the first issuer is reflected in this result."
            )
        } else {
            emptyList()
        }

        // SEC-06: When the presentation proof was not verified, subjectIri is unverified — set it
        // to null so callers cannot rely on an unverified holder DID.
        val warnings = credentialResults.flatMap { it.allWarnings } + multiIssuerWarnings
        val proofWarnings = if (trustEvaluator != null && !options.verifyPresentationProof) {
            listOf(
                "Trust evaluation was performed but presentation proof was not verified. subjectIri is unverified."
            )
        } else {
            emptyList()
        }

        return VerificationResult.Valid(
            credential = presentation.verifiableCredential.first(),
            issuerIri = firstValidResult?.issuerIri ?: presentation.verifiableCredential.first().issuer.id,
            // SEC-06: only populate subjectIri from the holder when the proof has been verified;
            // an unverified holder value could be trivially forged by an attacker.
            subjectIri = if (options.verifyPresentationProof) presentation.holder else null,
            issuedAt = firstValidResult?.issuedAt
                ?: presentation.verifiableCredential.firstOrNull()?.issuanceDate
                ?: presentation.verifiableCredential.firstOrNull()?.validFrom
                ?: Clock.System.now(),
            expiresAt = presentation.expirationDate ?: firstValidResult?.expiresAt,
            warnings = warnings + proofWarnings
        )
    }
    
    // Helper methods for presentation verification
    
    private fun buildPresentationDocumentWithoutProof(presentation: VerifiablePresentation): kotlinx.serialization.json.JsonObject {
        val fullJson = json.encodeToJsonElement(VerifiablePresentation.serializer(), presentation).jsonObject
        return kotlinx.serialization.json.buildJsonObject {
            fullJson.entries.forEach { (k, v) ->
                if (k != "proof") put(k, v)
            }
        }
    }
    
    override suspend fun status(
        credential: VerifiableCredential,
        clockSkewTolerance: kotlin.time.Duration
    ): CredentialStatusInfo {
        val now = Clock.System.now()
        val clockSkewKt = clockSkewTolerance

        // Use the same VC-version-aware expiry/notBefore logic as CredentialValidation so that
        // status() and verify() always agree on which field is authoritative for each VC version.
        // SEC-03: was incorrectly using raw `validUntil ?: expirationDate` for all VC versions.
        val effectiveExpiry = if (credential.isVc2 && !credential.isVc1) {
            credential.validUntil
        } else {
            credential.validUntil ?: credential.expirationDate
        }
        // SEC-05: apply clock-skew tolerance, matching verify()'s behaviour.
        val expired = effectiveExpiry != null && now > effectiveExpiry.plus(clockSkewKt)

        val revoked = if (credential.credentialStatus != null && revocationManager != null) {
            try {
                val revocationStatus = revocationManager.checkRevocationStatus(credential)
                revocationStatus.revoked || revocationStatus.suspended
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                true // Fail-closed: treat revocation check errors as revoked
            }
        } else {
            false
        }

        // SEC-03: VC-version-aware notBefore (matches CredentialValidation.validateNotBefore).
        val effectiveNotBefore = if (credential.isVc2 && !credential.isVc1) {
            credential.validFrom
        } else {
            credential.validFrom ?: credential.issuanceDate
        }
        // SEC-05: apply clock-skew tolerance.
        val notYetValid = effectiveNotBefore != null && now < effectiveNotBefore.minus(clockSkewKt)

        return CredentialStatusInfo(
            valid = !expired && !revoked && !notYetValid,
            revoked = revoked,
            expired = expired,
            notYetValid = notYetValid
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
