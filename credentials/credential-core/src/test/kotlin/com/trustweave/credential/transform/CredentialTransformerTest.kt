package com.trustweave.credential.transform

import com.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive tests for CredentialTransformer API.
 */
class CredentialTransformerTest {

    private val transformer = CredentialTransformer()

    @Test
    fun `test to JWT`() = runBlocking {
        val credential = createTestCredential()

        val jwt = transformer.toJwt(credential)

        assertNotNull(jwt)
        // Placeholder returns JSON string
        assertTrue(jwt.isNotEmpty())
    }

    @Test
    fun `test from JWT`() = runBlocking {
        val credential = createTestCredential()
        val jsonString = kotlinx.serialization.json.Json.encodeToString(VerifiableCredential.serializer(), credential)

        val parsed = transformer.fromJwt(jsonString)

        assertEquals(credential.id, parsed.id)
        assertEquals(credential.issuer, parsed.issuer)
    }

    @Test
    fun `test to JSON-LD`() = runBlocking {
        val credential = createTestCredential()

        val jsonLd = transformer.toJsonLd(credential)

        assertNotNull(jsonLd)
        assertTrue(jsonLd.containsKey("type"))
        assertTrue(jsonLd.containsKey("issuer"))
    }

    @Test
    fun `test from JSON-LD`() = runBlocking {
        val credential = createTestCredential()
        val jsonLd = transformer.toJsonLd(credential)

        val parsed = transformer.fromJsonLd(jsonLd)

        assertEquals(credential.id, parsed.id)
        assertEquals(credential.issuer, parsed.issuer)
        assertEquals(credential.type, parsed.type)
    }

    @Test
    fun `test to CBOR`() = runBlocking {
        val credential = createTestCredential()

        val cbor = transformer.toCbor(credential)

        assertNotNull(cbor)
        assertTrue(cbor.isNotEmpty())
    }

    @Test
    fun `test from CBOR`() = runBlocking {
        val credential = createTestCredential()
        val cbor = transformer.toCbor(credential)

        val parsed = transformer.fromCbor(cbor)

        assertEquals(credential.id, parsed.id)
        assertEquals(credential.issuer, parsed.issuer)
    }

    @Test
    fun `test round trip JSON-LD`() = runBlocking {
        val original = createTestCredential()

        val jsonLd = transformer.toJsonLd(original)
        val restored = transformer.fromJsonLd(jsonLd)

        assertEquals(original.id, restored.id)
        assertEquals(original.issuer, restored.issuer)
        assertEquals(original.type, restored.type)
    }

    @Test
    fun `test round trip CBOR`() = runBlocking {
        val original = createTestCredential()

        val cbor = transformer.toCbor(original)
        val restored = transformer.fromCbor(cbor)

        assertEquals(original.id, restored.id)
        assertEquals(original.issuer, restored.issuer)
    }

    @Test
    fun `test transform credential with proof`() = runBlocking {
        val credential = createTestCredential(
            proof = com.trustweave.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = Clock.System.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod"
            )
        )

        val jsonLd = transformer.toJsonLd(credential)

        assertTrue(jsonLd.containsKey("proof"))
    }

    @Test
    fun `test transform credential with expiration`() = runBlocking {
        val expirationDate = Clock.System.now().plus(86400.seconds).toString()
        val credential = createTestCredential(expirationDate = expirationDate)

        val jsonLd = transformer.toJsonLd(credential)

        assertTrue(jsonLd.containsKey("expirationDate"))
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = Clock.System.now().toString(),
        expirationDate: String? = null,
        proof: com.trustweave.credential.models.Proof? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            proof = proof
        )
    }
}

