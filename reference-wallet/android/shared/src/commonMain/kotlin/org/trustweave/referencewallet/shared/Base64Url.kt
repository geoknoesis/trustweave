package org.trustweave.referencewallet.shared

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Base64url (RFC 4648 §5) without padding — multiplatform via kotlin.io.encoding.Base64.
 *
 * Note: `Base64.UrlSafe.withPadding(PaddingOption.ABSENT)` would be cleaner but
 * landed in Kotlin 2.1. This module targets Kotlin 2.0, so we strip/re-pad manually.
 */
@OptIn(ExperimentalEncodingApi::class)
object Base64Url {
    fun encode(bytes: ByteArray): String =
        Base64.UrlSafe.encode(bytes).trimEnd('=')

    fun decode(s: String): ByteArray {
        // Re-pad to a multiple of 4 if the input lost its padding (typical for the
        // base64url-no-pad form used by JWS, SD-JWT VC, etc.).
        val padded = if (s.length % 4 == 0) s else s + "=".repeat(4 - s.length % 4)
        return Base64.UrlSafe.decode(padded)
    }

    fun encodeString(s: String): String = encode(s.encodeToByteArray())
    fun decodeString(s: String): String = decode(s).decodeToString()
}
