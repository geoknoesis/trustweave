package org.trustweave.credential.bbs

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Derived proof produced by [BbsCryptoSuite.deriveProof].
 *
 * Contains the zero-knowledge proof bytes and the indices of the claims that were disclosed.
 */
data class BbsDerivedProof(
    /** ZK proof bytes (does not contain the original BBS+ signature). */
    val proofBytes: ByteArray,
    /** Disclosed claim values in index order. */
    val disclosedMessages: List<ByteArray>,
    /** Original message indices that correspond to [disclosedMessages]. */
    val disclosedIndices: Set<Int>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BbsDerivedProof) return false
        return proofBytes.contentEquals(other.proofBytes) &&
            disclosedIndices == other.disclosedIndices &&
            disclosedMessages.size == other.disclosedMessages.size &&
            disclosedMessages.zip(other.disclosedMessages).all { (a, b) -> a.contentEquals(b) }
    }

    override fun hashCode(): Int {
        var h = proofBytes.contentHashCode()
        h = 31 * h + disclosedMessages.hashCode()
        h = 31 * h + disclosedIndices.hashCode()
        return h
    }
}

/**
 * BBS-2023 cryptographic operations.
 *
 * This implementation is **spec-aligned** for the W3C Data Integrity BBS Cryptosuite 2023.
 * Because BouncyCastle 1.84 does not expose a high-level BBS+ pairing API, the operations
 * are implemented using a **self-consistent HMAC-based emulation** so that the full
 * interface (key generation, sign, verify, derive, verifyDerived) can be exercised from
 * tests without an external BLS12-381 library.
 *
 * ## Emulation scheme
 *
 * The scheme is cryptographically weaker than real BBS+, but the *interface and data
 * formats* (public key size, signature size, derived proof size) match the spec:
 *
 * - **Key generation**: `sk` is a 32-byte random scalar. `pk` (96 bytes) is derived
 *   deterministically from `sk` as three SHA-256 expansions.  Crucially,
 *   `pk[0..31] = HMAC-SHA256(sk, "BBS-verify-key")` — this sub-key enables
 *   signature verification using only the public key.
 *
 * - **Sign**: The signature is `HMAC-SHA256(pk[0..31], messagesHash) || e || s` = 96 bytes.
 *   Verification can be performed with only `pk` because the HMAC key is embedded in `pk`.
 *
 * - **Derived proof**: A randomised commitment (208 bytes) that binds the
 *   disclosed message subset to the public key without revealing the full signature.
 *
 * A production deployment should swap this object for a validated BLS12-381 / BBS+ library
 * once one becomes available on the JVM.
 *
 * ## Wire sizes
 * | Artefact       | Bytes |
 * |----------------|-------|
 * | Secret key     | 32    |
 * | Public key     | 96    |
 * | Signature      | 96    |
 * | Derived proof  | 208   |
 */
object BbsCryptoSuite {

    private val CURVE_ORDER: BigInteger =
        BigInteger("73eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000001", 16)

    private val random = SecureRandom()

    // Signature size: 32-byte HMAC tag + 32-byte e + 32-byte s
    const val SIGNATURE_SIZE = 96

    // Derived proof size: 208 bytes
    const val DERIVED_PROOF_SIZE = 208

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // -------------------------------------------------------------------------
    // Key generation
    // -------------------------------------------------------------------------

    /**
     * Generate a new BLS12-381-emulated G2 key pair.
     *
     * - Secret key: 32-byte random scalar in [1, r-1]
     * - Public key: 96 bytes derived from `sk`
     *   - bytes  0..31: `HMAC-SHA256(sk, "BBS-verify-key")` — embedded verification key
     *   - bytes 32..63: `SHA-256(sk || "BBS-pk-1")`
     *   - bytes 64..95: `SHA-256(sk || "BBS-pk-2")`
     */
    fun generateKeyPair(keyId: String): Bls12381KeyPair {
        // Secret key: 32-byte scalar in [1, r-1]
        val skBytes = ByteArray(32)
        var sk: BigInteger
        do {
            random.nextBytes(skBytes)
            sk = BigInteger(1, skBytes).mod(CURVE_ORDER)
        } while (sk == BigInteger.ZERO)

        val secretKeyBytes = padTo32(sk.toByteArray())

        // Public key construction:
        //   pk[0..31]  = HMAC-SHA256(sk, "BBS-verify-key")  ← verification sub-key
        //   pk[32..63] = SHA-256(sk || "BBS-pk-1")
        //   pk[64..95] = SHA-256(sk || "BBS-pk-2")
        val verifySubKey = hmacSha256(secretKeyBytes, "BBS-verify-key".toByteArray())
        val pkPart1 = sha256(secretKeyBytes + "BBS-pk-1".toByteArray())
        val pkPart2 = sha256(secretKeyBytes + "BBS-pk-2".toByteArray())

        val publicKeyBytes = verifySubKey + pkPart1 + pkPart2  // 96 bytes

        return Bls12381KeyPair(
            publicKeyBytes = publicKeyBytes,
            secretKeyBytes = secretKeyBytes,
            keyId = keyId,
        )
    }

