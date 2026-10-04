package org.trustweave.wallet.file

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.wallet.exception.WalletException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Records are bound to their wallet, kind and credential id; legacy (no AAD) records still read. */
class FileWalletRecordBindingTest {
    @TempDir
    lateinit var tempDir: Path

    private val keyBytes = ByteArray(32) { it.toByte() }
    private val key: String = Base64.getEncoder().encodeToString(keyBytes)

    private fun wallet(
        dir: Path,
        walletId: String = "wallet-a",
    ) = FileWallet(
        walletId = walletId,
        walletDid = "did:key:z6MkWallet",
        holderDid = "did:key:z6MkHolder",
        walletDir = dir,
        encryptionKey = key,
    )

    private fun credential(id: String) =
        VerifiableCredential(
            id = CredentialId(id),
            type = listOf(CredentialType.Custom("TestCredential")),
            issuer = Issuer.fromDid(Did("did:key:z6MkTestIssuer")),
            credentialSubject = CredentialSubject.fromIri("did:key:z6MkTestSubject"),
            issuanceDate = Clock.System.now(),
            proof = null,
        )

    private fun fileFor(
        dir: Path,
        sub: String,
        id: String,
    ): Path = dir.resolve(sub).resolve(sha256Hex(id) + ".json")

    private fun sha256Hex(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    /** A blob in the legacy layout: version 1, no AAD. */
    private fun legacyBlob(plaintext: String): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return byteArrayOf(1) + iv + cipher.doFinal(plaintext.toByteArray())
    }

    private val json = Json { encodeDefaults = false }

    @Test
    fun `new records are written in format version 2`() =
        runBlocking<Unit> {
            val dir = tempDir.resolve("v2")
            wallet(dir).store(credential("urn:id:1"))

            assertEquals(2, Files.readAllBytes(fileFor(dir, "credentials", "urn:id:1"))[0].toInt())
            assertEquals(2, Files.readAllBytes(fileFor(dir, "metadata", "urn:id:1"))[0].toInt())
        }

    @Test
    fun `swapping two encrypted credential files is detected on read`() =
        runBlocking<Unit> {
            val dir = tempDir.resolve("swap")
            val wallet = wallet(dir)
            wallet.store(credential("urn:id:victim"))
            wallet.store(credential("urn:id:attacker"))
            val victim = fileFor(dir, "credentials", "urn:id:victim")
            val attacker = fileFor(dir, "credentials", "urn:id:attacker")
            val victimBytes = Files.readAllBytes(victim)
            Files.write(victim, Files.readAllBytes(attacker))
            Files.write(attacker, victimBytes)

            assertFailsWith<WalletException.StorageError> { wallet.get("urn:id:victim") }
            assertFailsWith<WalletException.StorageError> { wallet.get("urn:id:attacker") }
            assertFailsWith<WalletException.StorageError> { wallet.list() }
            assertEquals(0, wallet.recoverRecords().records.size)
        }

