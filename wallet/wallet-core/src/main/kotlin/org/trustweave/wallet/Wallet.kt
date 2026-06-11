package org.trustweave.wallet

import org.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Unified wallet interface.
 *
 * A wallet is a composition of capability interfaces. All wallets must implement
 * CredentialStorage. Other capabilities are optional and can be checked using
 * Kotlin's type system against the narrowest interface you need.
 *
 * **Example Usage**:
 * ```kotlin
 * val wallet: Wallet = createWallet()
 *
 * // Core operations (always available)
 * val id = wallet.store(credential)
 * val credential = wallet.get(id)
 *
 * // Optional capabilities — prefer the narrowest interface you actually need
 * if (wallet is CredentialCollections) {
 *     wallet.createCollection("My Collection")
 * }
 * if (wallet is CredentialTagging) {
 *     wallet.tagCredential(id, setOf("important"))
 * }
 *
 * if (wallet is CredentialPresentation) {
 *     val presentation = wallet.createPresentation(
 *         credentialIds = listOf(id),
 *         holderDid = "did:key:holder",
 *         options = proofOptions { ... }
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
interface Wallet : CredentialStorage, AutoCloseable {
    /**
     * Wallet identifier (DID or UUID).
     */
    val walletId: String

    /**
     * Release resources owned by this wallet (e.g. connection pools).
     *
     * The default implementation is a no-op so existing implementations remain
     * source- and binary-compatible. Implementations that own closeable resources
     * (such as a connection pool they created) should override this. Wallets that
     * were handed externally managed resources must NOT close them here.
     *
     * **Example**:
     * ```kotlin
     * factory.create(...).use { wallet ->
     *     wallet.store(credential)
     * } // pool owned by the wallet is closed here
     * ```
     */
    override fun close() {
        // Default: nothing to release.
    }

    /**
     * Wallet capabilities for runtime discovery.
     *
     * Useful for UI or dynamic feature detection.
     * For compile-time type safety, use `wallet is CredentialCollections` or
     * `wallet is CredentialTagging` instead.
     */
    val capabilities: WalletCapabilities
        get() = WalletCapabilities(
            credentialStorage = true,
            credentialQuery = true,
            collections = this is CredentialCollections,
            tags = this is CredentialTagging,
            metadata = this is CredentialTagging,
            archive = this is CredentialLifecycle,
            refresh = this is CredentialLifecycle,
            createPresentation = this is CredentialPresentation,
            selectiveDisclosure = this is CredentialPresentation,
            didManagement = this is DidManagement,
            keyManagement = this is KeyManagement,
            credentialIssuance = this is CredentialIssuance
        )

    /**
     * Check if wallet supports a capability.
     *
     * **Note**: For compile-time type safety, prefer `wallet is CredentialCollections` or
     * `wallet is CredentialTagging` instead.
     * For runtime discovery, use `wallet.capabilities.supports("collections")`.
     *
     * **Example**:
     * ```kotlin
     * // Compile-time type safety (preferred)
     * if (wallet is CredentialCollections) {
     *     wallet.createCollection("My Collection")
     * }
     *
     * // Runtime discovery (for UI)
     * if (wallet.capabilities.collections) {
     *     // Show collection UI
     * }
     * ```
     */

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
        val now = Clock.System.now()

        return WalletStatistics(
            totalCredentials = credentials.size,
            validCredentials = credentials.count { credential ->
                credential.proof != null &&
                (credential.expirationDate?.let { expirationDate ->
                    now < expirationDate
                } ?: true) &&
                credential.credentialStatus == null
            },
            expiredCredentials = credentials.count { credential ->
                credential.expirationDate?.let { expirationDate ->
                    now > expirationDate
                } ?: false
            },
            revokedCredentials = credentials.count { it.credentialStatus != null },
            collectionsCount = if (this is CredentialCollections) {
                listCollections().size
            } else {
                0
            },
            tagsCount = if (this is CredentialTagging) {
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
 * Extension function for type-safe access to the [CredentialCollections] capability.
 *
 * **Example**:
 * ```kotlin
 * wallet.withCollections { it.createCollection("My Collection") }
 * ```
 */
inline fun <T> Wallet.withCollections(block: (CredentialCollections) -> T): T? =
    (this as? CredentialCollections)?.let(block)

/**
 * Extension function for type-safe access to the [CredentialTagging] capability.
 *
 * **Example**:
 * ```kotlin
 * wallet.withTagging { it.tagCredential(id, setOf("important")) }
 * ```
 */
inline fun <T> Wallet.withTagging(block: (CredentialTagging) -> T): T? =
    (this as? CredentialTagging)?.let(block)

/**
 * Extension function for type-safe access to the combined [CredentialOrganization] capability.
 *
 * Prefer [withCollections] or [withTagging] when only one capability is needed.
 */
inline fun <T> Wallet.withOrganization(block: (CredentialOrganization) -> T): T? =
    (this as? CredentialOrganization)?.let(block)

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

