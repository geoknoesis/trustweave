package org.trustweave.trust.dsl

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.core.exception.ConfigException
import org.trustweave.credential.CredentialService
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.model.SchemaFormat
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.services.KmsService
import org.trustweave.revocation.services.StatusListRegistryFactory
import org.trustweave.trust.TrustRegistry
import org.trustweave.trust.domain.TrustedDomainManager
import org.trustweave.trust.dsl.builders.AnchorConfig
import org.trustweave.trust.dsl.builders.AnchorConfigBuilder
import org.trustweave.trust.dsl.builders.CredentialsBuilder
import org.trustweave.trust.dsl.builders.DidConfigBuilder
import org.trustweave.trust.dsl.builders.DidMethodConfig
import org.trustweave.trust.dsl.builders.DomainConfig
import org.trustweave.trust.dsl.builders.DomainConfigBuilder
import org.trustweave.trust.dsl.builders.KeysBuilder
import org.trustweave.trust.dsl.builders.RevocationConfigBuilder
import org.trustweave.contract.SmartContractService
import org.trustweave.trust.dsl.builders.TrustConfigBuilder
import org.trustweave.trust.services.TrustRegistryFactory
import org.trustweave.wallet.services.WalletFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Unified TrustWeave Configuration.
 *
 * Centralizes configuration for anchor layer (blockchain), credential keys (KMS),
 * and DID methods into a single configuration point. This makes it easier to
 * configure TrustWeave and provides context for DSL operations.
 *
 * **Example Usage**:
 * ```kotlin
 * val trustWeave = trustWeave {
 *     keys {
 *         provider("inMemory")
 *         algorithm("Ed25519")
 *     }
 *
 *     did {
 *         method("key") {
 *             algorithm("Ed25519")
 *         }
 *     }
 *
 *     anchor {
 *         chain("algorand:testnet") {
 *             provider("algorand")
 *         }
 *     }
 *
 *     credentials {
 *         defaultProofType("Ed25519Signature2020")
 *         autoAnchor(true)
 *         defaultChain("algorand:testnet")
 *     }
 * }
 * ```
 */
