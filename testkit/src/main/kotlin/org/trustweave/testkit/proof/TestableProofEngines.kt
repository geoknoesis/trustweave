package org.trustweave.testkit.proof

import kotlinx.coroutines.runBlocking
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.kms.KeyManagementService

// Proof engines are internal to credential-api and cannot be instantiated here. Tests that need
// real signing go through a CredentialService (see TrustWeaveTestFixture); the helpers below only
// build the ProofEngineConfig such a service is configured with.

/**
 * Extension function to create a ProofEngineConfig with KMS from a KeyManagementService.
 */
fun KeyManagementService.toProofEngineConfig(): ProofEngineConfig =
    ProofEngineConfig(
        properties =
            mapOf(
                "kms" to this,
                "signer" to { data: ByteArray, keyId: String ->
                    runBlocking {
                        this@toProofEngineConfig.sign(KeyId(keyId), data)
                    }
                },
            ),
    )

/**
 * Extension function to create a ProofEngineConfig with a custom signer function.
 */
fun createProofEngineConfigWithSigner(signer: suspend (ByteArray, String) -> ByteArray): ProofEngineConfig =
    ProofEngineConfig(
        properties =
            mapOf(
                "signer" to signer,
            ),
    )
