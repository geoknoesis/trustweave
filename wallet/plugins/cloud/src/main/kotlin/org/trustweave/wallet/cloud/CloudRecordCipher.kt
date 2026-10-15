package org.trustweave.wallet.cloud

import org.trustweave.wallet.exception.WalletException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Client-side AES-256-GCM for [CloudWallet] objects, mirroring the FileWallet scheme.
 *
 * Object layout: `"TWCW"` magic, 1 format-version byte, 12-byte random IV, ciphertext + 128-bit tag. The
 * AAD binds the wallet id, the record kind and the SHA-256 of the credential id, so an object cannot be
 * swapped between credentials, moved between wallets or replayed as another record kind.
 *
 * The magic header is what distinguishes an encrypted object from a legacy plaintext JSON object (which
 * always starts with `{`): an object WITH the header is always authenticated and never falls back to
 * plaintext, so tampering or the wrong key fails closed.
 */
internal class CloudRecordCipher(
    keyBytes: ByteArray,
    private val walletId: String,
) {
    private val key = SecretKeySpec(keyBytes.also { require(it.size == KEY_LENGTH_BYTES) { KEY_ERROR } }, "AES")
    private val secureRandom = SecureRandom()

    fun encrypt(
        plaintext: ByteArray,
        kind: String,
        credentialId: String,
    ): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad(kind, credentialId))
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC + byteArrayOf(FORMAT_VERSION) + iv + ciphertext
    }

    /**
     * Returns the plaintext of [data]. An object without the encryption header is a legacy plaintext
     * object and is returned unchanged (the documented migration path); an object with it must
     * authenticate or [WalletException.StorageError] is thrown.
     */
    fun decrypt(
        data: ByteArray,
        kind: String,
        credentialId: String,
    ): ByteArray {
        if (!hasHeader(data)) return data
        val headerLength = MAGIC.size + 1
        val minLength = headerLength + IV_LENGTH_BYTES + TAG_LENGTH_BITS / 8
        if (data.size < minLength || data[MAGIC.size] != FORMAT_VERSION) {
            throw WalletException.StorageError(
                operation = "decrypt",
                reason = "Cloud wallet object has an unsupported or truncated encryption header",
            )
        }
        val iv = data.copyOfRange(headerLength, headerLength + IV_LENGTH_BYTES)
        val ciphertext = data.copyOfRange(headerLength + IV_LENGTH_BYTES, data.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.updateAAD(aad(kind, credentialId))
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw WalletException.StorageError(
                operation = "decrypt",
                reason = "Cloud wallet object failed authentication: wrong key, tampered, or not this record",
                cause = e,
            )
        }
    }

    private fun aad(
        kind: String,
        credentialId: String,
    ): ByteArray = "trustweave-cloudwallet/v1\u0000$walletId\u0000$kind\u0000${sha256Hex(credentialId)}".toByteArray(Charsets.UTF_8)

    companion object {
        const val KIND_CREDENTIAL = "credential"
        const val KIND_METADATA = "metadata"
        const val KEY_LENGTH_BYTES = 32
        const val KEY_ERROR = "encryptionKey must be exactly 32 bytes (AES-256)"
        private val MAGIC = byteArrayOf('T'.code.toByte(), 'W'.code.toByte(), 'C'.code.toByte(), 'W'.code.toByte())
        private const val FORMAT_VERSION: Byte = 1
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        fun hasHeader(data: ByteArray): Boolean = data.size >= MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

        private fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
