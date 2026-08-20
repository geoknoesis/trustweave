package org.trustweave.kms.util

import java.math.BigInteger

/**
 * Outcome of auditing one persisted secp256k1 signature. See [Secp256k1SignatureAudit] for what
 * each value can and cannot conclude.
 */
enum class Secp256k1AuditVerdict {
    /** The signature verifies against the given public key and message digest. Not affected. */
    VALID,

    /**
     * The signature does not verify, and reconstructing what the pre-fix `writeFixedWidth` would
     * have overwritten yields a signature that *does* verify. This is the padding-defect
     * fingerprint: see [Secp256k1SignatureAudit] for exactly how much confidence that carries.
     */
    CORRUPTED_BY_PADDING_DEFECT,

    /**
     * The signature does not verify, and either its `s` component is outside the numeric range
     * this defect can produce, or no reconstruction attempt verified. Covers wrong keys, tampered
     * messages, truncated/malformed records, and corruption from any cause other than this
     * specific defect.
     */
    INVALID_OTHER,
}

/**
 * One persisted signature to audit: the signature bytes, the public key that should have
 * produced them, and the digest of the message they were computed over. [id] is not
 * interpreted — it is only echoed back in [Secp256k1AuditOutcome] so the caller can correlate a
 * result to the original record (a row id, a credential id, whatever the export uses).
 *
 * @param signature 64-byte P1363 (`r || s`) secp256k1 signature.
 * @param publicKey SEC 1-encoded point: 65-byte uncompressed (`0x04 || X || Y`) or 33-byte
 *   compressed (`0x02`/`0x03 || X`).
 * @param messageDigest the 32-byte hash the signature was computed over — **not** the raw
 *   message. Callers must hash it themselves with whatever algorithm was actually used at
 *   signing time (this codebase uses SHA-256 for VC/JWS-style proofs and Keccak-256 for
 *   Ethereum-anchored transactions; they are not interchangeable).
 */
data class Secp256k1AuditRecord(
    val id: String,
    val signature: ByteArray,
    val publicKey: ByteArray,
    val messageDigest: ByteArray,
)

/**
 * The [Secp256k1AuditVerdict] for one [Secp256k1AuditRecord.id]. [detail] is populated only when
 * [Secp256k1SignatureAudit.auditBatch] had to fall back to [Secp256k1AuditVerdict.INVALID_OTHER]
 * because the record itself was malformed (wrong-length signature/digest, undecodable public
 * key) rather than because a well-formed signature failed to verify.
 */
data class Secp256k1AuditOutcome(
    val id: String,
    val verdict: Secp256k1AuditVerdict,
    val detail: String? = null,
)

/**
 * Aggregate result of [Secp256k1SignatureAudit.auditBatch] over an exported corpus of signatures.
 * `verified + corruptedByPaddingDefect + invalidOther == total`.
 */
data class Secp256k1AuditSummary(
    val total: Int,
    val verified: Int,
    val corruptedByPaddingDefect: Int,
    val invalidOther: Int,
    val outcomes: List<Secp256k1AuditOutcome>,
)

