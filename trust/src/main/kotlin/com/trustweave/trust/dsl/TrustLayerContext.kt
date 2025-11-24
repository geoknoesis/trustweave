package com.trustweave.trust.dsl

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.verifier.CredentialVerifier
import com.trustweave.credential.dsl.CredentialDslProvider
import com.trustweave.credential.dsl.IssuanceBuilder
import com.trustweave.credential.dsl.VerificationBuilder
import com.trustweave.wallet.Wallet
import com.trustweave.wallet.dsl.WalletBuilder
import com.trustweave.wallet.dsl.WalletDslProvider
import com.trustweave.trust.dsl.did.DidDslProvider
import com.trustweave.did.resolver.DidResolver
import com.trustweave.credential.anchor.CredentialAnchorService
import com.trustweave.did.DidDocument
import com.trustweave.did.DidMethod
import com.trustweave.kms.services.KmsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Trust Layer Context.
 * 
 * Provides DSL operations that automatically use the trust layer configuration.
 * This context makes it easy to issue, verify, and manage credentials using
 * the configured trust layer components.
 * 
 * **Example Usage**:
 * ```kotlin
 * val trustLayer = trustLayer { ... }
 * 
 * val credential = trustLayer.issue {
 *     credential { ... }
 *     by(issuerDid = "did:key:issuer", keyId = "key-1")
 * }
 * ```
 */
class TrustLayerContext(
    private val config: TrustLayerConfig
) : DidDslProvider, WalletDslProvider, CredentialDslProvider {
    /**
     * Get a DID method by name.
     */
    override fun getDidMethod(name: String): DidMethod? {
        return config.registries.didRegistry.get(name) as? DidMethod
    }
    
    /**
     * Resolve a DID to a DID document.
     */
    override suspend fun resolveDid(did: String): DidDocument? {
        return config.registries.didRegistry.resolve(did)?.document as? DidDocument
    }
    
    /**
     * Get a DID resolver for delegation operations.
     * This returns the native DID resolver interface used by did:core.
     */
    override fun getDidResolver(): DidResolver {
        return DidResolver { did ->
            runCatching { config.registries.didRegistry.resolve(did) }
                .getOrNull()
        }
    }
    
    /**
     * Get wallet factory (WalletDslProvider implementation).
     */
    override fun getWalletFactory(): com.trustweave.wallet.services.WalletFactory? {
        return config.walletFactory
    }
    
    /**
     * Get an anchor client by chain ID.
     */
    fun getAnchorClient(chainId: String): Any? {
        return config.registries.blockchainRegistry.get(chainId)
    }
    
    /**
     * Get the trust layer config (for internal use).
     */
    fun getConfig(): TrustLayerConfig {
        return config
    }
    
    // CredentialDslProvider implementation
    override fun getIssuer() = config.issuer
    
    override fun getVerifier(): CredentialVerifier {
        val resolver = config.didResolver ?: getDidResolver()
        return CredentialVerifier(defaultDidResolver = resolver)
    }
    
    override fun getStatusListManager(): com.trustweave.credential.revocation.StatusListManager? {
        return config.statusListManager as? com.trustweave.credential.revocation.StatusListManager
    }
    
    override fun getDefaultProofType(): String {
        return config.credentialConfig.defaultProofType
    }
    
    /**
     * Get the credential anchor service.
     */
    fun getAnchorService(): CredentialAnchorService {
        val anchorClient = config.registries.blockchainRegistry.getAllClients().values.firstOrNull()
            ?: throw IllegalStateException("No anchor client configured in trust layer")
        return CredentialAnchorService(anchorClient)
    }
    
    /**
     * Get the schema registry.
     */
    fun getSchemaRegistry(): com.trustweave.credential.schema.SchemaRegistry {
        return com.trustweave.credential.schema.SchemaRegistry
    }
    
    /**
     * Get the DID registry.
     */
    fun getDidRegistry(): Any? {
        return config.registries.didRegistry
    }
    
    /**
     * Get the trust registry.
     */
    fun getTrustRegistry(): com.trustweave.trust.TrustRegistry? {
        return config.trustRegistry as? com.trustweave.trust.TrustRegistry
    }
    
    /**
     * Get the KMS service adapter.
     */
    fun getKmsService(): KmsService? {
        return config.kmsService
    }
    
    /**
     * Issue a credential using the trust layer configuration.
     * Uses CredentialDslProvider to delegate to credential DSL.
     */
    suspend fun issue(block: IssuanceBuilder.() -> Unit): VerifiableCredential = withContext(Dispatchers.IO) {
        (this@TrustLayerContext as CredentialDslProvider).issue(block)
    }
    
    /**
     * Verify a credential using the trust layer configuration.
     * Uses CredentialDslProvider to delegate to credential DSL.
     */
    suspend fun verify(block: VerificationBuilder.() -> Unit): com.trustweave.credential.CredentialVerificationResult {
        return (this as CredentialDslProvider).verify(block)
    }
    
    /**
     * Create a wallet using the trust layer configuration.
     * Delegates to WalletDslProvider extension function.
     */
    suspend fun wallet(block: WalletBuilder.() -> Unit): Wallet {
        return (this as WalletDslProvider).wallet(block)
    }
    
    /**
     * Create a DID using the trust layer configuration.
     * Delegates to DidDslProvider extension function.
     */
    suspend fun createDid(block: com.trustweave.did.dsl.DidBuilder.() -> Unit): String {
        return (this as DidDslProvider).createDid(block)
    }
    
    /**
     * Update a DID document using the trust layer configuration.
     * Delegates to DidDslProvider extension function.
     */
    suspend fun updateDid(block: com.trustweave.did.dsl.DidDocumentBuilder.() -> Unit): Any {
        return (this as DidDslProvider).updateDid(block)
    }
    
    /**
     * Verify a delegation chain using the trust layer configuration.
     * Delegates to DidDslProvider extension function.
     */
    suspend fun delegate(block: suspend com.trustweave.did.dsl.DelegationBuilder.() -> Unit): com.trustweave.did.delegation.DelegationChainResult {
        return (this as DidDslProvider).delegate(block)
    }
    
    /**
     * Get KMS service from trust layer.
     */
    fun getKmsService(): KmsService? {
        return config.kmsService
    }
    
    /**
     * Get KMS from trust layer.
     */
    fun getKms(): com.trustweave.kms.KeyManagementService? {
        return config.kms
    }
    
    /**
     * Rotate a key in a DID document using the trust layer configuration.
     */
    suspend fun rotateKey(block: KeyRotationBuilder.() -> Unit): Any {
        val kms = getKms() ?: throw IllegalStateException("KMS is not configured")
        val kmsService = getKmsService() ?: throw IllegalStateException("KmsService is not configured")
        val builder = KeyRotationBuilder(this, kms, kmsService)
        builder.block()
        return builder.rotate()
    }
}