class TrustWeaveConfig internal constructor(
    val name: String,
    val kms: KeyManagementService,
    val didRegistry: DidMethodRegistry,
    val blockchainRegistry: BlockchainAnchorRegistry,
    val credentialConfig: CredentialConfig,
    val credentialService: CredentialService?,
    val didResolver: DidResolver? = null,
    val revocationManager: CredentialRevocationManager? = null,
    val trustRegistry: TrustRegistry? = null,
    val walletFactory: WalletFactory? = null,
    val kmsService: KmsService? = null,
    val defaultDidMethod: String? = null,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val smartContractService: SmartContractService? = null,
    val schemaRegistry: SchemaRegistry? = null,
    val trustedDomainManager: TrustedDomainManager? = null,
) {
    /**
     * Copy with overridden credential service and/or revocation manager (e.g. tests sharing one manager).
     */
    internal fun copy(
        credentialService: CredentialService? = this.credentialService,
        revocationManager: CredentialRevocationManager? = this.revocationManager,
    ): TrustWeaveConfig = TrustWeaveConfig(
        name = name,
        kms = kms,
        didRegistry = didRegistry,
        blockchainRegistry = blockchainRegistry,
        credentialConfig = credentialConfig,
        credentialService = credentialService,
        didResolver = didResolver,
        revocationManager = revocationManager,
        trustRegistry = trustRegistry,
        walletFactory = walletFactory,
        kmsService = kmsService,
        defaultDidMethod = defaultDidMethod,
        ioDispatcher = ioDispatcher,
        smartContractService = smartContractService,
        schemaRegistry = schemaRegistry,
        trustedDomainManager = trustedDomainManager,
    )

    /**
     * All registered DID methods, keyed by method name.
     */
    val didMethods: Map<String, org.trustweave.did.DidMethod>
        get() = didRegistry.getAllMethods()

    /**
     * All registered blockchain anchor clients, keyed by chain ID.
     */
    val anchorClients: Map<String, BlockchainAnchorClient>
        get() = blockchainRegistry.getAllClients()

    /**
     * Credential configuration within TrustWeave.
     */
    data class CredentialConfig(
        val defaultProofType: ProofType = ProofType.Ed25519Signature2020,
        val autoAnchor: Boolean = false,
        val defaultChain: String? = null
    )

    /**
     * Immutable state snapshot read by [TrustWeaveFactory] to produce a [TrustWeaveConfig].
     * Kept internal to prevent external dependencies on builder internals.
     */
    internal data class BuilderState(
        val name: String,
        val kms: KeyManagementService?,
        val kmsProvider: String?,
        val kmsAlgorithm: String,
        val kmsSigner: (suspend (ByteArray, String) -> ByteArray)?,
        val didRegistry: DidMethodRegistry,
        val blockchainRegistry: BlockchainAnchorRegistry,
        val didMethodConfigs: Map<String, DidMethodConfig>,
        val defaultDidMethod: String?,
        val anchorConfigs: Map<String, AnchorConfig>,
        val defaultProofType: ProofType,
        val autoAnchor: Boolean,
        val defaultChain: String?,
        val revocationProvider: String?,
        val trustProvider: String?,
        val credentialService: CredentialService?,
        val statusListRegistryFactory: StatusListRegistryFactory?,
        val trustRegistryFactory: TrustRegistryFactory?,
        val walletFactory: WalletFactory?,
        val ioDispatcher: CoroutineDispatcher,
        val smartContractService: SmartContractService? = null,
        val domainConfig: DomainConfig? = null,
    )

    /**
     * Builder for TrustWeave configuration.
     *
     * Collects DSL configuration and delegates to [TrustWeaveFactory] for construction.
     */
    @TrustWeaveDsl
    class Builder(private val name: String = "default") {
        private val didRegistry = DidMethodRegistry()
        private val blockchainRegistry = BlockchainAnchorRegistry()
        private var kms: KeyManagementService? = null
        private var kmsProvider: String? = null
        private var kmsAlgorithm: String = "Ed25519"
        private var kmsSigner: (suspend (ByteArray, String) -> ByteArray)? = null
        private val didMethodConfigs = mutableMapOf<String, DidMethodConfig>()
        private var defaultDidMethod: String? = null
        private val anchorConfigs = mutableMapOf<String, AnchorConfig>()
        private var defaultProofType: ProofType = ProofType.Ed25519Signature2020
        private var autoAnchor: Boolean = false
        private var defaultChain: String? = null
        private var revocationProvider: String? = null
        private var trustProvider: String? = null
        private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        private var credentialService: CredentialService? = null
        private var statusListRegistryFactory: StatusListRegistryFactory? = null
        private var trustRegistryFactory: TrustRegistryFactory? = null
        private var walletFactory: WalletFactory? = null
        private var smartContractService: SmartContractService? = null
        private var domainConfig: DomainConfig? = null

        fun smartContractService(service: SmartContractService) {
            this.smartContractService = service
        }

        fun factories(
            statusListRegistryFactory: StatusListRegistryFactory? = null,
            trustRegistryFactory: TrustRegistryFactory? = null,
            walletFactory: WalletFactory? = null
        ) {
            if (statusListRegistryFactory != null) this.statusListRegistryFactory = statusListRegistryFactory
            if (trustRegistryFactory != null) this.trustRegistryFactory = trustRegistryFactory
            if (walletFactory != null) this.walletFactory = walletFactory
        }

        fun keys(block: KeysBuilder.() -> Unit) {
            val builder = KeysBuilder()
            builder.block()
            kmsProvider = builder.provider
            kmsAlgorithm = builder.algorithm ?: "Ed25519"
            kms = builder.kms
            builder.signer?.let { kmsSigner = it }
        }

        fun customKms(kms: KeyManagementService) {
            this.kms = kms
        }

        fun did(block: DidConfigBuilder.() -> Unit) {
            val builder = DidConfigBuilder()
            builder.block()
            didMethodConfigs.putAll(builder.methods)
            defaultDidMethod = builder.defaultMethod
        }

        fun anchor(block: AnchorConfigBuilder.() -> Unit) {
            val builder = AnchorConfigBuilder()
            builder.block()
            anchorConfigs.putAll(builder.chains)
        }

        fun credentials(block: CredentialsBuilder.() -> Unit) {
            val builder = CredentialsBuilder()
            builder.block()
            defaultProofType = builder.defaultProofType ?: defaultProofType
            autoAnchor = builder.autoAnchor ?: autoAnchor
            defaultChain = builder.defaultChain
        }

        fun credentialService(credentialService: CredentialService) {
            this.credentialService = credentialService
        }

        fun revocation(provider: String) {
            revocationProvider = provider
        }

        fun revocation(block: RevocationConfigBuilder.() -> Unit) {
            revocationProvider = RevocationConfigBuilder().apply(block).provider
        }

        /**
         * Schema configuration.
         *
         * @throws UnsupportedOperationException if autoValidate is true, as automatic schema
         *   validation is not yet implemented. Use [SchemaValidatorRegistry] directly for schema
         *   validation, accessible via [TrustWeave.registerSchema].
         * @throws org.trustweave.core.exception.ConfigException.UnsupportedValue if defaultFormat
         *   is anything other than [SchemaFormat.JSON_SCHEMA]; alternative default schema formats
         *   are not yet supported and would otherwise be silently ignored.
         */
        fun schemas(
            autoValidate: Boolean = false,
            defaultFormat: SchemaFormat = SchemaFormat.JSON_SCHEMA
        ) {
            if (autoValidate) {
                throw UnsupportedOperationException(
                    "Automatic schema validation (autoValidate=true) is not yet implemented. " +
                    "Register schemas manually using trustWeave.registerSchema { ... } instead."
                )
            }
            if (defaultFormat != SchemaFormat.JSON_SCHEMA) {
                throw ConfigException.UnsupportedValue(
                    field = "schemas.defaultFormat",
                    value = defaultFormat.name,
                    reason = "Configurable default schema formats are not yet supported; " +
                        "only SchemaFormat.JSON_SCHEMA is available."
                )
            }
        }

        fun trust(provider: String) {
            trustProvider = provider
        }

        fun trust(block: TrustConfigBuilder.() -> Unit) {
            trustProvider = TrustConfigBuilder().apply(block).provider
        }

        fun dispatcher(dispatcher: CoroutineDispatcher) {
            this.ioDispatcher = dispatcher
        }

        /**
         * Attach a Trusted Domain to this TrustWeave instance. Threading a
         * domain through enables [org.trustweave.trust.TrustWeave.activeDomain]
         * and [org.trustweave.trust.TrustWeave.anchorThroughDomain]; without
         * one, the legacy [org.trustweave.trust.TrustWeave.blockchains] surface
         * is the only anchoring path.
         */
        fun domain(block: DomainConfigBuilder.() -> Unit) {
            domainConfig = DomainConfigBuilder().apply(block).build()
        }

        suspend fun build(): TrustWeaveConfig = TrustWeaveFactory.build(
            BuilderState(
                name = name,
                kms = kms,
                kmsProvider = kmsProvider,
                kmsAlgorithm = kmsAlgorithm,
                kmsSigner = kmsSigner,
                didRegistry = didRegistry,
                blockchainRegistry = blockchainRegistry,
                didMethodConfigs = didMethodConfigs.toMap(),
                defaultDidMethod = defaultDidMethod,
                anchorConfigs = anchorConfigs.toMap(),
                defaultProofType = defaultProofType,
                autoAnchor = autoAnchor,
                defaultChain = defaultChain,
                revocationProvider = revocationProvider,
                trustProvider = trustProvider,
                credentialService = credentialService,
                statusListRegistryFactory = statusListRegistryFactory,
                trustRegistryFactory = trustRegistryFactory,
                walletFactory = walletFactory,
                ioDispatcher = ioDispatcher,
                smartContractService = smartContractService,
                domainConfig = domainConfig,
            )
        )
    }

}

/**
 * DSL function to create a TrustWeave configuration.
 */
suspend fun trustWeave(
    name: String = "default",
    block: TrustWeaveConfig.Builder.() -> Unit
): TrustWeaveConfig {
    val builder = TrustWeaveConfig.Builder(name)
    builder.block()
    return builder.build()
}
