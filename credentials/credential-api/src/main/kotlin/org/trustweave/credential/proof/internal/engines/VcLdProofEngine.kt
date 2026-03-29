package org.trustweave.credential.proof.internal.engines

import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.internal.infrastructure.DefaultJsonLdCanonicalizationAdapter
import org.trustweave.credential.internal.infrastructure.DefaultEd25519SignatureVerificationAdapter
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.spi.proof.JsonLdCanonicalizationPort
import org.trustweave.credential.spi.proof.SignatureVerificationPort
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import org.trustweave.did.model.VerificationMethod
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock
import java.util.*
import java.util.Base64
import org.slf4j.LoggerFactory

/**
 * VC-LD (Verifiable Credentials Linked Data) proof engine.
 *
 * Supports W3C Verifiable Credentials 2.0 with Linked Data Proofs.
 * Uses JSON-LD canonicalization and various signature suites.
 *
 * Infrastructure concerns (JSON-LD library, java.security) are accessed through
 * [JsonLdCanonicalizationPort] and [SignatureVerificationPort], keeping the engine
 * independent of specific cryptographic libraries.
 *
 * Document building, DID verification method resolution, and selective disclosure
 * are delegated to [JsonLdDocumentBuilder], [DidVerificationMethodResolver], and
 * [SelectiveDisclosureFilter] respectively.
 */
