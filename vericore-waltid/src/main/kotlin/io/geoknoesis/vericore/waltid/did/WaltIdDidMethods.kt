package io.geoknoesis.vericore.waltid.did

import io.geoknoesis.vericore.core.VeriCoreException
import io.geoknoesis.vericore.did.*
import io.geoknoesis.vericore.did.spi.DidMethodProvider
import io.geoknoesis.vericore.kms.KeyManagementService
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
     * Converts a walt.id DID document (JSON) to VeriCore's DidDocument model.
     */
    protected fun convertWaltIdDocument(waltIdDoc: JsonObject): DidDocument {
        val id = waltIdDoc["id"]?.jsonPrimitive?.content ?: ""
        
        val verificationMethods = waltIdDoc["verificationMethod"]
            ?.jsonArray
            ?.mapNotNull { vm ->
                val vmObj = vm.jsonObject
                VerificationMethodRef(
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
                Service(
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
                is JsonPrimitive -> when {
                    value.isString -> value.content
                    value.booleanOrNull != null -> value.boolean
                    value.longOrNull != null -> value.long
                    value.doubleOrNull != null -> value.double
                    else -> value.content
                }
                is JsonObject -> value.toMap()
                is JsonArray -> value.map { (it as? JsonObject)?.toMap() ?: it.toString() }
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

    override suspend fun createDid(options: Map<String, Any?>): DidDocument = withContext(Dispatchers.IO) {
        try {
            val algorithm = options["algorithm"] as? String ?: "Ed25519"
            val keyHandle = kms.generateKey(algorithm, options)

            // Use walt.id to create did:key
            // val waltIdDid = WaltIdDid.create("key", keyHandle.publicKeyJwk)
            // For now, create a simplified did:key format
            val didId = "z${keyHandle.id.replace("-", "").take(32)}"
            val did = "did:$method:$didId"

            val verificationMethodId = "$did#${keyHandle.id}"
            val verificationMethod = VerificationMethodRef(
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
            throw VeriCoreException("Failed to create did:key: ${e.message}", e)
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            // Use walt.id to resolve did:key
            // val waltIdDoc = WaltIdDid.resolve(did)
            // val document = convertWaltIdDocument(waltIdDoc)
            
            // For now, return from local cache or create resolution result
            val document = documents[did]
            DidResolutionResult(
                document = document,
                documentMetadata = mapOf(
                    "created" to System.currentTimeMillis(),
                    "updated" to System.currentTimeMillis()
                ),
                resolutionMetadata = mapOf(
                    "method" to method,
                    "provider" to "waltid"
                )
            )
        } catch (e: Exception) {
            throw VeriCoreException("Failed to resolve did:key: ${e.message}", e)
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

    override suspend fun createDid(options: Map<String, Any?>): DidDocument = withContext(Dispatchers.IO) {
        try {
            val domain = options["domain"] as? String
                ?: throw IllegalArgumentException("did:web requires 'domain' option")
            
            val algorithm = options["algorithm"] as? String ?: "Ed25519"
            val keyHandle = kms.generateKey(algorithm, options)

            // Use walt.id to create did:web
            // val waltIdDid = WaltIdDid.create("web", domain, keyHandle.publicKeyJwk)
            val did = "did:$method:$domain"

            val verificationMethodId = "$did#${keyHandle.id}"
            val verificationMethod = VerificationMethodRef(
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
            throw VeriCoreException("Failed to create did:web: ${e.message}", e)
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            // Use walt.id to resolve did:web
            // val waltIdDoc = WaltIdDid.resolve(did)
            // val document = convertWaltIdDocument(waltIdDoc)
            
            val document = documents[did]
            DidResolutionResult(
                document = document,
                documentMetadata = mapOf(
                    "created" to System.currentTimeMillis(),
                    "updated" to System.currentTimeMillis()
                ),
                resolutionMetadata = mapOf(
                    "method" to method,
                    "provider" to "waltid"
                )
            )
        } catch (e: Exception) {
            throw VeriCoreException("Failed to resolve did:web: ${e.message}", e)
        }
    }
}

/**
 * SPI provider for walt.id DID methods.
 */
class WaltIdDidMethodProvider : DidMethodProvider {

    override val name: String = "waltid"
    override val supportedMethods: List<String> = listOf("key", "web")

    override fun create(methodName: String, options: Map<String, Any?>): DidMethod? {
        // Get KMS from options or discover it via SPI
        val kms = options["kms"] as? KeyManagementService
            ?: run {
                // Try to discover KMS via SPI
                val kmsProviders = java.util.ServiceLoader.load(io.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider::class.java)
                kmsProviders.find { it.name == "waltid" }?.create(options)
                    ?: throw IllegalStateException("No KeyManagementService available. Provide 'kms' in options or ensure walt.id KMS provider is registered.")
            }

        return when (methodName.lowercase()) {
            "key" -> WaltIdKeyMethod(kms)
            "web" -> WaltIdWebMethod(kms)
            else -> null
        }
    }
}

