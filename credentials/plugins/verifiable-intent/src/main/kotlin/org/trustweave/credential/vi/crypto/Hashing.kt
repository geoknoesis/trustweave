package org.trustweave.credential.vi.crypto

import java.security.MessageDigest
import java.util.Base64

/** Base64url codec (no padding) and SHA-256 helpers shared across the VI crypto layer. */
internal object B64 {
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun decode(s: String): ByteArray = decoder.decode(s)
}

/** SHA-256 over [bytes], base64url-encoded (no padding). The VI canonical digest form. */
internal fun sha256B64Url(bytes: ByteArray): String =
    B64.encode(MessageDigest.getInstance("SHA-256").digest(bytes))
