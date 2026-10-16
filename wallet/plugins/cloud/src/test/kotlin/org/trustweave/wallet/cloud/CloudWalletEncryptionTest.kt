package org.trustweave.wallet.cloud

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.wallet.exception.WalletException
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Exercises the client-side AES-256-GCM path against an in-memory object store (no Docker). */
class CloudWalletEncryptionTest {
    private class MemoryWallet(
        id: String,
        val objects: ConcurrentHashMap<String, ByteArray> = ConcurrentHashMap(),
        key: ByteArray? = null,
        basePath: String = "wallet",
    ) : CloudWallet(id, "did:key:w-$id", "did:key:h", "bucket", basePath, key) {
        override suspend fun upload(
            key: String,
            data: ByteArray,
        ) {
            objects[key] = data
        }

        override suspend fun download(key: String): ByteArray? = objects[key]

        override suspend fun deleteFromStorage(key: String) = objects.remove(key) != null

        override suspend fun listKeys(prefix: String) = objects.keys.filter { it.startsWith(prefix) }
    }

    private fun key(seed: Int) = ByteArray(32) { (it + seed).toByte() }

    private fun credential(id: String) =
        VerifiableCredential(
            id = CredentialId(id),
            type = listOf(CredentialType.Custom("TestCredential")),
            issuer = Issuer.fromDid(Did("did:key:z6MkIssuer")),
            credentialSubject = CredentialSubject.fromIri("did:key:z6MkSubject"),
            issuanceDate = Clock.System.now(),
            proof = null,
        )

    private fun text(bytes: ByteArray) = String(bytes, Charsets.UTF_8)

    @Test
    fun `round trip stores only ciphertext with a random IV`() =
        runBlocking<Unit> {
            val wallet = MemoryWallet("w", key = key(1))
            val id = wallet.store(credential("urn:uuid:secret-subject"))
            assertEquals("urn:uuid:secret-subject", wallet.get(id)?.id?.value)
            assertEquals(1, wallet.list(null).size)

            assertTrue(wallet.objects.isNotEmpty())
            for ((k, v) in wallet.objects) {
                assertTrue(CloudRecordCipher.hasHeader(v), "$k must be encrypted")
                assertTrue(!text(v).contains("z6MkSubject"), "$k leaks plaintext")
            }
            val first = wallet.objects["wallet/credentials/urn:uuid:secret-subject.json"]!!.copyOf()
            wallet.store(credential("urn:uuid:secret-subject"))
            val second = wallet.objects["wallet/credentials/urn:uuid:secret-subject.json"]!!
            assertTrue(!first.contentEquals(second), "IV must be fresh per write")
        }

    @Test
    fun `unencrypted wallet is still plaintext by default`() =
        runBlocking<Unit> {
            val wallet = MemoryWallet("w")
            wallet.store(credential("urn:uuid:a"))
            assertTrue(wallet.objects.values.none { CloudRecordCipher.hasHeader(it) })
            assertTrue(text(wallet.objects["wallet/credentials/urn:uuid:a.json"]!!).startsWith("{"))
        }

    @Test
    fun `legacy plaintext objects remain readable and are encrypted on re-store`() =
        runBlocking<Unit> {
            val objects = ConcurrentHashMap<String, ByteArray>()
            MemoryWallet("w", objects).store(credential("urn:uuid:old"))
            val encrypted = MemoryWallet("w", objects, key(1))

            assertNotNull(encrypted.get("urn:uuid:old"))
            assertEquals(1, encrypted.list(null).size)

            encrypted.store(credential("urn:uuid:old"))
            assertTrue(CloudRecordCipher.hasHeader(objects["wallet/credentials/urn:uuid:old.json"]!!))
            assertTrue(CloudRecordCipher.hasHeader(objects["wallet/metadata/urn:uuid:old.json"]!!))
            assertNotNull(encrypted.get("urn:uuid:old"))
        }

    @Test
    fun `wrong key fails closed and never falls back to plaintext`() =
        runBlocking<Unit> {
            val objects = ConcurrentHashMap<String, ByteArray>()
            MemoryWallet("w", objects, key(1)).store(credential("urn:uuid:a"))
            val other = MemoryWallet("w", objects, key(2))
            assertFailsWith<WalletException.StorageError> { other.get("urn:uuid:a") }
            assertFailsWith<WalletException.StorageError> { other.list(null) }
            assertEquals(1, other.recoverRecords().failures.size)
            // A key-less wallet reading encrypted bytes does not yield a credential either.
            assertFailsWith<Exception> { MemoryWallet("w", objects).get("urn:uuid:a") }
        }

    @Test
    fun `tampered ciphertext fails closed`() =
        runBlocking<Unit> {
            val objects = ConcurrentHashMap<String, ByteArray>()
            val wallet = MemoryWallet("w", objects, key(1))
            wallet.store(credential("urn:uuid:a"))
            val k = "wallet/credentials/urn:uuid:a.json"
            val bytes = objects[k]!!.copyOf()
            bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
            objects[k] = bytes
            assertFailsWith<WalletException.StorageError> { wallet.get("urn:uuid:a") }

            // Truncated below header + IV + tag
            objects[k] = bytes.copyOf(10)
            assertFailsWith<WalletException.StorageError> { wallet.get("urn:uuid:a") }
        }

    @Test
    fun `tampered metadata sidecar fails closed on re-store`() =
        runBlocking<Unit> {
            val objects = ConcurrentHashMap<String, ByteArray>()
            val wallet = MemoryWallet("w", objects, key(1))
            wallet.store(credential("urn:uuid:a"))
            val k = "wallet/metadata/urn:uuid:a.json"
            objects[k] = objects[k]!!.also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
            assertFailsWith<WalletException.StorageError> { wallet.store(credential("urn:uuid:a")) }
        }

    @Test
    fun `records cannot be swapped between credentials, kinds or wallets`() =
        runBlocking<Unit> {
            val objects = ConcurrentHashMap<String, ByteArray>()
            val wallet = MemoryWallet("w", objects, key(1))
            wallet.store(credential("urn:uuid:a"))
            wallet.store(credential("urn:uuid:b"))

            // Credential a's ciphertext placed under b's key.
            objects["wallet/credentials/urn:uuid:b.json"] = objects["wallet/credentials/urn:uuid:a.json"]!!
            assertFailsWith<WalletException.StorageError> { wallet.get("urn:uuid:b") }

            // A metadata sidecar placed where the credential belongs.
            objects["wallet/credentials/urn:uuid:b.json"] = objects["wallet/metadata/urn:uuid:b.json"]!!
            assertFailsWith<WalletException.StorageError> { wallet.get("urn:uuid:b") }

            // Another wallet (same key) cannot adopt this wallet's objects.
            val sibling = MemoryWallet("other", objects, key(1))
            assertFailsWith<WalletException.StorageError> { sibling.get("urn:uuid:a") }
        }

    @Test
    fun `statistics and delete work on encrypted wallets`() =
        runBlocking<Unit> {
            val wallet = MemoryWallet("w", key = key(1))
            wallet.store(credential("urn:uuid:a"))
            assertEquals(1, wallet.getStatistics().totalCredentials)
            assertTrue(wallet.delete("urn:uuid:a"))
            assertTrue(wallet.objects.isEmpty())
        }

    @Test
    fun `wallet constructor rejects a key that is not 32 bytes`() {
        for (size in listOf(0, 16, 24, 31, 33)) {
            assertFailsWith<IllegalArgumentException> { MemoryWallet("w", key = ByteArray(size)) }
        }
    }
}