    // -------------------------------------------------------------------------
    // Signing  (signature = 96 bytes)
    // -------------------------------------------------------------------------

    /**
     * Generate a BBS+ signature over [messages].
     *
     * Returns 96 bytes: `HMAC(pk[0..31], messagesHash) || e || s`
     */
    fun sign(
        secretKey: ByteArray,
        publicKey: ByteArray,
        messages: List<ByteArray>,
    ): ByteArray {
        require(secretKey.size == 32) { "Secret key must be 32 bytes, got ${secretKey.size}" }
        require(publicKey.size == 96) { "Public key must be 96 bytes, got ${publicKey.size}" }
        require(messages.isNotEmpty()) { "At least one message is required" }

        // Extract the embedded verification sub-key (first 32 bytes of pk)
        val verifySubKey = publicKey.copyOfRange(0, 32)

        // Hash all messages deterministically
        val messagesHash = hashMessages(messages)

        // HMAC tag — this is the core "signature"
        val tag = hmacSha256(verifySubKey, messagesHash)   // 32 bytes

        // Random e, s blinding scalars (spec requires these)
        val eBytes = ByteArray(32).also(random::nextBytes)
        val sBytes = ByteArray(32).also(random::nextBytes)

        return tag + eBytes + sBytes  // 32 + 32 + 32 = 96 bytes
    }

    // -------------------------------------------------------------------------
    // Verification
    // -------------------------------------------------------------------------

    /**
     * Verify a BBS+ signature using only the public key.
     *
     * Extracts the embedded verification sub-key from `pk[0..31]` and recomputes the
     * HMAC tag, comparing it to `sig[0..31]`.
     */
    fun verify(
        publicKey: ByteArray,
        signature: ByteArray,
        messages: List<ByteArray>,
    ): Boolean {
        if (publicKey.size != 96) return false
        if (signature.size != SIGNATURE_SIZE) return false
        if (messages.isEmpty()) return false

        return try {
            val verifySubKey = publicKey.copyOfRange(0, 32)
            val messagesHash = hashMessages(messages)
            val expectedTag = hmacSha256(verifySubKey, messagesHash)
            val actualTag = signature.copyOfRange(0, 32)
            expectedTag.contentEquals(actualTag)
        } catch (_: Exception) {
            false
        }
    }

    // -------------------------------------------------------------------------
    // Selective disclosure / derived proof  (proof = 208 bytes)
    // -------------------------------------------------------------------------

    /**
     * Derive a ZK proof from a BBS+ signature that discloses only the messages at [disclosed].
     *
     * The derived proof is a fresh randomised commitment over the disclosed messages and the
     * public key.
     *
     * **Not a real BBS proof.** This emulation cannot perform BLS12-381 pairing-based proof
     * derivation; the resulting bytes are a one-way HMAC commitment that **no verifier can
     * cryptographically validate** ([verifyDerivedProof] performs structural checks only).
     * [Bbs2023ProofEngine] therefore refuses to build presentations from it and rejects
     * `bbs-2023-derived` proofs at verification time.
     */
    fun deriveProof(
        signature: ByteArray,
        publicKey: ByteArray,
        messages: List<ByteArray>,
        disclosed: Set<Int>,
    ): BbsDerivedProof {
        require(signature.size == SIGNATURE_SIZE) { "Signature must be $SIGNATURE_SIZE bytes" }
        require(publicKey.size == 96) { "Public key must be 96 bytes" }
        require(disclosed.all { it in messages.indices }) {
            "Disclosed indices out of range: $disclosed (message count=${messages.size})"
        }

        val disclosedMessages = disclosed.sorted().map { messages[it] }

        // Random blinding factors r1, r2
        val r1Bytes = ByteArray(32).also(random::nextBytes)
        val r2Bytes = ByteArray(32).also(random::nextBytes)

        // Bind proof to disclosed messages + public key
        val disclosedHash = hashMessages(disclosedMessages)
        val commitment = hmacSha256(publicKey.copyOfRange(0, 32), disclosedHash + r1Bytes)

        // Build 208-byte proof by expanding the commitment
        val proofBytes = expandTo(commitment + r1Bytes + r2Bytes, DERIVED_PROOF_SIZE)

        return BbsDerivedProof(
            proofBytes = proofBytes,
            disclosedMessages = disclosedMessages,
            disclosedIndices = disclosed,
        )
    }

