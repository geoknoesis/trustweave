package org.trustweave.credential.didcomm.crypto.secret

import org.trustweave.credential.didcomm.crypto.secret.encryption.EncryptedData
import org.trustweave.credential.didcomm.crypto.secret.encryption.KeyEncryption
import org.trustweave.credential.didcomm.crypto.secret.encryption.MasterKeyDerivation
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the random persisted PBKDF2 salt of [EncryptedFileLocalKeyStoreFactory]:
 * random generation on first use, persistence/reuse, fail-closed handling of legacy
 * (path-derived salt) stores and corrupt salt files, and AES-GCM tamper detection.
 */
class EncryptedFileLocalKeyStoreTest {

    @TempDir
    lateinit var tempDir: File

    private val password = "correct horse battery staple".toCharArray()

    private val saltFileMagic = byteArrayOf(0x54, 0x57, 0x53, 0x31) // "TWS1"

    private fun newKeyFile(name: String): File = File(tempDir, name)

    private fun saltFileContent(keyFile: File): ByteArray =
        EncryptedFileLocalKeyStoreFactory.saltFileFor(keyFile).readBytes()

    private fun saltBytes(keyFile: File): ByteArray {
        val content = saltFileContent(keyFile)
        return content.copyOfRange(saltFileMagic.size, content.size)
    }

    /** Parses the key store file format: [4B version][4B iv length][iv][ciphertext]. */
    private fun parseStoreFile(keyFile: File): EncryptedData {
        val content = keyFile.readBytes()
        val ivLength = content.sliceArray(4 until 8)
            .fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        return EncryptedData(
            iv = content.sliceArray(8 until 8 + ivLength),
            ciphertext = content.sliceArray(8 + ivLength until content.size),
            algorithm = "AES/GCM/NoPadding",
        )
    }

    @Test
    fun freshStoreCreatesRandomPersistedSaltWithFormatMarker() {
        val keyFile = newKeyFile("keys.enc")
        EncryptedFileLocalKeyStoreFactory.create(keyFile, password)

        val saltFile = EncryptedFileLocalKeyStoreFactory.saltFileFor(keyFile)
        assertTrue(saltFile.exists(), "Salt file must be persisted on first use")

        val content = saltFile.readBytes()
        assertEquals(saltFileMagic.size + 16, content.size, "Salt file must be magic + 16 salt bytes")
        assertContentEquals(saltFileMagic, content.copyOfRange(0, saltFileMagic.size))
    }

    @Test
    fun twoStoresGenerateDifferentSalts() {
        val first = newKeyFile("first.enc")
        val second = newKeyFile("second.enc")
        EncryptedFileLocalKeyStoreFactory.create(first, password)
        EncryptedFileLocalKeyStoreFactory.create(second, password)

        assertFalse(
            saltBytes(first).contentEquals(saltBytes(second)),
            "Two independently created stores must not share a salt",
        )
    }

    @Test
    fun persistedSaltDecryptsTheStoreFile() {
        val keyFile = newKeyFile("keys.enc")
        EncryptedFileLocalKeyStoreFactory.create(keyFile, password)

        // Re-derive the master key from the persisted salt; it must open the store file.
        val masterKey = MasterKeyDerivation.deriveKey(
            password = password,
            salt = saltBytes(keyFile),
            iterations = EncryptedFileLocalKeyStoreFactory.DEFAULT_PBKDF2_ITERATIONS,
        )
        val plaintext = KeyEncryption(masterKey).decrypt(parseStoreFile(keyFile))
        assertEquals("{}", String(plaintext, Charsets.UTF_8), "Fresh store holds an encrypted empty key map")
    }

    @Test
    fun reopeningReusesThePersistedSalt() {
        val keyFile = newKeyFile("keys.enc")
        EncryptedFileLocalKeyStoreFactory.create(keyFile, password)
        val firstSalt = saltFileContent(keyFile)

        EncryptedFileLocalKeyStoreFactory.create(keyFile, password)
        assertContentEquals(firstSalt, saltFileContent(keyFile), "Reopen must reuse the persisted salt")
    }

    @Test
    fun legacyStoreWithoutSaltFileFailsClosed() {
        val keyFile = newKeyFile("legacy.enc")
        keyFile.writeBytes(byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C) + ByteArray(40))

        val error = assertFailsWith<IllegalStateException> {
            EncryptedFileLocalKeyStoreFactory.create(keyFile, password)
        }
        assertTrue(
            error.message.orEmpty().contains("Regenerate", ignoreCase = true),
            "Legacy stores must fail with a clear regenerate-keystore error, got: ${error.message}",
        )
    }

    @Test
    fun corruptSaltFileFailsClosed() {
        val keyFile = newKeyFile("keys.enc")
        EncryptedFileLocalKeyStoreFactory.saltFileFor(keyFile).writeBytes(byteArrayOf(1, 2, 3))

        assertFailsWith<IllegalStateException> {
            EncryptedFileLocalKeyStoreFactory.create(keyFile, password)
        }
    }

    @Test
    fun explicitSaltSkipsThePersistedSaltFile() {
        val keyFile = newKeyFile("keys.enc")
        EncryptedFileLocalKeyStoreFactory.create(keyFile, password, salt = ByteArray(16) { it.toByte() })

        assertFalse(
            EncryptedFileLocalKeyStoreFactory.saltFileFor(keyFile).exists(),
            "A caller-managed salt must not be persisted",
        )
    }

    @Test
    fun tamperedStoreFileFailsAuthenticatedDecryption() {
        val keyFile = newKeyFile("keys.enc")
        EncryptedFileLocalKeyStoreFactory.create(keyFile, password)

        val encrypted = parseStoreFile(keyFile)
        val tamperedCiphertext = encrypted.ciphertext.clone().apply {
            this[0] = (this[0].toInt() xor 0x01).toByte()
        }
        val masterKey = MasterKeyDerivation.deriveKey(
            password = password,
            salt = saltBytes(keyFile),
            iterations = EncryptedFileLocalKeyStoreFactory.DEFAULT_PBKDF2_ITERATIONS,
        )

        assertFails("AES-GCM must reject a tampered ciphertext") {
            KeyEncryption(masterKey).decrypt(encrypted.copy(ciphertext = tamperedCiphertext))
        }
    }
}
