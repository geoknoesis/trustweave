package org.trustweave.testkit.proof

import org.trustweave.credential.format.ProofSuiteId
// Note: Proof engines are internal, using them here requires the modules to be in the same package or public
// For testing, we'll need to access them differently or make them public
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.runBlocking

/**
 * Factory functions for creating testable proof engines with KMS integration.
 * 
 * These functions create proof engines configured with a KMS so they can
 * actually sign and verify credentials during testing.
 * 
 * **Example:**
 * ```kotlin
 * val fixture = TrustWeaveTestFixture.minimal()
 * val helpers = fixture.proofEngineHelpers()
 * 
 * // Create testable VC-LD engine
 * val engine = helpers.createTestableVcLdEngine()
 * 
 * // Now it can actually sign credentials
 * val request = helpers.createIssuanceRequest(CredentialFormats.VC_LD)
 * val credential = engine.issue(request)
 * ```
 */
// TODO: Proof engines are internal classes, cannot be directly instantiated in testkit
// Use CredentialService instead which provides access to proof engines
// fun ProofEngineTestHelpers.createTestableVcLdEngine(): ProofEngine {
//     val config = createTestableProofEngineConfig()
//     val engine = VcLdProofEngine(config)
//     runBlocking { engine.initialize(config) }
//     return engine
// }
//
// fun ProofEngineTestHelpers.createTestableSdJwtEngine(): ProofEngine {
//     val config = createTestableProofEngineConfig()
//     val engine = SdJwtProofEngine(config)
//     runBlocking { engine.initialize(config) }
//     return engine
// }
//
// fun ProofEngineTestHelpers.createTestableAnonCredsEngine(): ProofEngine {
//     val config = createTestableProofEngineConfig()
//     val engine = AnonCredsProofEngine(config)
//     runBlocking { engine.initialize(config) }
//     return engine
// }

/**
 * Creates a testable proof engine for a specific format.
 * 
 * Note: This function is disabled because proof engines are internal.
 * Use CredentialService instead for testing proof functionality.
 */
// fun ProofEngineTestHelpers.createTestableEngine(format: ProofSuiteId): ProofEngine {
//     return when (format.value) {
//         "vc-ld" -> createTestableVcLdEngine()
//         "sd-jwt-vc" -> createTestableSdJwtEngine()
//         "anoncreds" -> createTestableAnonCredsEngine()
//         else -> throw IllegalArgumentException("Unsupported format: ${format.value}")
//     }
// }

/**
 * Extension function to create a ProofEngineConfig with KMS from a KeyManagementService.
 */
fun KeyManagementService.toProofEngineConfig(): ProofEngineConfig {
    return ProofEngineConfig(
        properties = mapOf(
            "kms" to this,
            "signer" to { data: ByteArray, keyId: String ->
                runBlocking {
                    this@toProofEngineConfig.sign(KeyId(keyId), data)
                }
            }
        )
    )
}

/**
 * Extension function to create a ProofEngineConfig with a custom signer function.
 */
fun createProofEngineConfigWithSigner(
    signer: suspend (ByteArray, String) -> ByteArray
): ProofEngineConfig {
    return ProofEngineConfig(
        properties = mapOf(
            "signer" to signer
        )
    )
}

