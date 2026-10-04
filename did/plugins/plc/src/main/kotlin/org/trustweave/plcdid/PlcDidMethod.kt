package org.trustweave.plcdid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService

/**
 * Implementation of did:plc method for AT Protocol.
 *
 * did:plc uses Personal Linked Container (PLC) DID method for AT Protocol:
 * - Format: `did:plc:{identifier}`
 * - Distributed registry for AT Protocol
 * - HTTP-based resolution
 *
 * **Scope:** this plugin resolves did:plc DIDs from the PLC directory. Creating, updating and
 * deactivating require signed PLC operations, which are not implemented; those calls fail with
 * [NOT_IMPLEMENTED] instead of fabricating identifiers or pretending to register documents.
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = PlcDidConfig.default()
 * val method = PlcDidMethod(kms, config)
 *
 * // Resolve DID
 * val result = method.resolveDid(Did("did:plc:ewvi7nxzyoun6zhxrhs64oiz"))
 * ```
 */
class PlcDidMethod(
    kms: KeyManagementService,
    private val config: PlcDidConfig = PlcDidConfig.default(),
) : AbstractDidMethod("plc", kms) {
    companion object {
        /** Error code for the operations this plugin does not implement (create, update, deactivate). */
        const val NOT_IMPLEMENTED = "PLC_NOT_IMPLEMENTED"

        /** A did:plc identifier: 24 characters of lowercase base32 (RFC 4648 alphabet, no padding). */
        private val IDENTIFIER = Regex("^[a-z2-7]{24}$")

        /** Upper bound on a directory response body (same cap as did:web). */
        private const val MAX_RESPONSE_BYTES = 1L * 1024 * 1024
    }

    private val httpClient: OkHttpClient

    init {
        httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                // A directory that redirects is misbehaving (or compromised); never follow blindly.
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }

    /** Reads the body, refusing anything over [MAX_RESPONSE_BYTES] without buffering more than that. */
    private fun readCapped(response: okhttp3.Response): String {
        val body =
            response.body
                ?: throw TrustWeaveException.Unknown(code = "EMPTY_RESPONSE", message = "Empty response body")
        if (body.contentLength() > MAX_RESPONSE_BYTES) throw tooLarge()
        val source = body.source()
        source.request(MAX_RESPONSE_BYTES + 1)
        if (source.buffer.size > MAX_RESPONSE_BYTES) throw tooLarge()
        return source.buffer.readUtf8()
    }

    private fun tooLarge() =
        TrustWeaveException.Unknown(
            code = "RESPONSE_TOO_LARGE",
            message = "PLC directory response exceeds the maximum allowed size ($MAX_RESPONSE_BYTES bytes)",
        )

    private fun notImplemented(operation: String): Nothing =
        throw TrustWeaveException.Unknown(
            code = NOT_IMPLEMENTED,
            message =
                "did:plc $operation is not implemented: it requires a PLC operation (DAG-CBOR encoded, " +
                    "signed with a secp256k1 or P-256 rotation key) submitted to the PLC directory, and the " +
                    "identifier of a new DID is derived from the signed genesis operation " +
                    "(base32(sha256(op))[0..24]). This plugin only resolves did:plc.",
        )

    /**
     * Not implemented: see [NOT_IMPLEMENTED]. Fails before generating any key, so no orphaned key
     * is left in the KMS.
     */
    override suspend fun createDid(options: DidCreationOptions): DidDocument = notImplemented("creation")

    /**
     * Resolves a did:plc DID from the PLC directory (`GET {plcRegistryUrl}/{did}`).
     */
    override suspend fun resolveDid(did: Did): DidResolutionResult =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                val didString = did.value
                val identifier = didString.removePrefix("did:plc:")
                if (!IDENTIFIER.matches(identifier)) {
                    return@withContext DidMethodUtils.createErrorResolutionResult(
                        "invalidDid",
                        "did:plc identifier must be 24 lowercase base32 characters: $didString",
                        method,
                        didString,
                    )
                }
                val registry =
                    config.plcRegistryUrl?.trimEnd('/')
                        ?: return@withContext DidMethodUtils.createErrorResolutionResult(
                            "internalError",
                            "No PLC directory configured (plcRegistryUrl)",
                            method,
                            didString,
                        )

                val request =
                    Request
                        .Builder()
                        .url("$registry/$didString")
                        .get()
                        .addHeader("Accept", "application/did+ld+json, application/json")
                        .build()

                val jsonString =
                    httpClient.newCall(request).execute().use { response ->
                        when {
                            response.code == 404 -> return@withContext DidMethodUtils.createErrorResolutionResult(
                                "notFound",
                                "DID not found in the PLC directory",
                                method,
                                didString,
                            )
                            // The PLC directory answers 410 for a tombstoned (deactivated) DID.
                            response.code == 410 -> {
                                // Tombstoned == deactivated (DID Resolution 1.0 §4.4): not a "not found".
                                removeStoredDocument(didString)
                                return@withContext DidResolutionResult.Deactivated(did = did)
                            }
                            response.isRedirect -> throw TrustWeaveException.Unknown(
                                code = "REDIRECT_REFUSED",
                                message = "PLC directory answered with a redirect (HTTP ${response.code}); redirects are not followed",
                            )
                            !response.isSuccessful -> throw TrustWeaveException.Unknown(
                                code = "RESOLVE_FAILED",
                                message = "PLC directory returned HTTP ${response.code} for $didString",
                            )
                        }
                        readCapped(response)
                    }

                val document = jsonElementToDocument(Json.parseToJsonElement(jsonString))

                // A document whose id is a different DID is rejected, never rewritten to the requested
                // DID (that would let a compromised or misbehaving directory impersonate it).
                if (document.id.value != didString) {
                    return@withContext DidMethodUtils.createErrorResolutionResult(
                        "invalidDidDocument",
                        "Document ID mismatch: expected $didString, got ${document.id.value}",
                        method,
                        didString,
                    )
                }

                storeDocument(document.id.value, document)
                DidMethodUtils.createSuccessResolutionResult(document, method, retrieved = getLastFetched(did))
            } catch (e: org.trustweave.did.exception.DidException.InvalidDidFormat) {
                DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                DidMethodUtils.createErrorResolutionResult("internalError", e.message, method, did.value)
            }
        }

    /** Not implemented: see [NOT_IMPLEMENTED]. */
    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument = notImplemented("update")

    /** Not implemented: see [NOT_IMPLEMENTED]. */
    override suspend fun deactivateDid(did: Did): Boolean = notImplemented("deactivation")
}
