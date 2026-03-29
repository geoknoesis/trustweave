package org.trustweave.trust.dsl

import org.trustweave.credential.CredentialService
import org.trustweave.credential.credentialService
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService

/**
 * Creates a [CredentialService] for tests with KMS and DID resolver configured so credentials are signed with proofs.
 */
fun createTestCredentialService(
    kms: KeyManagementService? = null,
    didResolver: DidResolver? = null,
): CredentialService {
    val actualKms = kms ?: InMemoryKeyManagementService()
    val actualDidResolver = didResolver ?: DidResolver { did ->
        org.trustweave.did.resolver.DidResolutionResult.Success(
            document = org.trustweave.did.model.DidDocument(
                id = did,
                verificationMethod = emptyList(),
                authentication = emptyList(),
                assertionMethod = emptyList(),
            ),
        )
    }

    val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
        when (val result = actualKms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
            is SignResult.Success -> result.signature
            else -> throw IllegalStateException("Signing failed: $result")
        }
    }

    return credentialService(
        didResolver = actualDidResolver,
        signer = signer,
    )
}
