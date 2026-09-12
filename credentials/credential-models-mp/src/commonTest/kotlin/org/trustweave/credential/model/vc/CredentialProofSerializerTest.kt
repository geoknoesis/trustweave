package org.trustweave.credential.model.vc

import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.serialization.SerializationModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [CredentialProofSerializer] decides which proof shape a credential carries, so a variant that
 * round-trips into the wrong branch, or silently loses a field, changes what a verifier checks.
 * It uses `@type` rather than the default discriminator because `LinkedDataProof` already has a
 * `type` property of its own.
 */
class CredentialProofSerializerTest {
    private val json =
        Json {
            serializersModule = SerializationModule.default
            ignoreUnknownKeys = true
        }

    private val linkedData =
        CredentialProof.LinkedDataProof(
            type = "Ed25519Signature2020",
            created = Instant.parse("2026-09-11T10:00:00Z"),
            verificationMethod = "did:key:z6Mk#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "z3FXQjecWufY46",
        )

    private fun roundTrip(proof: CredentialProof): CredentialProof =
        json.decodeFromString(CredentialProofSerializer, json.encodeToString(CredentialProofSerializer, proof))

    @Test
    fun `every proof variant survives a round trip`() {
        val proofs =
            listOf(
                linkedData,
                CredentialProof.JwtProof(jwt = "eyJhbGciOiJFZERTQSJ9.e30.sig"),
                CredentialProof.SdJwtVcProof(sdJwtVc = "eyJ0.e30.sig~disc1~", disclosures = listOf("disc1")),
                CredentialProof.SdJwtVcProof(sdJwtVc = "eyJ0.e30.sig", disclosures = null),
            )
        for (proof in proofs) {
            assertEquals(proof, roundTrip(proof), proof::class.simpleName)
        }
    }

    @Test
    fun `the discriminator is @type so it cannot collide with a LinkedDataProof type`() {
        val encoded = json.encodeToString(CredentialProofSerializer, linkedData).let { json.parseToJsonElement(it) }
        val fields = encoded.jsonObject
        assertEquals("LinkedDataProof", fields["@type"]?.jsonPrimitive?.content)
        // The suite name has to survive alongside the discriminator, not be overwritten by it.
        assertEquals("Ed25519Signature2020", fields["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `each variant encodes its own discriminator`() {
        val cases =
            mapOf<CredentialProof, String>(
                linkedData to "LinkedDataProof",
                CredentialProof.JwtProof("a.b.c") to "JwtProof",
                CredentialProof.SdJwtVcProof("a.b.c") to "SdJwtVcProof",
            )
        for ((proof, expected) in cases) {
            val encoded = json.parseToJsonElement(json.encodeToString(CredentialProofSerializer, proof))
            assertEquals(expected, encoded.jsonObject["@type"]?.jsonPrimitive?.content, expected)
        }
    }

    @Test
    fun `a proof with no discriminator is refused rather than guessed`() {
        val failure =
            assertFailsWith<SerializationException> {
                json.decodeFromString(CredentialProofSerializer, """{"jwt":"a.b.c"}""")
            }
        assertTrue("@type" in (failure.message ?: ""), failure.message ?: "")
    }

    @Test
    fun `an unknown discriminator is refused rather than falling back to a default`() {
        val failure =
            assertFailsWith<SerializationException> {
                json.decodeFromString(CredentialProofSerializer, """{"@type":"NotAProof","jwt":"a.b.c"}""")
            }
        assertTrue("NotAProof" in (failure.message ?: ""), failure.message ?: "")
    }

    @Test
    fun `a discriminator naming the wrong shape does not silently produce a half-built proof`() {
        // Claims to be a JWT proof but carries LinkedDataProof fields and no `jwt`.
        assertFailsWith<SerializationException> {
            json.decodeFromString(
                CredentialProofSerializer,
                """{"@type":"JwtProof","type":"Ed25519Signature2020","proofValue":"z3"}""",
            )
        }
    }

    @Test
    fun `additional linked-data properties are preserved across a round trip`() {
        val withExtras =
            linkedData.copy(
                additionalProperties = mapOf("domain" to JsonPrimitive("example.test"), "challenge" to JsonPrimitive("n-1")),
            )
        val decoded = roundTrip(withExtras) as CredentialProof.LinkedDataProof
        assertEquals(withExtras.additionalProperties, decoded.additionalProperties)
    }

    @Test
    fun `the created instant keeps its instant identity, not a reformatted string`() {
        val decoded = roundTrip(linkedData) as CredentialProof.LinkedDataProof
        assertEquals(Instant.parse("2026-09-11T10:00:00Z"), decoded.created)
    }

    @Test
    fun `unknown fields on a known variant are ignored rather than rejected`() {
        val decoded =
            json.decodeFromString(
                CredentialProofSerializer,
                """{"@type":"JwtProof","jwt":"a.b.c","somethingNew":123}""",
            )
        assertEquals(CredentialProof.JwtProof("a.b.c"), decoded)
    }

    @Test
    fun `sd-jwt disclosures round trip as a list, including the empty one`() {
        val empty = CredentialProof.SdJwtVcProof(sdJwtVc = "a.b.c", disclosures = emptyList())
        assertEquals(empty, roundTrip(empty))
    }

    @Test
    fun `the descriptor names the sealed type rather than a subclass`() {
        assertEquals("CredentialProof", CredentialProofSerializer.descriptor.serialName)
    }
}
