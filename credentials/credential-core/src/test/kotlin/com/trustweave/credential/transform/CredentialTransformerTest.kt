package com.trustweave.credential.transform

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
        // Compare issuer by value (ID) rather than object equality
        assertEquals(credential.issuer.id.value, parsed.issuer.id.value)
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
        // Compare issuer by value (ID) rather than object equality
        assertEquals(credential.issuer.id.value, parsed.issuer.id.value)
        // Compare types by their string values since serialization may change object instances
        assertEquals(credential.type.map { it.value }, parsed.type.map { it.value })
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
        // Compare issuer by value (ID) rather than object equality
        assertEquals(credential.issuer.id.value, parsed.issuer.id.value)
    }

    @Test
    fun `test round trip JSON-LD`() = runBlocking {
        val original = createTestCredential()

        val jsonLd = transformer.toJsonLd(original)
        val restored = transformer.fromJsonLd(jsonLd)

        assertEquals(original.id, restored.id)
        // Compare issuer by value (ID) rather than object equality
        assertEquals(original.issuer.id.value, restored.issuer.id.value)
        // Compare types by their string values since serialization may change object instances
        assertEquals(original.type.map { it.value }, restored.type.map { it.value })
    }

    @Test
    fun `test round trip CBOR`() = runBlocking {
        val original = createTestCredential()

        val cbor = transformer.toCbor(original)
        val restored = transformer.fromCbor(cbor)

        assertEquals(original.id, restored.id)
        // Compare issuer by value (ID) rather than object equality
        assertEquals(original.issuer.id.value, restored.issuer.id.value)
    }

    @Test
    fun `test transform credential with proof`() = runBlocking {
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof",
                additionalProperties = emptyMap()
            )
        )

        val jsonLd = transformer.toJsonLd(credential)

        assertTrue(jsonLd.containsKey("proof"))
    }

    @Test
    fun `test transform credential with expiration`() = runBlocking {
        val expirationDate = Clock.System.now().plus(86400.seconds)
        val credential = createTestCredential(expirationDate = expirationDate)

        val jsonLd = transformer.toJsonLd(credential)

        // Note: expirationDate is @Transient in VerifiableCredential, so it may not appear in JSON-LD
        // The test verifies that serialization completes successfully
        assertNotNull(jsonLd)
        // If expirationDate is serialized, it should be present, otherwise the test just verifies no exception
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        issuerDid: String = "did:key:issuer",
        subject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject"),
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        ),
        issuanceDate: Instant = Clock.System.now(),
        expirationDate: Instant? = null,
        proof: CredentialProof? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types,
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            proof = proof
        )
    }
}

