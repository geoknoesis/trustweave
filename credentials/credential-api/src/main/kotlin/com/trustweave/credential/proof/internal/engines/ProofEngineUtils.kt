package com.trustweave.credential.proof.internal.engines

import com.trustweave.core.identifiers.Iri
import com.trustweave.core.identifiers.KeyId
import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.did.model.VerificationMethod
import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.jwk.OctetKeyPair
import java.security.PublicKey
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Shared utilities for proof engines.
 * 
 * Provides common functionality for DID resolution, key extraction, and verification.
 */
internal object ProofEngineUtils {
    
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
        if (!issuerIri.isDid || didResolver == null) {
            return null
        }
        
        try {
            val issuerDid = Did(issuerIri.value)
            val resolutionResult = didResolver.resolve(issuerDid)
            
            val document = when (resolutionResult) {
                is DidResolutionResult.Success -> resolutionResult.document
                else -> return null
            }
            
            // Parse verification method ID
            val vmId = if (verificationMethodId != null) {
                try {
                    VerificationMethodId.parse(verificationMethodId, issuerDid)
                } catch (e: Exception) {
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
            
            // Find verification method in document
            return document.verificationMethod.find { it.id == vmId }
                ?: document.verificationMethod.find { 
                    it.id.value == vmId.value || 
                    it.id.value.endsWith("#${vmId.keyId.value}")
                }
        } catch (e: Exception) {
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
        // Try JWK first
        verificationMethod.publicKeyJwk?.let { jwkMap ->
            return extractPublicKeyFromJwk(jwkMap)
        }
        
        // Try multibase
        verificationMethod.publicKeyMultibase?.let { multibase ->
            return extractPublicKeyFromMultibase(multibase, verificationMethod.type)
        }
        
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
                    
                    // Create Ed25519 public key
                    try {
                        val keyFactory = KeyFactory.getInstance("Ed25519")
                        val keySpec = X509EncodedKeySpec(xBytes)
                        keyFactory.generatePublic(keySpec)
                    } catch (e: Exception) {
                        // Fallback: try EdECPublicKeySpec for Java 15+
                        try {
                            val keyFactory = KeyFactory.getInstance("Ed25519")
                            val paramsClass = Class.forName("java.security.spec.NamedParameterSpec")
                            val ed25519Field = paramsClass.getField("ED25519")
                            val params = ed25519Field.get(null)
                            
                            val keySpecClass = Class.forName("java.security.spec.EdECPublicKeySpec")
                            val keySpecConstructor = keySpecClass.getConstructor(paramsClass, ByteArray::class.java)
                            val keySpec = keySpecConstructor.newInstance(params, xBytes)
                            
                            keyFactory.generatePublic(keySpec as java.security.spec.KeySpec)
                        } catch (e2: Exception) {
                            null
                        }
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

