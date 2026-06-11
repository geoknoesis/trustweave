package org.trustweave.credential.vi.crypto

import kotlinx.serialization.json.JsonObject

/** Encodes a compact ES256 JWS (`b64u(header).b64u(payload).b64u(sig)`) using an [Es256Signer]. */
internal object Jws {
    suspend fun sign(header: JsonObject, payload: JsonObject, signer: Es256Signer): String {
        val h = B64.encode(header.toString().toByteArray(Charsets.UTF_8))
        val p = B64.encode(payload.toString().toByteArray(Charsets.UTF_8))
        val signature = signer.sign("$h.$p".toByteArray(Charsets.US_ASCII))
        return "$h.$p.${B64.encode(signature)}"
    }
}

/** Serializes an SD-JWT presentation: `<jwt>~<d1>~...~<dN>~`. Empty disclosures → `<jwt>~`. */
internal fun serializeSdJwt(jwt: String, disclosures: List<String>): String =
    buildString {
        append(jwt)
        disclosures.forEach { append('~').append(it) }
        append('~')
    }

/** Builds a selective L2 presentation the way an L3 `sd_hash` binds to it (subset of disclosures). */
internal fun selectivePresentation(baseJwt: String, disclosures: List<String>): String =
    serializeSdJwt(baseJwt, disclosures)
