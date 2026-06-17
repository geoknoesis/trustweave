package org.trustweave.credential.model.vc

import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.model.CredentialType
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the symmetric [CredentialSubjectSerializer] that flattens
 * `claims` to the top level of the `credentialSubject` object (W3C VC shape).
 *
 * Regression guard: prior to this serializer the kotlinx wire form nested
 * claims under `credentialSubject.claims.{...}`, which external verifiers reject.
 */
class CredentialSubjectSerializerTest {

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        serializersModule = SerializationModule.default
    }

    @Test
    fun `serialize flattens claims to top level and emits id`() {
        val subject = CredentialSubject(
            id = Iri("did:example:s"),
            claims = mapOf(
                "name" to JsonPrimitive("Jane"),
                "degree" to JsonPrimitive("BS"),
            ),
        )

        val obj = json.encodeToJsonElement(subject).jsonObject

        assertEquals("did:example:s", (obj["id"] as JsonPrimitive).content, "id must be the IRI string")
        assertEquals("Jane", (obj["name"] as JsonPrimitive).content)
        assertEquals("BS", (obj["degree"] as JsonPrimitive).content)
        assertFalse(obj.containsKey("claims"), "claims must be flattened, not nested under 'claims'")
        assertEquals(setOf("id", "name", "degree"), obj.keys)
    }

    @Test
    fun `deserialize collects flattened claims and reads id`() {
        val flattened = """{"id":"did:example:s","name":"Jane","degree":"BS"}"""

        val subject = json.decodeFromString(CredentialSubject.serializer(), flattened)

        assertEquals(Iri("did:example:s"), subject.id)
        assertEquals(2, subject.claims.size, "every non-id key must be collected into claims")
        assertEquals("Jane", (subject.claims["name"] as JsonPrimitive).content)
        assertEquals("BS", (subject.claims["degree"] as JsonPrimitive).content)
        assertFalse(subject.claims.containsKey("id"), "id must not leak into claims")
    }

    @Test
    fun `round trip preserves id and all claims`() {
        val original = CredentialSubject(
            id = Iri("did:example:holder"),
            claims = mapOf(
                "name" to JsonPrimitive("Jane Smith"),
                "age" to JsonPrimitive(28),
                "active" to JsonPrimitive(true),
            ),
        )

        val encoded = json.encodeToString(CredentialSubject.serializer(), original)
        val decoded = json.decodeFromString(CredentialSubject.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `null id serializes without id key and still flattens claims`() {
        val subject = CredentialSubject(
            id = null,
            claims = mapOf("name" to JsonPrimitive("Anon")),
        )

        val obj = json.encodeToJsonElement(subject).jsonObject

        assertFalse(obj.containsKey("id"), "id key must be absent when id is null")
        assertFalse(obj.containsKey("claims"), "claims must be flattened")
        assertEquals("Anon", (obj["name"] as JsonPrimitive).content)
        assertEquals(setOf("name"), obj.keys)
    }

    @Test
    fun `deserialize without id yields null id`() {
        val decoded = json.decodeFromString(
            CredentialSubject.serializer(),
            """{"name":"Anon"}""",
        )

        assertNull(decoded.id)
        assertEquals("Anon", (decoded.claims["name"] as JsonPrimitive).content)
    }

    @Test
    fun `claim literally named id is dropped to avoid collision with subject id`() {
        val subject = CredentialSubject(
            id = Iri("did:example:s"),
            claims = mapOf(
                "id" to JsonPrimitive("did:example:bogus"),
                "name" to JsonPrimitive("Jane"),
            ),
        )

        val obj = json.encodeToJsonElement(subject).jsonObject

        // The subject id wins; the colliding claim key must not duplicate or override it.
        assertEquals("did:example:s", (obj["id"] as JsonPrimitive).content)
        assertEquals("Jane", (obj["name"] as JsonPrimitive).content)

        // Documented/intended lossiness: "id" is reserved for the subject IRI, so a claim
        // literally named "id" does NOT survive a serialize -> deserialize round-trip. On the
        // way back in, the top-level "id" is lifted into CredentialSubject.id, never claims.
        val decoded = json.decodeFromString(CredentialSubject.serializer(), obj.toString())
        assertEquals(Iri("did:example:s"), decoded.id)
        assertFalse(
            decoded.claims.containsKey("id"),
            "a claim named 'id' is intentionally lossy across a round-trip (reserved for subject IRI)",
        )
        assertEquals("Jane", (decoded.claims["name"] as JsonPrimitive).content)
    }

    @Test
    fun `verifiable credential serializes credentialSubject with flattened claims`() {
        val vc = VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:issuer")),
            issuanceDate = Instant.parse("2026-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:holder"),
                claims = mapOf(
                    "name" to JsonPrimitive("Jane"),
                    "degree" to JsonPrimitive("BS"),
                ),
            ),
            proof = null,
        )

        val obj = json.encodeToJsonElement(VerifiableCredential.serializer(), vc).jsonObject
        val subject = obj["credentialSubject"] as JsonObject

        assertFalse(subject.containsKey("claims"), "VC wire form must not nest claims")
        assertEquals("did:key:holder", (subject["id"] as JsonPrimitive).content)
        assertEquals("Jane", (subject["name"] as JsonPrimitive).content)
        assertEquals("BS", (subject["degree"] as JsonPrimitive).content)
    }

    @Test
    fun `verifiable credential round trips through flattened wire form`() {
        val vc = VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:issuer")),
            issuanceDate = Instant.parse("2026-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:holder"),
                claims = mapOf("name" to JsonPrimitive("Jane")),
            ),
            proof = null,
        )

        val encoded = json.encodeToString(VerifiableCredential.serializer(), vc)
        val decoded = json.decodeFromString(VerifiableCredential.serializer(), encoded)

        assertEquals(vc.credentialSubject.id, decoded.credentialSubject.id)
        assertTrue(decoded.credentialSubject.claims.containsKey("name"))
        assertEquals("Jane", (decoded.credentialSubject.claims["name"] as JsonPrimitive).content)
    }

    // --- FIX 1: guarded deserialize throws SerializationException on malformed input ---

    @Test
    fun `deserialize throws SerializationException when credentialSubject is a JSON array`() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(
                CredentialSubject.serializer(),
                """[{"id":"did:example:s"}]""",
            )
        }
    }

    @Test
    fun `deserialize throws SerializationException when id is a JSON object`() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(
                CredentialSubject.serializer(),
                """{"id":{"nested":"value"}}""",
            )
        }
    }

    @Test
    fun `deserialize throws SerializationException when id is a JSON number`() {
        assertFailsWith<SerializationException> {
            json.decodeFromString(
                CredentialSubject.serializer(),
                """{"id":42}""",
            )
        }
    }

    @Test
    fun `deserialize throws SerializationException when id is a non-IRI blank string`() {
        // Blank string -> Iri(...) init throws IllegalArgumentException, must surface as SerializationException.
        assertFailsWith<SerializationException> {
            json.decodeFromString(
                CredentialSubject.serializer(),
                """{"id":""}""",
            )
        }
        // Malformed (non-IRI) string -> same.
        assertFailsWith<SerializationException> {
            json.decodeFromString(
                CredentialSubject.serializer(),
                """{"id":"not a valid iri"}""",
            )
        }
    }

    @Test
    fun `deserialize treats explicit JSON null id as null id`() {
        val decoded = json.decodeFromString(
            CredentialSubject.serializer(),
            """{"id":null,"name":"Anon"}""",
        )

        assertNull(decoded.id, "explicit JSON null id must decode to null, not throw")
        assertEquals("Anon", (decoded.claims["name"] as JsonPrimitive).content)
        assertFalse(decoded.claims.containsKey("id"), "id must not leak into claims")
    }

    // --- FIX 2: structured (non-primitive) claim values round-trip (the actual bug scenario) ---

    @Test
    fun `serialize flattens structured object and array claims at top level`() {
        val subject = CredentialSubject(
            id = Iri("did:example:s"),
            claims = mapOf(
                "degree" to buildJsonObject {
                    put("type", JsonPrimitive("BachelorDegree"))
                    put("name", JsonPrimitive("BS CS"))
                },
                "roles" to buildJsonArray {
                    add(JsonPrimitive("admin"))
                    add(JsonPrimitive("user"))
                },
            ),
        )

        val obj = json.encodeToJsonElement(subject).jsonObject

        assertEquals(setOf("id", "degree", "roles"), obj.keys, "structured claims must flatten to top level")
        assertFalse(obj.containsKey("claims"), "claims must not be nested under 'claims'")

        val degree = obj["degree"] as JsonObject
        assertEquals("BachelorDegree", (degree["type"] as JsonPrimitive).content)
        assertEquals("BS CS", (degree["name"] as JsonPrimitive).content)

        val roles = obj["roles"] as JsonArray
        assertEquals(2, roles.size)
        assertEquals("admin", (roles[0] as JsonPrimitive).content)
        assertEquals("user", (roles[1] as JsonPrimitive).content)
    }

    @Test
    fun `round trip preserves structured object and array claims with structural equality`() {
        val original = CredentialSubject(
            id = Iri("did:example:s"),
            claims = mapOf(
                "degree" to buildJsonObject {
                    put("type", JsonPrimitive("BachelorDegree"))
                    put("name", JsonPrimitive("BS CS"))
                },
                "roles" to buildJsonArray {
                    add(JsonPrimitive("admin"))
                    add(JsonPrimitive("user"))
                },
            ),
        )

        val encoded = json.encodeToString(CredentialSubject.serializer(), original)
        val decoded = json.decodeFromString(CredentialSubject.serializer(), encoded)

        assertEquals(original, decoded, "structured claims must round-trip with structural equality")
    }

    // --- FIX 3: empty-claims edge cases ---

    @Test
    fun `id with empty claims serializes to just the id key and round-trips`() {
        val subject = CredentialSubject(id = Iri("did:example:s"), claims = emptyMap())

        val encoded = json.encodeToString(CredentialSubject.serializer(), subject)
        assertEquals("""{"id":"did:example:s"}""", encoded)

        val decoded = json.decodeFromString(CredentialSubject.serializer(), encoded)
        assertEquals(subject, decoded)
    }

    @Test
    fun `null id with empty claims serializes to empty object and round-trips`() {
        val subject = CredentialSubject(id = null, claims = emptyMap())

        val encoded = json.encodeToString(CredentialSubject.serializer(), subject)
        assertEquals("{}", encoded)

        val decoded = json.decodeFromString(CredentialSubject.serializer(), encoded)
        assertEquals(subject, decoded)
        assertNull(decoded.id)
        assertTrue(decoded.claims.isEmpty())
    }
}
