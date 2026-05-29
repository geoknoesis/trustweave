package org.trustweave.anchor.indy

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Ed25519 signer for Indy ledger requests.
 *
 * Indy uses Ed25519 for transaction signing. Submitter keys are typically expressed
 * as Base58 strings on the wire ("seed-derived" verkeys and signing keys).
 *
 * This signer accepts a 32-byte raw Ed25519 secret seed (the "signing key" half) and
 * produces Base58-encoded 64-byte signatures matching what indy-vdr emits.
 */
internal class IndySigner private constructor(
    private val privateKey: Ed25519PrivateKeyParameters
) {

    /**
     * Sign [payload] and return the 64-byte signature encoded as Base58.
     */
    fun signBase58(payload: ByteArray): String {
        val signer = Ed25519Signer().apply {
            init(true, privateKey)
            update(payload, 0, payload.size)
        }
        val sig = signer.generateSignature()
        return Base58.encode(sig)
    }

    /**
     * Return the Base58-encoded 32-byte Ed25519 public verkey associated with this signer.
     * Useful for self-bootstrapping a NYM on a test pool.
     */
    fun publicVerkeyBase58(): String {
        val pub = privateKey.generatePublicKey().encoded
        return Base58.encode(pub)
    }

    companion object {
        /**
         * Create a signer from a 32-byte raw Ed25519 secret seed.
         */
        fun fromSeed(seed: ByteArray): IndySigner {
            require(seed.size == Ed25519PrivateKeyParameters.KEY_SIZE) {
                "Ed25519 seed must be ${Ed25519PrivateKeyParameters.KEY_SIZE} bytes, got ${seed.size}"
            }
            return IndySigner(Ed25519PrivateKeyParameters(seed, 0))
        }

        /**
         * Create a signer from a Base58-encoded 32-byte seed (matches Indy's wire format
         * for signing keys exported by `indy-cli` or `aries-askar`).
         */
        fun fromBase58Seed(seedBase58: String): IndySigner = fromSeed(Base58.decode(seedBase58))

        /**
         * Create a signer from a 64-byte Base58 string of the form `seed || pubkey`. This
         * is what `indy-vdr-proxy` exposes as the "signing key" of a wallet entry.
         */
        fun fromBase58SigningKey(signingKeyBase58: String): IndySigner {
            val bytes = Base58.decode(signingKeyBase58)
            require(bytes.size >= Ed25519PrivateKeyParameters.KEY_SIZE) {
                "Signing key must be at least ${Ed25519PrivateKeyParameters.KEY_SIZE} bytes, got ${bytes.size}"
            }
            return fromSeed(bytes.copyOfRange(0, Ed25519PrivateKeyParameters.KEY_SIZE))
        }
    }
}
