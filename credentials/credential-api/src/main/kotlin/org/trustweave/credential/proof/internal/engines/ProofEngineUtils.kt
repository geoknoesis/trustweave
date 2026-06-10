package org.trustweave.credential.proof.internal.engines

import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.internal.SecurityConstants
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.SignedJWT
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.PublicKey
import java.security.KeyFactory
import java.security.Security
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.slf4j.LoggerFactory

/**
 * Shared utilities for proof engines.
 * 
 * This utility object provides shared functionality for proof engine implementations,
 * including DID resolution, verification method extraction, and public key operations.
 * 
 * **Key Operations:**
 * - DID resolution and verification method lookup
 * - Public key extraction from verification methods (JWK, multibase)
 * - Ed25519 public key creation from various formats
 * - Key format conversion and validation
 * 
 * **Usage:**
 * ```kotlin
 * // Resolve verification method from DID
 * val verificationMethod = ProofEngineUtils.getVerificationMethodFromDid(
 *     issuerIri = issuerIri,
 *     verificationMethodId = proof.verificationMethod
 * )
 * 
 * // Extract public key
 * val publicKey = ProofEngineUtils.extractPublicKey(verificationMethod)
 * ```
 * 
 * **Note:** This is an internal utility used by proof engines (VC-LD, SD-JWT-VC, etc.)
 * for cryptographic operations. It handles multiple key formats and provides fallback
 * mechanisms for key extraction.
 */
internal object ProofEngineUtils {
    
    private val logger = LoggerFactory.getLogger(ProofEngineUtils::class.java)
    
    /**
     * Extract key ID from VerificationMethodId.
     * Handles both formats: "did:example:issuer#key-1" and "key-1"
     */
    fun extractKeyId(verificationMethodId: String?): String? {
        if (verificationMethodId == null) return null
        return if (verificationMethodId.contains("#")) {
            verificationMethodId.substringAfter("#")
        } else {
            verificationMethodId
        }
    }
    
    /**
     * Resolve verification method from DID document.
     *
     * When [expectedProofPurpose] is provided, the resolved verification method must be
     * referenced (or embedded) in the corresponding verification relationship array of the
     * DID document (e.g. `assertionMethod` for credential proofs, `authentication` for
     * presentation proofs). A key that is present in `verificationMethod` but not
     * authorized for the expected purpose (e.g. a `keyAgreement`-only key) is rejected.
     *
     * @param issuerIri The issuer IRI (must be a DID)
     * @param verificationMethodId The verification method ID (can be full or fragment)
     * @param didResolver The DID resolver to use
     * @param expectedProofPurpose Optional proof purpose the verification method must be
     *   authorized for (one of the W3C verification relationship names)
     * @return The verification method, or null if resolution fails or the method is not
     *   authorized for [expectedProofPurpose]
     */
    suspend fun resolveVerificationMethod(
        issuerIri: Iri,
        verificationMethodId: String?,
        didResolver: DidResolver?,
        expectedProofPurpose: String? = null
    ): VerificationMethod? {
        logger.debug("Resolving verification method: issuerIri={}, verificationMethodId={}, didResolverPresent={}", 
            issuerIri.value, verificationMethodId, didResolver != null)
        if (!issuerIri.isDid || didResolver == null) {
            logger.debug("Cannot resolve verification method: isDid={}, didResolverPresent={}", issuerIri.isDid, didResolver != null)
            return null
        }
        
        try {
            val issuerDid = Did(issuerIri.value)
            logger.debug("Resolving DID: {}", issuerDid.value)
            val resolutionResult = didResolver.resolve(issuerDid)
            
            val document = when (resolutionResult) {
                is DidResolutionResult.Success -> {
                    logger.debug("Successfully resolved DID: verificationMethodsCount={}", resolutionResult.document.verificationMethod.size)
                    resolutionResult.document
                }
                else -> {
                    logger.warn("Failed to resolve DID: issuerIri={}, resolutionResult={}", issuerIri.value, resolutionResult.javaClass.simpleName)
                    return null
                }
            }
            
            // Parse verification method ID
            val vmId = if (verificationMethodId != null) {
                try {
                    VerificationMethodId.parse(verificationMethodId, issuerDid)
                } catch (e: Exception) {
                    logger.debug("Failed to parse verificationMethodId '{}': error={}", verificationMethodId, e.message)
                    // Try with just the fragment
                    if (verificationMethodId.contains("#")) {
                        VerificationMethodId.parse(verificationMethodId, issuerDid)
                    } else {
                        VerificationMethodId(issuerDid, KeyId(verificationMethodId))
                    }
                }
            } else {
                // Default to first assertion method or first verification method
                document.assertionMethod.firstOrNull() 
                    ?: document.verificationMethod.firstOrNull()?.id
                    ?: return null
            }
            
            logger.debug("Looking for verification method: vmId={}, availableCount={}", vmId.value, document.verificationMethod.size)
            
            // Find verification method in document
            val found = document.verificationMethod.find { it.id == vmId }
                ?: document.verificationMethod.find { 
                    it.id.value == vmId.value || 
                    it.id.value.endsWith("#${vmId.keyId.value}")
                }
            
            if (found == null) {
                logger.warn("Could not find matching verification method: vmId={}", vmId.value)
                return null
            }
            logger.debug("Found verification method: vmId={}", found.id.value)

            // Enforce proof purpose authorization: the verification method must be listed
            // in the DID document's relationship array for the expected purpose.
            if (expectedProofPurpose != null &&
                !isAuthorizedForPurpose(document, found, expectedProofPurpose)
            ) {
                logger.warn(
                    "Verification method {} is not authorized for proof purpose '{}' in DID document {}",
                    found.id.value, expectedProofPurpose, document.id.value
                )
                return null
            }

            return found
        } catch (e: Exception) {
            logger.error("Exception while resolving verification method: issuerIri={}, error={}", issuerIri.value, e.message, e)
            return null
        }
    }

