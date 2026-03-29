package org.trustweave.keydid

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            val algorithm = options.algorithm.algorithmName
            val keyHandle = generateKey(algorithm, options.additionalProperties)

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
                authentication = listOf(verificationMethod.id.value),
                assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id.value)
                } else null
            )

            // Store locally (did:key documents are derived, not stored externally)
            storeDocument(document.id.value, document)

            document
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to create did:key: ${e.message ?: "Unknown error"}",
                context = mapOf("method" to "key"),
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            // Extract multibase-encoded public key from DID
            val multibaseEncoded = did.value.substringAfter("did:key:")

            // Decode multibase to get prefixed key
            val prefixedKey = try {
                decodeMultibase(multibaseEncoded)
            } catch (e: Exception) {
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "invalidDid",
                    "Invalid multibase encoding: ${e.message}",
                    method,
                    did.value
                )
            }

            // Extract multicodec prefix and algorithm
            val (algorithm, publicKeyBytes) = parseMulticodecPrefixedKey(prefixedKey)
                ?: return@withContext DidMethodUtils.createErrorResolutionResult(
                    "invalidDid",
                    "Unsupported multicodec prefix",
                    method,
                    did.value
                )

            // Check local storage first (cache hit for keys this instance created)
            val stored = getStoredDocument(did)
            if (stored != null) {
                return@withContext DidMethodUtils.createSuccessResolutionResult(
                    stored,
                    method,
                    getDocumentMetadata(did)?.created,
                    getDocumentMetadata(did)?.updated
                )
            }

            // did:key is self-certifying: reconstruct the document deterministically from
            // the public key bytes encoded in the DID itself. No external registry required.
            val vmType = DidMethodUtils.algorithmToVerificationMethodType(algorithm)
            val didStr = did.value
            val didObj = Did(didStr)
            val vmIdStr = "$didStr#$multibaseEncoded"
            val vmId = VerificationMethodId.parse(vmIdStr, didObj)

            val verificationMethod = VerificationMethod(
                id = vmId,
                type = vmType,
                controller = didObj,
                publicKeyMultibase = multibaseEncoded,
                publicKeyJwk = buildJwkFromBytes(algorithm, publicKeyBytes)
            )

            val document = DidMethodUtils.buildDidDocument(
                did = didStr,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(vmIdStr),
                assertionMethod = listOf(vmIdStr)
            )

            // Cache the derived document
            storeDocument(didStr, document)

            DidMethodUtils.createSuccessResolutionResult(document, method)
        } catch (e: TrustWeaveException) {
            DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method,
                did.value
            )
        } catch (e: Exception) {
            DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method,
                did.value
            )
        }
    }

    /**
     * Gets public key bytes from a key handle.
     */
    private fun getPublicKeyBytes(keyHandle: org.trustweave.kms.KeyHandle, algorithm: String): ByteArray {
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
                        throw org.trustweave.core.exception.TrustWeaveException.ValidationFailed(
                            field = "jwk.x",
                            reason = "Missing 'x' field in Ed25519 JWK",
                            value = null
                        )
                    }
                }
                "SECP256K1", "P-256", "P-384", "P-521" -> {
                    // EC public key: combine x and y coordinates
                    if (x != null && y != null) {
                        val xBytes = java.util.Base64.getUrlDecoder().decode(x)
                        val yBytes = java.util.Base64.getUrlDecoder().decode(y)
                        byteArrayOf(0x04) + xBytes + yBytes // 0x04 = uncompressed point
                    } else {
                        throw org.trustweave.core.exception.TrustWeaveException.ValidationFailed(
                            field = "jwk.x/y",
                            reason = "Missing 'x' or 'y' field in EC JWK",
                            value = null
                        )
                    }
                }
                else -> throw org.trustweave.core.exception.TrustWeaveException.UnsupportedAlgorithm(
                    algorithm = algorithm,
                    supportedAlgorithms = listOf("ED25519", "SECP256K1", "P-256", "P-384", "P-521")
                )
            }
        }

        throw org.trustweave.core.exception.TrustWeaveException.ValidationFailed(
            field = "keyHandle",
            reason = "KeyHandle must have either publicKeyMultibase or publicKeyJwk",
            value = null
        )
    }

    /**
     * Gets multicodec prefix for an algorithm.
     *
     * See: https://github.com/multiformats/multicodec/blob/master/table.csv
     */
    private fun getMulticodecPrefix(algorithm: String): ByteArray =
        DidMethodUtils.getMulticodecPrefix(algorithm)

    private fun parseMulticodecPrefixedKey(prefixedKey: ByteArray): Pair<String, ByteArray>? =
        DidMethodUtils.parseMulticodecKey(prefixedKey)

    /**
     * Builds a JWK map from raw public key bytes and algorithm name.
     * Returns null for unsupported algorithms (publicKeyMultibase is the primary representation).
     */
    private fun buildJwkFromBytes(algorithm: String, publicKeyBytes: ByteArray): Map<String, Any?>? {
        val b64url = java.util.Base64.getUrlEncoder().withoutPadding()
        return when (algorithm.uppercase()) {
            "ED25519" -> mapOf(
                "kty" to "OKP",
                "crv" to "Ed25519",
                "x" to b64url.encodeToString(publicKeyBytes)
            )
            "SECP256K1" -> {
                // Expect uncompressed point: 0x04 || x(32) || y(32)
                if (publicKeyBytes.size >= 65 && publicKeyBytes[0] == 0x04.toByte()) {
                    mapOf(
                        "kty" to "EC",
                        "crv" to "secp256k1",
                        "x" to b64url.encodeToString(publicKeyBytes.sliceArray(1..32)),
                        "y" to b64url.encodeToString(publicKeyBytes.sliceArray(33..64))
                    )
                } else null
            }
            "P-256" -> {
                if (publicKeyBytes.size >= 65 && publicKeyBytes[0] == 0x04.toByte()) {
                    mapOf(
                        "kty" to "EC",
                        "crv" to "P-256",
                        "x" to b64url.encodeToString(publicKeyBytes.sliceArray(1..32)),
                        "y" to b64url.encodeToString(publicKeyBytes.sliceArray(33..64))
                    )
                } else null
            }
            else -> null
        }
    }

    private fun encodeMultibase(bytes: ByteArray): String = "z${DidMethodUtils.encodeBase58(bytes)}"

    private fun decodeMultibase(encoded: String): ByteArray {
        require(encoded.isNotEmpty()) { "Empty multibase string" }
        return when (val prefix = encoded[0]) {
            'z' -> DidMethodUtils.decodeBase58(encoded.substring(1))
            else -> throw IllegalArgumentException("Unsupported multibase prefix: $prefix")
        }
    }
}

