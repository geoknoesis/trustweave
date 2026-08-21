package org.trustweave.credential.vi

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.trustweave.credential.vi.crypto.Es256

/**
 * Verifiable Intent permits exactly one algorithm, ES256, and the whole chain leans on that: every
 * layer is checked with [Es256.verify].
 *
 * The check must be explicit here rather than inherited from whatever Nimbus's verifier happens to
 * accept for a given key. Relying on a library's internal algorithm/curve pairing means the
 * guarantee silently depends on a dependency's implementation detail, and it moves when the
 * dependency does.
 */
class Es256AlgorithmPinningTest {
    private fun jwkOf(key: ECKey): JsonObject = Json.parseToJsonElement(key.toPublicJWK().toJSONString()) as JsonObject

    private fun signedJwt(
        key: ECKey,
        algorithm: JWSAlgorithm,
    ): String {
        val claims = JWTClaimsSet.Builder().subject("vi-test").build()
        return SignedJWT(JWSHeader.Builder(algorithm).build(), claims)
            .apply { sign(ECDSASigner(key)) }
            .serialize()
    }

    @Test
    fun `a genuine ES256 token verifies`() {
        val key = ECKeyGenerator(Curve.P_256).generate()

        Es256.verify(signedJwt(key, JWSAlgorithm.ES256), jwkOf(key)).shouldBeTrue()
    }

    @Test
    fun `a token signed with a curve other than P-256 is rejected`() {
        // ES512 over P-521: a perfectly valid JWS, but not the one algorithm VI allows.
        val key = ECKeyGenerator(Curve.P_521).generate()

        Es256.verify(signedJwt(key, JWSAlgorithm.ES512), jwkOf(key)).shouldBeFalse()
    }

    @Test
    fun `a P-256 key presented under a non-ES256 header is rejected`() {
        val key = ECKeyGenerator(Curve.P_256).generate()
        // Header claims ES384 while the key and signature are P-256/ES256.
        val tampered =
            signedJwt(key, JWSAlgorithm.ES256).let { jwt ->
                val parts = jwt.split(".")
                val header =
                    java.util.Base64
                        .getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("""{"alg":"ES384"}""".toByteArray())
                "$header.${parts[1]}.${parts[2]}"
            }

        Es256.verify(tampered, jwkOf(key)).shouldBeFalse()
    }
}
