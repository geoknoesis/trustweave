package org.trustweave.credential.internal.infrastructure

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64URL
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.VerificationMethod
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The JWS header must not be allowed to choose how the key is interpreted.
 *
 * `alg` travels inside the signature being checked, so it is attacker-controlled. The verification
 * method's own JWK states what the key actually is (`kty`, `crv`); when the two disagree the key
 * material is being reinterpreted on terms the attacker picked, which is the same class of mistake
 * as algorithm confusion. A verifier must refuse rather than resolve the disagreement in favour of
 * the header.
 */
class JsonWebSignature2020KeyBindingTest {
    private val adapter = DefaultJsonWebSignature2020Adapter()
    private val payload = "the signed document".toByteArray()

    private val issuerDid = Did("did:example:issuer")

    /** A real P-256 key and a genuinely valid ES256 detached JWS over [payload]. */
    private val signingKey: ECKey = ECKeyGenerator(Curve.P_256).keyID("key-1").generate()

    private fun detachedEs256Jws(): String {
        val header = JWSHeader.Builder(JWSAlgorithm.ES256).build()
        val jws =
            JWSObject(header, Payload(Base64URL.encode(payload))).apply {
                sign(ECDSASigner(signingKey))
            }
        val parts = jws.serialize().split(".")
        return "${parts[0]}..${parts[2]}"
    }

    private fun verificationMethod(jwk: Map<String, Any?>): VerificationMethod =
        VerificationMethod(
            id = VerificationMethodId.parse("${issuerDid.value}#key-1"),
            type = "JsonWebKey2020",
            controller = issuerDid,
            publicKeyJwk = jwk,
        )

    private fun jwk(
        kty: String = "EC",
        crv: String = "P-256",
    ): Map<String, Any?> =
        mapOf(
            "kty" to kty,
            "crv" to crv,
            "x" to signingKey.x.toString(),
            "y" to signingKey.y.toString(),
        )

    @Test
    fun `a matching key and algorithm still verifies`() {
        val verified =
            adapter.verifyDetachedJws(detachedEs256Jws(), payload, verificationMethod(jwk()))

        assertTrue(verified, "A P-256 key with an ES256 signature must verify")
    }

    @Test
    fun `a key whose declared curve contradicts the algorithm is rejected`() {
        // Same real P-256 coordinates, but the verification method declares P-384. The signature
        // is genuinely valid for P-256, so anything that picks the curve from the header alone
        // accepts it and never notices the key says something else.
        val verified =
            adapter.verifyDetachedJws(detachedEs256Jws(), payload, verificationMethod(jwk(crv = "P-384")))

        assertFalse(verified, "A JWK whose crv contradicts the header alg must not be trusted")
    }

    @Test
    fun `a key whose type is not EC is rejected on the ECDSA path`() {
        val verified =
            adapter.verifyDetachedJws(detachedEs256Jws(), payload, verificationMethod(jwk(kty = "OKP")))

        assertFalse(verified, "An ECDSA signature must not be verified against a non-EC key type")
    }
}
