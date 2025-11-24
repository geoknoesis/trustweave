package com.trustweave.keydid

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.did.base.AbstractDidMethod
import com.trustweave.did.base.DidMethodUtils
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.math.BigInteger

/**
 * Native implementation of did:key method.
 * 
 * did:key is the simplest DID method - no external registry required:
 * - Format: `did:key:{multibase-encoded-public-key}`
 * - Public key is encoded using multibase (base58btc with 'z' prefix)
 * - Document is derived from the public key itself
 * - No external resolution needed
 * 
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val method = KeyDidMethod(kms)
 * 
 * // Create DID
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.ED25519
 * }
 * val document = method.createDid(options)
 * 
 * // Resolve DID (derived from public key)
 * val result = method.resolveDid(document.id)
 * ```
 */
class KeyDidMethod(
    kms: KeyManagementService
) : AbstractDidMethod("key", kms) {

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            // Generate key using KMS
            val algorithm = options.algorithm.algorithmName
            val keyHandle = kms.generateKey(algorithm, options.additionalProperties)
            
            // Get public key bytes
            val publicKeyBytes = getPublicKeyBytes(keyHandle, algorithm)
            
            // Create multicodec prefix based on algorithm
            val multicodecPrefix = getMulticodecPrefix(algorithm)
            
            // Combine prefix + public key
            val prefixedKey = multicodecPrefix + publicKeyBytes
            
            // Encode as multibase (base58btc with 'z' prefix)
            val multibaseEncoded = encodeMultibase(prefixedKey)
            
            // Create did:key identifier
            val did = "did:key:$multibaseEncoded"
            
            // Create verification method
            val verificationMethod = DidMethodUtils.createVerificationMethod(
                did = did,
                keyHandle = keyHandle,
                algorithm = options.algorithm
            )
            
            // Build DID document
            val document = DidMethodUtils.buildDidDocument(
                did = did,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(verificationMethod.id),
                assertionMethod = if (options.purposes.contains(DidCreationOptions.KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id)
                } else null
            )
            
            // Store locally (did:key documents are derived, not stored externally)
            storeDocument(document.id, document)
            
            document
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException(
                "Failed to create did:key: ${e.message}",
                e
            )
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Extract multibase-encoded public key from DID
            val multibaseEncoded = did.substringAfter("did:key:")
            
            // Decode multibase to get prefixed key
            val prefixedKey = try {
                decodeMultibase(multibaseEncoded)
            } catch (e: Exception) {
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "invalidDid",
                    "Invalid multibase encoding: ${e.message}",
                    method
                )
            }
            
            // Extract multicodec prefix and algorithm
            val (algorithm, publicKeyBytes) = parseMulticodecPrefixedKey(prefixedKey)
                ?: return@withContext DidMethodUtils.createErrorResolutionResult(
                    "invalidDid",
                    "Unsupported multicodec prefix",
                    method
                )
            
            // Check local storage first (for keys we generated)
            val stored = getStoredDocument(did)
            if (stored != null) {
                return@withContext DidMethodUtils.createSuccessResolutionResult(
                    stored,
                    method,
                    getDocumentMetadata(did)?.created,
                    getDocumentMetadata(did)?.updated
                )
            }
            
            // For did:key, we derive the document from the public key
            // In a full implementation, we'd reconstruct the KeyHandle from the public key bytes
            // For now, return not found if not in storage (as we don't have the private key)
            DidMethodUtils.createErrorResolutionResult(
                "notFound",
                "DID document not found. did:key documents are typically derived from public keys.",
                method
            )
        } catch (e: TrustWeaveException) {
            DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method
            )
        } catch (e: Exception) {
            DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method
            )
        }
    }

    /**
     * Gets public key bytes from a key handle.
     */
    private fun getPublicKeyBytes(keyHandle: com.trustweave.kms.KeyHandle, algorithm: String): ByteArray {
        // Try multibase first
        val multibase = keyHandle.publicKeyMultibase
        if (multibase != null && multibase.startsWith("z")) {
            return decodeMultibase(multibase)
        }
        
        // Try JWK format
        val jwk = keyHandle.publicKeyJwk
        if (jwk != null) {
            // Extract public key from JWK
            // This is simplified - in production, use a proper JWK library
            val x = jwk["x"] as? String
            val y = jwk["y"] as? String
            val n = jwk["n"] as? String
            val e = jwk["e"] as? String
            
            return when (algorithm.uppercase()) {
                "ED25519" -> {
                    // Ed25519 public key from JWK 'x' field (base64url)
                    if (x != null) {
                        java.util.Base64.getUrlDecoder().decode(x)
                    } else {
                        throw TrustWeaveException("Missing 'x' field in Ed25519 JWK")
                    }
                }
                "SECP256K1", "P-256", "P-384", "P-521" -> {
                    // EC public key: combine x and y coordinates
                    if (x != null && y != null) {
                        val xBytes = java.util.Base64.getUrlDecoder().decode(x)
                        val yBytes = java.util.Base64.getUrlDecoder().decode(y)
                        byteArrayOf(0x04) + xBytes + yBytes // 0x04 = uncompressed point
                    } else {
                        throw TrustWeaveException("Missing 'x' or 'y' field in EC JWK")
                    }
                }
                else -> throw TrustWeaveException("Unsupported algorithm for public key extraction: $algorithm")
            }
        }
        
        throw TrustWeaveException("KeyHandle must have either publicKeyMultibase or publicKeyJwk")
    }

    /**
     * Gets multicodec prefix for an algorithm.
     * 
     * See: https://github.com/multiformats/multicodec/blob/master/table.csv
     */
    private fun getMulticodecPrefix(algorithm: String): ByteArray {
        return when (algorithm.uppercase()) {
            "ED25519" -> byteArrayOf(0xed.toByte(), 0x01) // 0xed01 = Ed25519 public key
            "SECP256K1" -> byteArrayOf(0xe7.toByte(), 0x01) // 0xe701 = secp256k1 public key
            "P-256" -> byteArrayOf(0x80.toByte(), 0x24) // 0x8024 = P-256 public key
            "P-384" -> byteArrayOf(0x81.toByte(), 0x24) // 0x8124 = P-384 public key
            "P-521" -> byteArrayOf(0x82.toByte(), 0x24) // 0x8224 = P-521 public key
            else -> throw IllegalArgumentException("Unsupported algorithm for did:key: $algorithm")
        }
    }

    /**
     * Parses multicodec-prefixed key to extract algorithm and public key bytes.
     */
    private fun parseMulticodecPrefixedKey(prefixedKey: ByteArray): Pair<String, ByteArray>? {
        if (prefixedKey.size < 2) return null
        
        val prefixByte1 = prefixedKey[0].toInt() and 0xFF
        val prefixByte2 = prefixedKey[1].toInt() and 0xFF
        
        val algorithm = when {
            prefixByte1 == 0xed && prefixByte2 == 0x01 -> "ED25519"
            prefixByte1 == 0xe7 && prefixByte2 == 0x01 -> "SECP256K1"
            prefixByte1 == 0x80 && prefixByte2 == 0x24 -> "P-256"
            prefixByte1 == 0x81 && prefixByte2 == 0x24 -> "P-384"
            prefixByte1 == 0x82 && prefixByte2 == 0x24 -> "P-521"
            else -> return null
        }
        
        val publicKeyBytes = prefixedKey.sliceArray(2 until prefixedKey.size)
        return algorithm to publicKeyBytes
    }

    /**
     * Encodes bytes as multibase (base58btc with 'z' prefix).
     */
    private fun encodeMultibase(bytes: ByteArray): String {
        val base58 = encodeBase58(bytes)
        return "z$base58" // 'z' prefix indicates base58btc
    }

    /**
     * Decodes multibase-encoded string to bytes.
     */
    private fun decodeMultibase(encoded: String): ByteArray {
        if (encoded.isEmpty()) {
            throw IllegalArgumentException("Empty multibase string")
        }
        
        val prefix = encoded[0]
        val data = encoded.substring(1)
        
        return when (prefix) {
            'z' -> decodeBase58(data) // base58btc
            else -> throw IllegalArgumentException("Unsupported multibase prefix: $prefix")
        }
    }

    /**
     * Encodes bytes as base58.
     */
    private fun encodeBase58(bytes: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger(1, bytes)
        val sb = StringBuilder()
        
        while (num > BigInteger.ZERO) {
            val remainder = num.mod(BigInteger.valueOf(58))
            sb.append(alphabet[remainder.toInt()])
            num = num.divide(BigInteger.valueOf(58))
        }
        
        // Add leading zeros
        for (byte in bytes) {
            if (byte.toInt() == 0) {
                sb.append('1')
            } else {
                break
            }
        }
        
        return sb.reverse().toString()
    }

    /**
     * Decodes base58 string to bytes.
     */
    private fun decodeBase58(encoded: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger.ZERO
        var leadingZeros = 0
        
        // Count leading zeros
        for (char in encoded) {
            if (char == '1') {
                leadingZeros++
            } else {
                break
            }
        }
        
        // Decode base58
        for (char in encoded) {
            val digit = alphabet.indexOf(char)
            require(digit >= 0) { "Invalid base58 character: $char" }
            num = num.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit.toLong()))
        }
        
        // Convert to bytes
        var bytes = num.toByteArray()
        
        // Remove leading zero byte if it exists (BigInteger adds one)
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes = bytes.sliceArray(1 until bytes.size)
        }
        
        // Add leading zeros back
        return ByteArray(leadingZeros) { 0 } + bytes
    }
}

