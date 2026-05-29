package org.trustweave.wallet

/**
 * Wallet capabilities for runtime discovery.
 *
 * Useful for UI that needs to show/hide features dynamically.
 * For compile-time type safety, prefer `wallet is CredentialCollections` or
 * `wallet is CredentialTagging` over `wallet is CredentialOrganization`.
 *
 * **Example Usage**:
 * ```kotlin
 * val wallet: Wallet = createWallet()
 *
 * // Runtime discovery (for UI)
 * if (wallet.capabilities.collections) {
 *     // Show collection UI
 * }
 *
 * // Compile-time type safety (preferred — use narrowest interface needed)
 * if (wallet is CredentialCollections) {
 *     wallet.createCollection("My Collection")
 * }
 * ```
 */
data class WalletCapabilities(
    val credentialStorage: Boolean = true,
    val credentialQuery: Boolean = true,
    val collections: Boolean = false,
    val tags: Boolean = false,
    val metadata: Boolean = false,
    val archive: Boolean = false,
    val refresh: Boolean = false,
    val createPresentation: Boolean = false,
    val selectiveDisclosure: Boolean = false,
    val didManagement: Boolean = false,
    val keyManagement: Boolean = false,
    val credentialIssuance: Boolean = false
) {
    /**
     * Lookup table derived from property values.
     *
     * Adding a new capability property only requires updating this map — [supports] itself
     * never needs to change (OCP). Multiple aliases per feature are supported.
     */
    private val featureIndex: Map<String, Boolean>
        get() = mapOf(
            "credentials" to credentialStorage,
            "credentialstorage" to credentialStorage,
            "query" to credentialQuery,
            "credentialquery" to credentialQuery,
            "collections" to collections,
            "tags" to tags,
            "metadata" to metadata,
            "archive" to archive,
            "refresh" to refresh,
            "createpresentation" to createPresentation,
            "presentation" to createPresentation,
            "selectivedisclosure" to selectiveDisclosure,
            "selective-disclosure" to selectiveDisclosure,
            "didmanagement" to didManagement,
            "did-management" to didManagement,
            "keymanagement" to keyManagement,
            "key-management" to keyManagement,
            "credentialissuance" to credentialIssuance,
            "credential-issuance" to credentialIssuance,
        )

    /**
     * Check if a capability is supported by feature name.
     *
     * @param feature Feature name (e.g., "collections", "tags")
     * @return true if supported
     */
    fun supports(feature: String): Boolean = featureIndex[feature.lowercase()] ?: false
}
