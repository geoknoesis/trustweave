package org.trustweave.referencewallet.shared

/**
 * Platform-specific crypto primitives. Each platform provides an actual
 * implementation (JVM uses java.security + BouncyCastle, iOS uses CommonCrypto + CryptoKit).
 *
 * Keeping these as `expect` declarations rather than depending on a multiplatform
 * crypto library (cryptography-kotlin, krypto, kotlin-multiplatform-crypto, etc.) is
 * a deliberate choice: each of those libraries has its own dependency footprint and
 * iOS-targeting story. For a reference wallet, four hand-rolled expect/actuals are
 * simpler than vendoring a crypto framework.
 */

/** SHA-256 of [input]. Returns the 32-byte raw digest. */
expect fun sha256(input: ByteArray): ByteArray

/** Cryptographically-random bytes (suitable for nonces, salts, key generation). */
expect fun secureRandomBytes(length: Int): ByteArray

/** Raw 32-byte Ed25519 key pair. */
data class Ed25519KeyPairBytes(val publicKey: ByteArray, val privateKey: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is Ed25519KeyPairBytes &&
            publicKey.contentEquals(other.publicKey) &&
            privateKey.contentEquals(other.privateKey)
    override fun hashCode(): Int = publicKey.contentHashCode() * 31 + privateKey.contentHashCode()
}

/** Generate a fresh Ed25519 key pair. */
expect fun ed25519GenerateKeyPair(): Ed25519KeyPairBytes

/** Sign [payload] with raw Ed25519 [privateKey]. Returns the raw 64-byte signature. */
expect fun ed25519Sign(payload: ByteArray, privateKey: ByteArray): ByteArray

/** Verify a raw Ed25519 [signature] over [payload] under raw [publicKey]. */
expect fun ed25519Verify(signature: ByteArray, payload: ByteArray, publicKey: ByteArray): Boolean
