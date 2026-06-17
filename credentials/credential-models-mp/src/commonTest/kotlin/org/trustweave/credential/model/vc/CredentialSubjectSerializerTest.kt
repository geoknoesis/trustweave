package org.trustweave.credential.model.vc

import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.model.CredentialType
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
