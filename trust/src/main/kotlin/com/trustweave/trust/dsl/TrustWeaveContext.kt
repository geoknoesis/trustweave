package com.trustweave.trust.dsl

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.verifier.CredentialVerifier
import com.trustweave.trust.dsl.credential.*
import com.trustweave.wallet.Wallet
import com.trustweave.trust.dsl.wallet.*
import com.trustweave.trust.dsl.did.*
import com.trustweave.credential.anchor.CredentialAnchorService
import com.trustweave.did.DidDocument
import com.trustweave.did.DidMethod
import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.verifier.DelegationChainResult
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.services.KmsService
import com.trustweave.credential.revocation.StatusListManager
import com.trustweave.credential.schema.SchemaRegistry
import com.trustweave.wallet.services.WalletFactory
import com.trustweave.trust.TrustRegistry
import com.trustweave.trust.types.ProofType
import com.trustweave.trust.types.Did
import com.trustweave.trust.types.VerificationResult
import com.trustweave.trust.dsl.KeyRotationBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TrustWeave Context.
 *
 * Provides DSL operations that automatically use the TrustWeave configuration.
 * This context makes it easy to issue, verify, and manage credentials using
 * the configured TrustWeave components.
 *
 * **Example Usage**:
 * ```kotlin
 * val trustWeave = trustWeave { ... }
 *
 * val credential = trustWeave.issue {
 *     credential { ... }
 *     by(issuerDid = "did:key:issuer", keyId = "key-1")
 * }
 * ```
 */
class TrustWeaveContext(
    private val config: TrustWeaveConfig
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
    override suspend fun resolveDid(did: Did): DidDocument? {
        val resolution = config.registries.didRegistry.resolve(did.value)
        return when (resolution) {
            is DidResolutionResult.Success -> resolution.document
            else -> null
        }
    }

    /**
     * Get a DID resolver for delegation operations.
     * This returns the native DID resolver interface used by did:core.
     */
    override fun getDidResolver(): DidResolver {
        return DidResolver { did ->
            runCatching { config.registries.didRegistry.resolve(did.value) }
                .getOrNull()
        }
    }

    /**
     * Get wallet factory (WalletDslProvider implementation).
     */
    override fun getWalletFactory(): WalletFactory? {
        return config.walletFactory
    }

    /**
     * Get an anchor client by chain ID.
     */
    fun getAnchorClient(chainId: String): Any? {
        return config.registries.blockchainRegistry.get(chainId)
    }

    /**
     * Get the TrustWeave config (for internal use).
     */
    fun getConfig(): TrustWeaveConfig {
        return config
    }

    // CredentialDslProvider implementation
    override fun getIssuer() = config.issuer

    override fun getVerifier(): CredentialVerifier {
        val resolver = config.didResolver ?: getDidResolver()
        return CredentialVerifier(defaultDidResolver = resolver)
    }

    override fun getStatusListManager(): StatusListManager? {
        return config.statusListManager as? StatusListManager
    }

    override fun getDefaultProofType(): ProofType {
        return config.credentialConfig.defaultProofType
    }

    /**
     * Get the credential anchor service.
     */
    fun getAnchorService(): CredentialAnchorService {
        val anchorClient = config.registries.blockchainRegistry.getAllClients().values.firstOrNull()
            ?: throw IllegalStateException(
                "No anchor client configured. " +
                "Configure it in TrustWeave.build { anchor { chain(\"algorand:testnet\") { provider(\"inMemory\") } } }"
            )
        return CredentialAnchorService(anchorClient)
    }

    /**
     * Get the schema registry.
     */
    fun getSchemaRegistry(): SchemaRegistry {
        return SchemaRegistry
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
    fun getTrustRegistry(): TrustRegistry? {
        return config.trustRegistry as? TrustRegistry
    }

    /**
     * Get the KMS service adapter.
     */
    fun getKmsService(): KmsService? {
        return config.kmsService
    }

    /**
     * Issue a credential using the TrustWeave configuration.
     * Uses CredentialDslProvider to delegate to credential DSL.
     */
    suspend fun issue(block: IssuanceBuilder.() -> Unit): VerifiableCredential = withContext(Dispatchers.IO) {
        (this@TrustWeaveContext as CredentialDslProvider).issue(block)
    }

    /**
     * Verify a credential using the TrustWeave configuration.
     * Uses CredentialDslProvider to delegate to credential DSL.
     */
    suspend fun verify(block: VerificationBuilder.() -> Unit): VerificationResult {
        return (this as CredentialDslProvider).verify(block)
    }

    /**
     * Create a wallet using the TrustWeave configuration.
     * Delegates to WalletDslProvider extension function.
     */
    suspend fun wallet(block: WalletBuilder.() -> Unit): Wallet {
        return (this as WalletDslProvider).wallet(block)
    }

    /**
     * Create a DID using the TrustWeave configuration.
     * Delegates to DidDslProvider extension function.
     */
    suspend fun createDid(block: DidBuilder.() -> Unit): Did {
        return (this as DidDslProvider).createDid(block)
    }

    /**
     * Update a DID document using the TrustWeave configuration.
     * Delegates to DidDslProvider extension function.
     */
    suspend fun updateDid(block: DidDocumentBuilder.() -> Unit): DidDocument {
        return (this as DidDslProvider).updateDid(block) as DidDocument
    }

    /**
     * Verify a delegation chain using the TrustWeave configuration.
     * Delegates to DidDslProvider extension function.
     */
    suspend fun delegate(block: suspend DelegationBuilder.() -> Unit): DelegationChainResult {
        return (this as DidDslProvider).delegate(block)
    }

    /**
     * Get KMS from TrustWeave.
     */
    fun getKms(): KeyManagementService? {
        return config.kms as? KeyManagementService
    }

    /**
     * Rotate a key in a DID document using the TrustWeave configuration.
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
 * Internal function to get DSL operations from TrustWeave config.
 * This is used internally by TrustWeave facade.
 */
internal fun TrustWeaveConfig.getDslContext(): TrustWeaveContext {
    return TrustWeaveContext(this)
}

