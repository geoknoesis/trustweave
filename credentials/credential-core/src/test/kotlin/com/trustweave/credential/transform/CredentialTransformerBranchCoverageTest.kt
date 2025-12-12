package com.trustweave.credential.transform

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive branch coverage tests for CredentialTransformer.
 * Tests all methods, branches, and edge cases.
 */
class CredentialTransformerBranchCoverageTest {

    @Test
    fun `test CredentialTransformer toJwt returns JWT string`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()

        val jwt = transformer.toJwt(credential)

        assertNotNull(jwt)
        assertTrue(jwt.isNotBlank())
    }

    @Test
    fun `test CredentialTransformer fromJwt returns credential`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()
        val jwt = transformer.toJwt(credential)

        val restored = transformer.fromJwt(jwt)

        assertNotNull(restored)
        // Compare issuer by value (ID) rather than object equality
        assertEquals(credential.issuer.id.value, restored.issuer.id.value)
    }

    @Test
    fun `test CredentialTransformer toJsonLd returns JSON object`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()

        val jsonLd = transformer.toJsonLd(credential)

        assertNotNull(jsonLd)
        assertTrue(jsonLd.containsKey("issuer"))
        assertTrue(jsonLd.containsKey("type"))
    }

    @Test
    fun `test CredentialTransformer fromJsonLd returns credential`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()
        val jsonLd = transformer.toJsonLd(credential)

        val restored = transformer.fromJsonLd(jsonLd)

        assertNotNull(restored)
        // Compare issuer by value (ID) rather than object equality
        assertEquals(credential.issuer.id.value, restored.issuer.id.value)
    }

    @Test
    fun `test CredentialTransformer toCbor returns bytes`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()

        val cbor = transformer.toCbor(credential)

        assertNotNull(cbor)
        assertTrue(cbor.isNotEmpty())
    }

    @Test
    fun `test CredentialTransformer fromCbor returns credential`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential()
        val cbor = transformer.toCbor(credential)

        val restored = transformer.fromCbor(cbor)

        assertNotNull(restored)
        // Compare issuer by value (ID) rather than object equality
        assertEquals(credential.issuer.id.value, restored.issuer.id.value)
    }

    @Test
    fun `test CredentialTransformer toJwt with proof`() = runBlocking {
        val transformer = CredentialTransformer()
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

        val jwt = transformer.toJwt(credential)

        assertNotNull(jwt)
    }

    @Test
    fun `test CredentialTransformer toJsonLd with all fields`() = runBlocking {
        val transformer = CredentialTransformer()
        val credential = createTestCredential(
            expirationDate = Clock.System.now().plus(86400.seconds),
            credentialStatus = CredentialStatus(
                id = StatusListId("status-list-1"),
                type = "StatusList2021Entry"
            ),
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

        assertNotNull(jsonLd)
    }

    @Test
    fun `test CredentialTransformer round trip JWT`() = runBlocking {
        val transformer = CredentialTransformer()
        val original = createTestCredential()

        val jwt = transformer.toJwt(original)
        val restored = transformer.fromJwt(jwt)

        // Compare issuer by value (ID) rather than object equality
        assertEquals(original.issuer.id.value, restored.issuer.id.value)
        // Compare types by their string values since serialization may change object instances
        assertEquals(original.type.map { it.value }, restored.type.map { it.value })
    }

    @Test
    fun `test CredentialTransformer round trip JSON-LD`() = runBlocking {
        val transformer = CredentialTransformer()
        val original = createTestCredential()

        val jsonLd = transformer.toJsonLd(original)
        val restored = transformer.fromJsonLd(jsonLd)

        // Compare issuer by value (ID) rather than object equality
        assertEquals(original.issuer.id.value, restored.issuer.id.value)
        // Compare types by their string values since serialization may change object instances
        assertEquals(original.type.map { it.value }, restored.type.map { it.value })
    }

    @Test
    fun `test CredentialTransformer round trip CBOR`() = runBlocking {
        val transformer = CredentialTransformer()
        val original = createTestCredential()

        val cbor = transformer.toCbor(original)
        val restored = transformer.fromCbor(cbor)

        // Compare issuer by value (ID) rather than object equality
        assertEquals(original.issuer.id.value, restored.issuer.id.value)
        // Compare types by their string values since serialization may change object instances
        assertEquals(original.type.map { it.value }, restored.type.map { it.value })
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        issuerDid: String = "did:key:issuer",
        subject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject"),
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        ),
        issuanceDate: Instant = Clock.System.now(),
        expirationDate: Instant? = null,
        credentialStatus: CredentialStatus? = null,
        proof: CredentialProof? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types,
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            credentialStatus = credentialStatus,
            proof = proof
        )
    }
}



