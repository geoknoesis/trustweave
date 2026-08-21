package org.trustweave.credential

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.model.vc.Issuer
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `issuer` in a W3C Verifiable Credential is either an IRI string or an object carrying an `id`.
 *
 * Left to the sealed-class default, kotlinx emitted a polymorphic wrapper naming the Kotlin class:
 * `{"type":"org.trustweave.credential.model.vc.Issuer.IriIssuer","id":"did:key:z…"}`. That leaks
 * internal type names into credential documents, no other implementation can read it, and this
 * library could not read the ordinary string form back.
 *
 * The JSON-LD path never used this serializer — it writes `credential.issuer.id.value` directly —
 * so signatures were computed over the correct shape all along and are unaffected by this.
 */
class IssuerSerializationTest {
    private val json = Json { serializersModule = SerializationModule.default }

    @Test
    fun `an IRI issuer serializes as a plain string`() {
        val encoded = json.encodeToString(Issuer.serializer(), Issuer.IriIssuer(Iri("did:key:zAbc")))

        assertEquals("\"did:key:zAbc\"", encoded)
    }

    @Test
    fun `a plain string deserializes to an IRI issuer`() {
        val decoded = json.decodeFromString(Issuer.serializer(), "\"did:key:zAbc\"")

        assertIs<Issuer.IriIssuer>(decoded)
        assertEquals("did:key:zAbc", decoded.id.value)
    }

    @Test
    fun `an object issuer serializes as an object carrying id and name`() {
        val encoded =
            json.encodeToString(
                Issuer.serializer(),
                Issuer.ObjectIssuer(id = Iri("did:key:zAbc"), name = "Example University"),
            )

        val obj = json.parseToJsonElement(encoded) as JsonObject
        assertEquals("did:key:zAbc", obj["id"]?.jsonPrimitive?.content)
        assertEquals("Example University", obj["name"]?.jsonPrimitive?.content)
        assertTrue("type" !in obj, "No Kotlin class discriminator belongs in a credential: $encoded")
    }

    @Test
    fun `an object with id deserializes to an object issuer`() {
        val decoded =
            json.decodeFromString(
                Issuer.serializer(),
                buildJsonObject {
                    put("id", "did:key:zAbc")
                    put("name", "Example University")
                }.toString(),
            )

        assertIs<Issuer.ObjectIssuer>(decoded)
        assertEquals("did:key:zAbc", decoded.id.value)
        assertEquals("Example University", decoded.name)
    }

    @Test
    fun `unrecognised members of an object issuer survive the round trip`() {
        val source =
            buildJsonObject {
                put("id", "did:key:zAbc")
                put("image", "https://example.org/logo.png")
            }

        val decoded = json.decodeFromString(Issuer.serializer(), source.toString())
        val reencoded = json.parseToJsonElement(json.encodeToString(Issuer.serializer(), decoded)) as JsonObject

        assertEquals(
            "https://example.org/logo.png",
            reencoded["image"]?.jsonPrimitive?.content,
            "Members this library does not model must not be dropped: $reencoded",
        )
    }

    @Test
    fun `the legacy polymorphic form is still readable`() {
        // Credentials persisted before this fix carry the class-name wrapper. Reading them must
        // keep working, or stored data becomes unloadable.
        val legacy =
            buildJsonObject {
                put("type", JsonPrimitive("org.trustweave.credential.model.vc.Issuer.IriIssuer"))
                put("id", JsonPrimitive("did:key:zAbc"))
            }

        val decoded = json.decodeFromString(Issuer.serializer(), legacy.toString())

        assertEquals("did:key:zAbc", decoded.id.value)
    }
}
