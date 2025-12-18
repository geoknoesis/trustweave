package com.trustweave.credential

import com.trustweave.credential.internal.DefaultCredentialService
import com.trustweave.credential.internal.createBuiltInEngines
import com.trustweave.credential.revocation.CredentialRevocationManager
import com.trustweave.credential.schema.SchemaRegistry
import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.proof.internal.engines.SdJwtProofEngine
import com.trustweave.credential.proof.internal.engines.VcLdProofEngine
import com.trustweave.credential.spi.proof.ProofEngineConfig
import com.trustweave.did.resolver.DidResolver

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

