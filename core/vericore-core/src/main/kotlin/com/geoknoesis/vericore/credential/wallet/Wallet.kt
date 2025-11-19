package com.geoknoesis.vericore.credential.wallet

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlin.reflect.KClass
import java.time.Instant

/**
 * Unified wallet interface.
 * 
 * A wallet is a composition of capability interfaces. All wallets must implement
 * CredentialStorage. Other capabilities are optional and can be checked using
 * Kotlin's type system (e.g., `wallet is CredentialOrganization`).
 * 
 * **Example Usage**:
 * ```kotlin
 * val wallet: Wallet = createWallet()
 * 
 * // Core operations (always available)
 * val id = wallet.store(credential)
 * val credential = wallet.get(id)
 * 
 * // Optional capabilities (type-safe check)
 * if (wallet is CredentialOrganization) {
 *     wallet.createCollection("My Collection")
 *     wallet.tagCredential(id, setOf("important"))
 * }
 * 
 * if (wallet is CredentialPresentation) {
 *     val presentation = wallet.createPresentation(
 *         credentialIds = listOf(id),
 *         holderDid = "did:key:holder",
 *         options = PresentationOptions(...)
 *     )
 * }
 * 
 * if (wallet is DidManagement) {
 *     val did = wallet.createDid("key")
 *     println("Wallet DID: ${wallet.walletDid}")
 * }
 * 
 * // Get statistics
 * val stats = wallet.getStatistics()
 * println("Total credentials: ${stats.totalCredentials}")
 * ```
 */
interface Wallet : CredentialStorage {
    /**
     * Wallet identifier (DID or UUID).
     */
    val walletId: String
    
    /**
     * Wallet capabilities for runtime discovery.
     * 
     * Useful for UI or dynamic feature detection.
     * For compile-time type safety, use `wallet is CredentialOrganization` instead.
     */
    val capabilities: WalletCapabilities
        get() = WalletCapabilities(
            credentialStorage = true,
            credentialQuery = true,
            collections = this is CredentialOrganization,
            tags = this is CredentialOrganization,
            metadata = this is CredentialOrganization,
            archive = this is CredentialLifecycle,
            refresh = this is CredentialLifecycle,
            createPresentation = this is CredentialPresentation,
            selectiveDisclosure = this is CredentialPresentation,
            didManagement = this is DidManagement,
            keyManagement = this is KeyManagement,
            credentialIssuance = this is CredentialIssuance
        )
    
    /**
     * Check if wallet supports a capability interface.
     * 
     * Type-safe alternative to string-based capability checking.
     * 
     * **Example**:
     * ```kotlin
 * if (wallet.supports(CredentialOrganization::class)) {
 *     // Use CredentialOrganization methods
 * }
 * ```
     * 
     * @param capability Capability class
     * @return true if wallet implements the capability
     */
    fun <T : Any> supports(capability: KClass<T>): Boolean {
        return when (capability) {
            CredentialOrganization::class -> this is CredentialOrganization
            CredentialLifecycle::class -> this is CredentialLifecycle
            CredentialPresentation::class -> this is CredentialPresentation
            DidManagement::class -> this is DidManagement
            KeyManagement::class -> this is KeyManagement
            CredentialIssuance::class -> this is CredentialIssuance
            else -> false
        }
    }
    
    /**
     * Get wallet statistics.
     * 
     * Provides an overview of credentials and wallet state.
     * 
     * **Example**:
     * ```kotlin
 * val stats = wallet.getStatistics()
 * println("Valid credentials: ${stats.validCredentials}/${stats.totalCredentials}")
 * ```
     * 
     * @return Wallet statistics
     */
    suspend fun getStatistics(): WalletStatistics {
        val credentials = list()
        val now = Instant.now()
        
        return WalletStatistics(
            totalCredentials = credentials.size,
            validCredentials = credentials.count { credential ->
                credential.proof != null &&
                (credential.expirationDate?.let { expirationDate ->
                    try {
                        val expiration = Instant.parse(expirationDate)
                        now.isBefore(expiration)
                    } catch (e: Exception) {
                        false
                    }
                } ?: true) &&
                credential.credentialStatus == null
            },
            expiredCredentials = credentials.count { credential ->
                credential.expirationDate?.let { expirationDate ->
                    try {
                        val expiration = Instant.parse(expirationDate)
                        now.isAfter(expiration)
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
            },
            revokedCredentials = credentials.count { it.credentialStatus != null },
            collectionsCount = if (this is CredentialOrganization) {
                listCollections().size
            } else {
                0
            },
            tagsCount = if (this is CredentialOrganization) {
                getAllTags().size
            } else {
                0
            },
            archivedCount = if (this is CredentialLifecycle) {
                getArchived().size
            } else {
                0
            }
        )
    }
}

/**
 * Extension function for type-safe capability access.
 * 
 * **Example**:
 * ```kotlin
 * wallet.withOrganization { org ->
 *     org.createCollection("My Collection")
 * }
 * ```
 */
inline fun <T> Wallet.withOrganization(block: (CredentialOrganization) -> T): T? {
    return (this as? CredentialOrganization)?.let(block)
}

/**
 * Extension function for type-safe capability access.
 */
inline fun <T> Wallet.withLifecycle(block: (CredentialLifecycle) -> T): T? {
    return (this as? CredentialLifecycle)?.let(block)
}

/**
 * Extension function for type-safe capability access.
 */
inline fun <T> Wallet.withPresentation(block: (CredentialPresentation) -> T): T? {
    return (this as? CredentialPresentation)?.let(block)
}

/**
 * Extension function for type-safe capability access.
 */
inline fun <T> Wallet.withDidManagement(block: (DidManagement) -> T): T? {
    return (this as? DidManagement)?.let(block)
}

/**
 * Extension function for type-safe capability access.
 */
inline fun <T> Wallet.withKeyManagement(block: (KeyManagement) -> T): T? {
    return (this as? KeyManagement)?.let(block)
}

/**
 * Extension function for type-safe capability access.
 */
inline fun <T> Wallet.withIssuance(block: (CredentialIssuance) -> T): T? {
    return (this as? CredentialIssuance)?.let(block)
}