    /**
     * Check whether [verificationMethod] is referenced in the verification relationship
     * array of [document] that corresponds to [proofPurpose].
     *
     * Unknown proof purposes are rejected (fail closed).
     */
    fun isAuthorizedForPurpose(
        document: DidDocument,
        verificationMethod: VerificationMethod,
        proofPurpose: String
    ): Boolean {
        val relationship = when (proofPurpose) {
            CredentialConstants.ProofPurposes.ASSERTION_METHOD -> document.assertionMethod
            CredentialConstants.ProofPurposes.AUTHENTICATION -> document.authentication
            "keyAgreement" -> document.keyAgreement
            "capabilityInvocation" -> document.capabilityInvocation
            "capabilityDelegation" -> document.capabilityDelegation
            else -> {
                logger.warn("Unknown proof purpose '{}' — rejecting", proofPurpose)
                return false
            }
        }
        return relationship.any { it == verificationMethod.id || it.value == verificationMethod.id.value }
    }

    /**
     * Compose the W3C Data Integrity signing payload:
     * `SHA-256(canonical proof options) || SHA-256(canonical document)`.
     *
     * Covering the canonicalized proof options (the proof node without `proofValue`/`jws`)
     * binds `challenge`, `domain`, `created`, `proofPurpose` and `verificationMethod` to
     * the signature, preventing replay attacks that rewrite those fields on a captured
     * credential or presentation.
     *
     * @param canonicalProofOptions Canonical N-Quads of the proof options document
     * @param canonicalDocument Canonical N-Quads of the secured document (without proof)
     * @return The 64-byte payload to sign or verify
     */
    fun composeDataIntegrityPayload(canonicalProofOptions: String, canonicalDocument: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val proofOptionsHash = digest.digest(canonicalProofOptions.toByteArray(Charsets.UTF_8))
        val documentHash = digest.digest(canonicalDocument.toByteArray(Charsets.UTF_8))
        return proofOptionsHash + documentHash
    }

    /**
     * Build the proof options document — the proof node without `proofValue`/`jws` —
     * for canonicalization, per the W3C Data Integrity specification.
     *
     * The document carries the secured document's `@context` so the proof terms
     * (`created`, `proofPurpose`, `verificationMethod`, `challenge`, `domain`) are defined
     * by the proof-suite context that the secured document already declares.
     *
     * @param context The `@context` list of the secured document
     * @param proofType The proof suite type (e.g. "Ed25519Signature2020")
     * @param created The proof creation timestamp (ISO 8601)
     * @param verificationMethod The verification method reference
     * @param proofPurpose The proof purpose
     * @param additionalProperties Additional proof properties (e.g. challenge, domain);
     *   `proofValue` and `jws` entries are excluded
     * @return The proof options document
     */
    fun buildProofOptionsDocument(
        context: List<String>,
        proofType: String,
        created: String,
        verificationMethod: String,
        proofPurpose: String,
        additionalProperties: Map<String, JsonElement> = emptyMap()
    ): JsonObject = buildJsonObject {
        put("@context", buildJsonArray {
            context.forEach { add(it) }
        })
        put("type", proofType)
        put("created", created)
        put("verificationMethod", verificationMethod)
        put("proofPurpose", proofPurpose)
        additionalProperties.forEach { (key, value) ->
            if (key != "proofValue" && key != "jws") {
                put(key, value)
            }
        }
    }

