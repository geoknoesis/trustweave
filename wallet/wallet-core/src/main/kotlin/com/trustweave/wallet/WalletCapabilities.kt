package com.trustweave.wallet

/**
 * Wallet capabilities for runtime discovery.
 *
 * Useful for UI that needs to show/hide features dynamically.
 * For compile-time type safety, use `wallet is CredentialOrganization` instead.
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
 * // Compile-time type safety (preferred)
 * if (wallet is CredentialOrganization) {
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
     * Check if a capability is supported by feature name.
     *
     * @param feature Feature name (e.g., "collections", "tags")
     * @return true if supported
     */
    fun supports(feature: String): Boolean {
        return when (feature.lowercase()) {
            "collections" -> collections
            "tags" -> tags
            "metadata" -> metadata
            "archive" -> archive
            "refresh" -> refresh
            "createpresentation", "presentation" -> createPresentation
            "selectivedisclosure", "selective-disclosure" -> selectiveDisclosure
            "didmanagement", "did-management" -> didManagement
            "keymanagement", "key-management" -> keyManagement
            "credentialissuance", "credential-issuance" -> credentialIssuance
            else -> false
        }
    }
}

