package org.trustweave.credential.proof.internal.engines

import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.jwk.OctetKeyPair
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import java.security.PublicKey
import java.security.KeyFactory
import java.security.Security
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.slf4j.LoggerFactory

/**
 * Shared utilities for proof engines.
 * 
 * Provides common functionality for DID resolution, key extraction, and verification.
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
     * @param issuerIri The issuer IRI (must be a DID)
     * @param verificationMethodId The verification method ID (can be full or fragment)
     * @param didResolver The DID resolver to use
     * @return The verification method, or null if resolution fails
     */
    suspend fun resolveVerificationMethod(
        issuerIri: Iri,
        verificationMethodId: String?,
        didResolver: DidResolver?
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
            } else {
                logger.debug("Found verification method: vmId={}", found.id.value)
            }
            
            return found
        } catch (e: Exception) {
            logger.error("Exception while resolving verification method: issuerIri={}, error={}", issuerIri.value, e.message, e)
            return null
        }
    }
    
    /**
     * Extract public key from verification method.
     * 
     * @param verificationMethod The verification method
     * @return The public key, or null if extraction fails
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
     * Extract public key from multibase encoding.
     * 
     * Note: Multibase decoding is not yet implemented. This function will return null
     * for multibase-encoded keys. JWK format is preferred and fully supported.
     */
    private fun extractPublicKeyFromMultibase(multibase: String, keyType: String): PublicKey? {
        // Multibase decoding requires base58btc decoding library
        // For now, return null - JWK format is preferred
        return null
    }
    
    /**
     * Create Ed25519 verifier from public key for JWT verification.
     * 
     * @param publicKey The Ed25519 public key
     * @return Ed25519Verifier instance, or null if creation fails
     */
    fun createEd25519Verifier(publicKey: PublicKey): Ed25519Verifier? {
        return try {
            // Extract raw 32-byte Ed25519 public key from PublicKey
            val publicKeyBytes = publicKey.encoded
            val rawKey = if (publicKeyBytes.size >= 44 && publicKeyBytes[0] == 0x30.toByte()) {
                // Ed25519 public key in DER: 30 2A 30 05 06 03 2B 65 70 03 21 00 [32 bytes]
                publicKeyBytes.sliceArray(12 until 44)
            } else {
                publicKeyBytes.takeLast(32).toByteArray()
            }
            
            if (rawKey.size != 32) return null
            
            // Create JWK map for Ed25519
            val jwkMap = mapOf(
                "kty" to "OKP",
                "crv" to "Ed25519",
                "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey)
            )
            
            // Parse JWK map to OctetKeyPair
            val okp = OctetKeyPair.parse(jwkMap)
            Ed25519Verifier(okp)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Create Ed25519 verifier from verification method.
     * 
     * @param verificationMethod The verification method
     * @return Ed25519Verifier instance, or null if creation fails
     */
    fun createEd25519Verifier(verificationMethod: VerificationMethod): Ed25519Verifier? {
        val publicKey = extractPublicKey(verificationMethod) ?: return null
        return createEd25519Verifier(publicKey)
    }
}

