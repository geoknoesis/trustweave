package org.trustweave.trust.dsl

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.dsl.credential.JsonObjectBuilder
import org.trustweave.trust.dsl.credential.SubjectBuilder
import org.trustweave.trust.dsl.credential.credential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class CredentialBuilderBehaviourTest {
    private val t0 = Instant.parse("2025-01-01T00:00:00Z")

    private fun org.trustweave.trust.dsl.credential.CredentialBuilder.minimal() {
        issuer("did:key:issuer")
        subject { id("did:key:holder") }
    }

    @Test
    fun `the base credential type is always present and listed first`() {
        val implicit =
            credential {
                minimal()
                type("Degree")
            }
        assertEquals(listOf("VerifiableCredential", "Degree"), implicit.type.map { it.value })

        val none = credential { minimal() }
        assertEquals(listOf("VerifiableCredential"), none.type.map { it.value })

        val explicit =
            credential {
                minimal()
                type("Degree", "VerifiableCredential")
            }
        assertEquals(listOf("Degree", "VerifiableCredential"), explicit.type.map { it.value }, "an explicit order is respected")

        val typed =
            credential {
                minimal()
                type(CredentialType.fromString("Licence"))
            }
        assertEquals(listOf("VerifiableCredential", "Licence"), typed.type.map { it.value })
    }

    @Test
    fun `an issuer and a subject are mandatory`() {
        assertTrue(
            assertFailsWith<IllegalStateException> { credential { subject { id("did:key:h") } } }.message!!.contains("Issuer is required"),
        )
        assertTrue(
            assertFailsWith<IllegalStateException> {
                credential {
                    issuer(
                        "did:key:i",
                    )
                }
            }.message!!.contains("subject is required", ignoreCase = true),
        )
    }

    @Test
    fun `identifiers are validated when they are set`() {
        assertFailsWith<IllegalArgumentException> { credential { id("") } }
        assertTrue(assertFailsWith<IllegalArgumentException> { credential { id("has space") } }.message!!.contains("invalid characters"))
        assertTrue(assertFailsWith<IllegalArgumentException> { credential { issuer("") } }.message!!.contains("cannot be blank"))
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                credential { issuer("https://example.org") }
            }.message!!.contains("must start with 'did:'"),
        )

        val ok =
            credential {
                minimal()
                id("urn:uuid:123e4567-e89b-12d3-a456-426614174000")
            }
        assertEquals("urn:uuid:123e4567-e89b-12d3-a456-426614174000", ok.id?.value)
    }

    @Test
    fun `issuance defaults to now unless a validity start or explicit date is given`() {
        val before = Clock.System.now()
        val defaulted = credential { minimal() }
        val issuedAt = assertNotNull(defaulted.issuanceDate)
        assertTrue(issuedAt >= before && issuedAt <= Clock.System.now())

        val explicit =
            credential {
                minimal()
                issued(t0)
            }
        assertEquals(t0, explicit.issuanceDate)

        val v2 =
            credential {
                minimal()
                validFrom(t0)
            }
        assertNull(v2.issuanceDate, "a validFrom-only credential omits the deprecated issuanceDate")
        assertEquals(t0, v2.validFrom)
    }

    @Test
    fun `validity periods must not end before they start`() {
        val inverted =
            assertFailsWith<IllegalArgumentException> {
                credential {
                    minimal()
                    issued(t0)
                    expires(t0 - 1.days)
                }
            }
        assertTrue(inverted.message!!.contains("validity period is invalid"), inverted.message)

        assertFailsWith<IllegalArgumentException> {
            credential {
                minimal()
                validFrom(t0)
                validUntil(t0 - 1.hours)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            credential {
                minimal()
                validFrom(t0 + 2.days)
                expires(t0 + 1.days)
            }
        }

        val boundary =
            credential {
                minimal()
                issued(t0)
                expires(t0)
            }
        assertEquals(t0, boundary.expirationDate, "start equal to end is allowed")
    }

    @Test
    fun `relative expiry is computed from the current time`() {
        val before = Clock.System.now()
        val a =
            credential {
                minimal()
                expires(30.days)
            }
        val b =
            credential {
                minimal()
                expiresIn(1.hours)
            }

        assertTrue(a.expirationDate!! >= before + 30.days && a.expirationDate!! <= Clock.System.now() + 30.days)
        assertTrue(b.expirationDate!! >= before + 1.hours && b.expirationDate!! <= Clock.System.now() + 1.hours)
    }

    @Test
    fun `status entries need an id and a known purpose`() {
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                credential {
                    minimal()
                    status { }
                }
            }.message!!.contains("status { id(...) } is required"),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                credential {
                    minimal()
                    status {
                        id("https://example.org/s/1")
                        statusPurpose("burn")
                    }
                }
            }.message!!.contains("Invalid status purpose: burn"),
        )

        val vc =
            credential {
                minimal()
                status {
                    id("https://example.org/s/1")
                    statusPurpose("Suspension")
                    statusListIndex("94567")
                    statusListCredential("https://example.org/lists/1")
                }
            }
        val status = assertNotNull(vc.credentialStatus)
        assertEquals(StatusPurpose.SUSPENSION, status.statusPurpose)
        assertEquals("94567", status.statusListIndex)
        assertEquals("https://example.org/lists/1", status.statusListCredential?.value)
        assertEquals("StatusList2021Entry", status.type)

        val defaulted =
            credential {
                minimal()
                status { id("https://example.org/s/2") }
            }
        assertEquals(StatusPurpose.REVOCATION, defaulted.credentialStatus?.statusPurpose)
    }

    @Test
    fun `schema and refresh service identifiers must be URIs`() {
        assertFailsWith<IllegalArgumentException> {
            credential {
                minimal()
                schema(" ")
            }
        }
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                credential {
                    minimal()
                    schema("not a uri")
                }
            }.message!!.contains("valid URI"),
        )
        assertFailsWith<IllegalArgumentException> {
            credential {
                minimal()
                refreshService("", "ManualRefreshService2018")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            credential {
                minimal()
                refreshService("https://example.org/r", "")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            credential {
                minimal()
                refreshService("relative/path", "T")
            }
        }

        val vc =
            credential {
                minimal()
                schema("https://example.org/schemas/degree.json")
                refreshService("https://example.org/refresh", "ManualRefreshService2018")
            }
        assertEquals("https://example.org/schemas/degree.json", vc.credentialSchema?.id?.value)
        assertEquals("JsonSchemaValidator2018", vc.credentialSchema?.type)
        assertEquals("ManualRefreshService2018", vc.refreshService?.type)
    }

    @Test
    fun `evidence and terms of use are attached only when declared`() {
        val bare = credential { minimal() }
        assertNull(bare.evidence)
        assertNull(bare.termsOfUse)

        val vc =
            credential {
                minimal()
                evidence {
                    id("urn:evidence:1")
                    verifier("did:key:verifier")
                    date("2025-01-01")
                    document { put("score", 97) }
                }
                evidence { }
                termsOfUse {
                    id("urn:terms:1")
                    type("IssuerPolicy")
                    terms { put("prohibition", "resale") }
                }
            }
        val evidence = assertNotNull(vc.evidence)
        assertEquals(2, evidence.size)
        assertEquals("urn:evidence:1", evidence[0].id?.value)
        assertEquals(
            97,
            evidence[0]
                .evidenceDocument!!
                .jsonObject["score"]!!
                .jsonPrimitive.int,
        )
        assertEquals(listOf("Evidence"), evidence[1].type, "evidence without a type is labelled generically")
        val terms = assertNotNull(vc.termsOfUse).single()
        assertEquals("IssuerPolicy", terms.type)
        assertEquals("resale", terms.additionalProperties["prohibition"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the subject claims builder keeps scalar types and nests objects`() {
        val rawJson = JsonObject(mapOf(Pair("x", JsonPrimitive(1))))
        val claims =
            SubjectBuilder()
                .apply {
                    id(Did("did:key:holder"))
                    "name" to "Alice"
                    "age" to 30
                    "active" to true
                    "nickname" to null
                    "address" {
                        "city" to "Paris"
                        "zip" to 75001
                    }
                    "raw" to rawJson
                    "other" to java.time.DayOfWeek.MONDAY
                    "viaBlock" to { put("deep", "yes") }
                }.build()

        assertEquals("did:key:holder", claims["id"]!!.jsonPrimitive.content)
        assertEquals("Alice", claims["name"]!!.jsonPrimitive.content)
        assertEquals(30, claims["age"]!!.jsonPrimitive.int)
        assertTrue(claims["active"]!!.jsonPrimitive.boolean)
        assertEquals(JsonNull, claims["nickname"])
        assertEquals("Paris", claims["address"]!!.jsonObject["city"]!!.jsonPrimitive.content)
        assertEquals(75001, claims["address"]!!.jsonObject["zip"]!!.jsonPrimitive.int)
        assertEquals(rawJson, claims["raw"])
        assertEquals("MONDAY", claims["other"]!!.jsonPrimitive.content)
        assertEquals("yes", claims["viaBlock"]!!.jsonObject["deep"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the JSON object builder supports typed puts, nested blocks and lists of blocks`() {
        val nestedMap = mapOf(Pair("k", 7))
        val json =
            JsonObjectBuilder()
                .apply {
                    put("s", "text")
                    put("n", 2.5)
                    put("b", false)
                    put("nested") { put("inner", 1) }
                    put("arr", listOf(JsonPrimitive("x"), JsonPrimitive(2)))
                    "mixed" to listOf("a", 1, true, nestedMap, JsonObject(emptyMap()))
                    put("obj", JsonObject(mapOf(Pair("o", JsonPrimitive(true)))))
                    "blocks" to listOf<JsonObjectBuilder.() -> Unit>({ put("i", 1) }, { put("i", 2) })
                }.build()

        assertEquals("text", json["s"]!!.jsonPrimitive.content)
        assertEquals("2.5", json["n"]!!.jsonPrimitive.content)
        assertEquals("false", json["b"]!!.jsonPrimitive.content)
        assertEquals(1, json["nested"]!!.jsonObject["inner"]!!.jsonPrimitive.int)
        assertEquals(2, (json["arr"] as JsonArray).size)
        val mixed = json["mixed"] as JsonArray
        assertEquals(listOf("a", "1", "true"), mixed.take(3).map { it.jsonPrimitive.content })
        assertEquals(7, mixed[3].jsonObject["k"]!!.jsonPrimitive.int)
        assertEquals(JsonObject(emptyMap()), mixed[4])
        assertTrue(json["obj"]!!.jsonObject["o"]!!.jsonPrimitive.boolean)
        assertEquals(listOf(1, 2), (json["blocks"] as JsonArray).map { it.jsonObject["i"]!!.jsonPrimitive.int })
    }
}