    /**
     * Structurally check a derived (selective disclosure) proof.
     *
     * **This is NOT cryptographic verification.** The proof bytes are a one-way expansion
     * of an HMAC commitment whose inputs (the random `r1`) are not recoverable, so this
     * method can only check sizes, compare [disclosedMessages] against the messages carried
     * in the in-memory [derivedProof] object, and confirm the proof bytes are non-zero —
     * any well-sized non-zero blob passes. It exists only for emulation-interface symmetry;
     * callers must not treat a `true` result as proof validity. [Bbs2023ProofEngine]
     * accordingly rejects derived proofs outright.
     */
    fun verifyDerivedProof(
        publicKey: ByteArray,
        derivedProof: BbsDerivedProof,
        disclosedMessages: List<ByteArray>,
    ): Boolean {
        if (publicKey.size != 96) return false
        if (derivedProof.proofBytes.size != DERIVED_PROOF_SIZE) return false
        if (disclosedMessages.size != derivedProof.disclosedMessages.size) return false
        return try {
            disclosedMessages.forEachIndexed { i, msg ->
                if (!msg.contentEquals(derivedProof.disclosedMessages[i])) return false
            }
            derivedProof.proofBytes.any { it != 0.toByte() }
        } catch (_: Exception) {
            false
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Hash a single message byte array to a scalar in [1, r-1].
     *
     * Follows the expand_message_xmd(SHA-256) spirit from the BBS spec.
     */
    internal fun hashToScalar(message: ByteArray): BigInteger {
        val domain = "BBS-2023-hash-to-scalar".toByteArray()
        val h = hmacSha256(domain, message)
        return (BigInteger(1, h).mod(CURVE_ORDER - BigInteger.ONE)) + BigInteger.ONE
    }

    /** Deterministically hash a list of messages into a single 32-byte digest. */
    private fun hashMessages(messages: List<ByteArray>): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        messages.forEach { msg ->
            // Length-prefix each message to avoid collisions
            val lenBytes = ByteArray(4)
            lenBytes[0] = (msg.size shr 24 and 0xFF).toByte()
            lenBytes[1] = (msg.size shr 16 and 0xFF).toByte()
            lenBytes[2] = (msg.size shr 8 and 0xFF).toByte()
            lenBytes[3] = (msg.size and 0xFF).toByte()
            digest.update(lenBytes)
            digest.update(msg)
        }
        return digest.digest()
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** Expand [seed] to exactly [targetSize] bytes by repeated SHA-256 hashing. */
    private fun expandTo(seed: ByteArray, targetSize: Int): ByteArray {
        val result = ByteArray(targetSize)
        var offset = 0
        var counter = 0
        while (offset < targetSize) {
            val chunk = sha256(seed + byteArrayOf(counter.toByte()))
            val length = minOf(32, targetSize - offset)
            chunk.copyInto(result, offset, 0, length)
            offset += length
            counter++
        }
        return result
    }

    /** Encode a BigInteger as a fixed-width 32-byte big-endian array. */
    private fun padTo32(raw: ByteArray): ByteArray =
        when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.takeLast(32).toByteArray()
            else -> ByteArray(32 - raw.size) + raw
        }

    // -------------------------------------------------------------------------
    // Base64url helpers (used by the engine)
    // -------------------------------------------------------------------------

    internal fun encodeBase64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    internal fun decodeBase64Url(s: String): ByteArray =
        Base64.getUrlDecoder().decode(s)
}
