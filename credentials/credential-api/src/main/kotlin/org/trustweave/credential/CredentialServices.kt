package org.trustweave.credential

import org.trustweave.credential.internal.DefaultCredentialService
import org.trustweave.credential.internal.createBuiltInEngines
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.proof.internal.engines.SdJwtProofEngine
import org.trustweave.credential.proof.internal.engines.VcLdProofEngine
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.did.resolver.DidResolver

/**
 * Creates a credential service with all built-in proof formats.
 * 
 * All proof formats (VC-LD, VC-JWT, SD-JWT-VC) are built-in and always available.
 * No adapter registration or discovery is needed.
 * 
 * **Example Usage:**
 * ```kotlin
 * val service = credentialService(didResolver)
 * 
 * // Or with optional configuration
 * val service = credentialService(
 *     didResolver = didResolver,
 *     schemaRegistry = mySchemaRegistry,
 *     revocationManager = myRevocationManager
 * )
 * ```
 * 
 * @param didResolver Required DID resolver for issuer/subject resolution
 * @param schemaRegistry Optional schema registry for credential validation
 * @param revocationManager Optional revocation manager for credential revocation checking
 * @return CredentialService instance with all built-in proof formats
 */
fun credentialService(
    didResolver: DidResolver,
    schemaRegistry: SchemaRegistry? = null,
    revocationManager: CredentialRevocationManager? = null
): CredentialService {
    // All proof engines are built-in - create map with all engines
    // Pass DID resolver to engines so they can resolve issuer DIDs during verification
    val engines = createBuiltInEngines(didResolver = didResolver)
    return DefaultCredentialService(
        engines = engines,
        didResolver = didResolver,
        schemaRegistry = schemaRegistry,
        revocationManager = revocationManager
    )
}

/**
 * Creates a credential service with all built-in proof formats and a custom signer.
 * 
 * This overload allows providing a signer function that will be used by proof engines
 * for signing operations. The signer function takes the data to sign and the key ID.
 * 
 * **Example Usage:**
 * ```kotlin
 * val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
 *     kms.sign(KeyId(keyId), data).signature
 * }
 * val service = credentialService(
 *     didResolver = didResolver,
 *     signer = signer
 * )
 * ```
 * 
 * @param didResolver Required DID resolver for issuer/subject resolution
 * @param signer Signer function for proof generation
 * @param schemaRegistry Optional schema registry for credential validation
 * @param revocationManager Optional revocation manager for credential revocation checking
 * @return CredentialService instance with all built-in proof formats and signer configured
 */
fun credentialService(
    didResolver: DidResolver,
    signer: suspend (ByteArray, String) -> ByteArray,
    schemaRegistry: SchemaRegistry? = null,
    revocationManager: CredentialRevocationManager? = null
): CredentialService {
    // Create proof engines with signer configured
    val config = ProofEngineConfig(
        didResolver = didResolver,
        properties = mapOf("signer" to signer)
    )
    val vcLdEngine = VcLdProofEngine(config)
    val sdJwtEngine = SdJwtProofEngine(config)
    val engines = mapOf(
        ProofSuiteId.VC_LD to vcLdEngine,
        ProofSuiteId.SD_JWT_VC to sdJwtEngine
    )
    return DefaultCredentialService(
        engines = engines,
        didResolver = didResolver,
        schemaRegistry = schemaRegistry,
        revocationManager = revocationManager
    )
}

/**
 * Factory object for creating CredentialService instances with custom configurations.
 */
object CredentialServices {
    /**
     * Creates a credential service with specified proof formats and KMS.
     * 
     * @param kms Key management service for signing operations
     * @param didResolver DID resolver for issuer/subject resolution
     * @param formats List of proof suite IDs to enable (defaults to all built-in formats)
     * @param schemaRegistry Optional schema registry for credential validation
     * @param revocationManager Optional revocation manager for credential revocation checking
     * @return CredentialService instance with specified proof formats
     */
    fun createCredentialService(
        kms: org.trustweave.kms.KeyManagementService,
        didResolver: DidResolver,
        formats: List<ProofSuiteId> = listOf(ProofSuiteId.VC_LD, ProofSuiteId.SD_JWT_VC),
        schemaRegistry: SchemaRegistry? = null,
        revocationManager: CredentialRevocationManager? = null
    ): CredentialService {
        // Create signer function from KMS
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            val signResult = kms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)
            when (signResult) {
                is org.trustweave.kms.results.SignResult.Success -> signResult.signature
                is org.trustweave.kms.results.SignResult.Failure.KeyNotFound -> {
                    throw IllegalStateException("Signing failed: Key not found: ${signResult.keyId.value}")
                }
                is org.trustweave.kms.results.SignResult.Failure.UnsupportedAlgorithm -> {
                    throw IllegalStateException("Signing failed: Unsupported algorithm: ${signResult.reason ?: "Algorithm mismatch"}")
                }
                is org.trustweave.kms.results.SignResult.Failure.Error -> {
                    throw IllegalStateException("Signing failed: ${signResult.reason}")
                }
            }
        }
        
        // Create config with DID resolver and signer
        val config = ProofEngineConfig(
            didResolver = didResolver,
            properties = mapOf("signer" to signer)
        )
        
        // Create engines for requested formats
        val engines = mutableMapOf<ProofSuiteId, org.trustweave.credential.spi.proof.ProofEngine>()
        if (formats.contains(ProofSuiteId.VC_LD)) {
            engines[ProofSuiteId.VC_LD] = VcLdProofEngine(config)
        }
        if (formats.contains(ProofSuiteId.SD_JWT_VC)) {
            engines[ProofSuiteId.SD_JWT_VC] = SdJwtProofEngine(config)
        }
        
        return DefaultCredentialService(
            engines = engines,
            didResolver = didResolver,
            schemaRegistry = schemaRegistry,
            revocationManager = revocationManager
        )
    }
}
