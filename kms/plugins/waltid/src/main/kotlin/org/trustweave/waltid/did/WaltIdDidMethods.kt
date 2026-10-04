package org.trustweave.waltid.did

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.model.parseServiceTypesFromJson
import org.trustweave.did.model.serviceEndpointFromJsonElement
import org.trustweave.did.resolver.DidResolutionError
import org.trustweave.did.resolver.DidResolutionMetadata
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

/**
 * Base class for walt.id DID method implementations.
 */
abstract class WaltIdDidMethodBase(
    protected val kms: KeyManagementService,
) : DidMethod {
    protected val documents = ConcurrentHashMap<String, DidDocument>()

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument =
        withContext(Dispatchers.IO) {
            val current =
                documents[did.value]
                    ?: throw IllegalArgumentException("DID not found: ${did.value}")
            val updated = updater(current)
            documents[did.value] = updated
            updated
        }

    override suspend fun deactivateDid(did: Did): Boolean =
        withContext(Dispatchers.IO) {
            documents.remove(did.value) != null
        }

    /**
     * Converts a walt.id DID document (JSON) to TrustWeave's DidDocument model.
     */
    protected fun convertWaltIdDocument(waltIdDoc: JsonObject): DidDocument {
        val idString = waltIdDoc["id"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing id in DID document")
        val id = Did(idString)

        val verificationMethods =
            waltIdDoc["verificationMethod"]
                ?.jsonArray
                ?.mapNotNull { vm ->
                    val vmObj = vm.jsonObject
                    val vmIdString = vmObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val type = vmObj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val controllerString = vmObj["controller"]?.jsonPrimitive?.content ?: idString
                    val controller = Did(controllerString)
                    VerificationMethod(
                        id = VerificationMethodId.parse(vmIdString, id),
                        type = type,
                        controller = controller,
                        publicKeyJwk = vmObj["publicKeyJwk"]?.jsonObject?.toMap(),
                        publicKeyMultibase = vmObj["publicKeyMultibase"]?.jsonPrimitive?.content,
                    )
                } ?: emptyList()

        val authentication =
            waltIdDoc["authentication"]
                ?.jsonArray
                ?.mapNotNull {
                    it.jsonPrimitive.content?.let { vmIdStr -> VerificationMethodId.parse(vmIdStr, id) }
                } ?: emptyList()

        val assertionMethod =
            waltIdDoc["assertionMethod"]
                ?.jsonArray
                ?.mapNotNull {
                    it.jsonPrimitive.content?.let { vmIdStr -> VerificationMethodId.parse(vmIdStr, id) }
                } ?: emptyList()

        val keyAgreement =
            waltIdDoc["keyAgreement"]
                ?.jsonArray
                ?.mapNotNull {
                    it.jsonPrimitive.content?.let { vmIdStr -> VerificationMethodId.parse(vmIdStr, id) }
                } ?: emptyList()

        val services =
            waltIdDoc["service"]
                ?.jsonArray
                ?.mapNotNull { s ->
                    val sObj = s.jsonObject
                    val serviceEndpoint =
                        serviceEndpointFromJsonElement(sObj["serviceEndpoint"])
                            ?: return@mapNotNull null
                    DidService(
                        id = sObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        type = parseServiceTypesFromJson(sObj["type"]) ?: return@mapNotNull null,
                        serviceEndpoint = serviceEndpoint,
                    )
                } ?: emptyList()

        return DidDocument(
            id = id,
            verificationMethod = verificationMethods,
            authentication = authentication,
            assertionMethod = assertionMethod,
            keyAgreement = keyAgreement,
            service = services,
        )
    }

    private fun JsonObject.toMap(): Map<String, Any?> =
        entries.associate { entry ->
            entry.key to
                when (val value = entry.value) {
                    is JsonPrimitive -> {
                        when {
                            value.isString -> value.content
                            value.contentOrNull?.toBooleanStrictOrNull() != null -> value.content.toBoolean()
                            value.contentOrNull?.toLongOrNull() != null -> value.content.toLong()
                            value.contentOrNull?.toDoubleOrNull() != null -> value.content.toDouble()
                            else -> value.content
                        }
                    }
                    is JsonObject -> value.toMap()
                    is JsonArray -> value.map { (it as? JsonObject)?.toMap() ?: it.toString() }
                    JsonNull -> null
                    else -> value.toString()
                }
        }
}

/**
 * Former placeholder did:key method.
 *
 * It never used walt.id: it produced identifiers that are not valid for the method and
 * resolved only from an in-process cache. It is no longer registered via SPI, and [createDid]
 * fails with [UnsupportedOperationException]; use the real implementation from the
 * `did:plugins:key` module.
 */
@Deprecated("Not a real did:key implementation; use KeyDidMethod from did:plugins:key")
class WaltIdKeyMethod(
    kms: KeyManagementService,
) : WaltIdDidMethodBase(kms) {
    override val method: String = "key"

    override suspend fun createDid(options: DidCreationOptions): DidDocument =
        throw UnsupportedOperationException(
            "WaltIdKeyMethod cannot create valid did:key identifiers; use KeyDidMethod from did:plugins:key",
        )

    override suspend fun resolveDid(did: Did): DidResolutionResult =
        withContext(Dispatchers.IO) {
            try {
                // Use walt.id to resolve did:key
                // val waltIdDoc = WaltIdDid.resolve(did.value)
                // val document = convertWaltIdDocument(waltIdDoc)

                // For now, return from local cache or create resolution result
                val document = documents[did.value]
                val now = Clock.System.now()
                if (document != null) {
                    DidResolutionResult.Success(
                        document = document,
                        documentMetadata =
                            DidDocumentMetadata(
                                created = now,
                                updated = now,
                            ),
                        resolutionMetadata =
                            DidResolutionMetadata(
                                pattern = method,
                                properties = mapOf("provider" to "waltid"),
                            ),
                    )
                } else {
                    DidResolutionResult.Failure.NotFound(
                        did = did,
                        reason = "DID not found in cache",
                        resolutionMetadata =
                            DidResolutionMetadata(
                                error = DidResolutionError.notFound("DID not found in cache"),
                                pattern = method,
                                properties = mapOf("provider" to "waltid"),
                            ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to resolve did:key: ${e.message ?: "Unknown error"}",
                    context = mapOf("method" to "key", "did" to did.value),
                    cause = e,
                )
            }
        }
}

/**
 * Former placeholder did:web method.
 *
 * It never used walt.id: it produced identifiers that are not valid for the method and
 * resolved only from an in-process cache. It is no longer registered via SPI, and [createDid]
 * fails with [UnsupportedOperationException]; use the real implementation from the
 * `did:plugins:web` module.
 */
@Deprecated("Not a real did:web implementation; use WebDidMethod from did:plugins:web")
class WaltIdWebMethod(
    kms: KeyManagementService,
) : WaltIdDidMethodBase(kms) {
    override val method: String = "web"

    override suspend fun createDid(options: DidCreationOptions): DidDocument =
        throw UnsupportedOperationException(
            "WaltIdWebMethod does not publish or resolve did:web documents; use WebDidMethod from did:plugins:web",
        )

    override suspend fun resolveDid(did: Did): DidResolutionResult =
        withContext(Dispatchers.IO) {
            try {
                // Use walt.id to resolve did:web
                // val waltIdDoc = WaltIdDid.resolve(did.value)
                // val document = convertWaltIdDocument(waltIdDoc)

                val document = documents[did.value]
                val now = Clock.System.now()
                if (document != null) {
                    DidResolutionResult.Success(
                        document = document,
                        documentMetadata =
                            DidDocumentMetadata(
                                created = now,
                                updated = now,
                            ),
                        resolutionMetadata =
                            DidResolutionMetadata(
                                pattern = method,
                                properties = mapOf("provider" to "waltid"),
                            ),
                    )
                } else {
                    DidResolutionResult.Failure.NotFound(
                        did = did,
                        reason = "DID not found in cache",
                        resolutionMetadata =
                            DidResolutionMetadata(
                                error = DidResolutionError.notFound("DID not found in cache"),
                                pattern = method,
                                properties = mapOf("provider" to "waltid"),
                            ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to resolve did:web: ${e.message ?: "Unknown error"}",
                    context = mapOf("method" to "web", "did" to did.value),
                    cause = e,
                )
            }
        }
}

/**
 * Provider for the deprecated placeholder walt.id DID methods.
 *
 * No longer registered in `META-INF/services`, so it never shadows the real did:key and did:web
 * providers during SPI discovery. The methods it creates refuse to create DIDs.
 */
@Deprecated("The walt.id DID methods were placeholders; use the did:plugins:key and did:plugins:web providers")
@Suppress("DEPRECATION")
class WaltIdDidMethodProvider : DidMethodProvider {
    override val name: String = "waltid"
    override val supportedMethods: List<String> = listOf("key", "web")

    override fun create(
        methodName: String,
        options: org.trustweave.did.DidCreationOptions,
    ): DidMethod? {
        // Get KMS from options or create via factory API
        val kms =
            options.additionalProperties["kms"] as? KeyManagementService
                ?: try {
                    org.trustweave.kms.KeyManagementServices
                        .create("waltid", options.additionalProperties)
                } catch (e: IllegalArgumentException) {
                    throw IllegalStateException(
                        "No KeyManagementService available. Provide 'kms' in options or ensure walt.id KMS provider is registered.",
                        e,
                    )
                }

        return when (methodName.lowercase()) {
            "key" -> WaltIdKeyMethod(kms)
            "web" -> WaltIdWebMethod(kms)
            else -> null
        }
    }
}