internal class VcLdProofEngine(
    private val config: ProofEngineConfig = ProofEngineConfig(),
    private val canonicalizationPort: JsonLdCanonicalizationPort = DefaultJsonLdCanonicalizationAdapter(),
    private val signatureVerificationPort: SignatureVerificationPort = DefaultEd25519SignatureVerificationAdapter()
) : ProofEngine {

    private val vmResolver: DidVerificationMethodResolver by lazy {
        DidVerificationMethodResolver(config.getDidResolver())
    }
    
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
        
        // Generate credential ID if not provided (must be consistent between document building and credential object)
        val credentialId = request.id ?: CredentialId("urn:uuid:${UUID.randomUUID()}")
        
        // Build VC document for canonicalization (must use same ID that will be in the credential object)
        val vcDocument = JsonLdDocumentBuilder.build(request, credentialId.value)
        
        // Canonicalize document (without proof)
        val canonicalDocument = canonicalizationPort.canonicalize(vcDocument)
        logger.debug("Canonicalized document for signing: length={}", canonicalDocument.length)
        
        // Generate signature using KMS
        val signature = signDocument(canonicalDocument, keyId)
        logger.debug("Generated signature for VC-LD credential: prefix={}", signature.take(20))
        
        // Get verification method ID
        // request.issuerKeyId?.value might already be a full verification method ID (did:key:...#key-1)
        // or just a fragment (key-1). getVerificationMethod handles both cases.
        val verificationMethodIdString = request.issuerKeyId?.value
            ?: throw IllegalArgumentException("issuerKeyId is required")
        
        val verificationMethod = if (verificationMethodIdString.contains("#") && verificationMethodIdString.startsWith("did:")) {
            verificationMethodIdString
        } else {
            vmResolver.buildVerificationMethodId(issuerIri.value, verificationMethodIdString)
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
            id = credentialId,
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
        // Validate proof type
        val proof = validateProofType(credential) ?: return createInvalidProofResult(
            credential,
            "VC-LD credential must have LinkedDataProof",
            "Expected LinkedDataProof but got ${credential.proof?.javaClass?.simpleName}"
        )
        
        return try {
            // Extract issuer IRI
            val issuerIri = extractIssuerIri(credential)
            
            // Resolve verification method
            val verificationMethod = vmResolver.resolve(issuerIri, proof.verificationMethod)
                ?: return createInvalidIssuerResult(credential, issuerIri)

            // Canonicalize document
            val canonical = canonicalizationPort.canonicalize(
                JsonLdDocumentBuilder.buildWithoutProof(credential)
            )
            logger.debug("Verifying VC-LD credential: canonicalLength={}, verificationMethod={}, publicKeyJwkPresent={}", 
                canonical.length, proof.verificationMethod, verificationMethod.publicKeyJwk != null)
            
            // Verify signature
            if (!verifyCredentialSignature(canonical, proof, verificationMethod)) {
                return createInvalidProofResult(
                    credential,
                    "Linked Data Proof signature verification failed",
                    "Invalid signature on VC-LD document"
                )
            }
            
            // Create valid result
            createValidVerificationResult(credential, issuerIri, proof)
            
        } catch (e: Exception) {
            logger.error("Failed to verify VC-LD proof: credentialId={}, error={}", credential.id?.value ?: "unknown", e.message, e)
            createInvalidProofResult(
                credential,
                "Failed to verify VC-LD proof: ${e.message}",
                "Verification error: ${e.message}"
            )
        }
    }
    
    private fun validateProofType(credential: VerifiableCredential): CredentialProof.LinkedDataProof? =
        credential.proof as? CredentialProof.LinkedDataProof

    private fun extractIssuerIri(credential: VerifiableCredential): Iri =
        when (val issuer = credential.issuer) {
            is Issuer.IriIssuer -> issuer.id
            is Issuer.ObjectIssuer -> issuer.id
        }

    private fun verifyCredentialSignature(
        canonical: String,
        proof: CredentialProof.LinkedDataProof,
        verificationMethod: VerificationMethod
    ): Boolean {
        val isValid = verifySignature(canonical, proof.proofValue, verificationMethod, proof.type)
        logger.debug("VC-LD signature verification result: isValid={}", isValid)
        return isValid
    }
    
    /**
     * Creates a valid verification result.
     * 
     * @param credential The verified credential
     * @param issuerIri The issuer IRI
     * @param proof The LinkedDataProof
     * @return VerificationResult.Valid
     */
    private fun createValidVerificationResult(
        credential: VerifiableCredential,
        issuerIri: Iri,
        proof: CredentialProof.LinkedDataProof
    ): VerificationResult.Valid {
        return VerificationResult.Valid(
            credential = credential,
            issuerIri = issuerIri,
            subjectIri = credential.credentialSubject.id,
            issuedAt = credential.issuanceDate,
            expiresAt = credential.expirationDate,
            warnings = emptyList(),
            formatMetadata = buildJsonObject {
                put("proofType", proof.type)
                put("verificationMethod", proof.verificationMethod)
                put("proofPurpose", proof.proofPurpose)
            }
        )
    }
    
    /**
     * Creates an invalid proof result.
     * 
     * @param credential The credential
     * @param reason The reason for failure
     * @param errorMessage The error message
     * @return VerificationResult.Invalid.InvalidProof
     */
    private fun createInvalidProofResult(
        credential: VerifiableCredential,
        reason: String,
        errorMessage: String
    ): VerificationResult.Invalid.InvalidProof {
        return VerificationResult.Invalid.InvalidProof(
            credential = credential,
            reason = reason,
            errors = listOf(errorMessage),
            warnings = emptyList()
        )
    }
    
    /**
     * Creates an invalid issuer result.
     * 
     * @param credential The credential
     * @param issuerIri The issuer IRI that failed to resolve
     * @return VerificationResult.Invalid.InvalidIssuer
     */
    private fun createInvalidIssuerResult(
        credential: VerifiableCredential,
        issuerIri: Iri
    ): VerificationResult.Invalid.InvalidIssuer {
        return VerificationResult.Invalid.InvalidIssuer(
            credential = credential,
            issuerIri = issuerIri,
            reason = "Could not resolve issuer IRI or get verification key",
            errors = listOf("Failed to resolve issuer: ${issuerIri.value}"),
            warnings = emptyList()
        )
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
            credentials.map { credential ->
                SelectiveDisclosureFilter.filter(credential, request.disclosedClaims)
            }
        } else {
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
    
    override suspend fun initialize(config: ProofEngineConfig) {
        // Initialize JSON-LD processor, signature suites, etc.
    }
    
    override suspend fun close() {
        // Cleanup resources
    }
    
    override fun isReady(): Boolean = true
    
    
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
     * Verify Linked Data Proof signature via [SignatureVerificationPort].
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

        val signatureBytes = try {
            Base64.getUrlDecoder().decode(proofValue)
        } catch (e: Exception) {
            logger.error("Failed to decode signature: error={}", e.message, e)
            return false
        }

        val documentBytes = canonical.toByteArray(Charsets.UTF_8)
        logger.debug("Verifying signature: signatureLength={}, documentLength={}", signatureBytes.size, documentBytes.size)

        return signatureVerificationPort.verify(documentBytes, signatureBytes, verificationMethod, proofType)
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
    
}

