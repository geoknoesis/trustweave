package org.trustweave.referencewallet.shared

import java.security.MessageDigest
import java.security.SecureRandom
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * JVM actuals for the shared crypto primitives.
 *
 * Uses Bouncy Castle's low-level Ed25519 API directly (bypassing JCA) to avoid the
 * provider-registration dance and to work identically on Android and plain JVM.
 *
 * NOTE: Java's MessageDigest + SecureRandom are part of the JCA — always available
 * on JVM and Android, no provider setup needed.
 */

actual fun sha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)

private val secureRandom: SecureRandom by lazy { SecureRandom() }

actual fun secureRandomBytes(length: Int): ByteArray {
    val bytes = ByteArray(length)
    secureRandom.nextBytes(bytes)
    return bytes
}

actual fun ed25519GenerateKeyPair(): Ed25519KeyPairBytes {
    val priv = Ed25519PrivateKeyParameters(secureRandom)
    val pub = priv.generatePublicKey()
    return Ed25519KeyPairBytes(publicKey = pub.encoded, privateKey = priv.encoded)
}

actual fun ed25519Sign(payload: ByteArray, privateKey: ByteArray): ByteArray {
    require(privateKey.size == 32) { "Ed25519 private key must be 32 bytes, got ${privateKey.size}" }
    val signer = Ed25519Signer()
    signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
    signer.update(payload, 0, payload.size)
    return signer.generateSignature()
}

actual fun ed25519Verify(signature: ByteArray, payload: ByteArray, publicKey: ByteArray): Boolean {
    require(publicKey.size == 32) { "Ed25519 public key must be 32 bytes, got ${publicKey.size}" }
    val verifier = Ed25519Signer()
    verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
    verifier.update(payload, 0, payload.size)
    return verifier.verifySignature(signature)
}