    /**
     * Extract public key from verification method.
     * 
     * Attempts to extract a Java PublicKey from a verification method by trying multiple
     * key formats in order of preference. This function handles various key encoding formats
     * commonly used in DID documents and verifiable credentials.
     * 
     * **Extraction Algorithm:**
     * 1. **JWK (JSON Web Key)**: Primary method - checks for `publicKeyJwk` field
     *    - Extracts key from JWK map using `extractPublicKeyFromJwk()`
     *    - Supports Ed25519 keys (OKP key type)
     * 2. **Multibase**: Secondary method - checks for `publicKeyMultibase` field
     *    - Extracts key from multibase-encoded string
     *    - Decodes multibase prefix and extracts raw key bytes
     * 3. **Fallback**: Returns null if neither format is available
     * 
     * **Key Format Support:**
     * - Ed25519 keys in JWK format (OKP with crv=Ed25519)
     * - Ed25519 keys in multibase format (base58-btc encoding)
     * - Additional key types may be supported via extensions
     * 
     * **Error Handling:**
     * - Returns null if extraction fails (graceful degradation)
     * - Logs warnings for debugging purposes
     * - Multiple extraction strategies ensure compatibility with different DID methods
     * 
     * @param verificationMethod The verification method containing the public key
     * @return The public key, or null if extraction fails or key format is unsupported
     */
    fun extractPublicKey(verificationMethod: VerificationMethod): PublicKey? {
        logger.debug("Extracting public key: verificationMethodId={}, type={}, hasJwk={}, hasMultibase={}", 
            verificationMethod.id.value, verificationMethod.type, 
            verificationMethod.publicKeyJwk != null, verificationMethod.publicKeyMultibase != null)
        // Try JWK first
        verificationMethod.publicKeyJwk?.let { jwkMap ->
            logger.debug("Attempting to extract public key from JWK: keys={}", jwkMap.keys)
            val result = extractPublicKeyFromJwk(jwkMap)
            if (result == null) {
                logger.warn("Failed to extract public key from JWK: verificationMethodId={}", verificationMethod.id.value)
            } else {
                logger.debug("Successfully extracted public key: algorithm={}", result.algorithm)
            }
            return result
        }
        
        // Try multibase
        verificationMethod.publicKeyMultibase?.let { multibase ->
            logger.debug("Attempting to extract public key from multibase: verificationMethodId={}", verificationMethod.id.value)
            return extractPublicKeyFromMultibase(multibase, verificationMethod.type)
        }
        
        logger.warn("No public key found in verification method: verificationMethodId={}", verificationMethod.id.value)
        return null
    }
    
