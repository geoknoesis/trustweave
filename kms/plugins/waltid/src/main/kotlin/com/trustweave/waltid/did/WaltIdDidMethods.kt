package com.trustweave.waltid.did

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.did.VerificationMethod
import com.trustweave.did.DidService
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for walt.id DID method implementations.
 */
abstract class WaltIdDidMethodBase(
    protected val kms: KeyManagementService
) : DidMethod {

    protected val documents = ConcurrentHashMap<String, DidDocument>()

    override suspend fun updateDid(
        did: String,
        updater: (DidDocument) -> DidDocument
    ): DidDocument = withContext(Dispatchers.IO) {
        val current = documents[did]
            ?: throw IllegalArgumentException("DID not found: $did")
        val updated = updater(current)
        documents[did] = updated
        updated
    }

    override suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        documents.remove(did) != null
    }

    /**
     * Converts a walt.id DID document (JSON) to TrustWeave's DidDocument model.
     */
    protected fun convertWaltIdDocument(waltIdDoc: JsonObject): DidDocument {
        val id = waltIdDoc["id"]?.jsonPrimitive?.content ?: ""

        val verificationMethods = waltIdDoc["verificationMethod"]
            ?.jsonArray
            ?.mapNotNull { vm ->
                val vmObj = vm.jsonObject
                VerificationMethod(
                    id = vmObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    type = vmObj["type"]?.jsonPrimitive?.content ?: "",
                    controller = vmObj["controller"]?.jsonPrimitive?.content ?: id,
                    publicKeyJwk = vmObj["publicKeyJwk"]?.jsonObject?.toMap(),
                    publicKeyMultibase = vmObj["publicKeyMultibase"]?.jsonPrimitive?.content
                )
            } ?: emptyList()

        val authentication = waltIdDoc["authentication"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content }
            ?: emptyList()

        val assertionMethod = waltIdDoc["assertionMethod"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content }
            ?: emptyList()

        val keyAgreement = waltIdDoc["keyAgreement"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content }
            ?: emptyList()

        val services = waltIdDoc["service"]
            ?.jsonArray
            ?.mapNotNull { s ->
                val sObj = s.jsonObject
                DidService(
                    id = sObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    type = sObj["type"]?.jsonPrimitive?.content ?: "",
                    serviceEndpoint = sObj["serviceEndpoint"]?.toString() ?: ""
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
            val keyHandle = kms.generateKey(algorithm, options.additionalProperties)

            // Use walt.id to create did:key
            // val waltIdDid = WaltIdDid.create("key", keyHandle.publicKeyJwk)
            // For now, create a simplified did:key format
            val didId = "z${keyHandle.id.value.replace("-", "").take(32)}"
            val did = "did:$method:$didId"

            val verificationMethodId = "$did#${keyHandle.id}"
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

            documents[did] = document
            document
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to create did:key: ${e.message ?: "Unknown error"}",
                context = mapOf("method" to "key"),
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            // Use walt.id to resolve did:key
            // val waltIdDoc = WaltIdDid.resolve(did)
            // val document = convertWaltIdDocument(waltIdDoc)

            // For now, return from local cache or create resolution result
            val document = documents[did]
            val now = java.time.Instant.now()
            if (document != null) {
                DidResolutionResult.Success(
                    document = document,
                    documentMetadata = com.trustweave.did.DidDocumentMetadata(
                        created = now,
                        updated = now
                    ),
                    resolutionMetadata = mapOf(
                        "method" to method,
                        "provider" to "waltid"
                    )
                )
            } else {
                DidResolutionResult.Failure.NotFound(
                    did = com.trustweave.core.types.Did(did),
                    reason = "DID not found in cache",
                    resolutionMetadata = mapOf("method" to method, "provider" to "waltid")
                )
            }
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve did:key: ${e.message ?: "Unknown error"}",
                context = mapOf("method" to "key", "did" to did),
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
            val keyHandle = kms.generateKey(algorithm, options.additionalProperties)

            // Use walt.id to create did:web
            // val waltIdDid = WaltIdDid.create("web", domain, keyHandle.publicKeyJwk)
            val did = "did:$method:$domain"

            val verificationMethodId = "$did#${keyHandle.id}"
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

            documents[did] = document
            document
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to create did:web: ${e.message ?: "Unknown error"}",
                context = mapOf("method" to "web"),
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            // Use walt.id to resolve did:web
            // val waltIdDoc = WaltIdDid.resolve(did)
            // val document = convertWaltIdDocument(waltIdDoc)

            val document = documents[did]
            val now = java.time.Instant.now()
            if (document != null) {
                DidResolutionResult.Success(
                    document = document,
                    documentMetadata = com.trustweave.did.DidDocumentMetadata(
                        created = now,
                        updated = now
                    ),
                    resolutionMetadata = mapOf(
                        "method" to method,
                        "provider" to "waltid"
                    )
                )
            } else {
                DidResolutionResult.Failure.NotFound(
                    did = com.trustweave.core.types.Did(did),
                    reason = "DID not found in cache",
                    resolutionMetadata = mapOf("method" to method, "provider" to "waltid")
                )
            }
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve did:web: ${e.message ?: "Unknown error"}",
                context = mapOf("method" to "web", "did" to did),
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

    override fun create(methodName: String, options: com.trustweave.did.DidCreationOptions): DidMethod? {
        // Get KMS from options or discover it via SPI
        val kms = options.additionalProperties["kms"] as? KeyManagementService
            ?: run {
                // Try to discover KMS via SPI
                val kmsProviders = java.util.ServiceLoader.load(com.trustweave.kms.spi.KeyManagementServiceProvider::class.java)
                kmsProviders.find { it.name == "waltid" }?.create(options.additionalProperties)
                    ?: throw IllegalStateException("No KeyManagementService available. Provide 'kms' in options or ensure walt.id KMS provider is registered.")
            }

        return when (methodName.lowercase()) {
            "key" -> WaltIdKeyMethod(kms)
            "web" -> WaltIdWebMethod(kms)
            else -> null
        }
    }
}