/**
 * Audits persisted secp256k1 signatures for the `writeFixedWidth` padding defect fixed in commit
 * `cbd1e616` (see that commit message and `EcdsaSignatureCodecTest` for the root cause). The
 * defect corrupted roughly 1 in 256 signatures normalized through [EcdsaSignatureCodec]'s
 * pre-fix `normalizeSecp256k1LowS`; a library upgrade does not repair signatures already
 * persisted before the fix, so they must be found and re-signed.
 *
 * **What this tool needs.** [classify] and [auditBatch] take the public key and message digest
 * alongside the signature — they perform full ECDSA verification, in pure Kotlin/[BigInteger]
 * (see [Secp256k1Curve]), with no KMS provider, JCA security provider, or network access. This is
 * an offline read-only tool: it never touches a private key.
 *
 * **How the corruption is fingerprinted.** The bug only fires when `writeFixedWidth` is asked to
 * write `n - s` into a 32-byte field and `n - s` needs *fewer* than 32 bytes: the pre-fix code
 * right-aligned the new bytes without zeroing the leading pad, so the top bytes of the *old*
 * (high) `s` survive untouched and only the low bytes become the correct value. That has a clean
 * algebraic consequence for a persisted, already-corrupted `s`:
 *
 * ```
 * corrupted_s = floor(original_high_s / 256^L) * 256^L + (n - original_high_s)
 * ```
 * where `L` is the byte length of `n - original_high_s`. Rearranging:
 * ```
 * n - corrupted_s = original_high_s mod 256^L
 * ```
 * which is, by construction, strictly less than `256^L` — i.e. it *always* has at least one
 * leading zero byte when `L <= 31`. That gives two provable facts this tool relies on:
 *
 * 1. **Necessary condition (the pre-filter, [canBeVictimOfPaddingDefect]).** Any signature this
 *    bug could have produced has `n - s` with a leading zero byte in its 32-byte encoding. A
 *    signature whose stored `s` does not have this shape was never touched by this bug — full
 *    stop, no verification needed. This is cheap (one subtraction) and needs only the signature
 *    bytes: no key, no message. It is **not sufficient**: plenty of genuinely valid signatures
 *    (any high-s signature whose `n - s` happens to be small) also have this shape by pure
 *    chance, at the same ~1-in-256 rate the bug itself occurs at. The pre-filter narrows a corpus
 *    before spending a public key + digest + verification on each record; it does not, by itself,
 *    prove or disprove corruption.
 * 2. **Reconstruction ([classify]'s second step).** Because `s`'s low `L'` bytes for any
 *    `L' <= L` are literally the low bytes of the correctly-computed low-s value, taking `s mod
 *    256^len` for `len` from `minimalByteLength(n - s)` up to 31 and re-verifying *must*, at
 *    `len == L`, reconstruct the exact signature that should have been persisted before the bug
 *    corrupted it. If that reconstruction verifies, the classifier reports
 *    [Secp256k1AuditVerdict.CORRUPTED_BY_PADDING_DEFECT].
 *
 * **How much can [classify] actually prove?** For a signature that fails direct verification but
 * whose reconstruction succeeds, the reconstructed `(r, len bytes of s)` is *itself* a genuine
 * valid ECDSA signature over the given key and digest. For an unrelated failure (wrong key,
 * altered message, random bytes that coincidentally landed in the vulnerable numeric range) to
 * also produce a reconstruction that verifies would require that reconstruction to itself be a
 * forged valid signature — as hard as breaking ECDSA outright. So in practice a
 * `CORRUPTED_BY_PADDING_DEFECT` verdict is about as conclusive as signature verification ever
 * gets, *given that the exact bug mechanics are what actually happened*. What it does **not**
 * prove: that this specific defect (as opposed to some other bit-for-bit-identical corruption
 * path nobody has described) is what produced the bytes — an assumption this tool cannot verify
 * because it only ever sees the corrupted output, never the original signing call. Treat the
 * verdict as "matches the defect's fingerprint, and a signature consistent with that fingerprint
 * exists and verifies" rather than a courtroom-grade causal proof.
 *
 * **What [classify] cannot conclude for `INVALID_OTHER`.** A verdict of `INVALID_OTHER` means
 * this specific defect's fingerprint was absent or did not reconstruct to a valid signature. It
 * says nothing about *why* the signature is invalid — wrong key, tampered payload, truncated
 * export row, or a completely different bug. Do not read `INVALID_OTHER` as "safe" or
 * "unaffected by any problem"; it only rules out *this* defect.
 *
 * **Performance.** [Secp256k1Curve] does affine-coordinate double-and-add, optimized for
 * correctness and auditability rather than throughput. Each [classify] call does one scalar-mult
 * pair for the direct check, and — only for the small fraction of signatures that fail direct
 * verification *and* pass the pre-filter — up to 31 more for reconstruction. Expect single-digit
 * milliseconds per record; this is an offline batch tool, not a hot path.
 */
object Secp256k1SignatureAudit {
    private const val SIGNATURE_SIZE_BYTES = 64
    private const val FIELD_SIZE_BYTES = 32
    private const val DIGEST_SIZE_BYTES = 32

    /** Largest byte length a corrupted `s` field's pre-fix delta can have (see class KDoc). */
    private const val MAX_VULNERABLE_LENGTH = 31

    /**
     * Cheap, signature-bytes-only pre-filter: returns `true` only if this signature's `s`
     * component is numerically consistent with having been produced by the padding defect (i.e.
     * `n - s` has a leading zero byte). Needs no public key, no message, no verification.
     *
     * This is a **necessary, not sufficient** condition — see the class KDoc for why. A `false`
     * result is a firm "not a victim of this bug"; a `true` result only means the record is worth
     * the cost of fetching its key/digest and running [classify].
     *
     * @throws IllegalArgumentException if [signature] is not 64 bytes.
     */
    fun canBeVictimOfPaddingDefect(signature: ByteArray): Boolean {
        require(signature.size == SIGNATURE_SIZE_BYTES) {
            "secp256k1 P1363 signature must be $SIGNATURE_SIZE_BYTES bytes, got ${signature.size}"
        }
        return vulnerablePadLength(extractS(signature)) != null
    }

