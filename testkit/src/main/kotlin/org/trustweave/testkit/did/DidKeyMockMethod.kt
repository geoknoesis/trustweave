package org.trustweave.testkit.did

import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.*

/**
 * Mock DID method implementation for testing.
 * Creates simplified did:key-style DIDs using a KeyManagementService.
 */
class DidKeyMockMethod(
    private val kms: KeyManagementService
) : DidMethod {

    override val method = "key"

    private val documents = mutableMapOf<String, DidDocument>()

    override suspend fun createDid(options: DidCreationOptions): DidDocument {
        // Convert KeyAlgorithm to Algorithm
        val algorithm = when (options.algorithm) {
            KeyAlgorithm.ED25519 -> Algorithm.Ed25519
            KeyAlgorithm.SECP256K1 -> Algorithm.Secp256k1
            KeyAlgorithm.P256 -> Algorithm.P256
            KeyAlgorithm.P384 -> Algorithm.P384
            KeyAlgorithm.P521 -> Algorithm.P521
        }

        val generateResult = kms.generateKey(algorithm, options.additionalProperties)
        val keyHandle = when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> generateResult.keyHandle
            is org.trustweave.kms.results.GenerateKeyResult.Failure.UnsupportedAlgorithm -> {
                throw IllegalArgumentException("Algorithm not supported: ${generateResult.algorithm}")
            }
            is org.trustweave.kms.results.GenerateKeyResult.Failure.InvalidOptions -> {
                throw IllegalArgumentException("Invalid options: ${generateResult.reason}")
            }
            is org.trustweave.kms.results.GenerateKeyResult.Failure.Error -> {
                throw IllegalArgumentException("Failed to generate key: ${generateResult.reason}")
            }
        }

        // Create a simplified did:key identifier
        val didId = "z${UUID.randomUUID().toString().replace("-", "")}"
        val didString = "did:$method:$didId"
        val did = Did(didString)

        val verificationMethodId = VerificationMethodId.parse("$didString#${keyHandle.id.value}")

        val verificationMethod = VerificationMethod(
            id = verificationMethodId,
            type = when (options.algorithm) {
                KeyAlgorithm.ED25519 -> "Ed25519VerificationKey2020"
                KeyAlgorithm.SECP256K1 -> "EcdsaSecp256k1VerificationKey2019"
                else -> "JsonWebKey2020"
            },
            controller = did,
            publicKeyJwk = keyHandle.publicKeyJwk
        )

        val document = DidDocument(
            id = did,
            verificationMethod = listOf(verificationMethod),
            authentication = listOf(verificationMethodId),
            assertionMethod = listOf(verificationMethodId)
        )

        documents[didString] = document
        return document
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult {
        val document = documents[did.value]
        val now = Clock.System.now()
        return if (document != null) {
            DidResolutionResult.Success(
                document = document,
                documentMetadata = DidDocumentMetadata(
                    created = now,
                    updated = now
                ),
                resolutionMetadata = mapOf(
                    "method" to method,
                    "mock" to true
                )
            )
        } else {
            DidResolutionResult.Failure.NotFound(
                did = did,
                reason = "DID not found in mock registry",
                resolutionMetadata = mapOf("method" to method, "mock" to true)
            )
        }
    }

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument
    ): DidDocument {
        val current = documents[did.value]
            ?: throw IllegalArgumentException("DID not found: ${did.value}")
        val updated = updater(current)
        documents[did.value] = updated
        return updated
    }

    override suspend fun deactivateDid(did: Did): Boolean {
        return documents.remove(did.value) != null
    }

    /**
     * Clears all stored DID documents (useful for test cleanup).
     */
    fun clear() {
        documents.clear()
    }
}

