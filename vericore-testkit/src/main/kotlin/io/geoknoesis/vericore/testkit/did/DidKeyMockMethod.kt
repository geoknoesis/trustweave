package io.geoknoesis.vericore.testkit.did

import io.geoknoesis.vericore.did.*
import io.geoknoesis.vericore.kms.KeyManagementService
import java.time.Instant
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
        val algorithm = options.algorithm.algorithmName
        val keyHandle = kms.generateKey(algorithm, options.additionalProperties)
        
        // Create a simplified did:key identifier
        val didId = "z${UUID.randomUUID().toString().replace("-", "")}"
        val did = "did:$method:$didId"
        
        val verificationMethodId = "$did#${keyHandle.id}"
        
        val verificationMethod = VerificationMethodRef(
            id = verificationMethodId,
            type = when (algorithm.uppercase()) {
                "ED25519" -> "Ed25519VerificationKey2020"
                "SECP256K1" -> "EcdsaSecp256k1VerificationKey2019"
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

        documents[did] = document
        return document
    }

    override suspend fun resolveDid(did: String): DidResolutionResult {
        val document = documents[did]
        val now = Instant.now()
        return DidResolutionResult(
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
    }

    override suspend fun updateDid(
        did: String,
        updater: (DidDocument) -> DidDocument
    ): DidDocument {
        val current = documents[did]
            ?: throw IllegalArgumentException("DID not found: $did")
        val updated = updater(current)
        documents[did] = updated
        return updated
    }

    override suspend fun deactivateDid(did: String): Boolean {
        return documents.remove(did) != null
    }

    /**
     * Clears all stored DID documents (useful for test cleanup).
     */
    fun clear() {
        documents.clear()
    }
}

