package org.trustweave.iondid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.KeyPurpose
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.model.parseServiceTypesFromJson
import org.trustweave.did.model.serviceEndpointFromJsonElement
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.sidetree.InMemorySidetreeKeyStore
import org.trustweave.did.sidetree.SidetreeJcs
import org.trustweave.did.sidetree.SidetreeKeyPair
import org.trustweave.did.sidetree.SidetreeKeyStore
import org.trustweave.did.sidetree.SidetreeP256KeyPair
import org.trustweave.did.util.ResolvedDocumentId
import org.trustweave.kms.KeyManagementService

/**
 * Implementation of did:ion method using Sidetree protocol.
 *
 * did:ion uses Microsoft ION (Identity Overlay Network) which is built on the Sidetree protocol:
 * - Format: `did:ion:{suffix}` (short-form) or `did:ion:{long-form}` (long-form)
 * - Operations anchored to Bitcoin blockchain
 * - Resolved through ION nodes
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementDidService()
 * val config = IonDidConfig.testnet()
 * val method = IonDidMethod(kms, config)
 *
 * // Create DID
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.ED25519
 * }
 * val document = method.createDid(options)
 *
 * // Resolve DID (short-form)
 * val result = method.resolveDid("did:ion:EiA2...")
 *
 * // Resolve DID (long-form, for newly created DIDs)
 * val longFormDid = document.id // Contains long-form DID
 * val result2 = method.resolveDid(longFormDid)
 * ```
 *
 * @see <a href="https://identity.foundation/ion/">ION Specification</a>
 * @see <a href="https://identity.foundation/sidetree/spec/">Sidetree Protocol</a>
 */
