package com.trustweave.credential.proof.internal.engines

import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.spi.proof.ProofEngine
import com.trustweave.credential.spi.proof.ProofEngineCapabilities
import com.trustweave.credential.spi.proof.ProofEngineConfig
// ProofEngineUtils is in the same package, no import needed
import com.trustweave.credential.requests.IssuanceRequest
import com.trustweave.credential.requests.PresentationRequest
import com.trustweave.credential.requests.VerificationOptions
import com.trustweave.credential.results.VerificationResult
import com.trustweave.core.identifiers.Iri
import com.trustweave.core.identifiers.KeyId
import com.trustweave.did.identifiers.Did
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.results.SignResult
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.security.SignatureException
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.*
import java.time.Instant as JavaInstant

/**
 * SD-JWT-VC (Selective Disclosure JWT Verifiable Credential) proof engine.
 * 
 * Supports IETF SD-JWT-VC draft specification for selective disclosure credentials.
 * 
 * **Note**: This is a basic implementation. Full SD-JWT-VC requires:
 * - Disclosure generation and hashing
 * - Selective disclosure support
 * - Key Binding JWT (KB-JWT) support
 */
internal class SdJwtProofEngine(
    private val config: ProofEngineConfig = ProofEngineConfig()
) : ProofEngine {
    
    override val format = ProofSuiteId.SD_JWT_VC
    override val formatName = "SD-JWT-VC"
    override val formatVersion = "draft-ietf-oauth-sd-jwt-vc-01"
    
    override val capabilities = ProofEngineCapabilities(
        selectiveDisclosure = true,    // Core feature of SD-JWT
        zeroKnowledge = false,
        revocation = true,
        presentation = true,
        predicates = false             // Not supported in SD-JWT
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
        
        // Build JWT payload with credential claims
        val claimsBuilder = JWTClaimsSet.Builder()
            .issuer(issuerIri.value)
            .subject(request.credentialSubject.id.value)
            .issueTime(Date.from(JavaInstant.ofEpochSecond(request.issuedAt.epochSeconds)))
            .claim("vc", buildVcClaim(request))
        
        request.validUntil?.let { claimsBuilder.expirationTime(Date.from(JavaInstant.ofEpochSecond(it.epochSeconds))) }
        
        val claimsSet = claimsBuilder.build()
        
        // Create signed JWT
        val header = JWSHeader.Builder(JWSAlgorithm.EdDSA)
            .keyID(keyId)
            .build()
        
        val signedJWT = SignedJWT(header, claimsSet)
        
        // Sign using KMS
        val signer = getSigner(keyId)
            ?: throw IllegalArgumentException("No signer available for issuer ${issuerIri.value}. Configure KMS via ProofEngineConfig.")
        
        signedJWT.sign(signer)
        
        // Build VerifiableCredential with SD-JWT-VC proof
        // Note: Full SD-JWT-VC implementation would include disclosures array for selective disclosure
        // For now, we serialize the JWT without disclosures (basic SD-JWT-VC)
        val sdJwtVcProof = CredentialProof.SdJwtVcProof(
            sdJwtVc = signedJWT.serialize(),
            disclosures = null // Disclosures would be added here for selective disclosure support
        )
        
        return VerifiableCredential(
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
            proof = sdJwtVcProof
        )
    }
    
    override suspend fun verify(
        credential: VerifiableCredential,
        options: VerificationOptions
    ): VerificationResult {
        val proof = credential.proof as? CredentialProof.SdJwtVcProof
            ?: return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "SD-JWT-VC credential must have SdJwtVcProof",
                errors = listOf("Expected SdJwtVcProof but got ${credential.proof?.javaClass?.simpleName}"),
                warnings = emptyList()
            )
        
        try {
            // Parse JWT
            val signedJWT = SignedJWT.parse(proof.sdJwtVc)
            
            // Get issuer IRI
            val issuerIri = when (val issuer = credential.issuer) {
                is Issuer.IriIssuer -> issuer.id
                is Issuer.ObjectIssuer -> issuer.id
            }
            
            // Resolve issuer and get verification key
            val verifier = getVerifier(issuerIri, proof.sdJwtVc)
                ?: return VerificationResult.Invalid.InvalidIssuer(
                    credential = credential,
                    issuerIri = issuerIri,
                    reason = "Could not resolve issuer IRI or get verification key",
                    errors = listOf("Failed to resolve issuer: ${issuerIri.value}"),
                    warnings = emptyList()
                )
            
            // Verify signature
            if (!signedJWT.verify(verifier)) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "JWT signature verification failed",
                    errors = listOf("Invalid signature on SD-JWT-VC"),
                    warnings = emptyList()
                )
            }
            
            // Extract subject IRI from JWT
            val claimsSet = signedJWT.jwtClaimsSet
            val subjectIri = claimsSet.subject?.let { Iri(it) } ?: credential.credentialSubject.id
            
            // Return valid result
            return VerificationResult.Valid(
                credential = credential,
                issuerIri = issuerIri,
                subjectIri = subjectIri,
                issuedAt = claimsSet.issueTime?.toInstant()?.let { Instant.fromEpochSeconds(it.epochSecond, it.nano) } ?: credential.issuanceDate,
                expiresAt = claimsSet.expirationTime?.toInstant()?.let { Instant.fromEpochSeconds(it.epochSecond, it.nano) } ?: credential.expirationDate,
                warnings = emptyList(),
                formatMetadata = buildJsonObject {
                    put("jwt_id", claimsSet.getJWTID() ?: "")
                }
            )
            
        } catch (e: Exception) {
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Failed to parse or verify JWT: ${e.message}",
                errors = listOf("JWT parsing error: ${e.message}"),
                warnings = emptyList()
            )
        }
    }
    
    override suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation {
        require(capabilities.presentation) {
            "SD-JWT-VC engine does not support presentations"
        }
        
        if (credentials.isEmpty()) {
            throw IllegalArgumentException("At least one credential is required for presentation")
        }
        
        // For SD-JWT-VC, selective disclosure is handled via disclosures array
        // This implementation includes all credentials in the presentation
        // Full selective disclosure would filter credentials based on requested disclosures
        
        // Get holder from first credential's subject
        val holder = credentials.first().credentialSubject.id
        
        // Create VerifiablePresentation
        return VerifiablePresentation(
            type = listOf(CredentialType.Custom("VerifiablePresentation")),
            holder = holder,
            verifiableCredential = credentials,
            proof = null, // SD-JWT-VC uses KB-JWT for presentation proof (not implemented yet)
            challenge = request.proofOptions?.challenge,
            domain = request.proofOptions?.domain
        )
    }
    
    override suspend fun initialize(config: ProofEngineConfig) {
        // Initialize SD-JWT processor, configure hash algorithm, etc.
    }
    
    override suspend fun close() {
        // Cleanup resources
    }
    
    override fun isReady(): Boolean = true
    
    // Helper methods
    
    private fun buildVcClaim(request: IssuanceRequest): Map<String, Any> {
        return buildMap {
            put("@context", listOf("https://www.w3.org/2018/credentials/v1"))
            put("type", request.type.map { it.value })
            put("credentialSubject", buildMap {
                put("id", request.credentialSubject.id.value)
                request.credentialSubject.claims.forEach { (key, value) ->
                    // Convert JsonElement to appropriate type for JWT
                    val convertedValue: Any = when {
                        value is JsonPrimitive -> {
                            when {
                                value.isString -> value.content
                                value.booleanOrNull != null -> value.boolean
                                value.longOrNull != null -> value.long
                                value.doubleOrNull != null -> value.double
                                else -> value.content
                            }
                        }
                        value is JsonArray -> {
                            value.map { element ->
                                when (element) {
                                    is JsonPrimitive -> element.content
                                    else -> element.toString()
                                }
                            }
                        }
                        value is JsonObject -> {
                            jsonObjectToMap(value)
                        }
                        else -> {
                            value.toString()
                        }
                    }
                    put(key, convertedValue)
                }
            })
            request.credentialStatus?.let { status ->
                put("credentialStatus", buildMap {
                    put("id", status.id.value)
                    put("type", status.type)
                    put("statusPurpose", status.statusPurpose.name.lowercase())
                    status.statusListIndex?.let { put("statusListIndex", it) }
                    status.statusListCredential?.let { put("statusListCredential", it.value) }
                })
            }
            request.credentialSchema?.let { schema ->
                put("credentialSchema", buildMap {
                    put("id", schema.id.value)
                    put("type", schema.type)
                })
            }
            request.evidence?.let { evidenceList ->
                put("evidence", evidenceList.map { evidence ->
                    buildMap {
                        evidence.id?.let { put("id", it.value) }
                        put("type", evidence.type)
                        evidence.evidenceDocument?.let { put("evidenceDocument", it) }
                        evidence.verifier?.let { put("verifier", it.value) }
                        evidence.evidenceDate?.let { put("evidenceDate", it) }
                    }
                })
            }
        }
    }
    
    private fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any> {
        return jsonObject.entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> {
                    when {
                        value.isString -> value.content
                        value.booleanOrNull != null -> value.boolean
                        value.longOrNull != null -> value.long
                        value.doubleOrNull != null -> value.double
                        else -> value.content
                    }
                }
                is JsonArray -> value.map { element ->
                    when (element) {
                        is JsonPrimitive -> element.content
                        is JsonObject -> jsonObjectToMap(element)
                        else -> element.toString()
                    }
                }
                is JsonObject -> jsonObjectToMap(value)
                else -> value.toString()
            }
        }
    }
    
    private fun getSigner(keyId: String): JWSSigner? {
        // Get signer function if available (preferred), otherwise get KMS and create signer
        val signerFunction = getSignerFunction() ?: run {
            val kms = getKms() ?: return null
            createKmsSigner(kms)
        }
        
        // Create JWSSigner adapter that uses signer function
        return KmsJwsSigner(keyId, signerFunction)
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
                    "Failed to sign SD-JWT: Key not found: ${signResult.keyId.value}"
                )
                is SignResult.Failure.UnsupportedAlgorithm -> throw IllegalStateException(
                    "Failed to sign SD-JWT: Unsupported algorithm: ${signResult.reason ?: "Algorithm ${signResult.requestedAlgorithm?.name} is not compatible with key algorithm ${signResult.keyAlgorithm.name}"}"
                )
                is SignResult.Failure.Error -> throw IllegalStateException(
                    "Failed to sign SD-JWT: ${signResult.reason}", signResult.cause
                )
            }
        }
    }
    
    /**
     * JWSSigner adapter that uses KMS for signing.
     * 
     * Note: JWSSigner interface requires synchronous sign() method, but KMS operations
     * are async. We use runBlocking here as a bridge. This is acceptable because:
     * 1. Signing is typically fast (< 100ms)
     * 2. The alternative would require changing the JWT library interface
     * 3. This only affects issuance, not verification (which is fully async)
     */
    private class KmsJwsSigner(
        private val keyId: String,
        private val signer: suspend (ByteArray, String) -> ByteArray
    ) : JWSSigner {
        override fun sign(header: JWSHeader, signingInput: ByteArray): com.nimbusds.jose.util.Base64URL {
            return try {
                // Note: runBlocking is necessary here because JWSSigner interface requires
                // synchronous sign() method, but KMS operations are async
                val signature = runBlocking {
                    signer(signingInput, keyId)
                }
                com.nimbusds.jose.util.Base64URL.encode(signature)
            } catch (e: Exception) {
                throw JOSEException("Failed to sign JWT with KMS: ${e.message}", e)
            }
        }
        
        override fun supportedJWSAlgorithms(): MutableSet<JWSAlgorithm> {
            return mutableSetOf(JWSAlgorithm.EdDSA)
        }
        
        override fun getJCAContext(): com.nimbusds.jose.jca.JCAContext {
            return com.nimbusds.jose.jca.JCAContext()
        }
    }
    
    /**
     * Get verifier for JWT signature verification.
     * 
     * Resolves the issuer DID and extracts the verification method to create an Ed25519 verifier.
     * 
     * @param issuerIri The issuer IRI (must be a DID)
     * @param jwtString The JWT string (used to extract key ID from header if needed)
     * @return Ed25519Verifier instance, or null if resolution fails
     */
    private suspend fun getVerifier(issuerIri: Iri, jwtString: String): Ed25519Verifier? {
        if (!issuerIri.isDid) {
            return null
        }
        
        val didResolver = config.getDidResolver() ?: return null
        
        // Try to extract key ID from JWT header
        val keyIdFromJwt = try {
            val signedJWT = SignedJWT.parse(jwtString)
            signedJWT.header.keyID
        } catch (e: Exception) {
            null
        }
        
        // Resolve verification method
        val verificationMethod = ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = keyIdFromJwt,
            didResolver = didResolver
        ) ?: return null
        
        // Create Ed25519 verifier from verification method
        return ProofEngineUtils.createEd25519Verifier(verificationMethod)
    }
    
}

