package org.trustweave.credential.bbs

import java.util.Base64

/**
 * BLS12-381 key pair for BBS+ signature operations.
 *
 * @param publicKeyBytes  96-byte compressed G2 point (public key)
 * @param secretKeyBytes  32-byte scalar (secret key); null for verification-only instances
 * @param keyId           Identifier for this key pair (e.g. a DID verification-method URL)
 */
data class Bls12381KeyPair(
    val publicKeyBytes: ByteArray,
    val secretKeyBytes: ByteArray?,
    val keyId: String,
) {
    /** Standard (non-URL-safe) Base64 encoding of the public key bytes. */
    val publicKeyBase64: String
        get() = Base64.getEncoder().encodeToString(publicKeyBytes)

    /** Base64url (no padding) encoding of the public key bytes. */
    val publicKeyBase64Url: String
        get() = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKeyBytes)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bls12381KeyPair) return false
        return keyId == other.keyId &&
            publicKeyBytes.contentEquals(other.publicKeyBytes) &&
            (secretKeyBytes == null && other.secretKeyBytes == null ||
                secretKeyBytes != null && other.secretKeyBytes != null &&
                    secretKeyBytes.contentEquals(other.secretKeyBytes))
    }

    override fun hashCode(): Int {
        var result = publicKeyBytes.contentHashCode()
        result = 31 * result + (secretKeyBytes?.contentHashCode() ?: 0)
        result = 31 * result + keyId.hashCode()
        return result
    }

    override fun toString(): String =
        "Bls12381KeyPair(keyId=$keyId, publicKeyBase64=${publicKeyBase64.take(16)}…)"
}
