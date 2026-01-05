package org.trustweave.waltid.did

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.model.DidService
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolutionMetadata
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for walt.id DID method implementations.
 */
abstract class WaltIdDidMethodBase(
    protected val kms: KeyManagementService
) : DidMethod {

    protected val documents = ConcurrentHashMap<String, DidDocument>()

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument
    ): DidDocument = withContext(Dispatchers.IO) {
        val current = documents[did.value]
            ?: throw IllegalArgumentException("DID not found: ${did.value}")
        val updated = updater(current)
        documents[did.value] = updated
        updated
    }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        documents.remove(did.value) != null
    }

    /**
     * Converts a walt.id DID document (JSON) to TrustWeave's DidDocument model.
     */
    protected fun convertWaltIdDocument(waltIdDoc: JsonObject): DidDocument {
        val idString = waltIdDoc["id"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing id in DID document")
        val id = Did(idString)

        val verificationMethods = waltIdDoc["verificationMethod"]
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
                    publicKeyMultibase = vmObj["publicKeyMultibase"]?.jsonPrimitive?.content
                )
            } ?: emptyList()

        val authentication = waltIdDoc["authentication"]
            ?.jsonArray
            ?.mapNotNull { 
                it.jsonPrimitive.content?.let { vmIdStr -> VerificationMethodId.parse(vmIdStr, id) }
            } ?: emptyList()

        val assertionMethod = waltIdDoc["assertionMethod"]
            ?.jsonArray
            ?.mapNotNull { 
                it.jsonPrimitive.content?.let { vmIdStr -> VerificationMethodId.parse(vmIdStr, id) }
            } ?: emptyList()

        val keyAgreement = waltIdDoc["keyAgreement"]
            ?.jsonArray
            ?.mapNotNull { 
                it.jsonPrimitive.content?.let { vmIdStr -> VerificationMethodId.parse(vmIdStr, id) }
            } ?: emptyList()

        val services = waltIdDoc["service"]
            ?.jsonArray
            ?.mapNotNull { s ->
                val sObj = s.jsonObject
                val serviceEndpoint = sObj["serviceEndpoint"]?.let {
                    when (it) {
                        is JsonPrimitive -> it.content
                        else -> it.toString()
                    }
                } ?: return@mapNotNull null
                DidService(
                    id = sObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    type = sObj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    serviceEndpoint = serviceEndpoint
                )
            } ?: emptyList()

        return DidDocument(
            id = id,
            verificationMethod = verificationMethods,
            authentication = authentication,
            assertionMethod = assertionMethod,
            keyAgreement = keyAgreement,
            service = services
        )
    }

    private fun JsonObject.toMap(): Map<String, Any?> {
        return entries.associate { entry ->
            entry.key to when (val value = entry.value) {
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
}

/**
 * walt.id implementation of did:key method.
 */
class WaltIdKeyMethod(
    kms: KeyManagementService
) : WaltIdDidMethodBase(kms) {

    override val method: String = "key"

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            val algorithm = options.algorithm.algorithmName
            val generateResult = kms.generateKey(algorithm, options.additionalProperties)
            val keyHandle = when (generateResult) {
                is GenerateKeyResult.Success -> generateResult.keyHandle
                is GenerateKeyResult.Failure.UnsupportedAlgorithm -> throw TrustWeaveException.Unknown(
                    message = "Failed to generate key: ${generateResult.reason ?: "Algorithm not supported"}",
                    context = mapOf("method" to "key", "algorithm" to algorithm),
                    cause = null
                )
                is GenerateKeyResult.Failure.InvalidOptions -> throw TrustWeaveException.Unknown(
                    message = "Failed to generate key: ${generateResult.reason}",
                    context = mapOf("method" to "key", "algorithm" to algorithm),
                    cause = null
                )
                is GenerateKeyResult.Failure.Error -> throw TrustWeaveException.Unknown(
                    message = "Failed to generate key: ${generateResult.reason}",
                    context = mapOf("method" to "key", "algorithm" to algorithm),
                    cause = generateResult.cause
                )
            }

            // Use walt.id to create did:key
            // val waltIdDid = WaltIdDid.create("key", keyHandle.publicKeyJwk)
            // For now, create a simplified did:key format
            val didId = "z${keyHandle.id.value.replace("-", "").take(32)}"
            val didString = "did:$method:$didId"
            val did = Did(didString)

            val verificationMethodIdStr = "$didString#${keyHandle.id.value}"
            val verificationMethodId = VerificationMethodId.parse(verificationMethodIdStr, did)
            val verificationMethod = VerificationMethod(
                id = verificationMethodId,
                type = when (algorithm.uppercase()) {
                    "ED25519" -> "Ed25519VerificationKey2020"
                    else -> "JsonWebKey2020"
                },
                controller = did,
                publicKeyJwk = keyHandle.publicKeyJwk
            )

            val document = DidDocument(
                id = did,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(verificationMethodId),
                assertionMethod = listOf(verificationMethodId)
            )

            documents[didString] = document
            document
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
            // Use walt.id to resolve did:key
            // val waltIdDoc = WaltIdDid.resolve(did.value)
            // val document = convertWaltIdDocument(waltIdDoc)

            // For now, return from local cache or create resolution result
            val document = documents[did.value]
            val now = Clock.System.now()
            if (document != null) {
                DidResolutionResult.Success(
                    document = document,
                    documentMetadata = DidDocumentMetadata(
                        created = now,
                        updated = now
                    ),
                    resolutionMetadata = DidResolutionMetadata(
                        pattern = method,
                        properties = mapOf("provider" to "waltid")
                    )
                )
            } else {
                DidResolutionResult.Failure.NotFound(
                    did = did,
                    reason = "DID not found in cache",
                    resolutionMetadata = DidResolutionMetadata(
                        error = "notFound",
                        errorMessage = "DID not found in cache",
                        pattern = method,
                        properties = mapOf("provider" to "waltid")
                    )
                )
            }
        } catch (e: Exception) {
            throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve did:key: ${e.message ?: "Unknown error"}",
                context = mapOf("method" to "key", "did" to did.value),
                cause = e
            )
        }
    }
}

