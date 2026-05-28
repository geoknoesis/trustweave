package org.trustweave.credential.federation

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Handles signing and verification of OpenID Federation Entity Statement JWTs
 * using the Nimbus JOSE + JWT library.
 *
 * Supports EC (ES256, ES384, ES512) and RSA (RS256, RS384, RS512, PS256, PS384, PS512)
 * signing algorithms. EdDSA is not yet covered by this implementation.
 */
class EntityStatementJwtProcessor(
    @Suppress("unused") private val httpClient: OkHttpClient = OkHttpClient(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /**
     * Parses an Entity Statement from a compact serialized JWT string without
     * performing signature verification.
     *
     * Returns `null` if the JWT cannot be parsed or its claims cannot be mapped
     * to an [EntityStatement].
     *
     * @param jwt Compact serialized JWT (header.payload.signature).
     */
    fun parse(jwt: String): EntityStatement? =
        runCatching {
            val parsed = JWTParser.parse(jwt)
            // payload.toBytes() decodes the base64url-encoded payload to raw JSON bytes;
            // supports both signed JWTs and unsecured (alg=none) JWTs used in tests/drafts.
            val payloadJson = when (parsed) {
                is SignedJWT -> String(parsed.payload.toBytes(), Charsets.UTF_8)
                is PlainJWT -> String(parsed.payload.toBytes(), Charsets.UTF_8)
                else -> error("Unsupported JWT type: ${parsed::class.simpleName}")
            }
            json.decodeFromString(EntityStatement.serializer(), payloadJson)
        }.getOrNull()

    /**
     * Verifies the signature of [jwt] against the keys in [jwks].
     *
     * Iterates over all keys in the JWK Set and returns `true` as soon as one
     * key successfully verifies the signature. Returns `false` if no key matches
     * or if parsing fails.
     *
     * @param jwt Compact serialized JWT to verify.
     * @param jwks The JWK Set containing the verifying public key(s).
     */
    fun verify(jwt: String, jwks: FederationJwkSet): Boolean =
        runCatching {
            val signed = SignedJWT.parse(jwt)
            val jwkSetJson = json.encodeToString(FederationJwkSet.serializer(), jwks)
            val nimbusJwkSet = JWKSet.parse(jwkSetJson)

            nimbusJwkSet.keys.any { key ->
                runCatching {
                    when (key) {
                        is ECKey -> signed.verify(ECDSAVerifier(key))
                        is RSAKey -> signed.verify(RSASSAVerifier(key))
                        else -> false
                    }
                }.getOrDefault(false)
            }
        }.getOrDefault(false)

    /**
     * Signs an [EntityStatement] and returns the compact serialized JWT.
     *
     * @param statement The entity statement payload to sign.
     * @param privateKeyJwk JWK JSON string of the private signing key.
     * @param algorithm JWA algorithm identifier, e.g. "ES256". Defaults to "ES256".
     * @return Compact serialized signed JWT.
     * @throws IllegalArgumentException if the key type is unsupported.
     */
    fun sign(
        statement: EntityStatement,
        privateKeyJwk: String,
        algorithm: String = "ES256",
    ): String {
        val jwsAlgorithm = JWSAlgorithm.parse(algorithm)
        val claimsJson = json.encodeToString(EntityStatement.serializer(), statement)
        val claimsSet = JWTClaimsSet.parse(claimsJson)

        val nimbusKey = com.nimbusds.jose.jwk.JWK.parse(privateKeyJwk)

        val signer = when (nimbusKey) {
            is ECKey -> ECDSASigner(nimbusKey)
            is RSAKey -> RSASSASigner(nimbusKey)
            else -> throw IllegalArgumentException(
                "Unsupported key type for signing: ${nimbusKey.keyType}",
            )
        }

        val header = JWSHeader.Builder(jwsAlgorithm)
            .apply { nimbusKey.keyID?.let { keyID(it) } }
            .build()

        return SignedJWT(header, claimsSet).also { it.sign(signer) }.serialize()
    }

}
