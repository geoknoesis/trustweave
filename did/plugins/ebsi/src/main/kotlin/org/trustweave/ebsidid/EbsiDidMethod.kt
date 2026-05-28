package org.trustweave.ebsidid

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyPurpose
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Implementation of the did:ebsi DID method.
 *
 * The EBSI DID method stores DID documents in the EU Blockchain Services Infrastructure (EBSI)
 * DID Registry. The DID identifier is derived from the first 16 bytes of the SHA-256 hash of the
 * P-256 public key, base58btc-encoded.
 *
 * Format: `did:ebsi:<base58btc-16-bytes>`
 *
 * **Supported operations:**
 * - Resolve: always available (queries the EBSI DID Registry REST API, falls back to in-memory).
 * - Create / Update / Deactivate: requires a `bearerToken` in [EbsiDidConfig].
 *
 * **Example:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = EbsiDidConfig.pilot(bearerToken = "eyJ...")
 * val method = EbsiDidMethod(kms, config)
 *
 * val document = method.createDid(didCreationOptions { algorithm = KeyAlgorithm.P256 })
 * val result   = method.resolveDid(document.id)
 * ```
 */
class EbsiDidMethod(
    kms: KeyManagementService,
    private val config: EbsiDidConfig,
    httpClient: OkHttpClient? = null,
) : AbstractDidMethod("ebsi", kms) {

    private val client: OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .build()

    // ──────────────────────────────────────────────────────────────────────────────
    // DID identifier derivation
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Derives the did:ebsi identifier from a public key JWK map.
     *
     * Algorithm: SHA-256 of the canonical public-key bytes → first 16 bytes → base58btc.
     * For P-256 keys the "canonical bytes" is the concatenation of the x and y coordinates
     * decoded from the JWK.  For other key types the raw encoded bytes of the key-id are used
     * as a fallback so that the method remains usable in tests with non-P-256 keys.
     *
     * @param publicKeyJwk Public key in JWK format (from [org.trustweave.kms.KeyHandle]).
     * @param keyIdFallback Raw fallback bytes when JWK coordinates are unavailable.
     */
    internal fun deriveEbsiIdentifier(
        publicKeyJwk: Map<String, Any?>?,
        keyIdFallback: ByteArray,
    ): String {
        val keyBytes: ByteArray = if (publicKeyJwk != null) {
            val xB64 = publicKeyJwk["x"] as? String
            val yB64 = publicKeyJwk["y"] as? String
            if (xB64 != null && yB64 != null) {
                val x = java.util.Base64.getUrlDecoder().decode(xB64)
                val y = java.util.Base64.getUrlDecoder().decode(yB64)
                x + y
            } else {
                // OKP key (Ed25519) or unknown — use raw x bytes if available
                val xB64Only = publicKeyJwk["x"] as? String
                xB64Only?.let { java.util.Base64.getUrlDecoder().decode(it) } ?: keyIdFallback
            }
        } else {
            keyIdFallback
        }

        val hash = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        val first16 = hash.sliceArray(0 until 16)
        return "did:ebsi:${encodeBase58(first16)}"
    }

    /**
     * Base58btc-encodes [bytes] using the Bitcoin alphabet.
     */
    private fun encodeBase58(bytes: ByteArray): String {
        var num = BigInteger(1, bytes)
        val sb = StringBuilder()
        val base = BigInteger.valueOf(58)
        while (num > BigInteger.ZERO) {
            val rem = num.mod(base)
            sb.append(BASE58_ALPHABET[rem.toInt()])
            num = num.divide(base)
        }
        // Preserve leading zero bytes as '1' characters
        for (b in bytes) {
            if (b.toInt() == 0) sb.append('1') else break
        }
        return sb.reverse().toString()
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // DidMethod implementation
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new did:ebsi DID.
     *
     * 1. Generates (or reuses) a key via the KMS — defaults to P-256 as required by EBSI.
     * 2. Derives the DID identifier from the public key.
     * 3. Builds a minimal DID document.
     * 4. If [EbsiDidConfig.bearerToken] is set, registers the DID on the EBSI registry.
     * 5. Caches the document in-memory for fallback resolution.
     *
     * Callers may pass an existing key ID via `options.additionalProperties["keyId"]` to reuse a
     * key that was already generated in the KMS instead of creating a new one.
     */
    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            val algorithm = options.algorithm.algorithmName
            val keyHandle = generateKey(algorithm, options.additionalProperties)

            val did = deriveEbsiIdentifier(
                publicKeyJwk = keyHandle.publicKeyJwk,
                keyIdFallback = keyHandle.id.value.toByteArray(),
            )

            val verificationMethod = DidMethodUtils.createVerificationMethod(
                did = did,
                keyHandle = keyHandle,
                algorithm = options.algorithm,
            )

            val document = DidMethodUtils.buildDidDocument(
                did = did,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(verificationMethod.id.value),
                assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id.value)
                } else {
                    null
                },
            )

            // Register on EBSI if a bearer token is configured
            if (config.bearerToken != null) {
                registerOnEbsi(document)
            }

            // Cache locally for fallback resolution
            storeDocument(did, document)

            document
        } catch (e: EbsiException) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "EBSI_CREATE_FAILED",
                message = "Failed to create did:ebsi: ${e.message}",
                cause = e,
            )
        }
    }

    /**
     * Resolves a did:ebsi DID.
     *
     * Resolution order:
     * 1. Queries the EBSI DID Registry REST API.
     * 2. On 404, returns the locally cached document (for DIDs created in this session).
     * 3. On network error, falls back to the locally cached document.
     */
    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
        } catch (e: Exception) {
            return@withContext DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method,
                did.value,
            )
        }

        // Try EBSI registry first
        val apiResult = runCatching { resolveFromEbsiApi(did.value) }

        when {
            apiResult.isSuccess && apiResult.getOrNull() != null -> {
                val document = requireNotNull(apiResult.getOrNull())
                storeDocument(did, document)
                DidMethodUtils.createSuccessResolutionResult(
                    document,
                    method,
                    getDocumentMetadata(did)?.created,
                    getDocumentMetadata(did)?.updated,
                )
            }

            else -> {
                // Check if the API returned a "not found" (null) or threw an error
                val stored = getStoredDocument(did)
                if (stored != null) {
                    DidMethodUtils.createSuccessResolutionResult(
                        stored,
                        method,
                        getDocumentMetadata(did)?.created,
                        getDocumentMetadata(did)?.updated,
                    )
                } else {
                    val cause = apiResult.exceptionOrNull()
                    if (cause is EbsiException && cause.httpStatus == 404) {
                        DidMethodUtils.createErrorResolutionResult(
                            "notFound",
                            "DID document not found on EBSI: ${did.value}",
                            method,
                            did.value,
                        )
                    } else {
                        DidMethodUtils.createErrorResolutionResult(
                            "notFound",
                            cause?.message ?: "DID document not found: ${did.value}",
                            method,
                            did.value,
                        )
                    }
                }
            }
        }
    }

    /**
     * Updates a did:ebsi DID document.
     *
     * Resolves the current document, applies [updater], then PATCHes the EBSI registry
     * (when a bearer token is configured) and updates the in-memory cache.
     */
    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val currentResult = resolveDid(did)
            val currentDocument = when (currentResult) {
                is DidResolutionResult.Success -> currentResult.document
                else -> throw TrustWeaveException.NotFound(
                    resource = did.value,
                    message = "DID document not found: ${did.value}",
                )
            }

            val updatedDocument = updater(currentDocument)

            if (config.bearerToken != null) {
                patchOnEbsi(did.value, updatedDocument)
            }

            storeDocument(did, updatedDocument)
            updatedDocument
        } catch (e: EbsiException) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "EBSI_UPDATE_FAILED",
                message = "Failed to update did:ebsi: ${e.message}",
                cause = e,
            )
        }
    }

    /**
     * Deactivates a did:ebsi DID.
     *
     * If a bearer token is configured, sends a PATCH to the EBSI registry marking the DID
     * as deactivated (empty verification methods), then removes the local cache entry.
     */
    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            if (config.bearerToken != null) {
                val currentResult = resolveDid(did)
                if (currentResult is DidResolutionResult.Success) {
                    val deactivated = currentResult.document.copy(
                        verificationMethod = emptyList(),
                        authentication = emptyList(),
                        assertionMethod = emptyList(),
                        keyAgreement = emptyList(),
                        capabilityInvocation = emptyList(),
                        capabilityDelegation = emptyList(),
                    )
                    patchOnEbsi(did.value, deactivated)
                }
            }

            // Remove from local cache regardless
            val wasPresent = getStoredDocument(did) != null
            documents.remove(did.value)
            documentMetadata.remove(did.value)
            wasPresent
        } catch (e: EbsiException) {
            throw e
        } catch (e: TrustWeaveException) {
            false
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "EBSI_DEACTIVATE_FAILED",
                message = "Failed to deactivate did:ebsi: ${e.message}",
                cause = e,
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // EBSI REST API helpers
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Resolves a DID document from the EBSI DID Registry.
     *
     * `GET {apiBaseUrl}/did-registry/v5/identifiers/{did}`
     *
     * @return Parsed [DidDocument] or `null` if the server returned 404.
     * @throws EbsiException for non-404 HTTP errors.
     */
    private fun resolveFromEbsiApi(did: String): DidDocument? {
        val url = "${config.apiBaseUrl}/did-registry/v5/identifiers/$did"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                throw EbsiException.httpError(response.code, body)
            }
            val body = response.body?.string()
                ?: throw EbsiException("EBSI_EMPTY_BODY", "EBSI API returned empty body for $did")
            val jsonElement = Json.parseToJsonElement(body)
            return jsonElementToDocument(jsonElement)
        }
    }

    /**
     * Registers a new DID document on the EBSI DID Registry via JSON-RPC.
     *
     * `POST {apiBaseUrl}/did-registry/v5/jsonrpc` with a JSON-RPC 2.0 body.
     */
    private fun registerOnEbsi(document: DidDocument) {
        requireBearerToken("create")
        val docJson = documentToJsonElement(document)
        val jsonRpcBody = """
            {
              "jsonrpc": "2.0",
              "method": "insertDidDocument",
              "id": 1,
              "params": [$docJson]
            }
        """.trimIndent()

        val url = "${config.apiBaseUrl}/did-registry/v5/jsonrpc"
        val requestBody = jsonRpcBody.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${config.bearerToken}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                throw EbsiException.httpError(response.code, body)
            }
        }
    }

    /**
     * Updates a DID document on the EBSI DID Registry.
     *
     * `PATCH {apiBaseUrl}/did-registry/v5/identifiers/{did}`
     */
    private fun patchOnEbsi(did: String, document: DidDocument) {
        requireBearerToken("update/deactivate")
        val docJson = documentToJsonElement(document)
        val url = "${config.apiBaseUrl}/did-registry/v5/identifiers/$did"
        val requestBody = docJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .patch(requestBody)
            .addHeader("Authorization", "Bearer ${config.bearerToken}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                throw EbsiException.httpError(response.code, body)
            }
        }
    }

    private fun requireBearerToken(operation: String) {
        if (config.bearerToken == null) throw EbsiException.authRequired(operation)
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────────────

    companion object {
        private const val BASE58_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
