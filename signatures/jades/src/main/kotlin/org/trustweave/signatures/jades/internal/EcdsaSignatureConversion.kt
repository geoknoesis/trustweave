package org.trustweave.signatures.jades.internal

import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import java.io.ByteArrayInputStream
import java.math.BigInteger
import org.bouncycastle.asn1.ASN1InputStream

/**
 * Convert ECDSA signatures between JCA's DER-encoded form and the JWS-canonical raw R||S form.
 *
 * JCA's `Signature.getInstance("SHAxxxwithECDSA")` returns and accepts DER:
 *   `SEQUENCE { INTEGER r, INTEGER s }`
 * (RFC 3279 §2.2.3). JWS, by contrast, mandates a fixed-length unsigned big-endian R||S
 * concatenation (RFC 7515 §3.4, RFC 7518 §3.4): 64 bytes for P-256, 96 for P-384, 132 for P-521.
 *
 * Conversion is purely structural — no curve arithmetic.
 */
internal object EcdsaSignatureConversion {

    /**
     * Convert a JCA DER ECDSA signature to JWS raw form.
     *
     * @param der        DER-encoded `SEQUENCE { r, s }`.
     * @param rawSize    Target raw size (the JWS-canonical R||S length: 64, 96 or 132).
     * @return raw signature of exactly [rawSize] bytes.
     * @throws IllegalArgumentException if [der] is not a well-formed ECDSA signature.
     */
    fun derToRaw(der: ByteArray, rawSize: Int): ByteArray {
        require(rawSize % 2 == 0) { "rawSize must be even (got $rawSize)" }
        val componentSize = rawSize / 2
        val (r, s) = parseRs(der)

        val raw = ByteArray(rawSize)
        leftPadBigEndian(r, componentSize).copyInto(raw, destinationOffset = 0)
        leftPadBigEndian(s, componentSize).copyInto(raw, destinationOffset = componentSize)
        return raw
    }

    /**
     * Convert a JWS raw ECDSA signature back into JCA's DER form so that
     * [java.security.Signature.verify] can consume it.
     *
     * @param raw   Raw R||S signature, [rawSize] bytes.
     * @param rawSize  Expected raw size.
     * @return DER-encoded `SEQUENCE { r, s }`.
     * @throws IllegalArgumentException if [raw] has the wrong length.
     */
    fun rawToDer(raw: ByteArray, rawSize: Int): ByteArray {
        require(raw.size == rawSize) {
            "raw ECDSA signature is ${raw.size} bytes; expected $rawSize"
        }
        val componentSize = rawSize / 2
        val r = BigInteger(1, raw.copyOfRange(0, componentSize))
        val s = BigInteger(1, raw.copyOfRange(componentSize, rawSize))
        val seq = DERSequence(arrayOf(ASN1Integer(r), ASN1Integer(s)))
        return seq.getEncoded(ASN1Encoding.DER)
    }

    private fun parseRs(der: ByteArray): Pair<BigInteger, BigInteger> {
        return try {
            ASN1InputStream(ByteArrayInputStream(der)).use { input ->
                val obj = input.readObject() as? ASN1Sequence
                    ?: error("not an ASN.1 SEQUENCE")
                val r = (obj.getObjectAt(0) as ASN1Integer).value
                val s = (obj.getObjectAt(1) as ASN1Integer).value
                r to s
            }
        } catch (t: Throwable) {
            throw IllegalArgumentException("Malformed ECDSA DER signature: ${t.message}", t)
        }
    }

    private fun leftPadBigEndian(value: BigInteger, size: Int): ByteArray {
        // BigInteger.toByteArray() returns two's-complement; for a non-negative R/S this means
        // an optional leading 0x00 sign byte when the high bit is set. Strip it, then left-pad
        // with zeros to the fixed component size.
        val bytes = value.toByteArray()
        val unsigned = if (bytes.size > 1 && bytes[0] == 0x00.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        require(unsigned.size <= size) {
            "ECDSA component is ${unsigned.size} bytes; expected at most $size"
        }
        val padded = ByteArray(size)
        unsigned.copyInto(padded, destinationOffset = size - unsigned.size)
        return padded
    }
}
