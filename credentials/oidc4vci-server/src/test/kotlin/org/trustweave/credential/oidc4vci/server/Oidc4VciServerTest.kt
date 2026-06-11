package org.trustweave.credential.oidc4vci.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.trustweave.core.util.encodeBase58
import java.net.URLDecoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Oidc4VciServerTest {

    private val issuerUrl = "https://issuer.example.com"

    private val service = Oidc4VciIssuerService(
        baseUrl = issuerUrl,
        issuerDid = "did:key:z6MkTestIssuer",
    )

    private val holderKeyPair: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    private fun testOidc(
        issuerService: Oidc4VciIssuerService = service,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            val server = Oidc4VciServer(issuerService)
            with(server) { configureApplication() }
        }
        block()
    }

    /** Extracts and decodes the credential_offer JSON from a spec-format offer URI. */
    private fun decodeCredentialOffer(offerUri: String): JsonObject {
        val encoded = offerUri.substringAfter("credential_offer=")
        return Json.parseToJsonElement(URLDecoder.decode(encoded, "UTF-8")).jsonObject
    }

    // ========== Metadata / offer ==========

    @Test
    fun `GET well-known returns metadata with credential_endpoint`() = testOidc {
        val response = client.get("/.well-known/openid-credential-issuer")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(issuerUrl, body["credential_issuer"]?.jsonPrimitive?.content)
        assertEquals("$issuerUrl/credential", body["credential_endpoint"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST api-offer returns spec-format offer with a single credential_offer parameter`() = testOidc {
        val response = client.post("/api/offer") {
            contentType(ContentType.Application.Json)
            setBody("""{"credentialTypes":["UniversityDegreeCredential"]}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val offerUri = body["offer_uri"]?.jsonPrimitive?.content ?: ""
        val preAuthCode = body["pre_authorized_code"]?.jsonPrimitive?.content ?: ""

        // OID4VCI v1.0 §4.1: a single credential_offer query parameter carrying URL-encoded JSON
        assertTrue(
            offerUri.startsWith("openid-credential-offer://?credential_offer="),
            "Offer must be carried in a single credential_offer parameter, got: $offerUri"
        )
        assertFalse(offerUri.contains("credential_issuer="), "Raw top-level params are not spec-compliant")
        assertFalse(offerUri.contains("&grants="), "grants must be embedded in the credential_offer JSON")

        val offerJson = decodeCredentialOffer(offerUri)
        assertEquals(issuerUrl, offerJson["credential_issuer"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("UniversityDegreeCredential"),
            offerJson["credential_configuration_ids"]?.jsonArray?.map { it.jsonPrimitive.content }
        )
        val preAuthGrant = offerJson["grants"]!!
            .jsonObject["urn:ietf:params:oauth:grant-type:pre-authorized_code"]!!.jsonObject
        assertEquals(preAuthCode, preAuthGrant["pre-authorized_code"]?.jsonPrimitive?.content)
        assertFalse(preAuthGrant.containsKey("tx_code"), "No tx_code object when the offer requires none")
    }

    @Test
    fun `POST api-offer embeds tx_code requirement in the pre-authorized grant`() = testOidc {
        val response = client.post("/api/offer") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "credentialTypes": ["UniversityDegreeCredential"],
                  "txCode": {"input_mode": "numeric", "length": 4, "description": "PIN from SMS"},
                  "txCodeValue": "1234"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val offerUri = body["offer_uri"]?.jsonPrimitive?.content ?: ""

        val offerJson = decodeCredentialOffer(offerUri)
        val txCode = offerJson["grants"]!!
            .jsonObject["urn:ietf:params:oauth:grant-type:pre-authorized_code"]!!
            .jsonObject["tx_code"]?.jsonObject
        assertTrue(txCode != null, "tx_code object must be embedded in the pre-authorized grant")
        assertEquals("numeric", txCode["input_mode"]?.jsonPrimitive?.content)
        assertEquals(4, txCode["length"]?.jsonPrimitive?.content?.toInt())
        assertEquals("PIN from SMS", txCode["description"]?.jsonPrimitive?.content)
    }

    // ========== Token endpoint ==========

    @Test
    fun `POST token with valid pre-auth code returns access_token and c_nonce`() = testOidc {
        val tokenBody = obtainToken(createOffer())
        assertTrue(tokenBody.containsKey("access_token"))
        assertEquals("Bearer", tokenBody["token_type"]?.jsonPrimitive?.content)
        // PoP enforcement: the token response must carry the c_nonce the wallet echoes
        // in its proof (OID4VCI v1.0 §6.2)
        assertNotNull(tokenBody["c_nonce"]?.jsonPrimitive?.contentOrNull, "Token response must carry a c_nonce")
        assertNotNull(tokenBody["c_nonce_expires_in"], "Token response must carry c_nonce_expires_in")
    }

    @Test
    fun `POST token with invalid pre-auth code returns 400`() = testOidc {
        val response = client.post("/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code&pre-authorized_code=invalid-code")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("invalid_grant", body["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST token with wrong tx_code is rejected`() = testOidc {
        val offer = service.createOffer(
            credentialTypes = listOf("DegreeCredential"),
            txCode = org.trustweave.credential.oidc4vci.models.TxCode(length = 4),
            txCodeValue = "1234",
        )
        val response = client.post("/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code" +
                    "&pre-authorized_code=${offer.preAuthCode}&tx_code=9999"
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "invalid_grant",
            Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `POST token with correct tx_code succeeds`() = testOidc {
        val offer = service.createOffer(
            credentialTypes = listOf("DegreeCredential"),
            txCode = org.trustweave.credential.oidc4vci.models.TxCode(length = 4),
            txCodeValue = "1234",
        )
        val response = client.post("/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code" +
                    "&pre-authorized_code=${offer.preAuthCode}&tx_code=1234"
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ========== Credential endpoint: proof of possession ==========

    @Test
    fun `full pre-auth flow with valid proof returns credential bound to the proven key`() = testOidc {
        val tokenBody = obtainToken(createOffer())
        val accessToken = tokenBody["access_token"]!!.jsonPrimitive.content
        val cNonce = tokenBody["c_nonce"]!!.jsonPrimitive.content

        val credResp = postCredential(accessToken, proofJwt(nonce = cNonce))

        assertEquals(HttpStatusCode.OK, credResp.status)
        val credBody = Json.parseToJsonElement(credResp.bodyAsText()).jsonObject
        val credentialJson = Json.parseToJsonElement(credBody["credential"]!!.jsonPrimitive.content).jsonObject

        // Subject binding: credentialSubject.id is the did:key derived from the proof's jwk
        assertEquals(
            expectedHolderDidKey(),
            credentialJson["credentialSubject"]?.jsonObject?.get("id")?.jsonPrimitive?.content,
            "Issued credential must be bound to the key proven in the PoP JWT"
        )
        assertEquals("did:key:z6MkTestIssuer", credentialJson["issuer"]?.jsonPrimitive?.content)
        // Successful responses rotate the c_nonce
        assertNotNull(credBody["c_nonce"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `credential request with wrong nonce returns invalid_proof with a fresh c_nonce`() = testOidc {
        val tokenBody = obtainToken(createOffer())
        val accessToken = tokenBody["access_token"]!!.jsonPrimitive.content
        val cNonce = tokenBody["c_nonce"]!!.jsonPrimitive.content

        val credResp = postCredential(accessToken, proofJwt(nonce = "not-the-right-nonce"))

        assertEquals(HttpStatusCode.BadRequest, credResp.status)
        val errorBody = Json.parseToJsonElement(credResp.bodyAsText()).jsonObject
        assertEquals("invalid_proof", errorBody["error"]?.jsonPrimitive?.content)
        val freshNonce = errorBody["c_nonce"]?.jsonPrimitive?.contentOrNull
        assertNotNull(freshNonce, "invalid_proof error must carry a fresh c_nonce for retry")
        assertNotEquals(cNonce, freshNonce, "The c_nonce must be rotated on a failed proof")

        // The wallet retry with the fresh nonce must now succeed
        val retry = postCredential(accessToken, proofJwt(nonce = freshNonce))
        assertEquals(HttpStatusCode.OK, retry.status)
    }

    @Test
    fun `credential request without proof returns invalid_proof`() = testOidc {
        val tokenBody = obtainToken(createOffer())
        val accessToken = tokenBody["access_token"]!!.jsonPrimitive.content

        val credResp = postCredential(accessToken, proof = null)

        assertEquals(HttpStatusCode.BadRequest, credResp.status)
        val errorBody = Json.parseToJsonElement(credResp.bodyAsText()).jsonObject
        assertEquals("invalid_proof", errorBody["error"]?.jsonPrimitive?.content)
        assertNotNull(errorBody["c_nonce"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `credential request signed by a different key than the jwk header is rejected`() = testOidc {
        val tokenBody = obtainToken(createOffer())
        val accessToken = tokenBody["access_token"]!!.jsonPrimitive.content
        val cNonce = tokenBody["c_nonce"]!!.jsonPrimitive.content

        val otherKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        // jwk header advertises holderKeyPair but the JWS is signed with otherKeyPair
        val forged = proofJwt(nonce = cNonce, signingKeyPair = otherKeyPair, jwkKeyPair = holderKeyPair)

        val credResp = postCredential(accessToken, forged)

        assertEquals(HttpStatusCode.BadRequest, credResp.status)
        assertEquals(
            "invalid_proof",
            Json.parseToJsonElement(credResp.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `credential request with wrong aud is rejected`() = testOidc {
        val tokenBody = obtainToken(createOffer())
        val accessToken = tokenBody["access_token"]!!.jsonPrimitive.content
        val cNonce = tokenBody["c_nonce"]!!.jsonPrimitive.content

        val credResp = postCredential(accessToken, proofJwt(nonce = cNonce, audience = "https://other-issuer.example"))

        assertEquals(HttpStatusCode.BadRequest, credResp.status)
        assertEquals(
            "invalid_proof",
            Json.parseToJsonElement(credResp.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
        )
    }

    // ========== Token expiry ==========

    @Test
    fun `expired access token returns invalid_token`() {
        val expiringService = Oidc4VciIssuerService(
            baseUrl = issuerUrl,
            issuerDid = "did:key:z6MkTestIssuer",
            tokenTtlSeconds = 0, // every token is immediately expired
        )
        testOidc(expiringService) {
            val offer = expiringService.createOffer(listOf("DegreeCredential"))
            val tokenResp = client.post("/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code" +
                        "&pre-authorized_code=${offer.preAuthCode}"
                )
            }
            val tokenBody = Json.parseToJsonElement(tokenResp.bodyAsText()).jsonObject
            val accessToken = tokenBody["access_token"]!!.jsonPrimitive.content
            val cNonce = tokenBody["c_nonce"]!!.jsonPrimitive.content

            val credResp = postCredential(accessToken, proofJwt(nonce = cNonce))

            assertEquals(HttpStatusCode.Unauthorized, credResp.status)
            assertEquals(
                "invalid_token",
                Json.parseToJsonElement(credResp.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
            )
        }
    }

    // ========== JSON injection ==========

    @Test
    fun `injection attempt in credentialTypes produces valid escaped JSON`() = testOidc {
        // A credential type carrying JSON metacharacters must end up as a single escaped
        // string value — not as injected JSON structure overriding e.g. the issuer.
        val maliciousType = """Degree","evil":true,"issuer":"did:evil:attacker"""
        val offer = service.createOffer(listOf(maliciousType))
        val tokenBody = obtainToken(offer.preAuthCode)
        val accessToken = tokenBody["access_token"]!!.jsonPrimitive.content
        val cNonce = tokenBody["c_nonce"]!!.jsonPrimitive.content

        val credResp = postCredential(accessToken, proofJwt(nonce = cNonce))

        assertEquals(HttpStatusCode.OK, credResp.status)
        val credBody = Json.parseToJsonElement(credResp.bodyAsText()).jsonObject
        // Must parse as valid JSON (string-concatenated JSON would be malformed or injected)
        val credentialJson = Json.parseToJsonElement(credBody["credential"]!!.jsonPrimitive.content).jsonObject

        assertEquals(
            listOf(maliciousType),
            credentialJson["type"]?.jsonArray?.map { it.jsonPrimitive.content },
            "The malicious type must survive as one literal string"
        )
        assertEquals(
            "did:key:z6MkTestIssuer",
            credentialJson["issuer"]?.jsonPrimitive?.content,
            "The injection must not override the issuer"
        )
        assertFalse(credentialJson.containsKey("evil"), "The injection must not add JSON members")
    }

    // ========== Helpers ==========

    private suspend fun ApplicationTestBuilder.createOffer(): String {
        val offerResp = client.post("/api/offer") {
            contentType(ContentType.Application.Json)
            setBody("""{"credentialTypes":["DegreeCredential"]}""")
        }
        return Json.parseToJsonElement(offerResp.bodyAsText())
            .jsonObject["pre_authorized_code"]!!.jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.obtainToken(preAuthCode: String): JsonObject {
        val tokenResp = client.post("/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code" +
                    "&pre-authorized_code=$preAuthCode"
            )
        }
        assertEquals(HttpStatusCode.OK, tokenResp.status)
        return Json.parseToJsonElement(tokenResp.bodyAsText()).jsonObject
    }

    private suspend fun ApplicationTestBuilder.postCredential(
        accessToken: String,
        proof: String?,
    ): HttpResponse = client.post("/credential") {
        headers { append("Authorization", "Bearer $accessToken") }
        contentType(ContentType.Application.Json)
        setBody(
            buildJsonObject {
                put("format", "jwt_vc_json")
                put("credential_definition", buildJsonObject {
                    put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"), JsonPrimitive("DegreeCredential"))))
                })
                if (proof != null) {
                    put("proof", buildJsonObject {
                        put("proof_type", "jwt")
                        put("jwt", proof)
                    })
                }
            }.toString()
        )
    }

    /** Raw 32-byte Ed25519 public key = last 32 bytes of the X.509 SubjectPublicKeyInfo. */
    private fun rawPublicKey(keyPair: KeyPair): ByteArray =
        keyPair.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }

    /** did:key the issuer must bind the credential subject to (multicodec 0xED01 + raw key). */
    private fun expectedHolderDidKey(): String =
        "did:key:z" + (byteArrayOf(0xED.toByte(), 0x01) + rawPublicKey(holderKeyPair)).encodeBase58()

    /**
     * Builds an OID4VCI proof-of-possession JWT (§7.2.1.1) signed via JCA Ed25519, with
     * the public key embedded in the JOSE `jwk` header.
     */
    private fun proofJwt(
        nonce: String?,
        audience: String = issuerUrl,
        signingKeyPair: KeyPair = holderKeyPair,
        jwkKeyPair: KeyPair = signingKeyPair,
    ): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "openid4vci-proof+jwt")
            put("jwk", buildJsonObject {
                put("kty", "OKP")
                put("crv", "Ed25519")
                put("x", enc.encodeToString(rawPublicKey(jwkKeyPair)))
            })
        }
        val payload = buildJsonObject {
            put("aud", audience)
            put("iat", System.currentTimeMillis() / 1000)
            nonce?.let { put("nonce", it) }
        }
        val headerB64 = enc.encodeToString(header.toString().toByteArray(Charsets.UTF_8))
        val payloadB64 = enc.encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
        val signature = Signature.getInstance("Ed25519").apply {
            initSign(signingKeyPair.private)
            update("$headerB64.$payloadB64".toByteArray(Charsets.UTF_8))
        }.sign()
        return "$headerB64.$payloadB64.${enc.encodeToString(signature)}"
    }
}