    /**
     * Classifies one persisted signature: does it verify, and if not, does it bear this defect's
     * fingerprint? See the class KDoc for exactly what each [Secp256k1AuditVerdict] does and does
     * not establish.
     *
     * @param signature 64-byte P1363 (`r || s`) secp256k1 signature.
     * @param publicKey SEC 1-encoded point (65-byte uncompressed or 33-byte compressed).
     * @param messageDigest the 32-byte digest the signature was computed over.
     * @throws IllegalArgumentException if [signature] is not 64 bytes, [messageDigest] is not 32
     *   bytes, or [publicKey] is not a validly-encoded, on-curve secp256k1 point.
     */
    fun classify(
        signature: ByteArray,
        publicKey: ByteArray,
        messageDigest: ByteArray,
    ): Secp256k1AuditVerdict {
        require(signature.size == SIGNATURE_SIZE_BYTES) {
            "secp256k1 P1363 signature must be $SIGNATURE_SIZE_BYTES bytes, got ${signature.size}"
        }
        require(messageDigest.size == DIGEST_SIZE_BYTES) {
            "messageDigest must be $DIGEST_SIZE_BYTES bytes, got ${messageDigest.size}"
        }
        val point =
            Secp256k1Curve.decodePoint(publicKey)
                ?: throw IllegalArgumentException(
                    "publicKey is not a valid SEC1-encoded secp256k1 point (${publicKey.size} bytes)",
                )

        val r = extractR(signature)
        val s = extractS(signature)
        val digest = BigInteger(1, messageDigest)

        if (Secp256k1Curve.verifySignature(point, digest, r, s)) {
            return Secp256k1AuditVerdict.VALID
        }

        val lowerBoundLength = vulnerablePadLength(s) ?: return Secp256k1AuditVerdict.INVALID_OTHER

        for (len in lowerBoundLength..MAX_VULNERABLE_LENGTH) {
            val candidate = s.mod(BigInteger.ONE.shiftLeft(8 * len))
            if (candidate.signum() == 0) continue
            if (Secp256k1Curve.verifySignature(point, digest, r, candidate)) {
                return Secp256k1AuditVerdict.CORRUPTED_BY_PADDING_DEFECT
            }
        }
        return Secp256k1AuditVerdict.INVALID_OTHER
    }

    /**
     * Audits a batch of exported signatures and returns a summary an operator can act on
     * directly: how many verify untouched, how many carry this defect's fingerprint, and how
     * many are invalid for some other reason. [Secp256k1AuditSummary.outcomes] carries the
     * per-record verdict so specific records can be located for re-signing.
     *
     * A record that is itself malformed (wrong-length signature/digest, an undecodable public
     * key) is reported as [Secp256k1AuditVerdict.INVALID_OTHER] with a
     * [Secp256k1AuditOutcome.detail] explaining why, rather than aborting the whole sweep.
     */
    fun auditBatch(records: List<Secp256k1AuditRecord>): Secp256k1AuditSummary {
        val outcomes =
            records.map { record ->
                classifyOrMalformed(record)
            }
        val corruptedCount =
            outcomes.count {
                it.verdict == Secp256k1AuditVerdict.CORRUPTED_BY_PADDING_DEFECT
            }
        return Secp256k1AuditSummary(
            total = outcomes.size,
            verified = outcomes.count { it.verdict == Secp256k1AuditVerdict.VALID },
            corruptedByPaddingDefect = corruptedCount,
            invalidOther = outcomes.count { it.verdict == Secp256k1AuditVerdict.INVALID_OTHER },
            outcomes = outcomes,
        )
    }

    private fun classifyOrMalformed(record: Secp256k1AuditRecord): Secp256k1AuditOutcome =
        try {
            val verdict = classify(record.signature, record.publicKey, record.messageDigest)
            Secp256k1AuditOutcome(record.id, verdict)
        } catch (e: IllegalArgumentException) {
            Secp256k1AuditOutcome(
                record.id,
                Secp256k1AuditVerdict.INVALID_OTHER,
                detail = "malformed record: ${e.message}",
            )
        }

    private fun extractR(signature: ByteArray): BigInteger = BigInteger(1, signature.copyOfRange(0, FIELD_SIZE_BYTES))

    private fun extractS(signature: ByteArray): BigInteger = BigInteger(1, signature.copyOfRange(FIELD_SIZE_BYTES, SIGNATURE_SIZE_BYTES))

    /**
     * Returns the minimal byte length of `n - s` when it is `<= 31` (i.e. has a leading zero byte
     * in a 32-byte encoding — the necessary condition from the class KDoc), or `null` when `s` is
     * outside the numeric range this defect can produce.
     */
    private fun vulnerablePadLength(s: BigInteger): Int? {
        val delta = Secp256k1Curve.ORDER.subtract(s)
        if (delta.signum() <= 0) return null
        val len = minimalByteLength(delta)
        return len.takeIf { it in 1..MAX_VULNERABLE_LENGTH }
    }

    private fun minimalByteLength(value: BigInteger): Int {
        if (value.signum() == 0) return 0
        val raw = value.toByteArray()
        return if (raw.size > 1 && raw[0] == 0.toByte()) raw.size - 1 else raw.size
    }
}
