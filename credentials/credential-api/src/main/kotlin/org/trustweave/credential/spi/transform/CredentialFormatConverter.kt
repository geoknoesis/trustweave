package org.trustweave.credential.spi.transform

import org.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.json.JsonObject

/**
 * Port interface for credential format conversion.
 *
 * Abstracts the format-conversion infrastructure (Nimbus JWT, Jackson CBOR) from the
 * credential domain. Implementations may use any JWT or binary encoding library.
 *
 * **Supported formats:**
 * - JSON-LD (W3C VC default)
 * - JWT (compact, unsigned — signing is handled by the proof engine)
 * - CBOR (binary, RFC 8949)
 */
interface CredentialFormatConverter {
    /** Convert a credential to an unsigned JWT string. */
    suspend fun toJwt(credential: VerifiableCredential): String

    /** Parse an unsigned (or signed) JWT and extract the embedded credential. */
    suspend fun fromJwt(jwt: String): VerifiableCredential

    /** Serialize a credential to its JSON-LD [JsonObject] representation. */
    suspend fun toJsonLd(credential: VerifiableCredential): JsonObject

    /** Deserialize a credential from a JSON-LD [JsonObject]. */
    suspend fun fromJsonLd(json: JsonObject): VerifiableCredential

    /** Encode a credential to CBOR bytes. */
    suspend fun toCbor(credential: VerifiableCredential): ByteArray

    /** Decode a credential from CBOR bytes. */
    suspend fun fromCbor(bytes: ByteArray): VerifiableCredential
}
