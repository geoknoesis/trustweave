package org.trustweave.credential.vi.crypto

import kotlinx.serialization.json.JsonObject

/**
 * `cnf` (RFC 7800) key-confirmation helpers for VI's embedded-JWK delegation.
 *
 * VI resolves keys purely from embedded JWKs — no DID resolution: L1 `cnf.jwk` verifies L2; an L2
 * open-mandate `cnf.jwk` (+ `kid`) verifies L3; L3 carries no `cnf`. This differs fundamentally from
 * the DID-`assertionMethod` resolution the existing `SdJwtProofEngine` uses.
 */
internal object Cnf {
    /** Extracts `cnf.jwk` (an EC P-256 JWK) from a JWT payload, or null if absent/malformed. */
    fun jwk(payload: JsonObject): JsonObject? =
        (payload["cnf"] as? JsonObject)?.get("jwk") as? JsonObject

    /** Extracts the `kid` from a `cnf.jwk` object, or null. */
    fun kid(jwk: JsonObject): String? = jwk["kid"]?.contentOrNull()
}
