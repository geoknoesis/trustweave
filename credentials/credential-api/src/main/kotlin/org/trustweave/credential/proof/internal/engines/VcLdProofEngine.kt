package org.trustweave.credential.proof.internal.engines

import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.internal.JsonLdUtils
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.spi.proof.ProofEngineConfig
// ProofEngineUtils is in the same package, no import needed
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.identifiers.Did
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import org.trustweave.did.model.VerificationMethod
import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.*
import java.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import org.slf4j.LoggerFactory

/**
 * VC-LD (Verifiable Credentials Linked Data) proof engine.
 * 
 * Supports W3C Verifiable Credentials 2.0 with Linked Data Proofs.
 * Uses JSON-LD canonicalization and various signature suites.
 */
internal class VcLdProofEngine(
    private val config: ProofEngineConfig = ProofEngineConfig()
) : ProofEngine {
    
    private val logger = LoggerFactory.getLogger(VcLdProofEngine::class.java)
    
    override val format = ProofSuiteId.VC_LD
    override val formatName = "Verifiable Credentials (Linked Data)"
    override val formatVersion = "2.0"
    
    override val capabilities = ProofEngineCapabilities(
        selectiveDisclosure = true,
        zeroKnowledge = false,
        revocation = true,
        presentation = true,
        predicates = true
    )
    
    override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
        require(request.format == format) {
            "Request format ${request.format.value} does not match engine format ${format.value}"
        }
        
        // Get issuer IRI
        val issuerIri = when (val issuer = request.issuer) {
            is Issuer.IriIssuer -> issuer.id
            is Issuer.ObjectIssuer -> issuer.id
        }
        
        // Get key ID for signing (extract key part from VerificationMethodId if needed)
        val keyId = ProofEngineUtils.extractKeyId(request.issuerKeyId?.value)
            ?: throw IllegalArgumentException("issuerKeyId is required for signing")
        
        logger.debug("Issuing VC-LD credential: issuerKeyId={}, extracted keyId={}", request.issuerKeyId?.value, keyId)
        
        // Build VC document for canonicalization
        val vcDocument = buildVcDocument(request)
        
        // Canonicalize document (without proof)
        val canonicalDocument = canonicalizeDocument(vcDocument)
        logger.debug("Canonicalized document for signing: length={}", canonicalDocument.length)
        
        // Generate signature using KMS
        val signature = signDocument(canonicalDocument, keyId)
        logger.debug("Generated signature for VC-LD credential: prefix={}", signature.take(20))
        
        // Get verification method ID
        // request.issuerKeyId?.value might already be a full verification method ID (did:key:...#key-1)
        // or just a fragment (key-1). getVerificationMethod handles both cases.
        val verificationMethodIdString = request.issuerKeyId?.value
            ?: throw IllegalArgumentException("issuerKeyId is required")
        
        // Use the verification method ID directly if it's already a full ID, otherwise construct it
        val verificationMethod = if (verificationMethodIdString.contains("#") && verificationMethodIdString.startsWith("did:")) {
            // Already a full verification method ID - use it directly
            verificationMethodIdString
        } else {
            // Just a fragment - construct full ID
            getVerificationMethod(issuerIri.value, verificationMethodIdString)
                ?: throw IllegalArgumentException("Could not determine verification method")
        }
        
        // Create Linked Data Proof
        val proofPurpose = request.proofOptions?.purpose?.standardValue ?: CredentialConstants.ProofPurposes.ASSERTION_METHOD
        val proofCreated = Clock.System.now()
        val additionalProperties = buildMap<String, JsonElement> {
            request.proofOptions?.challenge?.let { put("challenge", JsonPrimitive(it)) }
            request.proofOptions?.domain?.let { put("domain", JsonPrimitive(it)) }
        }
        
        val linkedDataProof = CredentialProof.LinkedDataProof(
            type = request.proofOptions?.additionalOptions?.get("proofType")?.toString()?.removeSurrounding("\"") ?: CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020,
            created = proofCreated,
            verificationMethod = verificationMethod,
            proofPurpose = proofPurpose,
            proofValue = signature,
            additionalProperties = additionalProperties
        )
        
        // Build VerifiableCredential
        return VerifiableCredential(
            context = listOf(
                CredentialConstants.VcContexts.VC_1_1,
                CredentialConstants.SecuritySuites.ED25519_2020_V1
            ),
            id = request.id ?: CredentialId("urn:uuid:${UUID.randomUUID()}"),
            type = request.type,
            issuer = request.issuer,
            issuanceDate = request.issuedAt,
            validFrom = request.validFrom,
            credentialSubject = request.credentialSubject,
            expirationDate = request.validUntil,
            credentialStatus = request.credentialStatus,
            credentialSchema = request.credentialSchema,
            evidence = request.evidence,
            proof = linkedDataProof
        )
    }
    
    override suspend fun verify(
        credential: VerifiableCredential,
        options: VerificationOptions
    ): VerificationResult {
        val proof = credential.proof as? CredentialProof.LinkedDataProof
            ?: return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "VC-LD credential must have LinkedDataProof",
                errors = listOf("Expected LinkedDataProof but got ${credential.proof?.javaClass?.simpleName}"),
                warnings = emptyList()
            )
        
        try {
            // Get issuer IRI
            val issuerIri = when (val issuer = credential.issuer) {
                is Issuer.IriIssuer -> issuer.id
                is Issuer.ObjectIssuer -> issuer.id
            }
            
            // Get verifier using DID resolution
            val verificationMethod = getVerificationMethodFromDid(issuerIri, proof.verificationMethod)
                ?: return VerificationResult.Invalid.InvalidIssuer(
                    credential = credential,
                    issuerIri = issuerIri,
                    reason = "Could not resolve issuer IRI or get verification key",
                    errors = listOf("Failed to resolve issuer: ${issuerIri.value}"),
                    warnings = emptyList()
                )
            
            // Build VC document without proof for canonicalization
            val vcDocument = buildVcDocumentWithoutProof(credential)
            val canonical = canonicalizeDocument(vcDocument)
            logger.debug("Verifying VC-LD credential: canonicalLength={}, verificationMethod={}, publicKeyJwkPresent={}", 
                canonical.length, proof.verificationMethod, verificationMethod.publicKeyJwk != null)
            
            // Verify signature
            val isValid = verifySignature(canonical, proof.proofValue, verificationMethod, proof.type)
            logger.debug("VC-LD signature verification result: isValid={}", isValid)
            
            if (!isValid) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "Linked Data Proof signature verification failed",
                    errors = listOf("Invalid signature on VC-LD document"),
                    warnings = emptyList()
                )
            }
            
            // Get subject IRI
            val subjectIri = credential.credentialSubject.id
            
            return VerificationResult.Valid(
                credential = credential,
                issuerIri = issuerIri,
                subjectIri = subjectIri,
                issuedAt = credential.issuanceDate,
                expiresAt = credential.expirationDate,
                warnings = emptyList(),
                formatMetadata = buildJsonObject {
                    put("proofType", proof.type)
                    put("verificationMethod", proof.verificationMethod)
                    put("proofPurpose", proof.proofPurpose)
                }
            )
            
        } catch (e: Exception) {
            logger.error("Failed to verify VC-LD proof: credentialId={}, error={}", credential.id?.value ?: "unknown", e.message, e)
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Failed to verify VC-LD proof: ${e.message}",
                errors = listOf("Verification error: ${e.message}"),
                warnings = emptyList()
            )
        }
    }
    
    override suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation {
        require(capabilities.presentation) {
            "VC-LD engine does not support presentations"
        }
        
        if (credentials.isEmpty()) {
            throw IllegalArgumentException("At least one credential is required for presentation")
        }
        
        // Handle selective disclosure if disclosedClaims is specified
        val credentialsToInclude = if (request.disclosedClaims != null && request.disclosedClaims.isNotEmpty()) {
            // Apply selective disclosure - filter claims based on disclosedClaims
            credentials.map { credential ->
                createSelectiveDisclosureCredential(credential, request.disclosedClaims)
            }
        } else {
            // Include all credentials as-is
            credentials
        }
        
        // Get holder from first credential's subject
        val holder = credentialsToInclude.first().credentialSubject.id
        
        // Create VerifiablePresentation
        // Note: VC-LD presentations may need a proof on the presentation itself
        return VerifiablePresentation(
            type = listOf(CredentialType.Custom("VerifiablePresentation")),
            holder = holder,
            verifiableCredential = credentialsToInclude,
            proof = null, // Presentation-level proof would be added here if required by the protocol
            challenge = request.proofOptions?.challenge,
            domain = request.proofOptions?.domain
        )
    }
    
    /**
     * Create a credential with only disclosed claims for selective disclosure.
     * 
     * This is a basic implementation that filters claims. For true zero-knowledge
     * selective disclosure, BBS+ proofs would be required.
     */
    private fun createSelectiveDisclosureCredential(
        credential: VerifiableCredential,
        disclosedClaims: Set<String>
    ): VerifiableCredential {
        // If all claims should be disclosed, return as-is
        if (disclosedClaims.isEmpty()) {
            return credential
        }
        
        // Filter credentialSubject claims based on disclosedClaims
        val subject = credential.credentialSubject
        val filteredClaims = if (subject.claims.isNotEmpty()) {
            // Filter claims - support both "claimName" and "credentialSubject.claimName" formats
            subject.claims.filterKeys { claimName ->
                disclosedClaims.contains(claimName) || 
                disclosedClaims.contains("credentialSubject.$claimName")
            }
        } else {
            subject.claims
        }
        
        // Create new credential with filtered claims
        // Note: This creates a derived credential without proof - for production use,
        // this would need to be re-signed or use zero-knowledge proofs
        return credential.copy(
            credentialSubject = subject.copy(claims = filteredClaims),
            proof = null // Derived credential needs new proof for selective disclosure
        )
    }
    
    override suspend fun initialize(config: ProofEngineConfig) {
        // Initialize JSON-LD processor, signature suites, etc.
    }
    
    override suspend fun close() {
        // Cleanup resources
    }
    
    override fun isReady(): Boolean = true
    
    // Helper methods
    
    private fun buildVcDocument(request: IssuanceRequest): JsonObject {
        val issuerIri = when (val issuer = request.issuer) {
            is Issuer.IriIssuer -> issuer.id.value
            is Issuer.ObjectIssuer -> issuer.id.value
        }
        
        return buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
                add("https://w3id.org/security/suites/ed25519-2020/v1")
            })
            request.id?.let { put("id", it.value) }
            put("type", buildJsonArray {
                request.type.forEach { type ->
                    add(type.value)
                }
            })
            put("issuer", issuerIri)
            put("issuanceDate", request.issuedAt.toString())
            request.validUntil?.let { put("expirationDate", it.toString()) }
            put("credentialSubject", buildJsonObject {
                put("id", request.credentialSubject.id.value)
                request.credentialSubject.claims.entries.forEach { (key, value) ->
                    put(key, value)
                }
            })
            request.credentialStatus?.let { status ->
                put("credentialStatus", buildJsonObject {
                    put("id", status.id.value)
                    put("type", status.type)
                    put("statusPurpose", status.statusPurpose.name.lowercase())
                    status.statusListIndex?.let { put("statusListIndex", it) }
                    status.statusListCredential?.let { put("statusListCredential", it.value) }
                    status.formatData?.let { formatData ->
                        put("formatData", buildJsonObject {
                            formatData.forEach { (key, value) ->
                                put(key, value)
                            }
                        })
                    }
                })
            }
            request.credentialSchema?.let { schema ->
                put("credentialSchema", buildJsonObject {
                    put("id", schema.id.value)
                    put("type", schema.type)
                })
            }
            request.evidence?.let { evidenceList ->
                put("evidence", buildJsonArray {
                    evidenceList.forEach { evidence ->
                        add(buildJsonObject {
                            evidence.id?.let { put("id", it.value) }
                            put("type", buildJsonArray {
                                evidence.type.forEach { type ->
                                    add(type)
                                }
                            })
                            evidence.evidenceDocument?.let { put("evidenceDocument", it) }
                            evidence.verifier?.let { put("verifier", it.value) }
                            evidence.evidenceDate?.let { put("evidenceDate", it) }
                        })
                    }
                })
            }
        }
    }
    
    private fun buildVcDocumentWithoutProof(credential: VerifiableCredential): JsonObject {
        val issuerIri = when (val issuer = credential.issuer) {
            is Issuer.IriIssuer -> issuer.id.value
            is Issuer.ObjectIssuer -> issuer.id.value
        }
        
        return buildJsonObject {
            put("@context", buildJsonArray {
                credential.context.forEach { ctx ->
                    add(ctx)
                }
            })
            credential.id?.let { put("id", it.value) }
            put("type", buildJsonArray {
                credential.type.forEach { type ->
                    add(type.value)
                }
            })
            put("issuer", issuerIri)
            put("issuanceDate", credential.issuanceDate.toString())  // ISO 8601 format
            credential.validFrom?.let { put("validFrom", it.toString()) }  // ISO 8601 format
            credential.expirationDate?.let { put("expirationDate", it.toString()) }  // ISO 8601 format
            put("credentialSubject", buildJsonObject {
                put("id", credential.credentialSubject.id.value)
                credential.credentialSubject.claims.entries.forEach { (key, value) ->
                    put(key, value)
                }
            })
            credential.credentialStatus?.let { status ->
                put("credentialStatus", buildJsonObject {
                    put("id", status.id.value)
                    put("type", status.type)
                    put("statusPurpose", status.statusPurpose.name.lowercase())
                    status.statusListIndex?.let { put("statusListIndex", it) }
                    status.statusListCredential?.let { put("statusListCredential", it.value) }
                    status.formatData?.let { formatData ->
                        put("formatData", buildJsonObject {
                            formatData.forEach { (key, value) ->
                                put(key, value)
                            }
                        })
                    }
                })
            }
            credential.credentialSchema?.let { schema ->
                put("credentialSchema", buildJsonObject {
                    put("id", schema.id.value)
                    put("type", schema.type)
                })
            }
            credential.evidence?.let { evidenceList ->
                put("evidence", buildJsonArray {
                    evidenceList.forEach { evidence ->
                        add(buildJsonObject {
                            evidence.id?.let { put("id", it.value) }
                            put("type", buildJsonArray {
                                evidence.type.forEach { type ->
                                    add(type)
                                }
                            })
                            evidence.evidenceDocument?.let { put("evidenceDocument", it) }
                            evidence.verifier?.let { put("verifier", it.value) }
                            evidence.evidenceDate?.let { put("evidenceDate", it) }
                        })
                    }
                })
            }
            // Explicitly exclude proof for canonicalization
        }
    }
    
    private fun canonicalizeDocument(document: JsonObject): String {
        // Use shared JSON-LD canonicalization utility
        val json = Json { serializersModule = org.trustweave.core.serialization.SerializationModule.default }
        return JsonLdUtils.canonicalizeDocument(document, json)
    }
    
    private suspend fun signDocument(canonical: String, keyId: String): String {
        logger.debug("Signing document: keyId={}, canonicalLength={}", keyId, canonical.length)
        // Get signer function if available (preferred), otherwise get KMS and create signer
        val signer = getSignerFunction() ?: run {
            val kms = getKms() ?: throw IllegalStateException(
                "KMS not configured. Provide KMS via ProofEngineConfig.properties[\"kms\"] or signer via properties[\"signer\"]"
            )
            createKmsSigner(kms)
        }
        
        // Sign canonical document
        val canonicalBytes = canonical.toByteArray(Charsets.UTF_8)
        val signatureBytes = signer(canonicalBytes, keyId)
        logger.debug("Signed document: keyId={}, signatureLength={}", keyId, signatureBytes.size)
        
        // Return base64url-encoded signature
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
    }
    
    /**
     * Verify Linked Data Proof signature.
     * 
     * @param canonical The canonicalized document (without proof)
     * @param proofValue The base64url-encoded signature
     * @param verificationMethod The verification method from DID document
     * @param proofType The proof type (e.g., "Ed25519Signature2020")
     * @return True if signature is valid, false otherwise
     */
    private fun verifySignature(
        canonical: String,
        proofValue: String,
        verificationMethod: VerificationMethod,
        proofType: String
    ): Boolean {
        logger.debug("Verifying signature: proofType={}, proofValueLength={}", proofType, proofValue.length)
        if (proofValue.isBlank()) {
            logger.warn("Proof value is blank")
            return false
        }
        
        // Only support Ed25519Signature2020 for now
        if (proofType != CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020) {
            logger.warn("Unsupported proof type: {}", proofType)
            return false
        }
        
        try {
            // Extract public key from verification method
            // Use the utility function which tries multiple methods
            val publicKey = ProofEngineUtils.extractPublicKey(verificationMethod)
            
            if (publicKey == null) {
                logger.warn("Failed to extract public key from verification method: verificationMethodId={}", verificationMethod.id.value)
                return false
            }
            logger.debug("Extracted public key: algorithm={}, encodedLength={}", publicKey.algorithm, publicKey.encoded.size)
            
            // Decode signature (base64url)
            val signatureBytes = try {
                Base64.getUrlDecoder().decode(proofValue)
            } catch (e: Exception) {
                logger.error("Failed to decode signature: error={}", e.message, e)
                return false
            }
            
            // Get canonical document bytes
            val documentBytes = canonical.toByteArray(Charsets.UTF_8)
            logger.debug("Verifying signature: signatureLength={}, documentLength={}", signatureBytes.size, documentBytes.size)
            
            // Verify Ed25519 signature
            val result = verifyEd25519Signature(documentBytes, signatureBytes, publicKey)
            logger.debug("Ed25519 signature verification result: isValid={}", result)
            return result
        } catch (e: Exception) {
            logger.error("Exception during signature verification: error={}", e.message, e)
            return false
        }
    }
    
    /**
     * Verify Ed25519 signature.
     */
    private fun verifyEd25519Signature(
        documentBytes: ByteArray,
        signatureBytes: ByteArray,
        publicKey: java.security.PublicKey
    ): Boolean {
        return try {
            val signature = java.security.Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(documentBytes)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getKms(): KeyManagementService? {
        return config.properties["kms"] as? KeyManagementService
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun getSignerFunction(): (suspend (ByteArray, String) -> ByteArray)? {
        return config.properties["signer"] as? (suspend (ByteArray, String) -> ByteArray)
    }
    
    private fun createKmsSigner(kms: KeyManagementService): suspend (ByteArray, String) -> ByteArray {
        return { data: ByteArray, kid: String ->
            val signResult = kms.sign(KeyId(kid), data)
            when (signResult) {
                is SignResult.Success -> signResult.signature
                is SignResult.Failure.KeyNotFound -> throw IllegalStateException(
                    "Failed to sign VC-LD document: Key not found: ${signResult.keyId.value}"
                )
                is SignResult.Failure.UnsupportedAlgorithm -> throw IllegalStateException(
                    "Failed to sign VC-LD document: Unsupported algorithm: ${signResult.reason ?: "Algorithm ${signResult.requestedAlgorithm?.name} is not compatible with key algorithm ${signResult.keyAlgorithm.name}"}"
                )
                is SignResult.Failure.Error -> throw IllegalStateException(
                    "Failed to sign VC-LD document: ${signResult.reason}", signResult.cause
                )
            }
        }
    }
    
    /**
     * Get verification method from DID document.
     * 
     * Resolves the issuer DID and extracts the verification method.
     * 
     * @param issuerIri The issuer IRI (must be a DID)
     * @param verificationMethodId The verification method ID (can be full or fragment)
     * @return VerificationMethod instance, or null if resolution fails
     */
    private suspend fun getVerificationMethodFromDid(
        issuerIri: Iri,
        verificationMethodId: String
    ): VerificationMethod? {
        if (!issuerIri.isDid) {
            return null
        }
        
        val didResolver = config.getDidResolver() ?: return null
        
        return ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = verificationMethodId,
            didResolver = didResolver
        )
    }
    
    private fun getVerificationMethod(issuerIri: String, keyId: String?): String? {
        // Construct verification method ID from issuer IRI and key ID
        // This is used during issuance to construct the verification method reference
        return keyId?.let { "$issuerIri#$it" } ?: "$issuerIri#key-1"
    }
}

