package org.trustweave.credential.transform

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.time.Clock

/** Security behaviour of the public [CredentialTransformer] facade. */
class CredentialTransformerSecurityTest {
    private val transformer = CredentialTransformer()

    private val credential =
        VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri("did:example:subject"), claims = mapOf("name" to JsonPrimitive("Alice"))),
        )

    @Test
    fun `unsecured JWT is rejected by default`() =
        runBlocking<Unit> {
            val unsecured = transformer.toJwt(credential)
            val e = shouldThrow<IllegalArgumentException> { transformer.fromJwt(unsecured) }
            e.message!! shouldContain "unsecured"
            shouldThrow<IllegalArgumentException> { unsecured.fromJwt() }
        }

    @Test
    fun `unsecured JWT is accepted with explicit opt-in`() =
        runBlocking<Unit> {
            val recovered = transformer.fromJwt(transformer.toJwt(credential), allowUnsecured = true)
            recovered.issuer.id.value shouldBe "did:example:issuer"
            credential.roundTripJwt().credentialSubject.claims["name"] shouldBe JsonPrimitive("Alice")
        }

    @Test
    fun `raw JSON is not accepted as a JWT`() =
        runBlocking<Unit> {
            val json = transformer.toJsonLd(credential).toString()
            shouldThrow<IllegalArgumentException> { transformer.fromJwt(json, allowUnsecured = true) }
        }

    @Test
    fun `JWS-secured JWT with a vc claim is parsed`() =
        runBlocking<Unit> {
            val unsecured = transformer.toJwt(credential)
            val vcClaim =
                com.nimbusds.jwt.JWTParser
                    .parse(unsecured)
                    .jwtClaimsSet
                    .getClaim("vc")
            val signed =
                SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder().claim("vc", vcClaim).build())
                    .apply { sign(MACSigner(ByteArray(32) { 7 })) }
                    .serialize()
            transformer
                .fromJwt(signed)
                .credentialSubject.id
                ?.value shouldBe "did:example:subject"
        }

    @Test
    fun `JWT without a vc claim is rejected`() =
        runBlocking<Unit> {
            val signed =
                SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder().subject("x").build())
                    .apply { sign(MACSigner(ByteArray(32) { 7 })) }
                    .serialize()
            shouldThrow<IllegalArgumentException> { transformer.fromJwt(signed) }
        }
}
