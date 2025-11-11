package com.geoknoesis.vericore.credential.wallet

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.did.DidCreationOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Tests for Wallet extension functions (withOrganization, withLifecycle, etc.).
 */
class WalletExtensionFunctionsTest {

    @Test
    fun `test withOrganization with organization wallet`() = runBlocking {
        val wallet = createMockOrganizationWallet()

        val result = wallet.withOrganization { org ->
            org.createCollection("Test Collection")
        }

        assertNotNull(result)
        assertTrue(result!!.startsWith("collection-"))
    }

    @Test
    fun `test withOrganization with non-organization wallet`() = runBlocking {
        val wallet = createBasicWallet()

        val result = wallet.withOrganization { org ->
            org.createCollection("Test Collection")
        }

        assertNull(result)
    }

    @Test
    fun `test withLifecycle with lifecycle wallet`() = runBlocking {
        val wallet = createMockLifecycleWallet()
        val credential = createTestCredential()
        wallet.store(credential)

        val result = wallet.withLifecycle { lifecycle ->
            lifecycle.archive(credential.id!!)
        }

        assertNotNull(result)
        assertTrue(result == true)
    }

    @Test
    fun `test withLifecycle with non-lifecycle wallet`() = runBlocking {
        val wallet = createBasicWallet()

        val result = wallet.withLifecycle { lifecycle ->
            lifecycle.archive("cred-1")
        }

        assertNull(result)
    }

    @Test
    fun `test withPresentation with presentation wallet`() = runBlocking {
        val wallet = createMockPresentationWallet()
        val credential = createTestCredential()
        wallet.store(credential)

        val result = wallet.withPresentation { presentation ->
            presentation.createPresentation(
                credentialIds = listOf(credential.id!!),
                holderDid = "did:key:holder",
                options = com.geoknoesis.vericore.credential.PresentationOptions(holderDid = "did:key:holder")
            )
        }

        assertNotNull(result)
    }

    @Test
    fun `test withPresentation with non-presentation wallet`() = runBlocking {
        val wallet = createBasicWallet()

        val result = wallet.withPresentation { presentation ->
            presentation.createPresentation(
                credentialIds = listOf("cred-1"),
                holderDid = "did:key:holder",
                options = com.geoknoesis.vericore.credential.PresentationOptions(holderDid = "did:key:holder")
            )
        }

        assertNull(result)
    }

    @Test
    fun `test withDidManagement with DID management wallet`() = runBlocking {
        val wallet = createMockDidManagementWallet()

        val result = wallet.withDidManagement { didMgmt ->
            didMgmt.createDid("key")
        }

        assertNotNull(result)
        assertTrue(result!!.startsWith("did:key:"))
    }

    @Test
    fun `test withDidManagement with non-DID management wallet`() = runBlocking {
        val wallet = createBasicWallet()

        val result = wallet.withDidManagement { didMgmt ->
            didMgmt.createDid("key")
        }

        assertNull(result)
    }

    @Test
    fun `test withKeyManagement with key management wallet`() = runBlocking {
        val wallet = createMockKeyManagementWallet()

        val result = wallet.withKeyManagement { keyMgmt ->
            keyMgmt.generateKey("Ed25519", emptyMap())
        }

        assertNotNull(result)
        assertTrue(result!!.isNotEmpty())
    }

    @Test
    fun `test withKeyManagement with non-key management wallet`() = runBlocking {
        val wallet = createBasicWallet()

        val result = wallet.withKeyManagement { keyMgmt ->
            keyMgmt.generateKey("Ed25519", emptyMap())
        }

        assertNull(result)
    }

    @Test
    fun `test withIssuance with issuance wallet`() = runBlocking {
        val wallet = createMockIssuanceWallet()

        val result = wallet.withIssuance { issuance ->
            issuance.issueCredential(
                subjectDid = "did:key:subject",
                credentialType = "TestCredential",
                claims = mapOf("name" to "Test"),
                options = com.geoknoesis.vericore.credential.CredentialIssuanceOptions()
            )
        }

        assertNotNull(result)
        assertNotNull(result?.proof)
    }

    @Test
    fun `test withIssuance with non-issuance wallet`() = runBlocking {
        val wallet = createBasicWallet()

        val result = wallet.withIssuance { issuance ->
            issuance.issueCredential(
                subjectDid = "did:key:subject",
                credentialType = "TestCredential",
                claims = mapOf("name" to "Test"),
                options = com.geoknoesis.vericore.credential.CredentialIssuanceOptions()
            )
        }

        assertNull(result)
    }

    @Test
    fun `test extension functions propagate exceptions`() = runBlocking {
        val wallet = createMockOrganizationWallet()

        assertFailsWith<RuntimeException> {
            wallet.withOrganization { org ->
                throw RuntimeException("Test exception")
            }
        }
    }

    // ========== Helper Methods ==========

    private fun createBasicWallet(): Wallet {
        return object : Wallet {
            override val walletId = "wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = storage.values.toList()
        }
    }

