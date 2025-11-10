package io.geoknoesis.vericore.spi.services

/**
 * Default stub implementations for service interfaces. These provide
 * compatibility for scenarios where dedicated adapters have not been wired in
 * yet. Applications are encouraged to supply real implementations via
 * `AdapterLoader`-compatible modules, but these fallbacks keep the DSL usable
 * in reduced environments (e.g., documentation snippets or tests).
 */

/**
 * Default implementation of DidDocumentAccess that returns stub values.
 */
class DefaultDidDocumentAccessImpl : DidDocumentAccess {
    override fun getDocument(result: Any): Any? = result
    override fun getAssertionMethod(doc: Any): List<String> = emptyList()
    override fun getAuthentication(doc: Any): List<String> = emptyList()
    override fun getKeyAgreement(doc: Any): List<String> = emptyList()
    override fun getCapabilityInvocation(doc: Any): List<String> = emptyList()
    override fun getCapabilityDelegation(doc: Any): List<String> = emptyList()
    override fun getVerificationMethod(doc: Any): List<Any> = emptyList()
    override fun getService(doc: Any): List<Any> = emptyList()
    override fun getContext(doc: Any): List<String> = emptyList()

    override fun createVerificationMethod(
        id: String,
        type: String,
        controller: String,
        publicKeyJwk: Map<String, Any?>?,
        publicKeyMultibase: String?
    ): Any {
        return object {
            val id = id
            val type = type
            val controller = controller
        }
    }

    override fun createService(id: String, type: String, serviceEndpoint: Any): Any {
        return object {
            val id = id
            val type = type
            val serviceEndpoint = serviceEndpoint
        }
    }

    override fun copyDocument(
        doc: Any,
        id: String?,
        context: List<String>?,
        alsoKnownAs: List<String>?,
        controller: List<String>?,
        verificationMethod: List<Any>?,
        authentication: List<String>?,
        assertionMethod: List<String>?,
        keyAgreement: List<String>?,
        capabilityInvocation: List<String>?,
        capabilityDelegation: List<String>?,
        service: List<Any>?
    ): Any {
        return doc
    }
}

/**
 * Default implementation of VerificationMethodAccess that returns stub values.
 */
class DefaultVerificationMethodAccessImpl : VerificationMethodAccess {
    override fun getId(vm: Any): String = "stub-vm-id"
    override fun getController(vm: Any): String = "stub-controller"
}

/**
 * Default implementation of DidMethodService that returns stub values.
 */
class DefaultDidMethodServiceImpl : DidMethodService {
    override suspend fun createDid(didMethod: Any, options: Any?): Any {
        return "did:key:stub"
    }

    override suspend fun updateDid(didMethod: Any, did: String, updater: (Any) -> Any): Any {
        return did
    }

    override fun getDidId(document: Any): String {
        return document.toString()
    }
}

/**
 * Default implementation of KmsService that returns stub values.
 */
class DefaultKmsServiceImpl : KmsService {
    override suspend fun generateKey(kms: Any, algorithm: String, options: Map<String, Any?>): Any {
        return object {
            val id = "stub-key-id"
            val algorithm = algorithm
            val publicKeyJwk = emptyMap<String, Any?>()
        }
    }

    override fun getKeyId(keyHandle: Any): String {
        return "stub-key-id"
    }

    override fun getPublicKeyJwk(keyHandle: Any): Map<String, Any?>? {
        return emptyMap()
    }
}

/**
 * Default implementation of WalletFactory that returns stub values.
 */
class DefaultWalletFactoryImpl : WalletFactory {
    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: Map<String, Any?>
    ): Any {
        throw UnsupportedOperationException(
            "DefaultWalletFactoryImpl is a stub. " +
                "Register a real WalletFactory using AdapterLoader-friendly module or use VeriCore facade."
        )
    }
}

/**
 * Extension methods for Any to provide backward compatibility.
 */
suspend fun Any.getDocument(): Any? = this

suspend fun Any.getId(): String? = this.toString()

suspend fun Any.getController(): String? = null

suspend fun Any.getVerificationMethod(): List<Any>? = emptyList()

suspend fun Any.getAssertionMethod(): List<Any>? = emptyList()

suspend fun Any.getAuthentication(): List<Any>? = emptyList()

suspend fun Any.getKeyAgreement(): List<Any>? = emptyList()

suspend fun Any.getCapabilityInvocation(): List<String>? = emptyList()

suspend fun Any.getCapabilityDelegation(): List<String>? = emptyList()

operator fun Any?.not(): Boolean = this == null

/**
 * Extension method for KmsService access.
 */
suspend fun Any.generateKey(algorithm: String): Any {
    return object {
        val id = "stub-key"
        val algorithm = algorithm
        val publicKeyJwk = emptyMap<String, Any?>()
    }
}

fun Any.getPublicKeyJwk(): Map<String, Any?>? = emptyMap()

fun Any.getKeyId(): String = "stub-key-id"

/**
 * Extension methods for DidMethodService.
 */
suspend fun Any.createDid(options: Any? = null): Any {
    return "did:key:stub"
}

suspend fun Any.updateDid(updater: (Any) -> Any): Any {
    return this
}

fun Any.getDidId(): String? = this.toString()

/**
 * Extension method for BlockchainRegistryService.
 */
fun Any.register(chainId: String, client: Any) {
    // Stub - no-op
}

/**
 * Extension method for WalletFactory.
 */
suspend fun Any.create(
    holderDid: String,
    walletId: String? = null,
    walletDid: String? = null,
    options: Map<String, Any?> = emptyMap()
): Any {
    return object {
        val walletId = walletId ?: "stub-wallet"
        val holderDid = holderDid
    }
}


