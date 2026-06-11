package org.trustweave.wallet.file

import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.wallet.exception.WalletException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileWalletTest {

    @TempDir
    lateinit var tempDir: Path

    private val issuerDid = "did:key:z6MkTestIssuer"
    private val subjectDid = "did:key:z6MkTestSubject"

    /** Base64-encoded 32-byte (AES-256) key. */
    private val validKey: String = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    private fun wallet(dir: Path, encryptionKey: String? = validKey): FileWallet =
        FileWallet(
            walletId = "wallet-test",
            walletDid = "did:key:z6MkWallet",
            holderDid = "did:key:z6MkHolder",
            walletDir = dir,
            encryptionKey = encryptionKey
        )

    private fun credential(id: String = "urn:uuid:${UUID.randomUUID()}"): VerifiableCredential =
        VerifiableCredential(
            id = CredentialId(id),
            type = listOf(CredentialType.Custom("TestCredential")),
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = CredentialSubject.fromIri(subjectDid),
            issuanceDate = Clock.System.now(),
            proof = null
        )

    private fun credentialFiles(walletDir: Path): List<Path> =
        Files.list(walletDir.resolve("credentials")).use { stream -> stream.toList() }

    private fun metadataFiles(walletDir: Path): List<Path> =
        Files.list(walletDir.resolve("metadata")).use { stream -> stream.toList() }

    // ========== Encryption ==========

    @Test
    fun `encrypted store and get round-trip`() {
        runBlocking {
            val dir = tempDir.resolve("roundtrip")
            val wallet = wallet(dir)
            val credential = credential()

            val id = wallet.store(credential)
            assertEquals(credential.id?.value, id)

            val loaded = wallet.get(id)
            assertNotNull(loaded)
            assertEquals(credential.id?.value, loaded.id?.value)
            assertEquals(issuerDid, loaded.issuer.id.value)

            // The on-disk file must not contain the plaintext JSON
            val onDisk = String(Files.readAllBytes(credentialFiles(dir).single()), Charsets.ISO_8859_1)
            assertFalse(onDisk.contains(issuerDid))
        }
    }

    @Test
    fun `tampered ciphertext fails with clear storage error instead of corrupt data`() {
        runBlocking {
            val dir = tempDir.resolve("tamper")
            val wallet = wallet(dir)
            val id = wallet.store(credential())

            val file = credentialFiles(dir).single()
            val bytes = Files.readAllBytes(file)
            bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
            Files.write(file, bytes)

            val exception = assertFailsWith<WalletException.StorageError> {
                wallet.get(id)
            }
            assertTrue(exception.message.contains("tampered"))
        }
    }

    @Test
    fun `same plaintext stored twice produces different ciphertexts`() {
        runBlocking {
            val dir = tempDir.resolve("iv-randomness")
            val wallet = wallet(dir)
            val credential = credential()

            wallet.store(credential)
            val firstCiphertext = Files.readAllBytes(credentialFiles(dir).single())

            wallet.store(credential)
            val secondCiphertext = Files.readAllBytes(credentialFiles(dir).single())

            assertFalse(firstCiphertext.contentEquals(secondCiphertext))
        }
    }

    @Test
    fun `decrypted credentials are listed correctly`() {
        runBlocking {
            val dir = tempDir.resolve("list")
            val wallet = wallet(dir)
            val first = credential()
            val second = credential()

            wallet.store(first)
            wallet.store(second)

            val listed = wallet.list(null)
            assertEquals(
                setOf(first.id?.value, second.id?.value),
                listed.map { it.id?.value }.toSet()
            )
        }
    }

    // ========== Metadata sidecar encryption ==========

    @Test
    fun `encrypted wallet does not leak the credential id in the metadata sidecar`() {
        runBlocking {
            val dir = tempDir.resolve("metadata-encrypted")
            val wallet = wallet(dir)
            val credentialId = "urn:uuid:pii-bearing-${UUID.randomUUID()}"

            wallet.store(credential(id = credentialId))

            val sidecar = metadataFiles(dir).single()
            val onDisk = String(Files.readAllBytes(sidecar), Charsets.ISO_8859_1)
            // Neither the raw credential id nor the plaintext JSON structure may appear
            assertFalse(onDisk.contains(credentialId))
            assertFalse(onDisk.contains("credentialId"))
            assertFalse(onDisk.contains("createdAt"))
        }
    }

    @Test
    fun `plaintext wallet keeps a readable metadata sidecar`() {
        runBlocking {
            val dir = tempDir.resolve("metadata-plaintext")
            val wallet = wallet(dir, encryptionKey = null)
            val credentialId = "urn:uuid:plain-${UUID.randomUUID()}"

            wallet.store(credential(id = credentialId))

            val sidecar = metadataFiles(dir).single()
            val onDisk = String(Files.readAllBytes(sidecar), Charsets.UTF_8)
            assertTrue(onDisk.contains(credentialId))
            assertTrue(onDisk.contains("credentialId"))
        }
    }

    @Test
    fun `delete removes the encrypted metadata sidecar`() {
        runBlocking {
            val dir = tempDir.resolve("metadata-delete")
            val wallet = wallet(dir)
            val id = wallet.store(credential())

            assertEquals(1, metadataFiles(dir).size)
            assertTrue(wallet.delete(id))
            assertTrue(metadataFiles(dir).isEmpty())
        }
    }

    // ========== Key validation ==========

    @Test
    fun `invalid key length is rejected at construction`() {
        val tooShort = Base64.getEncoder().encodeToString(ByteArray(10))
        val exception = assertFailsWith<WalletException.WalletCreationFailed> {
            wallet(tempDir.resolve("bad-key-length"), encryptionKey = tooShort)
        }
        assertTrue(exception.message.contains("16, 24, or 32"))
    }

    @Test
    fun `non-base64 key is rejected at construction`() {
        assertFailsWith<WalletException.WalletCreationFailed> {
            wallet(tempDir.resolve("bad-key-encoding"), encryptionKey = "!!!not base64!!!")
        }
    }

    @Test
    fun `all standard AES key lengths are accepted`() {
        for (length in listOf(16, 24, 32)) {
            wallet(
                tempDir.resolve("key-$length"),
                encryptionKey = Base64.getEncoder().encodeToString(ByteArray(length))
            )
        }
    }

    // ========== Path traversal ==========

    @Test
    fun `relative-path credential id cannot escape the wallet directory`() {
        runBlocking {
            val dir = tempDir.resolve("nested").resolve("traversal")
            val wallet = wallet(dir)
            val evilId = "../../evil"

            val id = wallet.store(credential(id = evilId))
            assertEquals(evilId, id)

            // The credential file must live inside the credentials directory,
            // named after the SHA-256 of the id (never the raw id).
            val files = credentialFiles(dir)
            assertEquals(1, files.size)
            val file = files.single().toAbsolutePath().normalize()
            assertEquals(dir.resolve("credentials").toAbsolutePath().normalize(), file.parent)
            assertFalse(file.fileName.toString().contains("evil"))
            assertFalse(Files.exists(tempDir.resolve("evil.json")))
            assertFalse(Files.exists(tempDir.resolve("nested").resolve("evil.json")))

            // store + get + delete all work through the hashed filename
            assertEquals(evilId, wallet.get(evilId)?.id?.value)
            assertTrue(wallet.delete(evilId))
            assertNull(wallet.get(evilId))
        }
    }

    @Test
    fun `absolute-path-looking credential id cannot escape the wallet directory`() {
        runBlocking {
            val dir = tempDir.resolve("absolute")
            val wallet = wallet(dir)
            val evilId = "C:/Windows/Temp/evil"

            wallet.store(credential(id = evilId))

            val files = credentialFiles(dir)
            assertEquals(1, files.size)
            assertEquals(
                dir.resolve("credentials").toAbsolutePath().normalize(),
                files.single().toAbsolutePath().normalize().parent
            )

            assertEquals(evilId, wallet.get(evilId)?.id?.value)
            assertTrue(wallet.delete(evilId))
            assertNull(wallet.get(evilId))
        }
    }

    @Test
    fun `delete with traversal id does not touch files outside the wallet directory`() {
        runBlocking {
            val dir = tempDir.resolve("delete-traversal")
            val wallet = wallet(dir, encryptionKey = null)
            val outside = tempDir.resolve("outside.json")
            Files.write(outside, "sentinel".toByteArray(Charsets.UTF_8))

            assertFalse(wallet.delete("../outside"))
            assertNull(wallet.get("../../outside"))
            assertTrue(Files.exists(outside))
        }
    }

    // ========== Query tag/collection filters ==========

    @Test
    fun `query with byTag throws instead of silently returning all credentials`() {
        runBlocking {
            val dir = tempDir.resolve("query-by-tag")
            val wallet = wallet(dir)
            wallet.store(credential())

            val exception = assertFailsWith<UnsupportedOperationException> {
                wallet.query { byTag("important") }
            }
            assertTrue(exception.message!!.contains("byTag"))
        }
    }

    @Test
    fun `query with byCollection throws instead of silently returning all credentials`() {
        runBlocking {
            val dir = tempDir.resolve("query-by-collection")
            val wallet = wallet(dir)
            wallet.store(credential())

            assertFailsWith<UnsupportedOperationException> {
                wallet.query { byCollection("collection-1") }
            }
        }
    }

    @Test
    fun `query without tag or collection filters still works`() {
        runBlocking {
            val dir = tempDir.resolve("query-plain")
            val wallet = wallet(dir)
            val stored = credential()
            wallet.store(stored)

            val results = wallet.query { byIssuer(issuerDid) }
            assertEquals(listOf(stored.id?.value), results.map { it.id?.value })

            val noMatch = wallet.query { byIssuer("did:key:someoneElse") }
            assertTrue(noMatch.isEmpty())
        }
    }

    // ========== Plaintext fallback ==========

    @Test
    fun `wallet without encryption key still round-trips in plaintext`() {
        runBlocking {
            val dir = tempDir.resolve("plaintext")
            val wallet = wallet(dir, encryptionKey = null)
            val credential = credential()

            val id = wallet.store(credential)
            assertEquals(credential.id?.value, wallet.get(id)?.id?.value)

            // Stored as readable JSON when no key is configured
            val onDisk = String(Files.readAllBytes(credentialFiles(dir).single()), Charsets.UTF_8)
            assertTrue(onDisk.contains(issuerDid))
        }
    }
}
