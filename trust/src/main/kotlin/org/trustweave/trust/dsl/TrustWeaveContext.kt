package org.trustweave.trust.dsl

import org.trustweave.credential.model.vc.VerifiableCredential
// CredentialService from credential-api is used for verification
import org.trustweave.trust.dsl.credential.*
import org.trustweave.wallet.Wallet
import org.trustweave.trust.dsl.wallet.*
import org.trustweave.trust.dsl.did.*
// CredentialAnchorService is available via credentials:plugins:anchor module
import org.trustweave.did.model.DidDocument
import org.trustweave.did.DidMethod
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.verifier.DelegationChainResult
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.services.KmsService
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.wallet.services.WalletFactory
import org.trustweave.trust.TrustRegistry
import org.trustweave.credential.model.ProofType
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.trust.types.VerificationResult
import org.trustweave.trust.types.WalletCreationResult
import org.trustweave.trust.dsl.KeyRotationBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

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
 *     signedBy(issuerDid = "did:key:issuer", keyId = "key-1")
 * }
 * ```
 */
class TrustWeaveContext(
    private val config: TrustWeaveConfig
) : DidDslProvider, WalletDslProvider, CredentialDslProvider {
    
    private val logger = LoggerFactory.getLogger(TrustWeaveContext::class.java)
    /**
     * Get a DID method by name.
     */
    override fun getDidMethod(name: String): DidMethod? {
        val method = config.registries.didRegistry.get(name) as? DidMethod
        logger.debug("Getting DID method: name={}, found={}", name, method != null)
        return method
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
                .getOrElse { null } ?: DidResolutionResult.Failure.ResolutionError(
                    did = did,
                    reason = "DID resolution failed"
                )
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
     *
     * @param chainId The blockchain chain identifier (e.g., "algorand:testnet")
     * @return The blockchain anchor client, or null if not registered
     */
    fun getAnchorClient(chainId: String): BlockchainAnchorClient? {
        return config.registries.blockchainRegistry.get(chainId)
    }

    /**
     * Get the TrustWeave config (for internal use).
     */
    fun getConfig(): TrustWeaveConfig {
        return config
    }

    // CredentialDslProvider implementation
    override fun getIssuer() = config.issuer // CredentialService from credential-api

    override fun getVerifier(): Any? {
        // CredentialService from credential-api handles verification
        // Use credentialService.verify() method
        return null
    }

    override fun getRevocationManager(): CredentialRevocationManager? {
        return config.revocationManager
    }

    override fun getDefaultProofType(): ProofType {
        return config.credentialConfig.defaultProofType
    }

    /**
     * Get the credential anchor service.
     */
    fun getAnchorService(): Any? {
        // CredentialAnchorService is available via credentials:plugins:anchor module
        // Use extension methods: credentialService.anchorCredential(..., anchorClient, ...)
        return null
    }

    /**
     * Get the schema registry.
     */
    override fun getSchemaRegistry(): SchemaRegistry? {
        return org.trustweave.credential.schema.SchemaRegistries.default()
    }

    /**
     * Get the DID registry.
     *
     * @return The DID method registry
     */
    fun getDidRegistry(): DidMethodRegistry {
        return config.registries.didRegistry
    }

    /**
     * Get the trust registry.
     */
    fun getTrustRegistry(): TrustRegistry? {
        return config.trustRegistry
    }

    /**
     * Get the KMS service adapter.
     */
    fun getKmsService(): KmsService? {
        return config.kmsService
    }

    /**
     * Get the configured I/O dispatcher.
     * 
     * Returns the dispatcher configured in TrustWeaveConfig, or Dispatchers.IO as default.
     */
    private fun getIoDispatcher() = config.ioDispatcher

    /**
     * Issue a credential using the TrustWeave configuration.
     * Uses CredentialDslProvider to delegate to credential DSL.
     * 
     * This operation performs I/O-bound work and uses the configured dispatcher.
     *
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun issue(block: IssuanceBuilder.() -> Unit): IssuanceResult = withContext(getIoDispatcher()) {
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
     *
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun wallet(block: WalletBuilder.() -> Unit): WalletCreationResult {
        return try {
            val wallet = (this as WalletDslProvider).wallet(block)
            WalletCreationResult.Success(wallet)
        } catch (e: IllegalStateException) {
            when {
                e.message?.contains("WalletFactory", ignoreCase = true) == true ->
                    WalletCreationResult.Failure.FactoryNotConfigured(e.message ?: "Wallet factory not configured")
                e.message?.contains("holder", ignoreCase = true) == true ->
                    WalletCreationResult.Failure.InvalidHolderDid(
                        holderDid = "",
                        reason = e.message ?: "Invalid holder DID"
                    )
                else ->
                    WalletCreationResult.Failure.Other(e.message ?: "Wallet creation failed", e)
            }
        } catch (e: Throwable) {
            WalletCreationResult.Failure.Other(e.message ?: "Wallet creation failed", e)
        }
    }

    /**
     * Create a DID using the TrustWeave configuration.
     * Delegates to DidDslProvider extension function.
     *
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun createDid(block: DidBuilder.() -> Unit): DidCreationResult {
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
     * 
     * This operation uses the configured dispatcher for I/O-bound work.
     */
    suspend fun rotateKey(block: KeyRotationBuilder.() -> Unit): DidDocument {
        val kms = getKms() ?: throw IllegalStateException(
            "KMS is not configured. Configure it in TrustWeave.build { keys { provider(\"inMemory\") } }"
        )
        val kmsService = getKmsService() ?: throw IllegalStateException(
            "KmsService is not configured. Configure it in TrustWeave.build { keys { provider(\"inMemory\") } }"
        )
        val builder = KeyRotationBuilder(this, kms, kmsService, config.ioDispatcher)
        builder.block()
        return builder.rotate()
    }

    /**
     * Revoke a credential using the TrustWeave configuration.
     * 
     * This operation uses the configured dispatcher for I/O-bound work.
     */
    suspend fun revoke(block: RevocationBuilder.() -> Unit): Boolean {
        val revocationManager = getRevocationManager()
        val builder = RevocationBuilder(revocationManager)
        builder.block()
        return withContext(config.ioDispatcher) {
            builder.revoke()
        }
    }
}

/**
 * Internal function to get DSL operations from TrustWeave config.
 * This is used internally by TrustWeave facade.
 */
internal fun TrustWeaveConfig.getDslContext(): TrustWeaveContext {
    return TrustWeaveContext(this)
}

