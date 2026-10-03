package org.trustweave.credential.oidc4vp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.credential.oidc4vp.models.DcqlClaimsQuery
import org.trustweave.credential.oidc4vp.models.DcqlCredentialQuery
import org.trustweave.credential.oidc4vp.models.DcqlQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestedClaimsExtractorTest {
    @Test
    fun `presentation definition fields become requested claim paths per descriptor`() {
        val pd =
            Json.parseToJsonElement(
                """
                {"id":"pd","input_descriptors":[
                  {"id":"pid","constraints":{"fields":[
                    {"path":["$.vct"],"filter":{"type":"string"}},
                    {"path":["$.given_name","$.credentialSubject.given_name"]},
                    {"path":["$.family_name"]}
                  ]}},
                  {"id":"no-constraints"}
                ]}
                """.trimIndent(),
            ) as JsonObject
        val claims = RequestedClaimsExtractor.extract(pd, null)
        assertEquals(mapOf("pid" to listOf("$.vct", "$.given_name", "$.family_name")), claims)
    }

    @Test
    fun `dcql claim paths are rendered as JSONPath`() {
        val dcql =
            DcqlQuery(
                credentials =
                    listOf(
                        DcqlCredentialQuery(
                            id = "mdl",
                            format = "mso_mdoc",
                            claims =
                                listOf(
                                    DcqlClaimsQuery(path = listOf(JsonPrimitive("org.iso.18013.5.1"), JsonPrimitive("family_name"))),
                                    DcqlClaimsQuery(path = listOf(JsonPrimitive("nationalities"), JsonNull)),
                                    DcqlClaimsQuery(path = listOf(JsonPrimitive("address"), JsonPrimitive(0), JsonPrimitive("street"))),
                                ),
                        ),
                        DcqlCredentialQuery(id = "no-claims", format = "dc+sd-jwt"),
                    ),
            )
        val claims = RequestedClaimsExtractor.extract(null, dcql)
        assertEquals(
            mapOf("mdl" to listOf("$['org.iso.18013.5.1'].family_name", "$.nationalities[*]", "$.address[0].street")),
            claims,
        )
    }

    @Test
    fun `nothing requested yields an empty map`() {
        assertTrue(RequestedClaimsExtractor.extract(null, null).isEmpty())
    }
}
