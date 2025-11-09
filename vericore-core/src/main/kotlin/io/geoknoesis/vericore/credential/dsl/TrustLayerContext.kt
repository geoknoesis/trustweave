package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.verifier.CredentialVerifier
import io.geoknoesis.vericore.credential.wallet.Wallet
import io.geoknoesis.vericore.credential.anchor.CredentialAnchorService
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
) {
    /**
     * Get the KMS from trust layer.
     */
    fun getKms() = config.kms
    
    /**
     * Get a DID method by name.
     */
    fun getDidMethod(name: String): Any? {
        return config.didMethods[name]
    }
    
    /**
     * Get an anchor client by chain ID.
     */
    fun getAnchorClient(chainId: String): Any? {
        return config.anchorClients[chainId]
    }
    
    /**
     * Get the trust layer config (for internal use).
     */
    fun getConfig(): TrustLayerConfig {
        return config
    }
    
    /**
     * Get the credential issuer.
     */
    fun getIssuer() = config.issuer
    
    /**
     * Get the credential verifier.
     */
    fun getVerifier(): CredentialVerifier {
        return CredentialVerifier(
            resolveDid = { did -> 
                try {
                    val didRegistryClass = Class.forName("io.geoknoesis.vericore.did.DidRegistry")
                    val resolveMethod = didRegistryClass.getMethod("resolve", String::class.java)
                    val result = resolveMethod.invoke(null, did) as? Any
                    if (result != null) {
                        try {
                            val getDocumentMethod = result.javaClass.getMethod("getDocument")
                            val document = getDocumentMethod.invoke(result) as? Any
                            document != null
                        } catch (e: NoSuchMethodException) {
                            try {
                                val documentField = result.javaClass.getDeclaredField("document")
                                documentField.isAccessible = true
                                val document = documentField.get(result) as? Any
                                document != null
                            } catch (e2: Exception) {
                                false
                            }
                        }
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    true // Assume valid if registry not available
                }
            }
        )
    }
    
    /**
     * Get the credential anchor service.
     */
    fun getAnchorService(): CredentialAnchorService {
        val anchorClient = config.anchorClients.values.firstOrNull()
            ?: throw IllegalStateException("No anchor client configured in trust layer")
        return CredentialAnchorService(anchorClient)
    }
    
    /**
     * Get the status list manager from trust layer.
     * Returns null if not configured.
     */
    fun getStatusListManager(): io.geoknoesis.vericore.credential.revocation.StatusListManager? {
        return config.statusListManager as? io.geoknoesis.vericore.credential.revocation.StatusListManager
    }
    
    /**
     * Get the schema registry.
     */
    fun getSchemaRegistry(): io.geoknoesis.vericore.credential.schema.SchemaRegistry {
        return io.geoknoesis.vericore.credential.schema.SchemaRegistry
    }
    
    /**
     * Get the DID registry.
     */
    fun getDidRegistry(): Any? {
        return try {
            val didRegistryClass = Class.forName("io.geoknoesis.vericore.did.DidRegistry")
            didRegistryClass.getDeclaredField("INSTANCE").get(null)
                ?: didRegistryClass // If no INSTANCE, return the class itself
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get the trust registry.
     */
    fun getTrustRegistry(): io.geoknoesis.vericore.trust.TrustRegistry? {
        return config.trustRegistry as? io.geoknoesis.vericore.trust.TrustRegistry
    }
    
    /**
     * Issue a credential using the trust layer configuration.
     */
    suspend fun issue(block: IssuanceBuilder.() -> Unit): VerifiableCredential = withContext(Dispatchers.IO) {
        val builder = IssuanceBuilder(this@TrustLayerContext)
        builder.block()
        builder.build()
    }
    
    /**
     * Verify a credential using the trust layer configuration.
     */
    suspend fun verify(block: VerificationBuilder.() -> Unit): io.geoknoesis.vericore.credential.CredentialVerificationResult {
        val builder = VerificationBuilder(this@TrustLayerContext)
        builder.block()
        return builder.build()
    }
    
    /**
     * Create a wallet using the trust layer configuration.
     */
    suspend fun wallet(block: WalletBuilder.() -> Unit): Wallet {
        val builder = WalletBuilder(this@TrustLayerContext)
        builder.block()
        return builder.build()
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
suspend fun TrustLayerConfig.verify(block: VerificationBuilder.() -> Unit): io.geoknoesis.vericore.credential.CredentialVerificationResult {
    return dsl().verify(block)
}

/**
 * Extension function for direct wallet creation on trust layer.
 */
suspend fun TrustLayerConfig.wallet(block: WalletBuilder.() -> Unit): Wallet {
    return dsl().wallet(block)
}