/**
 * walt.id implementation of did:web method.
 */
class WaltIdWebMethod(
    kms: KeyManagementService
) : WaltIdDidMethodBase(kms) {

    override val method: String = "web"

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            val domain = options.additionalProperties["domain"] as? String
                ?: throw IllegalArgumentException("did:web requires 'domain' option")

            val algorithm = options.algorithm.algorithmName
            val generateResult = kms.generateKey(algorithm, options.additionalProperties)
            val keyHandle = when (generateResult) {
                is GenerateKeyResult.Success -> generateResult.keyHandle
                is GenerateKeyResult.Failure.UnsupportedAlgorithm -> throw TrustWeaveException.Unknown(
                    message = "Failed to generate key: ${generateResult.reason ?: "Algorithm not supported"}",
                    context = mapOf("method" to "web", "algorithm" to algorithm),
                    cause = null
                )
                is GenerateKeyResult.Failure.InvalidOptions -> throw TrustWeaveException.Unknown(
                    message = "Failed to generate key: ${generateResult.reason}",
                    context = mapOf("method" to "web", "algorithm" to algorithm),
                    cause = null
                )
                is GenerateKeyResult.Failure.Error -> throw TrustWeaveException.Unknown(
                    message = "Failed to generate key: ${generateResult.reason}",
                    context = mapOf("method" to "web", "algorithm" to algorithm),
                    cause = generateResult.cause
                )
            }

            // Use walt.id to create did:web
            // val waltIdDid = WaltIdDid.create("web", domain, keyHandle.publicKeyJwk)
            val didString = "did:$method:$domain"
            val did = Did(didString)

            val verificationMethodIdStr = "$didString#${keyHandle.id.value}"
            val verificationMethodId = VerificationMethodId.parse(verificationMethodIdStr, did)
            val verificationMethod = VerificationMethod(
                id = verificationMethodId,
                type = when (algorithm.uppercase()) {
                    "ED25519" -> "Ed25519VerificationKey2020"
                    else -> "JsonWebKey2020"
                },
                controller = did,
                publicKeyJwk = keyHandle.publicKeyJwk
            )

            val document = DidDocument(
                id = did,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(verificationMethodId),
                assertionMethod = listOf(verificationMethodId)
            )

            documents[didString] = document
            document
        } catch (e: Exception) {
            throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to create did:web: ${e.message ?: "Unknown error"}",
                context = mapOf("method" to "web"),
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            // Use walt.id to resolve did:web
            // val waltIdDoc = WaltIdDid.resolve(did.value)
            // val document = convertWaltIdDocument(waltIdDoc)

            val document = documents[did.value]
            val now = Clock.System.now()
            if (document != null) {
                DidResolutionResult.Success(
                    document = document,
                    documentMetadata = DidDocumentMetadata(
                        created = now,
                        updated = now
                    ),
                    resolutionMetadata = DidResolutionMetadata(
                        pattern = method,
                        properties = mapOf("provider" to "waltid")
                    )
                )
            } else {
                DidResolutionResult.Failure.NotFound(
                    did = did,
                    reason = "DID not found in cache",
                    resolutionMetadata = DidResolutionMetadata(
                        error = "notFound",
                        errorMessage = "DID not found in cache",
                        pattern = method,
                        properties = mapOf("provider" to "waltid")
                    )
                )
            }
        } catch (e: Exception) {
            throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve did:web: ${e.message ?: "Unknown error"}",
                context = mapOf("method" to "web", "did" to did.value),
                cause = e
            )
        }
    }
}

/**
 * SPI provider for walt.id DID methods.
 */
class WaltIdDidMethodProvider : DidMethodProvider {

    override val name: String = "waltid"
    override val supportedMethods: List<String> = listOf("key", "web")

    override fun create(methodName: String, options: org.trustweave.did.DidCreationOptions): DidMethod? {
        // Get KMS from options or create via factory API
        val kms = options.additionalProperties["kms"] as? KeyManagementService
            ?: try {
                org.trustweave.kms.KeyManagementServices.create("waltid", options.additionalProperties)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("No KeyManagementService available. Provide 'kms' in options or ensure walt.id KMS provider is registered.", e)
            }

        return when (methodName.lowercase()) {
            "key" -> WaltIdKeyMethod(kms)
            "web" -> WaltIdWebMethod(kms)
            else -> null
        }
    }
}

