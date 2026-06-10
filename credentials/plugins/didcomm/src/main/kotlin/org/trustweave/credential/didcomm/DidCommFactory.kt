package org.trustweave.credential.didcomm

import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.didcomm.crypto.DidCommCryptoAdapter
import org.trustweave.credential.didcomm.packing.DidCommPacker
import org.trustweave.credential.didcomm.storage.DidCommMessageStorage
import org.trustweave.credential.didcomm.storage.InMemoryDidCommMessageStorage
import org.trustweave.did.model.DidDocument
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.KeyManagementService
import org.didcommx.didcomm.secret.SecretResolver

/**
 * Factory for creating DIDComm V2 services and components using [org.didcommx.didcomm.DIDComm] for crypto.
 */
object DidCommFactory {

    /**
     * Creates a DIDComm service with in-memory storage and **didcomm-java** crypto.
     *
     * You must supply a [SecretResolver] that can resolve private keys for every `kid` used in
     * pack/unpack (typically sender and recipient key agreement keys as JWK with `d`).
     */
    fun createInMemoryService(
        kms: KeyManagementService,
        resolveDid: suspend (String) -> DidDocument?,
        secretResolver: SecretResolver,
    ): DidCommService {
        val crypto = DidCommCryptoAdapter(
            kms = kms,
            resolveDid = resolveDid,
            useDidcommJava = true,
            secretResolver = secretResolver,
        )
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            when (val result = kms.sign(KeyId(keyId), data)) {
                is SignResult.Success -> result.signature
                is SignResult.Failure -> throw IllegalStateException("Failed to sign: $result")
            }
        }
        val packer = DidCommPacker(crypto, resolveDid, signer)
        return InMemoryDidCommService(packer, resolveDid)
    }

    /**
     * Creates a DIDComm service whose crypto is **fail-closed**: every encrypt/decrypt throws
     * [UnsupportedOperationException]. Storage-only operations still work.
     *
     * The placeholder crypto this method used to wire produced ciphertext from a constant shared
     * secret and has been removed. Use [createInMemoryService] with a [SecretResolver] instead.
     */
    @Deprecated(
        message = "Placeholder crypto has been removed; the returned service fails closed on " +
            "encrypt/decrypt. Use createInMemoryService(kms, resolveDid, secretResolver) instead.",
    )
    fun createInMemoryServiceWithPlaceholderCrypto(
        kms: KeyManagementService,
        resolveDid: suspend (String) -> DidDocument?,
    ): DidCommService {
        val crypto = DidCommCryptoAdapter(
            kms = kms,
            resolveDid = resolveDid,
            useDidcommJava = false,
            secretResolver = null,
        )
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            when (val result = kms.sign(KeyId(keyId), data)) {
                is SignResult.Success -> result.signature
                is SignResult.Failure -> throw IllegalStateException("Failed to sign: $result")
            }
        }
        val packer = DidCommPacker(crypto, resolveDid, signer)
        return InMemoryDidCommService(packer, resolveDid)
    }

    /**
     * Creates a DIDComm packer with didcomm-java crypto.
     */
    fun createPacker(
        kms: KeyManagementService,
        resolveDid: suspend (String) -> DidDocument?,
        secretResolver: SecretResolver,
    ): DidCommPacker {
        val crypto = DidCommCryptoAdapter(
            kms = kms,
            resolveDid = resolveDid,
            useDidcommJava = true,
            secretResolver = secretResolver,
        )
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            when (val result = kms.sign(KeyId(keyId), data)) {
                is SignResult.Success -> result.signature
                is SignResult.Failure -> throw IllegalStateException("Failed to sign: $result")
            }
        }
        return DidCommPacker(crypto, resolveDid, signer)
    }

    /**
     * Creates a DIDComm packer whose crypto is **fail-closed**: every encrypt/decrypt throws
     * [UnsupportedOperationException]. Plaintext (unencrypted) pack/unpack still works.
     *
     * The placeholder crypto this method used to wire produced ciphertext from a constant shared
     * secret and has been removed. Use [createPacker] with a [SecretResolver] instead.
     */
    @Deprecated(
        message = "Placeholder crypto has been removed; the returned packer fails closed on " +
            "encrypt/decrypt. Use createPacker(kms, resolveDid, secretResolver) instead.",
    )
    fun createPackerWithPlaceholderCrypto(
        kms: KeyManagementService,
        resolveDid: suspend (String) -> DidDocument?,
    ): DidCommPacker {
        val crypto = DidCommCryptoAdapter(
            kms = kms,
            resolveDid = resolveDid,
            useDidcommJava = false,
            secretResolver = null,
        )
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            when (val result = kms.sign(KeyId(keyId), data)) {
                is SignResult.Success -> result.signature
                is SignResult.Failure -> throw IllegalStateException("Failed to sign: $result")
            }
        }
        return DidCommPacker(crypto, resolveDid, signer)
    }

    /**
     * Creates a database-backed DIDComm service.
     */
    fun createDatabaseService(
        packer: DidCommPacker,
        resolveDid: suspend (String) -> DidDocument?,
        storage: DidCommMessageStorage,
    ): DidCommService {
        return DatabaseDidCommService(packer, resolveDid, storage)
    }

    /**
     * Creates a DIDComm service with a custom [SecretResolver] (e.g. [HybridKmsSecretResolver]).
     */
    fun createInMemoryServiceWithSecretResolver(
        kms: KeyManagementService,
        resolveDid: suspend (String) -> DidDocument?,
        secretResolver: SecretResolver,
    ): DidCommService = createInMemoryService(kms, resolveDid, secretResolver)
}