    private fun createMockOrganizationWallet(): Wallet {
        return object : Wallet, CredentialOrganization {
            override val walletId = "org-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            private val collections = mutableMapOf<String, CredentialCollection>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = storage.values.toList()

            override suspend fun createCollection(name: String, description: String?): String {
                val id = "collection-${collections.size + 1}"
                collections[id] = CredentialCollection(id, name, description)
                return id
            }

            override suspend fun getCollection(collectionId: String) = collections[collectionId]
            override suspend fun listCollections() = collections.values.toList()
            override suspend fun deleteCollection(collectionId: String) = collections.remove(collectionId) != null
            override suspend fun addToCollection(credentialId: String, collectionId: String) = true
            override suspend fun removeFromCollection(credentialId: String, collectionId: String) = true
            override suspend fun getCredentialsInCollection(collectionId: String) = emptyList<VerifiableCredential>()
            override suspend fun tagCredential(credentialId: String, tags: Set<String>) = true
            override suspend fun untagCredential(credentialId: String, tags: Set<String>) = true
            override suspend fun getTags(credentialId: String) = emptySet<String>()
            override suspend fun getAllTags() = emptySet<String>()
            override suspend fun findByTag(tag: String) = emptyList<VerifiableCredential>()
            override suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>) = true
            override suspend fun getMetadata(credentialId: String) = null
            override suspend fun updateNotes(credentialId: String, notes: String?) = true
        }
    }

    private fun createMockLifecycleWallet(): Wallet {
        return object : Wallet, CredentialLifecycle {
            override val walletId = "lifecycle-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            private val archived = mutableSetOf<String>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = storage.values.toList()

            override suspend fun archive(credentialId: String) = archived.add(credentialId)
            override suspend fun unarchive(credentialId: String) = archived.remove(credentialId)
            override suspend fun getArchived() = archived.mapNotNull { storage[it] }
            override suspend fun refreshCredential(credentialId: String) = storage[credentialId]
        }
    }

    private fun createMockPresentationWallet(): Wallet {
        return object : Wallet, CredentialPresentation {
            override val walletId = "presentation-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = storage.values.toList()

            override suspend fun createPresentation(
                credentialIds: List<String>,
                holderDid: String,
                options: com.geoknoesis.vericore.credential.PresentationOptions
            ) = com.geoknoesis.vericore.credential.models.VerifiablePresentation(
                type = listOf("VerifiablePresentation"),
                verifiableCredential = credentialIds.mapNotNull { storage[it] },
                holder = holderDid
            )

            override suspend fun createSelectiveDisclosure(
                credentialIds: List<String>,
                disclosedFields: List<String>,
                holderDid: String,
                options: com.geoknoesis.vericore.credential.PresentationOptions
            ) = createPresentation(credentialIds, holderDid, options)
        }
    }

    private fun createMockDidManagementWallet(): Wallet {
        return object : Wallet, DidManagement {
            override val walletId = "did-wallet-1"
            override val walletDid = "did:key:wallet123"
            override val holderDid = "did:key:holder123"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = storage.values.toList()

            override suspend fun createDid(method: String, options: DidCreationOptions) = "did:$method:${System.currentTimeMillis()}"
            override suspend fun getDids() = listOf(walletDid)
            override suspend fun getPrimaryDid() = walletDid
            override suspend fun setPrimaryDid(did: String) = did == walletDid
            override suspend fun resolveDid(did: String) = mapOf("id" to did)
        }
    }

    private fun createMockKeyManagementWallet(): Wallet {
        return object : Wallet, KeyManagement {
            override val walletId = "kms-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()
            private val keys = mutableMapOf<String, KeyInfo>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = storage.values.toList()

            override suspend fun generateKey(algorithm: String, options: Map<String, Any?>) = "key-${keys.size + 1}".also {
                keys[it] = KeyInfo(it, algorithm, emptyMap())
            }

            override suspend fun getKeys() = keys.values.toList()
            override suspend fun getKey(keyId: String) = keys[keyId]
            override suspend fun deleteKey(keyId: String) = keys.remove(keyId) != null
            override suspend fun sign(keyId: String, data: ByteArray) = "signature".toByteArray()
        }
    }

    private fun createMockIssuanceWallet(): Wallet {
        return object : Wallet, CredentialIssuance {
            override val walletId = "issuance-wallet-1"
            private val storage = mutableMapOf<String, VerifiableCredential>()

            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: "cred-${System.currentTimeMillis()}"
                storage[id] = credential
                return id
            }

            override suspend fun get(credentialId: String) = storage[credentialId]
            override suspend fun list(filter: CredentialFilter?) = storage.values.toList()
            override suspend fun delete(credentialId: String) = storage.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = storage.values.toList()

            override suspend fun issueCredential(
                subjectDid: String,
                credentialType: String,
                claims: Map<String, Any>,
                options: com.geoknoesis.vericore.credential.CredentialIssuanceOptions
            ) = VerifiableCredential(
                type = listOf("VerifiableCredential", credentialType),
                issuer = "did:key:issuer",
                issuanceDate = java.time.Instant.now().toString(),
                credentialSubject = buildJsonObject {
                    put("id", subjectDid)
                    claims.forEach { (key, value) ->
                        put(key, value.toString())
                    }
                },
                proof = com.geoknoesis.vericore.credential.models.Proof(
                    type = options.proofType,
                    created = java.time.Instant.now().toString(),
                    verificationMethod = "did:key:issuer#key-1",
                    proofPurpose = "assertionMethod"
                )
            )
        }
    }

    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            id = "cred-${System.currentTimeMillis()}",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = "did:key:issuer",
            issuanceDate = java.time.Instant.now().toString(),
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "Test Subject")
            }
        )
    }
}

