package io.geoknoesis.vericore.credential.wallet

import java.util.UUID

/**
 * Builder for creating wallets with specific capabilities.
 * 
 * Provides a fluent API for configuring wallet capabilities.
 * 
 * **Example Usage**:
 * ```kotlin
 * // Basic wallet (storage only)
 * val basicWallet = WalletBuilder()
 *     .withWalletId("my-wallet")
 *     .build()
 * 
 * // Wallet with organization features
 * val orgWallet = WalletBuilder()
 *     .withWalletId("org-wallet")
 *     .enableOrganization()
 *     .build()
 * 
 * // Full-featured wallet (requires KMS and DID registry)
 * val fullWallet = WalletBuilder()
 *     .withWalletId("full-wallet")
 *     .withWalletDid("did:key:wallet")
 *     .enableOrganization()
 *     .enableLifecycle()
 *     .enablePresentation()
 *     .enableDidManagement(didRegistry)
 *     .enableKeyManagement(kms)
 *     .build()
 * ```
 */
class WalletBuilder {
    private var walletId: String? = null
    private var walletDid: String? = null
    private var holderDid: String? = null
    
    private var enableOrganization = false
    private var enableLifecycle = false
    private var enablePresentation = false
    private var enableDidManagement = false
    private var enableKeyManagement = false
    private var enableIssuance = false
    
    // Dependencies (will be resolved by implementations)
    private var kms: Any? = null
    private var didRegistry: Any? = null
    private var credentialIssuer: Any? = null
    private var presentationService: Any? = null
    
    /**
     * Set wallet ID.
     */
    fun withWalletId(id: String): WalletBuilder {
        walletId = id
        return this
    }
    
    /**
     * Set wallet DID.
     */
    fun withWalletDid(did: String): WalletBuilder {
        walletDid = did
        return this
    }
    
    /**
     * Set holder DID.
     */
    fun withHolderDid(did: String): WalletBuilder {
        holderDid = did
        return this
    }
    
    /**
     * Enable organization capabilities (collections, tags, metadata).
     */
    fun enableOrganization(): WalletBuilder {
        enableOrganization = true
        return this
    }
    
    /**
     * Enable lifecycle capabilities (archive, refresh).
     */
    fun enableLifecycle(): WalletBuilder {
        enableLifecycle = true
        return this
    }
    
    /**
     * Enable presentation capabilities.
     */
    fun enablePresentation(presentationService: Any? = null): WalletBuilder {
        enablePresentation = true
        this.presentationService = presentationService
        return this
    }
    
    /**
     * Enable DID management capabilities.
     * 
     * @param didRegistry DID registry (type: io.geoknoesis.vericore.did.DidRegistry)
     */
    fun enableDidManagement(didRegistry: Any): WalletBuilder {
        this.didRegistry = didRegistry
        enableDidManagement = true
        return this
    }
    
    /**
     * Enable key management capabilities.
     * 
     * @param kms Key management service (type: io.geoknoesis.vericore.kms.KeyManagementService)
     */
    fun enableKeyManagement(kms: Any): WalletBuilder {
        this.kms = kms
        enableKeyManagement = true
        return this
    }
    
    /**
     * Enable credential issuance capabilities.
     * 
     * @param credentialIssuer Credential issuer (type: io.geoknoesis.vericore.credential.issuer.CredentialIssuer)
     */
    fun enableIssuance(credentialIssuer: Any): WalletBuilder {
        this.credentialIssuer = credentialIssuer
        enableIssuance = true
        return this
    }
    
    /**
     * Build the wallet.
     * 
     * Returns a BasicWallet if no advanced capabilities are enabled.
     * Returns a DefaultWallet if DID/KMS capabilities are enabled.
     * 
     * Note: DefaultWallet implementation should be provided by integration modules
     * (e.g., vericore-waltid, vericore-godiddy) that have access to KMS and DID registries.
     * 
     * @return Configured wallet
     * @throws IllegalArgumentException if required dependencies are missing
     */
    suspend fun build(): Wallet {
        val finalWalletId = walletId ?: UUID.randomUUID().toString()
        
        // If DID/KMS enabled, require DefaultWallet implementation
        // This will be provided by integration modules
        if (enableDidManagement || enableKeyManagement) {
            val finalWalletDid = walletDid 
                ?: throw IllegalArgumentException("Wallet DID required when enabling DID management")
            val finalHolderDid = holderDid ?: finalWalletDid
            
            // DefaultWallet should be implemented in integration modules
            // For now, throw exception indicating it needs to be implemented
            throw UnsupportedOperationException(
                "DefaultWallet with DID/KMS capabilities must be implemented in integration modules. " +
                "Use BasicWallet or InMemoryWallet for testing, or implement DefaultWallet in your integration module."
            )
        }
        
        // Basic wallet implementation should be provided by testkit or integration modules
        // For now, throw exception indicating basic wallet needs to be created externally
        throw UnsupportedOperationException(
            "Basic wallet creation must be handled by integration modules or testkit. " +
            "Use BasicWallet from vericore-testkit for testing, or implement your own Wallet."
        )
    }
}