/**
 * Extension function to get DSL operations from trust layer config.
 */
fun TrustLayerConfig.dsl(): TrustLayerContext {
    return TrustLayerContext(this)
}

/**
 * Extension function for direct DSL operations on trust layer.
 */
suspend fun TrustLayerConfig.issue(block: IssuanceBuilder.() -> Unit): VerifiableCredential {
    return dsl().issue(block)
}

/**
 * Extension function for direct verification on trust layer.
 */
suspend fun TrustLayerConfig.verify(block: VerificationBuilder.() -> Unit): com.trustweave.credential.CredentialVerificationResult {
    return dsl().verify(block)
}

/**
 * Extension function for direct wallet creation on trust layer.
 */
suspend fun TrustLayerConfig.wallet(block: WalletBuilder.() -> Unit): Wallet {
    return dsl().wallet(block)
}

/**
 * Extension function for direct DID creation on trust layer.
 */
suspend fun TrustLayerConfig.createDid(block: com.trustweave.did.dsl.DidBuilder.() -> Unit): String {
    return dsl().createDid(block)
}

/**
 * Extension function for direct DID document update on trust layer.
 */
suspend fun TrustLayerConfig.updateDid(block: com.trustweave.did.dsl.DidDocumentBuilder.() -> Unit): Any {
    return dsl().updateDid(block)
}

/**
 * Extension function for direct delegation on trust layer.
 */
suspend fun TrustLayerConfig.delegate(block: suspend com.trustweave.did.dsl.DelegationBuilder.() -> Unit): com.trustweave.did.delegation.DelegationChainResult {
    return dsl().delegate(block)
}

/**
 * Extension function for direct key rotation on trust layer.
 */
suspend fun TrustLayerConfig.rotateKey(block: KeyRotationBuilder.() -> Unit): Any {
    return dsl().rotateKey(block)
}

