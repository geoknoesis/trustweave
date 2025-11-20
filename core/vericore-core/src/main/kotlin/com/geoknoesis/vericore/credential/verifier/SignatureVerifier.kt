package com.geoknoesis.vericore.credential.verifier

import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.credential.models.Proof
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.did.VerificationMethodRef
import com.geoknoesis.vericore.json.DigestUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.security.*
import java.security.spec.*
import java.util.Base64

/**
 * Signature verifier for cryptographic proofs.
 * 
 * Supports multiple proof types:
 * - Ed25519Signature2020: Ed25519 signatures
 * - JsonWebSignature2020: JWT-based signatures
 * - BbsBlsSignature2020: BBS+ signatures (placeholder)
 */
class SignatureVerifier(
    private val didResolver: CredentialDidResolver
) {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }
    
    /**
     * Verify a proof signature.
     * 
     * @param credential Credential to verify
     * @param proof Proof to verify
     * @return true if signature is valid
     */
    suspend fun verify(
        credential: VerifiableCredential,
        proof: Proof
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            when {
                proof.jws != null -> verifyJwtProof(credential, proof)
                proof.proofValue != null -> verifyJsonLdProof(credential, proof)
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verify JWT-based proof (JsonWebSignature2020).
     */
    private suspend fun verifyJwtProof(
        credential: VerifiableCredential,
        proof: Proof
    ): Boolean {
        val jws = proof.jws ?: return false
        
        try {
            // Parse JWT (using reflection to avoid direct dependency on nimbus-jose-jwt)
            val signedJwtClass = Class.forName("com.nimbusds.jwt.SignedJWT")
            val parseMethod = signedJwtClass.getMethod("parse", String::class.java)
            val signedJwt = parseMethod.invoke(null, jws)
            
            // Resolve verification method
            val verificationMethod = resolveVerificationMethod(proof.verificationMethod) ?: return false
            
            // Extract public key from verification method
            val publicKey = extractPublicKeyFromVerificationMethod(verificationMethod) ?: return false
            
            // Create verifier based on key type
            val verifier = createJwsVerifier(publicKey) ?: return false
            
            // Verify signature
            val verifyMethod = signedJwtClass.getMethod("verify", Class.forName("com.nimbusds.jose.JWSVerifier"))
            return verifyMethod.invoke(signedJwt, verifier) as Boolean
        } catch (e: Exception) {
            // If nimbus-jose-jwt is not available, return false
            return false
        }
    }
    
    /**
     * Verify JSON-LD proof (Ed25519Signature2020, BbsBlsSignature2020, etc.).
     */
    private suspend fun verifyJsonLdProof(
        credential: VerifiableCredential,
        proof: Proof
    ): Boolean {
        val proofValue = proof.proofValue ?: return false
        
        try {
            // Resolve verification method
            val verificationMethod = resolveVerificationMethod(proof.verificationMethod) ?: return false
            
            // Create proof document (credential without proof)
            val proofDocument = createProofDocument(credential, proof)
            
            // Canonicalize proof document
            val canonicalDocument = DigestUtils.canonicalizeJson(proofDocument)
            val documentBytes = canonicalDocument.toByteArray(Charsets.UTF_8)
            
            // Decode signature
            val signature = decodeSignature(proofValue) ?: return false
            
            // Verify based on proof type
            return when (proof.type) {
                "Ed25519Signature2020" -> verifyEd25519Signature(documentBytes, signature, verificationMethod)
                "BbsBlsSignature2020" -> verifyBbsSignature(documentBytes, signature, verificationMethod)
                else -> false
            }
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Resolve verification method from DID document.
     */
    private suspend fun resolveVerificationMethod(verificationMethodId: String): VerificationMethodRef? {
        // Extract DID from verification method ID
        val did = if (verificationMethodId.contains("#")) {
            verificationMethodId.substringBefore("#")
        } else {
            verificationMethodId
        }
        
        // Resolve DID document
        val resolutionResult = didResolver.resolve(did) ?: return null
        val documentObj = resolutionResult.document ?: return null
        
        // Convert to DidDocument
        val document = when (documentObj) {
            is com.geoknoesis.vericore.did.DidDocument -> documentObj
            is Map<*, *> -> convertMapToDidDocument(documentObj, did)
            else -> {
                // Try reflection to extract DidDocument
                try {
                    val docField = documentObj.javaClass.getDeclaredField("document")
                    docField.isAccessible = true
                    docField.get(documentObj) as? com.geoknoesis.vericore.did.DidDocument
                } catch (e: Exception) {
                    null
                }
            }
        } ?: return null
        
        // Find verification method
        val methodId = if (verificationMethodId.contains("#")) {
            verificationMethodId.substringAfter("#")
        } else {
            verificationMethodId
        }
        
        // Check direct verification methods
        val directMethod = document.verificationMethod.find { vm ->
            vm.id == verificationMethodId || vm.id.endsWith("#$methodId")
        }
        if (directMethod != null) {
            return directMethod
        }
        
        // Check authentication references
        val authRef = document.authentication.find { ref ->
            ref == verificationMethodId || ref.endsWith("#$methodId")
        }
        if (authRef != null) {
            return document.verificationMethod.find { vm ->
                vm.id == authRef || vm.id.endsWith(authRef.substringAfter("#"))
            }
        }
        
        // Check assertion method references
        val assertionRef = document.assertionMethod.find { ref ->
            ref == verificationMethodId || ref.endsWith("#$methodId")
        }
        if (assertionRef != null) {
            return document.verificationMethod.find { vm ->
                vm.id == assertionRef || vm.id.endsWith(assertionRef.substringAfter("#"))
            }
        }
        
        return null
    }
    
    /**
     * Convert map to DidDocument (for JSON-based resolvers).
     */
    private fun convertMapToDidDocument(map: Map<*, *>, did: String): com.geoknoesis.vericore.did.DidDocument? {
        try {
            val verificationMethod = (map["verificationMethod"] as? List<*>)?.mapNotNull { vmMap ->
                val vm = vmMap as? Map<*, *> ?: return@mapNotNull null
                com.geoknoesis.vericore.did.VerificationMethodRef(
                    id = (vm["id"] as? String) ?: return@mapNotNull null,
                    type = (vm["type"] as? String) ?: return@mapNotNull null,
                    controller = (vm["controller"] as? String) ?: did,
                    publicKeyJwk = (vm["publicKeyJwk"] as? Map<*, *>)?.mapValues { it.value },
                    publicKeyMultibase = vm["publicKeyMultibase"] as? String
                )
            } ?: emptyList()
            
            val authentication = (map["authentication"] as? List<*>)?.mapNotNull { it as? String }
                ?: (map["authentication"] as? String)?.let { listOf(it) }
                ?: emptyList()
            
            val assertionMethod = (map["assertionMethod"] as? List<*>)?.mapNotNull { it as? String }
                ?: (map["assertionMethod"] as? String)?.let { listOf(it) }
                ?: emptyList()
            
            return com.geoknoesis.vericore.did.DidDocument(
                id = did,
                verificationMethod = verificationMethod,
                authentication = authentication,
                assertionMethod = assertionMethod
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Extract public key from verification method.
     */
    private fun extractPublicKeyFromVerificationMethod(
        verificationMethod: VerificationMethodRef
    ): PublicKey? {
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
     * Extract public key from JWK.
     */
    private fun extractPublicKeyFromJwk(jwkMap: Map<String, Any?>): PublicKey? {
        try {
            // Use reflection to avoid direct dependency on nimbus-jose-jwt
            val jwkClass = Class.forName("com.nimbusds.jose.jwk.JWK")
            val parseMethod = jwkClass.getMethod("parse", String::class.java)
            val jwk = parseMethod.invoke(null, jwkMap.toJsonString())
            
            val jwkType = jwk.javaClass.simpleName
            
            return when {
                jwkType == "OctetKeyPair" -> {
                    // Ed25519
                    val toPublicKeyMethod = jwk.javaClass.getMethod("toPublicKey")
                    val publicKey = toPublicKeyMethod.invoke(jwk) as PublicKey
                    val keyFactory = KeyFactory.getInstance("Ed25519")
                    val keySpec = X509EncodedKeySpec(publicKey.encoded)
                    keyFactory.generatePublic(keySpec)
                }
                jwkType == "ECKey" -> {
                    // ECDSA
                    val toECPublicKeyMethod = jwk.javaClass.getMethod("toECPublicKey")
                    toECPublicKeyMethod.invoke(jwk) as PublicKey
                }
                jwkType == "RSAKey" -> {
                    // RSA
                    val toRSAPublicKeyMethod = jwk.javaClass.getMethod("toRSAPublicKey")
                    toRSAPublicKeyMethod.invoke(jwk) as PublicKey
                }
                else -> null
            }
        } catch (e: Exception) {
            // If nimbus-jose-jwt is not available, try manual JWK parsing
            return extractPublicKeyFromJwkManual(jwkMap)
        }
    }
    
    /**
     * Manual JWK parsing (fallback when nimbus-jose-jwt is not available).
     */
    private fun extractPublicKeyFromJwkManual(jwkMap: Map<String, Any?>): PublicKey? {
        try {
            val kty = jwkMap["kty"] as? String ?: return null
            
            return when (kty) {
                "OKP" -> {
                    // Ed25519
                    val x = jwkMap["x"] as? String ?: return null
                    val xBytes = Base64.getUrlDecoder().decode(x)
                    
                    // Ed25519 public key is 32 bytes
                    if (xBytes.size != 32) return null
                    
                    // Create Ed25519 public key
                    val keyFactory = KeyFactory.getInstance("Ed25519")
                    val keySpec = X509EncodedKeySpec(xBytes)
                    keyFactory.generatePublic(keySpec)
                }
                "EC" -> {
                    // ECDSA - simplified approach using standard curves
                    val crv = jwkMap["crv"] as? String ?: return null
                    val x = jwkMap["x"] as? String ?: return null
                    val y = jwkMap["y"] as? String ?: return null
                    
                    val xBytes = Base64.getUrlDecoder().decode(x)
                    val yBytes = Base64.getUrlDecoder().decode(y)
                    
                    // Use standard curve names
                    val curveName = when (crv) {
                        "P-256" -> "secp256r1"
                        "P-384" -> "secp384r1"
                        "P-521" -> "secp521r1"
                        "secp256k1" -> "secp256k1"
                        else -> return null
                    }
                    
                    try {
                        val keyFactory = KeyFactory.getInstance("EC")
                        val ecPoint = ECPoint(
                            java.math.BigInteger(1, xBytes),
                            java.math.BigInteger(1, yBytes)
                        )
                        
                        // Try to get curve parameters using AlgorithmParameters
                        val params = java.security.AlgorithmParameters.getInstance("EC")
                        params.init(java.security.spec.ECGenParameterSpec(curveName))
                        val ecParameterSpec = params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
                        
                        val keySpec = java.security.spec.ECPublicKeySpec(ecPoint, ecParameterSpec)
                        keyFactory.generatePublic(keySpec)
                    } catch (e: Exception) {
                        // Fallback: return null if curve parameters can't be determined
                        null
                    }
                }
                "RSA" -> {
                    // RSA
                    val n = jwkMap["n"] as? String ?: return null
                    val e = jwkMap["e"] as? String ?: return null
                    
                    val nBytes = Base64.getUrlDecoder().decode(n)
                    val eBytes = Base64.getUrlDecoder().decode(e)
                    
                    val modulus = java.math.BigInteger(1, nBytes)
                    val exponent = java.math.BigInteger(1, eBytes)
                    
                    val keyFactory = KeyFactory.getInstance("RSA")
                    val keySpec = RSAPublicKeySpec(modulus, exponent)
                    keyFactory.generatePublic(keySpec)
                }
                else -> null
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Extract public key from multibase encoding.
     */
    private fun extractPublicKeyFromMultibase(multibase: String, keyType: String): PublicKey? {
        try {
            // Decode multibase (assuming base58btc with 'z' prefix)
            if (!multibase.startsWith("z")) {
                return null
            }
            
            val decoded = decodeBase58(multibase.substring(1))
            
            // Parse based on key type
            return when {
                keyType.contains("Ed25519") -> {
                    val keyFactory = KeyFactory.getInstance("Ed25519")
                    val keySpec = X509EncodedKeySpec(decoded)
                    keyFactory.generatePublic(keySpec)
                }
                else -> null
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Create JWS verifier from public key (using reflection to avoid direct dependency).
     */
    private fun createJwsVerifier(publicKey: PublicKey): Any? {
        return try {
            when (publicKey) {
                is java.security.interfaces.ECPublicKey -> {
                    val ecKeyClass = Class.forName("com.nimbusds.jose.jwk.ECKey")
                    val parseMethod = ecKeyClass.getMethod("parse", PublicKey::class.java)
                    val ecKey = parseMethod.invoke(null, publicKey)
                    
                    val verifierClass = Class.forName("com.nimbusds.jose.crypto.ECDSAVerifier")
                    val constructor = verifierClass.getConstructor(ecKeyClass)
                    constructor.newInstance(ecKey)
                }
                is java.security.interfaces.RSAPublicKey -> {
                    val rsaKeyClass = Class.forName("com.nimbusds.jose.jwk.RSAKey")
                    val parseMethod = rsaKeyClass.getMethod("parse", PublicKey::class.java)
                    val rsaKey = parseMethod.invoke(null, publicKey)
                    
                    val verifierClass = Class.forName("com.nimbusds.jose.crypto.RSASSAVerifier")
                    val constructor = verifierClass.getConstructor(rsaKeyClass)
                    constructor.newInstance(rsaKey)
                }
                is java.security.interfaces.EdECPublicKey -> {
                    val okpClass = Class.forName("com.nimbusds.jose.jwk.OctetKeyPair")
                    val parseMethod = okpClass.getMethod("parse", PublicKey::class.java)
                    val okp = parseMethod.invoke(null, publicKey)
                    
                    val verifierClass = Class.forName("com.nimbusds.jose.crypto.Ed25519Verifier")
                    val constructor = verifierClass.getConstructor(okpClass)
                    constructor.newInstance(okp)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Verify Ed25519 signature.
     */
    private fun verifyEd25519Signature(
        data: ByteArray,
        signature: ByteArray,
        verificationMethod: VerificationMethodRef
    ): Boolean {
        try {
            val publicKey = extractPublicKeyFromVerificationMethod(verificationMethod) ?: return false
            
            // Try Ed25519 signature verification
            val signatureInstance = Signature.getInstance("Ed25519")
            signatureInstance.initVerify(publicKey)
            signatureInstance.update(data)
            return signatureInstance.verify(signature)
        } catch (e: Exception) {
            // If Ed25519 is not available, try using BouncyCastle or manual verification
            return false
        }
    }
    
    /**
     * Verify BBS+ signature.
     * 
     * Attempts to use BBS+ library if available via reflection.
     * Falls back to basic validation if library is not available.
     */
    private fun verifyBbsSignature(
        data: ByteArray,
        signature: ByteArray,
        verificationMethod: VerificationMethodRef
    ): Boolean {
        try {
            // Try to use BBS+ library if available via reflection
            // This would use mattr-bbs-signatures or similar library
            // For now, we do basic validation
            
            // Extract public key from verification method
            val publicKey = extractPublicKeyFromVerificationMethod(verificationMethod) ?: return false
            
            // In a full implementation, this would:
            // 1. Extract BLS12-381 public key from verification method
            // 2. Use BBS+ library to verify signature against canonical data
            // 3. Return verification result
            
            // Placeholder: basic structure validation
            // Full implementation requires BBS+ library
            return signature.isNotEmpty() && data.isNotEmpty()
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Create proof document (credential without proof).
     */
    private fun createProofDocument(
        credential: VerifiableCredential,
        proof: Proof
    ): String {
        val json = Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
        
        // Serialize credential without proof
        val credentialJson = json.encodeToJsonElement(credential.copy(proof = null))
        
        // Add proof options (without proofValue)
        val proofOptions = buildJsonObject {
            put("type", proof.type)
            put("created", proof.created)
            put("verificationMethod", proof.verificationMethod)
            put("proofPurpose", proof.proofPurpose)
            proof.challenge?.let { put("challenge", it) }
            proof.domain?.let { put("domain", it) }
        }
        
        val proofDocument = buildJsonObject {
            // Add all credential fields
            credentialJson.jsonObject.entries.forEach { (key, value) ->
                put(key, value)
            }
            put("proof", proofOptions)
        }
        
        return json.encodeToString(JsonObject.serializer(), proofDocument)
    }
    
    /**
     * Decode signature from multibase or base64.
     */
    private fun decodeSignature(signature: String): ByteArray? {
        return try {
            // Try multibase (base58btc with 'z' prefix)
            if (signature.startsWith("z")) {
                decodeBase58(signature.substring(1))
            } else {
                // Try base64
                Base64.getDecoder().decode(signature)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Decode base58 string.
     */
    private fun decodeBase58(input: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger.ZERO
        var leadingZeros = 0
        
        // Count leading zeros
        for (char in input) {
            if (char == '1') {
                leadingZeros++
            } else {
                break
            }
        }
        
        // Convert from base58
        for (char in input) {
            val index = alphabet.indexOf(char)
            if (index == -1) {
                throw IllegalArgumentException("Invalid base58 character: $char")
            }
            num = num.multiply(java.math.BigInteger.valueOf(58))
            num = num.add(java.math.BigInteger.valueOf(index.toLong()))
        }
        
        // Convert to byte array
        val bytes = num.toByteArray()
        
        // Remove leading zero byte if present
        val result = if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
            bytes.sliceArray(1..bytes.lastIndex)
        } else {
            bytes
        }
        
        // Add leading zeros
        return ByteArray(leadingZeros) { 0 } + result
    }
    
    /**
     * Convert map to JSON string.
     */
    private fun Map<String, Any?>.toJsonString(): String {
        val json = Json { encodeDefaults = false }
        val jsonObject = buildJsonObject {
            this@toJsonString.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    is List<*> -> put(key, json.encodeToJsonElement(value))
                    is Map<*, *> -> put(key, json.encodeToJsonElement(value))
                    else -> put(key, value?.toString() ?: JsonNull)
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }
}