    @Test
    fun `copying one credential's record over another id is detected`() =
        runBlocking<Unit> {
            val dir = tempDir.resolve("copy")
            val wallet = wallet(dir)
            wallet.store(credential("urn:id:a"))
            wallet.store(credential("urn:id:b"))
            Files.copy(
                fileFor(dir, "credentials", "urn:id:a"),
                fileFor(dir, "credentials", "urn:id:b"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )

            assertFailsWith<WalletException.StorageError> { wallet.get("urn:id:b") }
            assertEquals(credential("urn:id:a").id, wallet.get("urn:id:a")!!.id, "the untouched record still reads")
        }

    @Test
    fun `a metadata sidecar cannot stand in for a credential record or the other way round`() =
        runBlocking<Unit> {
            val dir = tempDir.resolve("kinds")
            val wallet = wallet(dir)
            wallet.store(credential("urn:id:k"))
            val credentialFile = fileFor(dir, "credentials", "urn:id:k")
            val metadataFile = fileFor(dir, "metadata", "urn:id:k")
            val credentialBytes = Files.readAllBytes(credentialFile)
            Files.write(credentialFile, Files.readAllBytes(metadataFile))
            Files.write(metadataFile, credentialBytes)

            assertFailsWith<WalletException.StorageError> { wallet.get("urn:id:k") }
            assertFailsWith<WalletException.StorageError> { wallet.list() }
        }

    @Test
    fun `records cannot be moved between wallets`() =
        runBlocking<Unit> {
            val dir = tempDir.resolve("scope")
            wallet(dir, walletId = "wallet-a").store(credential("urn:id:s"))

            val other = wallet(dir, walletId = "wallet-b")

            assertFailsWith<WalletException.StorageError> { other.get("urn:id:s") }
            assertFailsWith<WalletException.StorageError> { other.list() }
            assertEquals("urn:id:s", wallet(dir, walletId = "wallet-a").get("urn:id:s")!!.id!!.value)
        }

    @Test
    fun `a version 2 record cannot be downgraded by relabelling it as legacy`() =
        runBlocking<Unit> {
            val dir = tempDir.resolve("downgrade")
            val wallet = wallet(dir)
            wallet.store(credential("urn:id:d"))
            val file = fileFor(dir, "credentials", "urn:id:d")
            val bytes = Files.readAllBytes(file)
            bytes[0] = 1
            Files.write(file, bytes)

            assertFailsWith<WalletException.StorageError> { wallet.get("urn:id:d") }
        }

    @Test
    fun `legacy records without AAD are still read and upgraded when stored again`() =
        runBlocking<Unit> {
            val dir = tempDir.resolve("legacy")
            val wallet = wallet(dir)
            val id = "urn:id:legacy"
            val original = credential(id)
            // Lay down the files exactly as an earlier version would have.
            Files.createDirectories(dir.resolve("credentials"))
            Files.createDirectories(dir.resolve("metadata"))
            Files.write(fileFor(dir, "credentials", id), legacyBlob(json.encodeToString(VerifiableCredential.serializer(), original)))
            val sidecar =
                buildJsonObject {
                    put("credentialId", id)
                    put("createdAt", "2025-01-01T00:00:00Z")
                    put("updatedAt", "2025-01-01T00:00:00Z")
                }
            Files.write(fileFor(dir, "metadata", id), legacyBlob(sidecar.toString()))

            assertEquals(original, wallet.get(id))
            assertEquals(listOf(id), wallet.listRecords().map { it.storageId })
            assertTrue(wallet.recoverRecords().complete)

            wallet.store(original)
            assertEquals(2, Files.readAllBytes(fileFor(dir, "credentials", id))[0].toInt(), "storing again upgrades the credential blob")
            assertEquals(original, wallet.get(id))
        }

    @Test
    fun `a failed credential write leaves no orphan metadata sidecar behind`() =
        runBlocking<Unit> {
            val dir = tempDir.resolve("rollback")
            val wallet = wallet(dir)
            val id = "urn:id:blocked"
            // A non-empty directory where the credential file must go makes the atomic move fail.
            val blocker = fileFor(dir, "credentials", id)
            Files.createDirectories(blocker)
            Files.write(blocker.resolve("keep"), byteArrayOf(1))

            assertFailsWith<Exception> { wallet.store(credential(id)) }

            assertFalse(Files.exists(fileFor(dir, "metadata", id)), "the sidecar created by the failed store must be rolled back")
            Files.list(dir.resolve("credentials")).use { assertEquals(listOf(blocker), it.toList(), "no temp files left") }
        }

    @Test
    fun `a failed store keeps a sidecar that already existed`() =
        runBlocking<Unit> {
            val dir = tempDir.resolve("rollback-existing")
            val wallet = wallet(dir)
            val id = "urn:id:existing"
            wallet.store(credential(id))
            val sidecarBefore = Files.readAllBytes(fileFor(dir, "metadata", id))
            val credentialFile = fileFor(dir, "credentials", id)
            Files.delete(credentialFile)
            Files.createDirectories(credentialFile)
            Files.write(credentialFile.resolve("keep"), byteArrayOf(1))

            assertFailsWith<Exception> { wallet.store(credential(id)) }

            assertTrue(sidecarBefore.contentEquals(Files.readAllBytes(fileFor(dir, "metadata", id))))
        }
}
