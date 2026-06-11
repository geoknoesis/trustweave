package org.trustweave.credential.oidc4vci.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Oidc4VciServerTest {

    private val service = Oidc4VciIssuerService(
        baseUrl = "https://issuer.example.com",
        issuerDid = "did:key:z6MkTestIssuer",
    )

    private fun testOidc(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            val server = Oidc4VciServer(service)
            with(server) { configureApplication() }
        }
        block()
    }

    /** Extracts and decodes the credential_offer JSON from a spec-format offer URI. */
    private fun decodeCredentialOffer(offerUri: String): JsonObject {
        val encoded = offerUri.substringAfter("credential_offer=")
        return Json.parseToJsonElement(URLDecoder.decode(encoded, "UTF-8")).jsonObject
    }

    @Test
    fun `GET well-known returns metadata with credential_endpoint`() = testOidc {
        val response = client.get("/.well-known/openid-credential-issuer")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("https://issuer.example.com", body["credential_issuer"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example.com/credential", body["credential_endpoint"]?.jsonPrimitive?.content)
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
        assertEquals("https://issuer.example.com", offerJson["credential_issuer"]?.jsonPrimitive?.content)
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

    @Test
    fun `POST token with valid pre-auth code returns access_token`() = testOidc {
        val offerResp = client.post("/api/offer") {
            contentType(ContentType.Application.Json)
            setBody("""{"credentialTypes":["DegreeCredential"]}""")
        }
        val preAuthCode = Json.parseToJsonElement(offerResp.bodyAsText()).jsonObject["pre_authorized_code"]!!.jsonPrimitive.content

        val tokenResp = client.post("/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code&pre-authorized_code=$preAuthCode")
        }
        assertEquals(HttpStatusCode.OK, tokenResp.status)
        val tokenBody = Json.parseToJsonElement(tokenResp.bodyAsText()).jsonObject
        assertTrue(tokenBody.containsKey("access_token"))
        assertEquals("Bearer", tokenBody["token_type"]?.jsonPrimitive?.content)
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
    fun `full flow offer to credential returns credential field`() = testOidc {
        val offerResp = client.post("/api/offer") {
            contentType(ContentType.Application.Json)
            setBody("""{"credentialTypes":["DegreeCredential"]}""")
        }
        val preAuthCode = Json.parseToJsonElement(offerResp.bodyAsText()).jsonObject["pre_authorized_code"]!!.jsonPrimitive.content

        val tokenResp = client.post("/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code&pre-authorized_code=$preAuthCode")
        }
        val accessToken = Json.parseToJsonElement(tokenResp.bodyAsText()).jsonObject["access_token"]!!.jsonPrimitive.content

        val credResp = client.post("/credential") {
            headers { append("Authorization", "Bearer $accessToken") }
            contentType(ContentType.Application.Json)
            setBody("""{"format":"jwt_vc_json","credential_definition":{"type":["VerifiableCredential","DegreeCredential"]}}""")
        }
        assertEquals(HttpStatusCode.OK, credResp.status)
        val credBody = Json.parseToJsonElement(credResp.bodyAsText()).jsonObject
        assertTrue(credBody.containsKey("credential"))
    }
}