    /**
     * Extract public key from JWK map.
     */
    private fun extractPublicKeyFromJwk(jwkMap: Map<String, Any?>): PublicKey? {
        try {
            val kty = jwkMap["kty"] as? String ?: return null
            
            return when (kty) {
                "OKP" -> {
                    // Ed25519
                    val crv = jwkMap["crv"] as? String
                    if (crv != "Ed25519") return null
                    
                    val x = jwkMap["x"] as? String ?: return null
                    val xBytes = Base64.getUrlDecoder().decode(x)
                    
                    if (xBytes.size != 32) return null
                    
                    // Approach 1: Use BouncyCastle Ed25519PublicKeyParameters - most reliable
                    try {
                        // Ensure BouncyCastle provider is registered
                        if (Security.getProvider("BC") == null) {
                            Security.addProvider(BouncyCastleProvider())
                        }
                        
                        // Create Ed25519 public key parameters from raw bytes
                        val publicKeyParams = Ed25519PublicKeyParameters(xBytes, 0)
                        
                        // Convert BouncyCastle parameters to Java PublicKey using SubjectPublicKeyInfo
                        // This is the standard ASN.1 encoding for Ed25519 public keys
                        // Ed25519 OID: 1.3.101.112 (from RFC 8410)
                        val ed25519Oid = org.bouncycastle.asn1.ASN1ObjectIdentifier("1.3.101.112")
                        val algorithmIdentifier = AlgorithmIdentifier(ed25519Oid)
                        val subjectPublicKey = DERBitString(publicKeyParams.encoded)
                        val subjectPublicKeyInfo = SubjectPublicKeyInfo(algorithmIdentifier, subjectPublicKey)
                        val derEncoded = subjectPublicKeyInfo.encoded
                        
                        // Use KeyFactory with X509EncodedKeySpec (which expects SubjectPublicKeyInfo format)
                        val keyFactory = KeyFactory.getInstance("Ed25519", "BC")
                        val keySpec = X509EncodedKeySpec(derEncoded)
                        val publicKey = keyFactory.generatePublic(keySpec)
                        logger.debug("Successfully extracted Ed25519 public key using BouncyCastle")
                        return publicKey
                    } catch (e: Exception) {
                        logger.debug("BouncyCastle approach failed: error={}", e.message)
                        // Fall through to Java built-in approach
                    }
                    
                    // Approach 2: Use Java's built-in EdECPublicKeySpec (Java 15+)
                    // EdECPublicKeySpec constructor is (NamedParameterSpec, EdECPoint)
                    // EdECPoint constructor is (boolean odd, BigInteger coordinate)
                    try {
                        val keyFactory = KeyFactory.getInstance("Ed25519")
                        val paramsClass = Class.forName("java.security.spec.NamedParameterSpec")
                        val ed25519Field = paramsClass.getField("ED25519")
                        val params = ed25519Field.get(null)
                        
                        // Create EdECPoint: For Ed25519, the 32 bytes are the y coordinate
                        // The last bit indicates the odd flag for compressed point representation
                        val odd = (xBytes.last().toInt() and 1) != 0
                        val coordinate = java.math.BigInteger(1, xBytes) // Positive BigInteger
                        
                        val edecPointClass = Class.forName("java.security.spec.EdECPoint")
                        val edecPointConstructor = edecPointClass.getConstructor(Boolean::class.java, java.math.BigInteger::class.java)
                        val edecPoint = edecPointConstructor.newInstance(odd, coordinate)
                        
                        val keySpecClass = Class.forName("java.security.spec.EdECPublicKeySpec")
                        val keySpecConstructor = keySpecClass.getConstructor(paramsClass, edecPointClass)
                        val keySpec = keySpecConstructor.newInstance(params, edecPoint)
                        
                        val publicKey = keyFactory.generatePublic(keySpec as java.security.spec.KeySpec)
                        logger.debug("Successfully created Ed25519 public key using EdECPublicKeySpec")
                        publicKey
                    } catch (e: Exception) {
                        logger.warn("EdECPublicKeySpec approach also failed: error={}", e.message)
                        null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Extract public key from multibase-encoded string.
     * 
     * Parses a multibase-encoded public key string and extracts the corresponding Java PublicKey.
     * Multibase encoding uses a single character prefix to indicate the base encoding scheme,
     * followed by the base-encoded key data.
     * 
     * **Supported Multibase Encodings:**
     * - **base58-btc** (prefix 'z'): Most common for Ed25519 keys in DID documents
     * - Additional encodings may be supported via extensions
     * 
     * **Extraction Algorithm:**
     * 1. Extract multibase prefix (first character)
     * 2. Decode the base-encoded portion based on prefix
     * 3. Validate key length (Ed25519 requires 32 bytes)
     * 4. Convert raw bytes to Java PublicKey using BouncyCastle
     * 
     * **Key Format:**
     * - Ed25519 keys are 32 bytes in length
     * - Multibase format: `{prefix}{base-encoded-key}`
     * - Example: `z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK`
     * 
     * **Error Handling:**
     * - Returns null if multibase prefix is unsupported
     * - Returns null if key length is incorrect
     * - Returns null if key conversion fails
     * - Logs warnings for debugging purposes
     * 
     * **Current Status:**
     * Multibase decoding is not yet implemented. This function returns null for multibase-encoded keys.
     * JWK format (via `publicKeyJwk`) is preferred and fully supported.
     * 
     * @param multibase The multibase-encoded key string
     * @param keyType The key type (used for validation)
     * @return The extracted PublicKey, or null if extraction fails or format is not yet supported
     */
    private fun extractPublicKeyFromMultibase(multibase: String, keyType: String): PublicKey? {
        // Multibase decoding requires base58btc decoding library
        // For now, return null - JWK format is preferred
        return null
    }
    
    /**
     * Verify an EdDSA (Ed25519) JWS signature using the Java Security API.
     *
     * Verifies the JWS signing input (`BASE64URL(header) || '.' || BASE64URL(payload)`)
     * against the signature using the public key extracted from [verificationMethod].
     *
     * This intentionally avoids Nimbus' `Ed25519Verifier`, which requires the OPTIONAL
     * `com.google.crypto.tink` dependency at runtime — not declared by this module — and
     * would otherwise throw [NoClassDefFoundError] during verification.
     *
     * Fail-closed: any error (wrong `alg` header, unsupported key, malformed signature)
     * yields `false`.
     *
     * @param signedJwt The parsed signed JWT
     * @param verificationMethod The verification method holding the Ed25519 public key
     * @return true if the signature is valid, false otherwise
     */
    fun verifyEd25519Jws(
        signedJwt: SignedJWT,
        verificationMethod: VerificationMethod
    ): Boolean {
        // Reject algorithm confusion: only EdDSA is acceptable for Ed25519 keys.
        if (signedJwt.header.algorithm != JWSAlgorithm.EdDSA) {
            return false
        }
        val publicKey = extractPublicKey(verificationMethod) ?: return false
        return try {
            val signatureBytes = signedJwt.signature.decode()
            if (signatureBytes.size != SecurityConstants.ED25519_SIGNATURE_LENGTH_BYTES) {
                return false
            }
            val signature = java.security.Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(signedJwt.signingInput)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}

