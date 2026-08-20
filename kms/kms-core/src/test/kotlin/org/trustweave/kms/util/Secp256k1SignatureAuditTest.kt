package org.trustweave.kms.util

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD coverage for [Secp256k1SignatureAudit]: the classifier must (1) recognize a genuine victim
 * of the `writeFixedWidth` padding defect, (2) never flag a valid signature as a victim, and (3)
 * never flag a signature that is merely invalid for some unrelated reason (wrong key, altered
 * message) as a victim either.
 *
 * All signature/key/digest byte vectors below are genuine — computed by an independent Python
 * ECDSA implementation (curve arithmetic cross-checked against the well-known `2*G` constant; see
 * [Secp256k1CurveTest]) and then corrupted here by literally reproducing the pre-fix
 * `writeFixedWidth`: copy the original 32-byte high-`s` field, overwrite only the low `len` bytes
 * with the correct `n - s` value, and leave the untouched leading bytes as stale high-`s` bytes —
 * exactly what shipped before commit `cbd1e616`.
 */
class Secp256k1SignatureAuditTest {
    private fun hex(s: String): ByteArray {
        require(s.length % 2 == 0)
        return ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    // ------------------------------------------------------------------------------------
    // Vector A: private key 1 (public key = generator point G). Corruption length L = 31,
    // and the pre-filter's own lower bound (from n - corrupted_s) also lands on 31, so
    // reconstruction succeeds on its first candidate length.
    // ------------------------------------------------------------------------------------

    private val q1Uncompressed =
        hex(
            "04" +
                "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798" +
                "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",
        )
    private val q1Compressed = hex("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")

    // Private key 2 (public key = 2G) — used only to prove classify() is tied to the actual key.
    private val q2Uncompressed =
        hex(
            "04" +
                "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5" +
                "1ae168fea63dc339a3c58419466ceaeef7f632653266d0e1236431a950cfe52a",
        )

    private val digest = hex("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20")
    private val wrongDigest = hex("2122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40")

    private val r = hex("2426978711ec6592c35616e1a4ef002d2bf892deb50e2be529f83c4797d737bc")
    private val sValidLow = hex("009d5b320f805f26fb8951a6ef49ca486709feaaa95b8b850b63d8bb61510659")
    private val sValidHigh = hex("ff62a4cdf07fa0d90476ae5910b635b653a4de3c05ed14b6b46e85d16ee53ae8")
    private val sCorrupted = hex("ff9d5b320f805f26fb8951a6ef49ca486709feaaa95b8b850b63d8bb61510659")

    // A signature with the same r/pubkey/digest but s perturbed by flipping a bit away from the
    // 32/1-byte pad boundary: invalid, and NOT in the numeric range this defect can produce.
    private val sGarbage = hex("009d5b320f805f26fb8951a6ef49ca486709febaa95b8b850b63d8bb61510659")

    private val validSignature = r + sValidLow
    private val highSSignature = r + sValidHigh
    private val corruptedSignature = r + sCorrupted
    private val garbageSignature = r + sGarbage

    // ------------------------------------------------------------------------------------
    // Vector B: same key/digest, a different genuine signature whose corruption length is also
    // 31, but where n - corrupted_s (the pre-filter's lower bound) computes to 30 — one short of
    // the true corruption length — so classify() must try more than one candidate length before
    // its reconstruction succeeds.
    // ------------------------------------------------------------------------------------

    private val rB = hex("b3be157fafcbd41f4a7b049b83b5256d9e32c40556cf68ad2a4a3924e89284bc")
    private val sBValidLow = hex("00ff11822828dca2bee605f052dc44f5e87ffc93adaf81899fb12bd57f9c389a")
    private val sBCorrupted = hex("ffff11822828dca2bee605f052dc44f5e87ffc93adaf81899fb12bd57f9c389a")

    private val validSignatureB = rB + sBValidLow
    private val corruptedSignatureB = rB + sBCorrupted

    // ------------------------------------------------------------------------------------
    // classify(): the core TDD requirements from the task
    // ------------------------------------------------------------------------------------

    @Test
    fun `a genuine victim of the padding defect is classified as corrupted`() {
        assertEquals(
            Secp256k1AuditVerdict.CORRUPTED_BY_PADDING_DEFECT,
            Secp256k1SignatureAudit.classify(corruptedSignature, q1Uncompressed, digest),
        )
    }

    @Test
    fun `a victim requiring more than one reconstruction attempt is still classified as corrupted`() {
        assertEquals(
            Secp256k1AuditVerdict.CORRUPTED_BY_PADDING_DEFECT,
            Secp256k1SignatureAudit.classify(corruptedSignatureB, q1Uncompressed, digest),
        )
    }

    @Test
    fun `a genuinely valid low-s signature is classified as valid`() {
        assertEquals(
            Secp256k1AuditVerdict.VALID,
            Secp256k1SignatureAudit.classify(validSignature, q1Uncompressed, digest),
        )
        assertEquals(
            Secp256k1AuditVerdict.VALID,
            Secp256k1SignatureAudit.classify(validSignatureB, q1Uncompressed, digest),
        )
    }

    @Test
    fun `a valid but non-canonical high-s signature is classified valid, not corrupted`() {
        // (r, n-s) is mathematically as valid as (r, s) — ECDSA malleability. This s value even
        // happens to land in the numeric range the defect can produce (it must, since s_valid_low
        // was chosen to have a leading zero byte), but the signature genuinely verifies, so
        // classify() must never reach the corruption check for it.
        assertEquals(
            Secp256k1AuditVerdict.VALID,
            Secp256k1SignatureAudit.classify(highSSignature, q1Uncompressed, digest),
        )
    }

    @Test
    fun `wrong key is classified as invalid-other, not corrupted`() {
        assertEquals(
            Secp256k1AuditVerdict.INVALID_OTHER,
            Secp256k1SignatureAudit.classify(validSignature, q2Uncompressed, digest),
        )
    }

    @Test
    fun `a padding-defect-shaped signature checked against the wrong key is invalid-other`() {
        // corruptedSignature's bytes are exactly what the defect produces, but the reconstruction
        // must be checked against the key that actually signed it. Checked against a different
        // key, reconstruction cannot succeed and this must NOT be reported as corrupted.
        assertEquals(
            Secp256k1AuditVerdict.INVALID_OTHER,
            Secp256k1SignatureAudit.classify(corruptedSignature, q2Uncompressed, digest),
        )
    }

    @Test
    fun `altered message is classified as invalid-other, not corrupted`() {
        assertEquals(
            Secp256k1AuditVerdict.INVALID_OTHER,
            Secp256k1SignatureAudit.classify(validSignature, q1Uncompressed, wrongDigest),
        )
    }

    @Test
    fun `a padding-defect-shaped signature checked against the wrong message is invalid-other`() {
        assertEquals(
            Secp256k1AuditVerdict.INVALID_OTHER,
            Secp256k1SignatureAudit.classify(corruptedSignature, q1Uncompressed, wrongDigest),
        )
    }

    @Test
    fun `an invalid signature outside the defect's numeric range is invalid-other`() {
        assertEquals(
            Secp256k1AuditVerdict.INVALID_OTHER,
            Secp256k1SignatureAudit.classify(garbageSignature, q1Uncompressed, digest),
        )
    }

    @Test
    fun `classify accepts a compressed public key with the same result as uncompressed`() {
        assertEquals(
            Secp256k1SignatureAudit.classify(corruptedSignature, q1Uncompressed, digest),
            Secp256k1SignatureAudit.classify(corruptedSignature, q1Compressed, digest),
        )
    }

    @Test
    fun `classify validates input sizes and key encoding`() {
        assertFailsWith<IllegalArgumentException> {
            Secp256k1SignatureAudit.classify(ByteArray(63), q1Uncompressed, digest)
        }
        assertFailsWith<IllegalArgumentException> {
            Secp256k1SignatureAudit.classify(validSignature, q1Uncompressed, ByteArray(31))
        }
        assertFailsWith<IllegalArgumentException> {
            Secp256k1SignatureAudit.classify(validSignature, ByteArray(10), digest)
        }
    }

    // ------------------------------------------------------------------------------------
    // canBeVictimOfPaddingDefect(): the cheap, signature-only pre-filter
    // ------------------------------------------------------------------------------------

    @Test
    fun `prefilter excludes a genuinely valid low-s signature`() {
        assertFalse(Secp256k1SignatureAudit.canBeVictimOfPaddingDefect(validSignature))
        assertFalse(Secp256k1SignatureAudit.canBeVictimOfPaddingDefect(validSignatureB))
    }

    @Test
    fun `prefilter admits both the corrupted signature and its uncorrupted high-s counterpart`() {
        // The prefilter is a NECESSARY, not sufficient, condition: it is purely about the shape
        // of s relative to n, so a still-valid high-s signature that happens to have the same
        // shape also passes. That is expected and documented; classify() is what disambiguates.
        assertTrue(Secp256k1SignatureAudit.canBeVictimOfPaddingDefect(corruptedSignature))
        assertTrue(Secp256k1SignatureAudit.canBeVictimOfPaddingDefect(highSSignature))
    }

    @Test
    fun `prefilter excludes a signature whose s is not in the vulnerable numeric range`() {
        assertFalse(Secp256k1SignatureAudit.canBeVictimOfPaddingDefect(garbageSignature))
    }

    @Test
    fun `prefilter validates signature size`() {
        assertFailsWith<IllegalArgumentException> {
            Secp256k1SignatureAudit.canBeVictimOfPaddingDefect(ByteArray(65))
        }
    }

    /**
     * Reproduces the exact sweep style of `EcdsaSignatureCodecTest`'s
     * `high-s normalization zero-pads a short n minus s` test: construct `highS = n -
     * 2^(8*(L-1))` for every byte length `L` in `1..32`, apply the pre-fix `writeFixedWidth`
     * behaviour, and check the prefilter's necessary condition holds for exactly the range this
     * defect can produce (`L in 1..31`) and does not for the boundary case that never corrupts
     * (`L == 32`, i.e. `n - s` needs the full field width and `writeFixedWidth`'s missing zero-fill
     * is a no-op).
     */
    @Test
    fun `prefilter necessary condition holds across every writeFixedWidth pad length`() {
        val order = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
        for (byteLength in 1..32) {
            val delta = BigInteger.ONE.shiftLeft(8 * (byteLength - 1))
            val highS = order.subtract(delta)
            val sHighBytes = to32(highS)
            val deltaMinimal = to32(delta).copyOfRange(32 - byteLength, 32)
            val corrupted = sHighBytes.copyOf()
            System.arraycopy(deltaMinimal, 0, corrupted, 32 - byteLength, byteLength)
            val signature = ByteArray(32) + corrupted // r is irrelevant to this check

            val isVulnerable = Secp256k1SignatureAudit.canBeVictimOfPaddingDefect(signature)
            if (byteLength <= 31) {
                assertTrue(isVulnerable, "expected byteLength=$byteLength to be flagged vulnerable")
            } else {
                // byteLength == 32: writeFixedWidth's fill is a zero-length no-op, so this
                // "corrupted" array is actually just the correctly-written value, and it is the
                // full field width — the prefilter must not flag it.
                assertFalse(isVulnerable, "expected byteLength=32 (no actual corruption) to be excluded")
            }
        }
    }

    private fun to32(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        val start = if (raw.size > 1 && raw[0] == 0.toByte()) 1 else 0
        val out = ByteArray(32)
        System.arraycopy(raw, start, out, 32 - (raw.size - start), raw.size - start)
        return out
    }

    // ------------------------------------------------------------------------------------
    // auditBatch(): the operator-facing summary
    // ------------------------------------------------------------------------------------

    @Test
    fun `auditBatch tallies every category and preserves per-record correlation`() {
        val records =
            listOf(
                Secp256k1AuditRecord("valid-1", validSignature, q1Uncompressed, digest),
                Secp256k1AuditRecord("valid-2", validSignatureB, q1Uncompressed, digest),
                Secp256k1AuditRecord("corrupted-1", corruptedSignature, q1Uncompressed, digest),
                Secp256k1AuditRecord("corrupted-2", corruptedSignatureB, q1Uncompressed, digest),
                Secp256k1AuditRecord("wrong-key", validSignature, q2Uncompressed, digest),
                Secp256k1AuditRecord("wrong-message", validSignature, q1Uncompressed, wrongDigest),
                Secp256k1AuditRecord("garbage", garbageSignature, q1Uncompressed, digest),
            )

        val summary = Secp256k1SignatureAudit.auditBatch(records)

        assertEquals(7, summary.total)
        assertEquals(2, summary.verified)
        assertEquals(2, summary.corruptedByPaddingDefect)
        assertEquals(3, summary.invalidOther)
        assertEquals(7, summary.outcomes.size)

        val byId = summary.outcomes.associateBy { it.id }
        assertEquals(Secp256k1AuditVerdict.VALID, byId.getValue("valid-1").verdict)
        assertEquals(Secp256k1AuditVerdict.VALID, byId.getValue("valid-2").verdict)
        assertEquals(Secp256k1AuditVerdict.CORRUPTED_BY_PADDING_DEFECT, byId.getValue("corrupted-1").verdict)
        assertEquals(Secp256k1AuditVerdict.CORRUPTED_BY_PADDING_DEFECT, byId.getValue("corrupted-2").verdict)
        assertEquals(Secp256k1AuditVerdict.INVALID_OTHER, byId.getValue("wrong-key").verdict)
        assertEquals(Secp256k1AuditVerdict.INVALID_OTHER, byId.getValue("wrong-message").verdict)
        assertEquals(Secp256k1AuditVerdict.INVALID_OTHER, byId.getValue("garbage").verdict)
    }

    @Test
    fun `auditBatch reports a malformed record as invalid-other with a diagnostic detail instead of throwing`() {
        val records =
            listOf(
                Secp256k1AuditRecord("good", validSignature, q1Uncompressed, digest),
                Secp256k1AuditRecord("truncated", ByteArray(10), q1Uncompressed, digest),
            )

        val summary = Secp256k1SignatureAudit.auditBatch(records)

        assertEquals(2, summary.total)
        assertEquals(1, summary.verified)
        assertEquals(1, summary.invalidOther)
        val truncated = summary.outcomes.single { it.id == "truncated" }
        assertEquals(Secp256k1AuditVerdict.INVALID_OTHER, truncated.verdict)
        assertTrue(truncated.detail != null && truncated.detail!!.contains("malformed"))
        val good = summary.outcomes.single { it.id == "good" }
        assertNull(good.detail)
    }

    @Test
    fun `auditBatch over an empty corpus reports all zeros`() {
        val summary = Secp256k1SignatureAudit.auditBatch(emptyList())
        assertEquals(0, summary.total)
        assertEquals(0, summary.verified)
        assertEquals(0, summary.corruptedByPaddingDefect)
        assertEquals(0, summary.invalidOther)
        assertTrue(summary.outcomes.isEmpty())
    }
}
