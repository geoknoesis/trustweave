package org.trustweave.credential.proof.internal.engines

import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.internal.infrastructure.DefaultJsonLdCanonicalizationAdapter
import org.trustweave.credential.internal.infrastructure.DefaultEd25519SignatureVerificationAdapter
import org.trustweave.credential.internal.infrastructure.DefaultJsonWebSignature2020Adapter
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
import org.trustweave.credential.spi.status.CredentialStatusChecker
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import org.trustweave.did.model.VerificationMethod
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.util.Base64URL
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock
import java.util.*
import java.util.Base64
import org.slf4j.LoggerFactory

/**
 * VC-LD (Verifiable Credentials Linked Data) proof engine.
 *
 * Supports the W3C Verifiable Credentials Data Model 1.1 and 2.0 with Linked Data Proofs.
 * Issuance targets VC 1.1 by default; a request targets VC 2.0 by declaring the
 * `https://www.w3.org/ns/credentials/v2` base context via the
 * [JsonLdDocumentBuilder.CONTEXTS_OPTION] proof option. VC 2.0 credentials are emitted with
 * `validFrom`/`validUntil` instead of `issuanceDate`/`expirationDate`.
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
    private val signatureVerificationPort: SignatureVerificationPort = DefaultEd25519SignatureVerificationAdapter(),
    private val jwsVerificationAdapter: DefaultJsonWebSignature2020Adapter = DefaultJsonWebSignature2020Adapter(),
    private val statusChecker: CredentialStatusChecker? = null,
) : ProofEngine {

    private val vmResolver: DidVerificationMethodResolver by lazy {
        DidVerificationMethodResolver(config.getDidResolver())
    }
    
    private val logger = LoggerFactory.getLogger(VcLdProofEngine::class.java)
    
    override val format = ProofSuiteId.VC_LD
    override val formatName = "Verifiable Credentials (Linked Data)"

    /**
     * Supported W3C VC Data Model versions. Issuance emits VC 1.1 by default and VC 2.0
     * when the request declares the v2 base context (see [issue]); verification accepts both.
     */
    override val formatVersion = "1.1, 2.0"
    
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

        val requestedSuite = request.proofOptions?.additionalOptions?.get("proofType")
            ?.toString()?.removeSurrounding("\"")
            ?: CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020

        val proofSuiteContext = if (requestedSuite == CredentialConstants.ProofTypes.JSON_WEB_SIGNATURE_2020) {
            CredentialConstants.SecuritySuites.JSON_WEB_SIGNATURE_2020_V1
        } else {
            CredentialConstants.SecuritySuites.ED25519_2020_V1
        }

        // Resolve the credential's @context: contexts declared by the request are preserved
        // (claim terms must be defined or canonicalization fails closed), the proof-suite
        // context is appended only if absent.
        val contexts = JsonLdDocumentBuilder.resolveContexts(request, proofSuiteContext)

        // Build VC document for canonicalization (must use same ID that will be in the credential object)
        val vcDocument = JsonLdDocumentBuilder.build(request, credentialId.value, contexts)

        // Canonicalize document (without proof)
        val canonicalDocument = canonicalizationPort.canonicalize(vcDocument)
        logger.debug("Canonicalized document for signing: length={}", canonicalDocument.length)

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

        // W3C Data Integrity: the signature must also cover the canonicalized proof options
        // (proof node without proofValue/jws) so that challenge, domain, created,
        // proofPurpose and verificationMethod cannot be rewritten after signing.
        val proofOptionsDocument = ProofEngineUtils.buildProofOptionsDocument(
            context = contexts,
            proofType = requestedSuite,
            created = proofCreated.toString(),
            verificationMethod = verificationMethod,
            proofPurpose = proofPurpose,
            additionalProperties = additionalProperties
        )
        val canonicalProofOptions = canonicalizationPort.canonicalize(proofOptionsDocument)
        val signingPayload = ProofEngineUtils.composeDataIntegrityPayload(canonicalProofOptions, canonicalDocument)

        // JsonWebSignature2020 uses a JWS-based proofValue; all other suites use base64url raw bytes
        val finalSignature = if (requestedSuite == CredentialConstants.ProofTypes.JSON_WEB_SIGNATURE_2020) {
            signPayloadAsJws(signingPayload, keyId, request.proofOptions
                ?.additionalOptions?.get("jwsAlgorithm")?.toString()?.removeSurrounding("\"") ?: "EdDSA")
        } else {
            signPayload(signingPayload, keyId)
        }
        logger.debug("Generated signature for VC-LD credential: prefix={}", finalSignature.take(20))

        val linkedDataProof = CredentialProof.LinkedDataProof(
            type = requestedSuite,
            created = proofCreated,
            verificationMethod = verificationMethod,
            proofPurpose = proofPurpose,
            proofValue = finalSignature,
            additionalProperties = additionalProperties
        )

        // Build VerifiableCredential (context must match the signed canonical document).
        // VC version honesty: a pure VC 2.0 credential carries validFrom/validUntil
        // (issuanceDate/expirationDate do not exist in the v2 vocabulary); VC 1.1 (and
        // dual-context) credentials keep issuanceDate/expirationDate. The same rule is
        // applied by JsonLdDocumentBuilder.build so the credential matches the signed
        // canonical document field-for-field.
        val isVc2 = JsonLdDocumentBuilder.isPureVc2(contexts)
        return VerifiableCredential(
            context = contexts,
            id = credentialId,
            type = request.type,
            issuer = request.issuer,
            issuanceDate = if (isVc2) null else request.issuedAt,
            validFrom = if (isVc2) request.validFrom ?: request.issuedAt else request.validFrom,
            credentialSubject = request.credentialSubject,
            expirationDate = if (isVc2) null else request.validUntil,
            validUntil = if (isVc2) request.validUntil else null,
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

            // Enforce proof purpose: credential proofs must be assertions. A proof created
            // for another purpose (e.g. keyAgreement) must not be accepted here.
            val expectedPurpose = CredentialConstants.ProofPurposes.ASSERTION_METHOD
            if (proof.proofPurpose != expectedPurpose) {
                return createInvalidProofResult(
                    credential,
                    "Proof purpose '${proof.proofPurpose}' is not acceptable for credential " +
                        "verification; expected '$expectedPurpose'",
                    "proofPurpose must be '$expectedPurpose' for credential proofs"
                )
            }

            // Resolve verification method; it must be authorized for assertionMethod in the
            // issuer's DID document.
            val verificationMethod = vmResolver.resolve(issuerIri, proof.verificationMethod, expectedPurpose)
                ?: return createInvalidIssuerResult(credential, issuerIri)

            // Canonicalize document
            val canonical = canonicalizationPort.canonicalize(
                JsonLdDocumentBuilder.buildWithoutProof(credential)
            )
            logger.debug("Verifying VC-LD credential: canonicalLength={}, verificationMethod={}, publicKeyJwkPresent={}",
                canonical.length, proof.verificationMethod, verificationMethod.publicKeyJwk != null)

            // Reconstruct and canonicalize the proof options (proof without proofValue/jws):
            // the signature covers SHA-256(proof options) || SHA-256(document), so tampering
            // with challenge, domain, created, proofPurpose or verificationMethod fails here.
            val proofOptionsDocument = ProofEngineUtils.buildProofOptionsDocument(
                context = credential.context,
                proofType = proof.type,
                created = proof.created.toString(),
                verificationMethod = proof.verificationMethod,
                proofPurpose = proof.proofPurpose,
                additionalProperties = proof.additionalProperties
            )
            val canonicalProofOptions = canonicalizationPort.canonicalize(proofOptionsDocument)
            val verificationPayload = ProofEngineUtils.composeDataIntegrityPayload(canonicalProofOptions, canonical)

            // Verify signature
            if (!verifyCredentialSignature(verificationPayload, proof, verificationMethod)) {
                return createInvalidProofResult(
                    credential,
                    "Linked Data Proof signature verification failed",
                    "Invalid signature on VC-LD document"
                )
            }

            // Revocation / suspension check
            val effectiveChecker = statusChecker
                ?: (config.properties["statusChecker"] as? CredentialStatusChecker)
            if (effectiveChecker != null && credential.credentialStatus != null) {
                when (val status = effectiveChecker.checkStatus(credential)) {
                    is CredentialStatusCheckResult.Revoked -> return VerificationResult.Invalid.Revoked(
                        credential = credential,
                        revokedAt = null,
                        errors = listOf("Credential has been revoked: ${status.reason ?: "no reason provided"}"),
                    )
                    is CredentialStatusCheckResult.Suspended -> return VerificationResult.Invalid.InvalidProof(
                        credential = credential,
                        reason = "Credential is suspended: ${status.reason ?: "no reason provided"}",
                        errors = listOf("Credential suspended"),
                    )
                    is CredentialStatusCheckResult.CheckFailed -> logger.warn(
                        "Status check failed for credential {}: {}",
                        credential.id?.value, status.reason
                    )
                    else -> {}
                }
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
        payload: ByteArray,
        proof: CredentialProof.LinkedDataProof,
        verificationMethod: VerificationMethod
    ): Boolean {
        val isValid = verifySignature(payload, proof.proofValue, verificationMethod, proof.type)
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
            issuedAt = credential.issuanceDate ?: credential.validFrom ?: kotlinx.datetime.Clock.System.now(),
            expiresAt = credential.expirationDate ?: credential.validUntil,
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
            reason = "Could not resolve issuer IRI, find the proof's verification method, or the " +
                "verification method is not authorized for the " +
                "'${CredentialConstants.ProofPurposes.ASSERTION_METHOD}' proof purpose in the " +
                "issuer's DID document",
            errors = listOf(
                "Failed to resolve issuer '${issuerIri.value}' or its verification method is not " +
                    "listed under '${CredentialConstants.ProofPurposes.ASSERTION_METHOD}'"
            ),
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
        
        // Handle selective disclosure if disclosedClaims is specified.
        // Copy to a local before checking + using — cross-module smart cast on a public
        // property declared in :credentials:credential-models-mp is not possible.
        val disclosedClaims = request.disclosedClaims
        val credentialsToInclude = if (disclosedClaims != null && disclosedClaims.isNotEmpty()) {
            credentials.map { credential ->
                SelectiveDisclosureFilter.filter(credential, disclosedClaims)
            }
        } else {
            credentials
        }
        
        // Get holder from first credential's subject
        val holder = credentialsToInclude.first().credentialSubject.id
            ?: throw IllegalArgumentException("Cannot create presentation: credential subject has no id")
        
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
    
    
    private suspend fun signPayload(payload: ByteArray, keyId: String): String {
        logger.debug("Signing payload: keyId={}, payloadLength={}", keyId, payload.size)
        // Get signer function if available (preferred), otherwise get KMS and create signer
        val signer = getSignerFunction() ?: run {
            val kms = getKms() ?: throw IllegalStateException(
                "KMS not configured. Provide KMS via ProofEngineConfig.properties[\"kms\"] or signer via properties[\"signer\"]"
            )
            createKmsSigner(kms)
        }

        // Sign the composed Data Integrity payload
        val signatureBytes = signer(payload, keyId)
        logger.debug("Signed payload: keyId={}, signatureLength={}", keyId, signatureBytes.size)

        // Return base64url-encoded signature
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
    }

    /**
     * Verify Linked Data Proof signature via [SignatureVerificationPort].
     *
     * @param payload The composed Data Integrity payload
     *   (`SHA-256(canonical proof options) || SHA-256(canonical document)`)
     * @param proofValue The base64url-encoded signature
     * @param verificationMethod The verification method from DID document
     * @param proofType The proof type (e.g., "Ed25519Signature2020")
     * @return True if signature is valid, false otherwise
     */
    private fun verifySignature(
        payload: ByteArray,
        proofValue: String,
        verificationMethod: VerificationMethod,
        proofType: String
    ): Boolean {
        logger.debug("Verifying signature: proofType={}, proofValueLength={}", proofType, proofValue.length)
        if (proofValue.isBlank()) {
            logger.warn("Proof value is blank")
            return false
        }

        // JsonWebSignature2020: proofValue is a detached JWS (header..signature, no payload in middle)
        if (proofType == CredentialConstants.ProofTypes.JSON_WEB_SIGNATURE_2020) {
            logger.debug("Verifying JsonWebSignature2020 detached JWS")
            return jwsVerificationAdapter.verifyDetachedJws(proofValue, payload, verificationMethod)
        }

        val signatureBytes = try {
            Base64.getUrlDecoder().decode(proofValue)
        } catch (e: Exception) {
            logger.error("Failed to decode signature: error={}", e.message, e)
            return false
        }

        logger.debug("Verifying signature: signatureLength={}, payloadLength={}", signatureBytes.size, payload.size)
        return signatureVerificationPort.verify(payload, signatureBytes, verificationMethod, proofType)
    }

    /**
     * Sign using detached JWS (JsonWebSignature2020 proof type).
     * Returns the compact JWS with empty second segment: `<header>..<signature>`.
     */
    private suspend fun signPayloadAsJws(payload: ByteArray, keyId: String, algorithm: String): String {
        val kms = getKms() ?: throw IllegalStateException("KMS required for JsonWebSignature2020 signing")
        val jwsAlgorithm = when (algorithm.uppercase()) {
            "EDDSA", "ED25519" -> JWSAlgorithm.EdDSA
            "ES256" -> JWSAlgorithm.ES256
            "ES256K", "SECP256K1" -> JWSAlgorithm.ES256K
            "ES384" -> JWSAlgorithm.ES384
            "ES512" -> JWSAlgorithm.ES512
            else -> JWSAlgorithm.EdDSA
        }
        val header = JWSHeader.Builder(jwsAlgorithm).build()
        val signingInput = "${header.toBase64URL()}.${Base64URL.encode(payload)}"
            .toByteArray(Charsets.UTF_8)
        val signResult = kms.sign(KeyId(keyId), signingInput)
        val sigBytes = when (signResult) {
            is SignResult.Success -> signResult.signature
            is SignResult.Failure.KeyNotFound -> throw IllegalStateException("Key not found: ${signResult.keyId.value}")
            is SignResult.Failure.UnsupportedAlgorithm -> throw IllegalStateException("Unsupported algorithm")
            is SignResult.Failure.Error -> throw IllegalStateException("Sign error: ${signResult.reason}")
        }
        // RFC 7518 §3.4: the JWS signature segment of an ECDSA algorithm MUST be the raw
        // IEEE P1363 r||s form. The KMS contract is P1363 for EC keys, but providers built
        // before that contract (and many backends) emit ASN.1 DER — embedding DER raw would
        // produce a JWS that can never verify. Transcode DER input; P1363 passes through.
        // Ed25519 signatures are already the raw 64-byte RFC 8032 form and are not touched.
        val jwsSignatureBytes = if (ProofEngineUtils.isEcdsaJwsAlgorithm(jwsAlgorithm)) {
            ProofEngineUtils.ensureP1363EcdsaJwsSignature(sigBytes, jwsAlgorithm)
        } else {
            sigBytes
        }
        // Detached JWS: header..signature (empty payload segment)
        return "${header.toBase64URL()}..${Base64URL.encode(jwsSignatureBytes)}"
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

