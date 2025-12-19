package com.trustweave.jwkdid

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.did.identifiers.Did
import VerificationMethodId
import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.VerificationMethod
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.base.AbstractDidMethod
import com.trustweave.did.base.DidMethodUtils
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.results.GenerateKeyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Base64

/**
 * Implementation of did:jwk method.
 *
 * did:jwk uses JSON Web Keys (JWK) directly:
 * - Format: `did:jwk:{base64url-encoded-jwk}`
 * - Public key is encoded as a JWK and then base64url-encoded
 * - Document is derived from the JWK itself
 * - No external registry required
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val method = JwkDidMethod(kms)
 *
 * // Create DID
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.ED25519
 * }
 * val document = method.createDid(options)
 *
 * // Resolve DID (derived from JWK)
 * val result = method.resolveDid(document.id)
 * ```
 */
class JwkDidMethod(
    kms: KeyManagementService
) : AbstractDidMethod("jwk", kms) {

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            // Generate key using KMS
            val algorithm = options.algorithm.algorithmName
            val generateResult = kms.generateKey(algorithm, options.additionalProperties)
            val keyHandle = when (generateResult) {
                is GenerateKeyResult.Success -> generateResult.keyHandle
                is GenerateKeyResult.Failure.UnsupportedAlgorithm -> throw TrustWeaveException.Unknown(
                    code = "UNSUPPORTED_ALGORITHM",
                    message = generateResult.reason ?: "Algorithm not supported"
                )
                is GenerateKeyResult.Failure.InvalidOptions -> throw TrustWeaveException.Unknown(
                    code = "INVALID_OPTIONS",
                    message = generateResult.reason
                )
                is GenerateKeyResult.Failure.Error -> throw TrustWeaveException.Unknown(
                    code = "KEY_GENERATION_ERROR",
                    message = generateResult.reason,
                    cause = generateResult.cause
                )
            }

            // Get JWK from key handle
            val jwk = keyHandle.publicKeyJwk
                ?: throw TrustWeaveException.Unknown(
                    code = "MISSING_JWK",
                    message = "KeyHandle must have publicKeyJwk for did:jwk"
                )

            // Normalize JWK (remove private key fields, sort keys)
            val normalizedJwk = normalizeJwk(jwk)

            // Convert JWK to JSON string
            val jwkJson = buildJsonObject {
                normalizedJwk.forEach { (key, value) ->
                    when (value) {
                        null -> put(key, JsonNull)
                        is String -> put(key, value)
                        is Number -> put(key, value)
                        is Boolean -> put(key, value)
                        is Map<*, *> -> put(key, buildJsonObject {
                            (value as Map<*, *>).forEach { (k, v) ->
                                when (v) {
                                    is String -> put(k.toString(), v)
                                    is Number -> put(k.toString(), v)
                                    is Boolean -> put(k.toString(), v)
                                    else -> put(k.toString(), v.toString())
                                }
                            }
                        })
                        else -> put(key, value.toString())
                    }
                }
            }

            val jwkString = Json.encodeToString(JsonElement.serializer(), jwkJson)

            // Encode JWK as base64url (no padding)
            val base64urlEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                jwkString.toByteArray(Charsets.UTF_8)
            )

            // Create did:jwk identifier
            val did = "did:jwk:$base64urlEncoded"

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

            // Store locally (did:jwk documents are derived, not stored externally)
            storeDocument(document.id, document)

            document
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "CREATE_FAILED",
                message = "Failed to create did:jwk: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value
            // Extract base64url-encoded JWK from DID
            val base64urlEncoded = didString.substringAfter("did:jwk:")

            // Decode base64url to get JWK JSON string
            val jwkString = try {
                String(Base64.getUrlDecoder().decode(base64urlEncoded), Charsets.UTF_8)
            } catch (e: Exception) {
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "invalidDid",
                    "Invalid base64url encoding: ${e.message}",
                    method
                )
            }

            // Parse JWK JSON
            val jwkJson = try {
                Json.parseToJsonElement(jwkString).jsonObject
            } catch (e: Exception) {
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "invalidDid",
                    "Invalid JWK JSON: ${e.message}",
                    method
                )
            }

            // Convert JSON to map
            val jwk = jwkJson.entries.associate { entry ->
                entry.key to when (val value = entry.value) {
                    is JsonPrimitive -> {
                        when {
                            value.isString -> value.content
                            value.booleanOrNull != null -> value.boolean
                            value.longOrNull != null -> value.long
                            value.doubleOrNull != null -> value.double
                            else -> value.content
                        }
                    }
                    is JsonNull -> null
                    else -> value.toString()
                }
            }

            // Validate JWK has required fields
            if (!jwk.containsKey("kty")) {
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "invalidDid",
                    "JWK missing 'kty' (key type) field",
                    method
                )
            }

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

            // For did:jwk, we can derive the document from the JWK
            // Build a minimal DID document from the JWK
            val verificationMethodId = "$didString#0"
            val verificationMethodType = when (jwk["kty"]) {
                "OKP" -> when (jwk["crv"]) {
                    "Ed25519" -> "Ed25519VerificationKey2020"
                    else -> "JsonWebKey2020"
                }
                "EC" -> "EcdsaSecp256k1VerificationKey2019"
                "RSA" -> "RsaVerificationKey2018"
                else -> "JsonWebKey2020"
            }

            val verificationMethod = VerificationMethod(
                id = VerificationMethodId.parse(verificationMethodId, Did(didString)),
                type = verificationMethodType,
                controller = Did(didString),
                publicKeyJwk = jwk as Map<String, Any?>
            )

            val document = DidMethodUtils.buildDidDocument(
                did = didString,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(verificationMethodId),
                assertionMethod = listOf(verificationMethodId)
            )

            // Store for caching
            storeDocument(document.id.value, document)

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
     * Normalizes a JWK by removing private key fields and ensuring required fields are present.
     */
    private fun normalizeJwk(jwk: Map<String, Any?>): Map<String, Any?> {
        // Private key fields to remove
        val privateKeyFields = setOf("d", "p", "q", "dp", "dq", "qi", "oth")

        return jwk.filterKeys { it !in privateKeyFields }
    }
}

