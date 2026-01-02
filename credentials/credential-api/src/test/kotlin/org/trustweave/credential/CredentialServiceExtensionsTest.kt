package org.trustweave.credential

import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
// Extension functions are in the same package, no import needed
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Tests for CredentialServiceExtensions extension functions.
 */
class CredentialServiceExtensionsTest {

    private fun createMockDidResolver(): DidResolver {
        return object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return DidResolutionResult.Success(
                    org.trustweave.did.model.DidDocument(
                        id = did,
                        verificationMethod = emptyList(),
                        assertionMethod = emptyList(),
                        authentication = emptyList(),
                        keyAgreement = emptyList()
                    )
                )
            }
        }
    }

    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:holder"),
                claims = mapOf(
                    "name" to JsonPrimitive("John Doe"),
                    "age" to JsonPrimitive(30)
                )
            ),
            proof = null
        )
    }

    @Test
    fun `test toJwt extension function`() = runBlocking {
        val didResolver = createMockDidResolver()
        val service = credentialService(didResolver = didResolver)
        val credential = createTestCredential()

        val jwt = service.toJwt(credential)

        assertNotNull(jwt, "JWT should be created")
        assertTrue(jwt.isNotBlank(), "JWT should not be blank")
        // JWT format: header.payload.signature (three parts separated by dots)
        val parts = jwt.split(".")
        assertTrue(parts.size >= 2, "JWT should have at least header and payload")
    }

    @Test
    fun `test fromJwt extension function`() = runBlocking {
        val didResolver = createMockDidResolver()
        val service = credentialService(didResolver = didResolver)
        val originalCredential = createTestCredential()

        // Convert to JWT and back
        val jwt = service.toJwt(originalCredential)
        val recoveredCredential = service.fromJwt(jwt)

        assertNotNull(recoveredCredential, "Credential should be recovered from JWT")
        assertEquals(originalCredential.type, recoveredCredential.type, "Type should match")
        assertEquals(originalCredential.issuer.id.value, recoveredCredential.issuer.id.value, "Issuer should match")
        assertEquals(originalCredential.credentialSubject.id.value, recoveredCredential.credentialSubject.id.value, "Subject should match")
    }

    @Test
    fun `test toJwt and fromJwt round trip`() = runBlocking {
        val didResolver = createMockDidResolver()
        val service = credentialService(didResolver = didResolver)
        val originalCredential = createTestCredential()

        val jwt = service.toJwt(originalCredential)
        val recoveredCredential = service.fromJwt(jwt)

        // Verify claims
        val originalClaims = originalCredential.credentialSubject.claims
        val recoveredClaims = recoveredCredential.credentialSubject.claims
        assertEquals(originalClaims.size, recoveredClaims.size, "Claims size should match")
        originalClaims.forEach { (key, value) ->
            assertTrue(recoveredClaims.containsKey(key), "Should contain claim: $key")
            val recoveredValue = recoveredClaims[key]
            assertNotNull(recoveredValue, "Recovered value should not be null for key: $key")
        }
    }

    @Test
    fun `test toJsonLd extension function`() = runBlocking {
        val didResolver = createMockDidResolver()
        val service = credentialService(didResolver = didResolver)
        val credential = createTestCredential()

        val jsonLd = service.toJsonLd(credential)

        assertNotNull(jsonLd, "JSON-LD should be created")
        // JSON-LD is a JsonObject, check it has content
        assertTrue(jsonLd.size > 0, "JSON-LD should have content")
    }

    @Test
    fun `test fromJsonLd extension function`() = runBlocking {
        val didResolver = createMockDidResolver()
        val service = credentialService(didResolver = didResolver)
        val originalCredential = createTestCredential()

        // Convert to JSON-LD and back
        val jsonLd = service.toJsonLd(originalCredential)
        val recoveredCredential = service.fromJsonLd(jsonLd)

        assertNotNull(recoveredCredential, "Credential should be recovered from JSON-LD")
        assertEquals(originalCredential.type, recoveredCredential.type, "Type should match")
        assertEquals(originalCredential.issuer.id.value, recoveredCredential.issuer.id.value, "Issuer should match")
    }

    @Test
    fun `test toJsonLd and fromJsonLd round trip`() = runBlocking {
        val didResolver = createMockDidResolver()
        val service = credentialService(didResolver = didResolver)
        val originalCredential = createTestCredential()

        val jsonLd = service.toJsonLd(originalCredential)
        val recoveredCredential = service.fromJsonLd(jsonLd)

        // Verify claims
        val originalClaims = originalCredential.credentialSubject.claims
        val recoveredClaims = recoveredCredential.credentialSubject.claims
        assertEquals(originalClaims.size, recoveredClaims.size, "Claims size should match")
    }

    @Test
    fun `test toCbor extension function`() = runBlocking {
        val didResolver = createMockDidResolver()
        val service = credentialService(didResolver = didResolver)
        val credential = createTestCredential()

        val cbor = service.toCbor(credential)

        assertNotNull(cbor, "CBOR should be created")
        assertTrue(cbor.isNotEmpty(), "CBOR should not be empty")
        // CBOR should be smaller or similar size to JSON
        assertTrue(cbor.size > 0, "CBOR should have content")
    }

    @Test
    fun `test fromCbor extension function`() = runBlocking {
        val didResolver = createMockDidResolver()
        val service = credentialService(didResolver = didResolver)
        val originalCredential = createTestCredential()

        // Convert to CBOR and back
        val cbor = service.toCbor(originalCredential)
        val recoveredCredential = service.fromCbor(cbor)

        assertNotNull(recoveredCredential, "Credential should be recovered from CBOR")
        assertEquals(originalCredential.type, recoveredCredential.type, "Type should match")
        assertEquals(originalCredential.issuer.id.value, recoveredCredential.issuer.id.value, "Issuer should match")
    }

    @Test
    fun `test toCbor and fromCbor round trip`() = runBlocking {
        val didResolver = createMockDidResolver()
        val service = credentialService(didResolver = didResolver)
        val originalCredential = createTestCredential()

        val cbor = service.toCbor(originalCredential)
        val recoveredCredential = service.fromCbor(cbor)

        // Verify claims
        val originalClaims = originalCredential.credentialSubject.claims
        val recoveredClaims = recoveredCredential.credentialSubject.claims
        assertEquals(originalClaims.size, recoveredClaims.size, "Claims size should match")
        originalClaims.forEach { (key, value) ->
            assertTrue(recoveredClaims.containsKey(key), "Should contain claim: $key")
        }
    }

    @Test
    fun `test all format conversions round trip`() = runBlocking {
        val didResolver = createMockDidResolver()
        val service = credentialService(didResolver = didResolver)
        val originalCredential = createTestCredential()

        // Test JWT round trip
        val jwt = service.toJwt(originalCredential)
        val fromJwt = service.fromJwt(jwt)
        assertEquals(originalCredential.type, fromJwt.type, "JWT round trip should preserve type")

        // Test JSON-LD round trip
        val jsonLd = service.toJsonLd(originalCredential)
        val fromJsonLd = service.fromJsonLd(jsonLd)
        assertEquals(originalCredential.type, fromJsonLd.type, "JSON-LD round trip should preserve type")

        // Test CBOR round trip
        val cbor = service.toCbor(originalCredential)
        val fromCbor = service.fromCbor(cbor)
        assertEquals(originalCredential.type, fromCbor.type, "CBOR round trip should preserve type")
    }
}