class IonDidMethod(
    kms: KeyManagementService,
    private val config: IonDidConfig,
    httpClient: OkHttpClient? = null,
    private val keyStore: SidetreeKeyStore = InMemorySidetreeKeyStore(),
) : AbstractDidMethod("ion", kms) {
    private val httpClient: OkHttpClient =
        httpClient ?: OkHttpClient
            .Builder()
            .connectTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .build()

    private val sidetreeClient: SidetreeClient = SidetreeClient(this.httpClient, config)

    override suspend fun createDid(options: DidCreationOptions): DidDocument =
        withContext(Dispatchers.IO) {
            try {
                val algorithm = options.algorithm.algorithmName
                val keyHandle = generateKey(algorithm, options.additionalProperties)

                val createResult =
                    sidetreeClient.buildCreateOperation(
                        publicKeyJwk =
                            keyHandle.publicKeyJwk
                                ?: throw TrustWeaveException.Unknown(
                                    code = "MISSING_JWK",
                                    message = "Public key JWK is required for did:ion",
                                ),
                    )

                // Submit operation to ION node (best-effort; long-form DID is usable immediately)
                val operationResponse = sidetreeClient.submitOperation(createResult.operation)

                // Prefer the DID returned by the ION node; fall back to locally derived long-form DID
                val nodeDid = operationResponse.did
                if (nodeDid != null && runCatching { parseIonDid(nodeDid).suffix }.getOrNull() != createResult.didSuffix) {
                    throw TrustWeaveException.Unknown(
                        code = "ION_SUFFIX_MISMATCH",
                        message = "The ION node returned a DID whose suffix differs from the created operation's suffix",
                    )
                }
                val longFormDid = nodeDid ?: createResult.longFormDid

                keyStore.put(
                    createResult.didSuffix,
                    SidetreeKeyPair(
                        updatePrivateJwk = createResult.updateKeyPair.privateJwk,
                        updatePublicJwk = createResult.updateKeyPair.publicJwk,
                        recoveryPrivateJwk = createResult.recoveryKeyPair.privateJwk,
                        recoveryPublicJwk = createResult.recoveryKeyPair.publicJwk,
                    ),
                )

                // Create DID document from operation
                val document =
                    buildIonDocument(
                        did = longFormDid,
                        keyHandle = keyHandle,
                        algorithm = options.algorithm,
                        purposes = options.purposes,
                    )

                // Store locally
                storeDocument(document.id, document)

                document
            } catch (e: TrustWeaveException) {
                throw e
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw TrustWeaveException.Unknown(
                    code = "CREATE_FAILED",
                    message = "Failed to create did:ion: ${e.message}",
                    cause = e,
                )
            }
        }

    override suspend fun resolveDid(did: Did): DidResolutionResult =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                val didString = did.value
                // Resolve through ION node
                val ionDid = parseIonDid(didString)
                // A long-form DID carries its own initial state: it must hash to the suffix, or the
                // DID names something other than what it claims to.
                verifyInitialState(ionDid)
                val resolutionResult = sidetreeClient.resolveDid(didString)
                val ionDocument = resolutionResult.document

                if (!resolutionResult.success || ionDocument == null) {
                    // Only a 404 means "no such DID". Transport failures (-1), node errors and
                    // unparseable answers are resolution errors, not a verdict on the DID.
                    val notFound = resolutionResult.httpStatus == 404
                    return@withContext DidMethodUtils.createErrorResolutionResult(
                        if (notFound) "notFound" else "internalError",
                        resolutionResult.error ?: "DID not found in ION network",
                        method,
                        didString,
                    )
                }

                // Convert ION document to TrustWeave format
                val convertedDocument = convertIonDocument(ionDocument)

                // The node's answer must be about the DID we asked for: reject, never rewrite, and
                // never cache a foreign document.
                // A long-form request may be answered with its canonical short form, nothing else.
                // The canonical form is computed here from the parsed DID: `did:ion:test:<suffix>`
                // (network + short form) must never be mistaken for a long form.
                if (convertedDocument.id.value != didString && convertedDocument.id.value != ionDid.canonicalForm) {
                    return@withContext DidMethodUtils.createErrorResolutionResult(
                        "invalidDidDocument",
                        ResolvedDocumentId.mismatchReason(didString, convertedDocument.id.value)
                            ?: "Resolved document id does not match the requested DID",
                        method,
                        didString,
                    )
                }

                // Store locally for caching (under the verified id, which is the requested DID or
                // its canonical form)
                storeDocument(convertedDocument.id.value, convertedDocument)

                DidMethodUtils.createSuccessResolutionResult(convertedDocument, method)
            } catch (e: org.trustweave.did.exception.DidException.InvalidDidFormat) {
                DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
            } catch (e: TrustWeaveException) {
                DidMethodUtils.createErrorResolutionResult("internalError", e.message, method, did.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: kotlinx.serialization.SerializationException) {
                DidMethodUtils.createErrorResolutionResult("invalidDidDocument", e.message, method, did.value)
            } catch (e: IllegalArgumentException) {
                // A node answer missing required members (for example no `id`) is a bad document.
                DidMethodUtils.createErrorResolutionResult("invalidDidDocument", e.message, method, did.value)
            } catch (e: Exception) {
                DidMethodUtils.createErrorResolutionResult("internalError", e.message, method, did.value)
            }
        }

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                val didString = did.value
                // Resolve current document
                val currentResult = resolveDid(did)
                val currentDocument =
                    when (currentResult) {
                        is DidResolutionResult.Success -> currentResult.document
                        else -> throw TrustWeaveException.NotFound(
                            message = "DID document not found: $didString",
                        )
                    }

                // Apply updater
                val updatedDocument = updater(currentDocument)

                val suffix = extractDidSuffix(didString)
                val previousKeys =
                    keyStore.get(suffix)
                        ?: throw TrustWeaveException.Unknown(
                            code = "ION_KEYS_NOT_FOUND",
                            message =
                                "Update keys for $didString are not in the key store. " +
                                    "Updates can only be issued by the instance that created the DID, " +
                                    "or one configured with a persistent SidetreeKeyStore containing the prior keys.",
                        )
                val nextUpdateKeyPair = sidetreeClient.generateP256KeyPair()
                val previousUpdateKeyPair =
                    SidetreeP256KeyPair(
                        privateJwk = previousKeys.updatePrivateJwk,
                        publicJwk = previousKeys.updatePublicJwk,
                    )

                val updateOperation =
                    sidetreeClient.buildUpdateOperation(
                        did = didString,
                        updatedDocument = updatedDocument,
                        previousUpdateKeyPair = previousUpdateKeyPair,
                        nextUpdatePublicJwk = nextUpdateKeyPair.publicJwk,
                    )

                // Submit update operation
                sidetreeClient.submitOperation(updateOperation)

                keyStore.put(
                    suffix,
                    previousKeys.copy(
                        updatePrivateJwk = nextUpdateKeyPair.privateJwk,
                        updatePublicJwk = nextUpdateKeyPair.publicJwk,
                    ),
                )
                // Store updated document
                storeDocument(updatedDocument.id.value, updatedDocument)

                updatedDocument
            } catch (e: TrustWeaveException.NotFound) {
                throw e
            } catch (e: TrustWeaveException) {
                throw e
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw TrustWeaveException.Unknown(
                    code = "UPDATE_FAILED",
                    message = "Failed to update did:ion: ${e.message}",
                    cause = e,
                )
            }
        }

    /**
     * Recovers a did:ion DID. Used when the update key has been lost or
     * compromised but the recovery key is still available. Rotates BOTH the
     * update and recovery keys in one operation and replaces the document state
     * with the result of [updater].
     *
     * The previous recovery key is loaded from the [keyStore]; the operation
     * fails fast with `ION_KEYS_NOT_FOUND` when it is absent.
     */
    suspend fun recoverDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                val didString = did.value
                val currentResult = resolveDid(did)
                val currentDocument =
                    when (currentResult) {
                        is DidResolutionResult.Success -> currentResult.document
                        else -> throw TrustWeaveException.NotFound(
                            message = "DID document not found: $didString",
                        )
                    }
                val recovered = updater(currentDocument)

                val suffix = extractDidSuffix(didString)
                val previousKeys =
                    keyStore.get(suffix)
                        ?: throw TrustWeaveException.Unknown(
                            code = "ION_KEYS_NOT_FOUND",
                            message =
                                "Recovery keys for $didString are not in the key store. " +
                                    "Recover can only be issued by the instance that created the DID, " +
                                    "or one configured with a persistent SidetreeKeyStore containing the prior keys.",
                        )
                val previousRecoveryKeyPair =
                    SidetreeP256KeyPair(
                        privateJwk = previousKeys.recoveryPrivateJwk,
                        publicJwk = previousKeys.recoveryPublicJwk,
                    )
                val nextUpdateKeyPair = sidetreeClient.generateP256KeyPair()
                val nextRecoveryKeyPair = sidetreeClient.generateP256KeyPair()

                val recoverOp =
                    sidetreeClient.buildRecoverOperation(
                        did = didString,
                        newDocument = recovered,
                        previousRecoveryKeyPair = previousRecoveryKeyPair,
                        nextUpdatePublicJwk = nextUpdateKeyPair.publicJwk,
                        nextRecoveryPublicJwk = nextRecoveryKeyPair.publicJwk,
                    )
                sidetreeClient.submitOperation(recoverOp)

                keyStore.put(
                    suffix,
                    SidetreeKeyPair(
                        updatePrivateJwk = nextUpdateKeyPair.privateJwk,
                        updatePublicJwk = nextUpdateKeyPair.publicJwk,
                        recoveryPrivateJwk = nextRecoveryKeyPair.privateJwk,
                        recoveryPublicJwk = nextRecoveryKeyPair.publicJwk,
                    ),
                )
                storeDocument(recovered.id.value, recovered)
                recovered
            } catch (e: TrustWeaveException) {
                throw e
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw TrustWeaveException.Unknown(
                    code = "RECOVER_FAILED",
                    message = "Failed to recover did:ion: ${e.message}",
                    cause = e,
                )
            }
        }

    override suspend fun deactivateDid(did: Did): Boolean =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                val didString = did.value
                // Resolve current document to get operation hash
                val currentResult = resolveDid(did)
                val currentDocument =
                    when (currentResult) {
                        is DidResolutionResult.Success -> currentResult.document
                        else -> return@withContext false
                    }

                val suffix = extractDidSuffix(didString)
                val previousKeys = keyStore.get(suffix) ?: return@withContext false

                val previousRecoveryKeyPair =
                    SidetreeP256KeyPair(
                        privateJwk = previousKeys.recoveryPrivateJwk,
                        publicJwk = previousKeys.recoveryPublicJwk,
                    )

                val deactivateOperation =
                    sidetreeClient.buildDeactivateOperation(
                        did = didString,
                        previousRecoveryKeyPair = previousRecoveryKeyPair,
                    )

                // Submit deactivate operation
                sidetreeClient.submitOperation(deactivateOperation)

                keyStore.remove(suffix)
                // Remove from local storage
                removeStoredDocument(didString)

                true
            } catch (e: TrustWeaveException.NotFound) {
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw TrustWeaveException.Unknown(
                    code = "DEACTIVATE_FAILED",
                    message = "Failed to deactivate did:ion: ${e.message}",
                    cause = e,
                )
            }
        }

    /**
     * Builds a DID document from ION/Sidetree operation.
     */
    private fun buildIonDocument(
        did: String,
        keyHandle: org.trustweave.kms.KeyHandle,
        algorithm: KeyAlgorithm,
        purposes: List<KeyPurpose>,
    ): DidDocument {
        // Create verification method
        val verificationMethod =
            DidMethodUtils.createVerificationMethod(
                did = did,
                keyHandle = keyHandle,
                algorithm = algorithm,
            )

        return DidMethodUtils.buildDidDocument(
            did = did,
            verificationMethod = listOf(verificationMethod),
            authentication = listOf(verificationMethod.id.value),
            assertionMethod =
                if (purposes.contains(KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id.value)
                } else {
                    null
                },
        )
    }

    /**
     * Converts ION document JSON to TrustWeave DidDocument.
     */
    private fun convertIonDocument(ionDocJson: JsonObject): DidDocument {
        val did =
            ionDocJson["id"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing id in ION document")

        val didObj = Did(did)
        val verificationMethods =
            ionDocJson["verificationMethod"]?.jsonArray?.mapNotNull { vm ->
                val vmObj = vm.jsonObject
                val vmIdString = vmObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                VerificationMethod(
                    id = VerificationMethodId.parse(vmIdString, didObj),
                    type = vmObj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    controller = Did(vmObj["controller"]?.jsonPrimitive?.content ?: did),
                    publicKeyJwk = vmObj["publicKeyJwk"]?.jsonObject?.let { jsonObjectToMap(it) },
                    publicKeyMultibase = vmObj["publicKeyMultibase"]?.jsonPrimitive?.content,
                )
            } ?: emptyList()

        val authentication =
            ionDocJson["authentication"]?.jsonArray?.mapNotNull {
                (it as? JsonPrimitive)?.content?.let { idStr -> VerificationMethodId.parse(idStr, didObj) }
            } ?: emptyList()

        val assertionMethod =
            ionDocJson["assertionMethod"]?.jsonArray?.mapNotNull {
                (it as? JsonPrimitive)?.content?.let { idStr -> VerificationMethodId.parse(idStr, didObj) }
            } ?: emptyList()

        val service =
            ionDocJson["service"]?.jsonArray?.mapNotNull { s ->
                val sObj = s.jsonObject
                val sTypes = parseServiceTypesFromJson(sObj["type"]) ?: return@mapNotNull null
                DidService(
                    id = sObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    type = sTypes,
                    serviceEndpoint =
                        serviceEndpointFromJsonElement(sObj["serviceEndpoint"])
                            ?: return@mapNotNull null,
                )
            } ?: emptyList()

        return DidDocument(
            id = didObj,
            verificationMethod = verificationMethods,
            authentication = authentication,
            assertionMethod = assertionMethod,
            service = service,
        )
    }

    private fun extractDidSuffix(did: String): String = parseIonDid(did).suffix

    /**
     * The parts of a did:ion identifier. The three forms are told apart by the *shape* of their
     * segments, never by counting colons:
     *  - `did:ion:<suffix>` (short form)
     *  - `did:ion:<network>:<suffix>` (e.g. `did:ion:test:<suffix>`); a network label is lowercase
     *  - `did:ion:<suffix>:<initial-state>` (long form)
     *  - `did:ion:<network>:<suffix>:<initial-state>`
     * A suffix starts with an uppercase `E` (the multihash prefix), so it can never be a network
     * label.
     */
    private class IonDid(
        val network: String?,
        val suffix: String,
        val initialState: String?,
    ) {
        val canonicalForm: String get() = "did:ion:" + (network?.let { "$it:" } ?: "") + suffix
    }

    private fun parseIonDid(did: String): IonDid {
        fun invalid(reason: String): Nothing =
            throw org.trustweave.did.exception.DidException
                .InvalidDidFormat(did = did, reason = reason)

        if (!did.startsWith("did:ion:")) invalid("DID does not match did:ion namespace")
        val segments = did.removePrefix("did:ion:").split(":")
        if (segments.any { it.isEmpty() }) invalid("did:ion contains an empty segment")
        val hasNetwork = segments.size >= 2 && NETWORK_LABEL.matches(segments.first())
        val rest = if (hasNetwork) segments.drop(1) else segments
        val network = if (hasNetwork) segments.first() else null
        return when (rest.size) {
            1 -> IonDid(network, rest[0], null)
            2 -> IonDid(network, rest[0], rest[1])
            else -> invalid("did:ion has too many segments")
        }
    }

    /**
     * For a long-form DID, checks that the initial state is `{suffixData, delta}` and that
     * `base64url(multihash(sha256(JCS(suffixData))))` equals the DID suffix (and, when a delta is
     * present, that `suffixData.deltaHash` is the hash of the delta). Short forms carry nothing to check.
     */
    private fun verifyInitialState(ionDid: IonDid) {
        val encoded = ionDid.initialState ?: return

        fun invalid(reason: String): Nothing =
            throw org.trustweave.did.exception.DidException.InvalidDidFormat(
                did = ionDid.canonicalForm,
                reason = "Long-form initial state is invalid: $reason",
            )
        val b64url =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
        val state =
            try {
                val bytes =
                    java.util.Base64
                        .getUrlDecoder()
                        .decode(encoded)
                kotlinx.serialization.json.Json
                    .parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: kotlinx.serialization.SerializationException) {
                null
            } ?: invalid("not base64url-encoded JSON object")
        val suffixData = state["suffixData"] as? JsonObject ?: invalid("missing suffixData")
        val computedSuffix = b64url.encodeToString(SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(suffixData)))
        if (computedSuffix != ionDid.suffix) invalid("it does not hash to the DID suffix")
        val delta = state["delta"]
        if (delta != null) {
            val deltaObject = delta as? JsonObject ?: invalid("delta is not an object")
            val expected = b64url.encodeToString(SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(deltaObject)))
            val declared = (suffixData["deltaHash"] as? JsonPrimitive)?.contentOrNull
            if (declared != expected) invalid("delta does not match suffixData.deltaHash")
        }
    }

    private companion object {
        /** Network labels such as `test`: lowercase, never a Sidetree suffix (which starts with `E`). */
        val NETWORK_LABEL = Regex("^[a-z][a-z0-9-]{0,15}$")
    }

    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> =
        obj.entries.associate { (key, value) ->
            key to
                when (value) {
                    is JsonPrimitive ->
                        value.contentOrNull ?: value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
                            ?: value.toString()
                    is JsonObject -> jsonObjectToMap(value)
                    is JsonArray -> value.map { (it as? JsonPrimitive)?.content ?: it.toString() }
                    else -> value.toString()
                }
        }
}
