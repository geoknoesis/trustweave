package com.trustweave.credential.didcomm

import com.trustweave.core.types.KeyId
import com.trustweave.credential.didcomm.crypto.DidCommCrypto
import com.trustweave.credential.didcomm.crypto.DidCommCryptoAdapter
import com.trustweave.credential.didcomm.crypto.DidCommCryptoProduction
import com.trustweave.credential.didcomm.crypto.secret.*
import com.trustweave.credential.didcomm.packing.DidCommPacker
import com.trustweave.credential.didcomm.storage.DidCommMessageStorage
import com.trustweave.credential.didcomm.storage.InMemoryDidCommMessageStorage
import com.trustweave.did.DidDocument
import com.trustweave.kms.KeyManagementService
import org.didcommx.didcomm.secret.SecretResolver

/**
 * Factory for creating DIDComm V2 services and components.
 *
 * Supports both placeholder (development) and production crypto implementations.
 */
object DidCommFactory {
    /**
     * Creates a DIDComm service with in-memory storage.
     *
     * @param kms Key management service
     * @param resolveDid Function to resolve DID documents
     * @param useProductionCrypto Whether to use production crypto (requires didcomm-java library)
     * @return In-memory DIDComm service
     */
    fun createInMemoryService(
        kms: KeyManagementService,
        resolveDid: suspend (String) -> DidDocument?,
        useProductionCrypto: Boolean = true // Default to production
    ): DidCommService {
        val crypto = if (useProductionCrypto) {
            DidCommCryptoAdapter(kms, resolveDid, useProduction = true, secretResolver = null)
        } else {
            DidCommCryptoAdapter(kms, resolveDid, useProduction = false, secretResolver = null)
        }
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            kms.sign(KeyId(keyId), data)
        }
        val packer = DidCommPacker(crypto, resolveDid, signer)
        return InMemoryDidCommService(packer, resolveDid)
    }

    /**
     * Creates a DIDComm packer for custom service implementations.
     *
     * @param kms Key management service
     * @param resolveDid Function to resolve DID documents
     * @param useProductionCrypto Whether to use production crypto (requires didcomm-java library)
     * @return DIDComm packer
     */
    fun createPacker(
        kms: KeyManagementService,
        resolveDid: suspend (String) -> DidDocument?,
        useProductionCrypto: Boolean = true // Default to production
    ): DidCommPacker {
        val crypto = if (useProductionCrypto) {
            DidCommCryptoAdapter(kms, resolveDid, useProduction = true, secretResolver = null)
        } else {
            DidCommCryptoAdapter(kms, resolveDid, useProduction = false, secretResolver = null)
        }
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            kms.sign(KeyId(keyId), data)
        }
        return DidCommPacker(crypto, resolveDid, signer)
    }

    /**
     * Creates a DIDComm service with placeholder crypto (for development/testing).
     *
     * ⚠️ **WARNING**: Uses placeholder crypto that returns dummy data.
     * Messages will NOT be actually encrypted.
     *
     * @param kms Key management service
     * @param resolveDid Function to resolve DID documents
     * @return In-memory DIDComm service with placeholder crypto
     */
    fun createInMemoryServiceWithPlaceholderCrypto(
        kms: KeyManagementService,
        resolveDid: suspend (String) -> DidDocument?
    ): DidCommService {
        return createInMemoryService(kms, resolveDid, useProductionCrypto = false)
    }

    /**
     * Creates a database-backed DIDComm service.
     *
     * Uses persistent storage for message persistence across restarts.
     * Suitable for production deployments.
     *
     * @param packer DIDComm packer
     * @param resolveDid DID resolution function
     * @param storage Message storage implementation
     * @return Database-backed DIDComm service
     */
    fun createDatabaseService(
        packer: DidCommPacker,
        resolveDid: suspend (String) -> DidDocument?,
        storage: DidCommMessageStorage
    ): DidCommService {
        return DatabaseDidCommService(packer, resolveDid, storage)
    }

    /**
     * Creates a DIDComm service with custom SecretResolver.
     *
     * Useful when you need to use a custom SecretResolver (e.g., HybridKmsSecretResolver)
     * for KMS that doesn't expose private keys.
     *
     * @param kms Key management service
     * @param resolveDid DID resolution function
     * @param secretResolver Custom SecretResolver (e.g., HybridKmsSecretResolver)
     * @param useProductionCrypto Whether to use production crypto
     * @return DIDComm service with custom resolver
     */
    fun createInMemoryServiceWithSecretResolver(
        kms: KeyManagementService,
        resolveDid: suspend (String) -> DidDocument?,
        secretResolver: SecretResolver,
        useProductionCrypto: Boolean = true
    ): DidCommService {
        val crypto = if (useProductionCrypto) {
            // Create adapter with custom resolver
            DidCommCryptoAdapter(
                kms = kms,
                resolveDid = resolveDid,
                useProduction = true,
                secretResolver = secretResolver
            )
        } else {
            DidCommCryptoAdapter(
                kms = kms,
                resolveDid = resolveDid,
                useProduction = false,
                secretResolver = null
            )
        }

        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            kms.sign(KeyId(keyId), data)
        }
        val packer = DidCommPacker(crypto, resolveDid, signer)
        return InMemoryDidCommService(packer, resolveDid, InMemoryDidCommMessageStorage())
    }
}

