package org.trustweave.credential.vi.verification

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.trustweave.credential.vi.crypto.Es256
import org.trustweave.credential.vi.crypto.ViSdJwt
import org.trustweave.credential.vi.crypto.sha256B64Url

/**
 * Verifier-owned trust and challenge for a merchant's ES256 checkout JWT.
 * Never populate this policy from the presented token, QR code or agent request.
 * The merchant identity is the provisioned mapping for the pinned issuer/key, used by allowlists.
 * This profile requires JWT typ, a single audience, nonce, iat/exp, and cart.items.
 * The host must atomically consume the challenge before performing the authorized action.
 */
public class CheckoutTrust(
    private val expectedIssuer: String,
    private val expectedAudience: String,
    private val expectedNonce: String,
    publicJwk: JsonObject,
    merchantIdentity: JsonObject,
) {
    private val key = JsonObject(publicJwk.toMap())
    private val merchant = JsonObject(merchantIdentity.toMap())

    init {
        require(expectedIssuer.isNotBlank() && expectedAudience.isNotBlank() && expectedNonce.isNotBlank()) {
            "Checkout trust requires issuer, audience and nonce"
        }
        require("d" !in key && string(key, "kty") == "EC" && string(key, "crv") == "P-256") {
            "Checkout trust requires a public P-256 key"
        }
        require(string(key, "x") != null && string(key, "y") != null) { "Checkout public key coordinates are required" }
        require(
            if ("id" in merchant) {
                string(merchant, "id") != null
            } else {
                string(merchant, "name") != null && string(merchant, "website") != null
            },
        ) { "Provisioned merchant identity requires id or name and website" }
    }

    internal fun authenticatedFulfillment(
        fulfillment: JsonObject,
        now: Long,
        skew: Long,
    ): JsonObject {
        require(now >= 0 && skew in 0..3600 && now <= Long.MAX_VALUE - skew) { "Invalid checkout verification time" }
        val compact = string(fulfillment, "checkout_jwt") ?: throw IllegalArgumentException("Missing checkout JWT")
        require(compact.length <= 1_048_576 && '~' !in compact && compact.all { it.code in 33..126 }) {
            "Invalid or oversized checkout JWT"
        }
        require(sha256B64Url(compact.toByteArray(Charsets.US_ASCII)) == string(fulfillment, "checkout_hash")) {
            "Checkout JWT hash mismatch"
        }
        require(Es256.verify(compact, key)) { "Checkout merchant signature verification failed" }
        val jwt = ViSdJwt.parse(compact)
        require(jwt.header.keys.all { it in setOf("alg", "typ", "kid") } && string(jwt.header, "typ") == "JWT") {
            "Unsupported checkout JWT header"
        }
        val claims = jwt.payload
        require(string(claims, "iss") == expectedIssuer) { "Checkout issuer mismatch" }
        require(string(claims, "aud") == expectedAudience) { "Checkout audience mismatch" }
        require(string(claims, "nonce") == expectedNonce) { "Checkout nonce mismatch" }

        fun timestamp(name: String): Long =
            (claims[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it >= 0 }
                ?: throw IllegalArgumentException("Checkout requires numeric $name")
        val issued = timestamp("iat")
        val expires = timestamp("exp")
        require(expires > issued && expires - issued <= 3600 && issued <= now + skew && expires > now - skew) {
            "Checkout JWT is expired, future-dated or has an invalid lifetime"
        }
        val cart = claims["cart"] as? JsonObject ?: throw IllegalArgumentException("Checkout JWT requires cart")
        val items = cart["items"] as? JsonArray ?: throw IllegalArgumentException("Checkout cart requires items")
        require("line_items" !in fulfillment || fulfillment["line_items"] == items) { "Agent cart differs from signed merchant cart" }
        require("merchant" !in fulfillment || fulfillment["merchant"] == merchant) { "Agent merchant differs from provisioned merchant" }
        return JsonObject(
            fulfillment +
                mapOf(
                    "line_items" to items,
                    "merchant" to merchant,
                    "merchant_recurrence" to (claims["recurrence"] ?: kotlinx.serialization.json.JsonNull),
                ),
        )
    }

    private fun string(
        value: JsonObject,
        name: String,
    ): String? = (value[name] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
}
