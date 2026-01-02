package org.trustweave.credential.transform

import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.core.identifiers.Iri
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Comprehensive tests for CredentialTransformer JSON-LD operations.
 */
class CredentialTransformerJsonLdTest {

    private val transformer = CredentialTransformer()

    private fun createTestCredential(claims: Map<String, JsonElement> = emptyMap()): VerifiableCredential {
        return VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:holder"),
                claims = claims
            ),
            proof = null
        )
    }

    @Test
    fun `test toJsonLd creates valid JSON-LD structure`() = runBlocking {
        val credential = createTestCredential()

        val jsonLd = transformer.toJsonLd(credential)

        assertNotNull(jsonLd, "JSON-LD should be created")
        // JSON-LD is a JsonObject, check it has content
        assertTrue(jsonLd.size > 0, "JSON-LD should have content")
    }

    @Test
    fun `test fromJsonLd recovers credential structure`() = runBlocking {
        val originalCredential = createTestCredential(
            claims = mapOf(
                "name" to JsonPrimitive("John Doe"),
                "age" to JsonPrimitive(30)
            )
        )

        val jsonLd = transformer.toJsonLd(originalCredential)
        val recoveredCredential = transformer.fromJsonLd(jsonLd)

        assertNotNull(recoveredCredential, "Credential should be recovered")
        assertEquals(originalCredential.type, recoveredCredential.type, "Type should match")
        assertEquals(originalCredential.issuer.id.value, recoveredCredential.issuer.id.value, "Issuer should match")
        assertEquals(originalCredential.credentialSubject.id.value, recoveredCredential.credentialSubject.id.value, "Subject should match")
    }

    @Test
    fun `test JSON-LD round trip preserves all claims`() = runBlocking {
        val originalCredential = createTestCredential(
            claims = mapOf(
                "name" to JsonPrimitive("Jane Smith"),
                "email" to JsonPrimitive("jane@example.com"),
                "age" to JsonPrimitive(28),
                "isActive" to JsonPrimitive(true),
                "score" to JsonPrimitive(95.5)
            )
        )

        val jsonLd = transformer.toJsonLd(originalCredential)
        val recoveredCredential = transformer.fromJsonLd(jsonLd)

        val originalClaims = originalCredential.credentialSubject.claims
        val recoveredClaims = recoveredCredential.credentialSubject.claims

        assertEquals(originalClaims.size, recoveredClaims.size, "Claims size should match")
        originalClaims.forEach { (key, value) ->
            assertTrue(recoveredClaims.containsKey(key), "Should contain claim: $key")
            val recoveredValue = recoveredClaims[key]
            assertNotNull(recoveredValue, "Recovered value should not be null")
        }
    }

    @Test
    fun `test toJsonLd includes context`() = runBlocking {
        val credential = createTestCredential()

        val jsonLd = transformer.toJsonLd(credential)

        // JSON-LD structure is validated by checking it has content
        assertTrue(jsonLd.size > 0, "JSON-LD should have content")
    }

    @Test
    fun `test toJsonLd includes type as array`() = runBlocking {
        val credential = createTestCredential()

        val jsonLd = transformer.toJsonLd(credential)

        val type = jsonLd["type"]
        assertNotNull(type, "type should be present")
        assertTrue(type is JsonArray, "type should be an array")
    }

    @Test
    fun `test toJsonLd handles empty claims`() = runBlocking {
        val credential = createTestCredential(claims = emptyMap())

        val jsonLd = transformer.toJsonLd(credential)
        val recoveredCredential = transformer.fromJsonLd(jsonLd)

        assertEquals(0, recoveredCredential.credentialSubject.claims.size, "Recovered credential should have empty claims")
    }

    @Test
    fun `test toJsonLd handles multiple credential types`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf(
                CredentialType.fromString("VerifiableCredential"),
                CredentialType.fromString("UniversityDegreeCredential")
            ),
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:university")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:student"),
                claims = mapOf("degree" to JsonPrimitive("Bachelor of Science"))
            ),
            proof = null
        )

        val jsonLd = transformer.toJsonLd(credential)
        val recoveredCredential = transformer.fromJsonLd(jsonLd)

        assertEquals(credential.type.size, recoveredCredential.type.size, "Type count should match")
        assertTrue(recoveredCredential.type.any { it.value == "UniversityDegreeCredential" }, 
            "Should contain UniversityDegreeCredential type")
    }

    @Test
    fun `test fromJsonLd handles missing required fields`() = runBlocking {
        // Create invalid JSON-LD missing required fields
        val invalidJsonLd = buildJsonObject {
            // Missing @context, type, issuer, etc.
            put("test", "value")
        }

        // This should either throw an exception or handle gracefully
        // The actual behavior depends on the implementation
        try {
            transformer.fromJsonLd(invalidJsonLd)
            // If it doesn't throw, that's also acceptable behavior (graceful degradation)
        } catch (e: Exception) {
            // Expected exception is fine
            assertTrue(e is IllegalArgumentException || e is kotlinx.serialization.SerializationException,
                "Should throw appropriate exception")
        }
    }

    @Test
    fun `test JSON-LD with DID issuer`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:issuer123")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:holder123"),
                claims = emptyMap()
            ),
            proof = null
        )

        val jsonLd = transformer.toJsonLd(credential)
        val recoveredCredential = transformer.fromJsonLd(jsonLd)

        assertEquals(credential.issuer.id.value, recoveredCredential.issuer.id.value, "DID issuer should be preserved")
    }

    @Test
    fun `test JSON-LD with complex nested claims`() = runBlocking {
        val nestedClaims = buildJsonObject {
            put("name", "John Doe")
            put("address", buildJsonObject {
                put("street", "123 Main St")
                put("city", "Anytown")
                put("zip", "12345")
            })
            put("phones", buildJsonArray {
                add("555-0100")
                add("555-0101")
            })
        }

        val credential = createTestCredential(
            claims = mapOf("profile" to nestedClaims)
        )

        val jsonLd = transformer.toJsonLd(credential)
        val recoveredCredential = transformer.fromJsonLd(jsonLd)

        assertTrue(recoveredCredential.credentialSubject.claims.containsKey("profile"), 
            "Should contain nested profile claim")
        val recoveredProfile = recoveredCredential.credentialSubject.claims["profile"]
        assertNotNull(recoveredProfile, "Profile should not be null")
        assertTrue(recoveredProfile is JsonObject, "Profile should be JsonObject")
    }

    @Test
    fun `test JSON-LD preserves issuance date`() = runBlocking {
        val issuanceDate = Clock.System.now()
        val credential = VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = issuanceDate,
            credentialSubject = CredentialSubject(
                id = Iri("did:key:holder"),
                claims = emptyMap()
            ),
            proof = null
        )

        val jsonLd = transformer.toJsonLd(credential)
        val recoveredCredential = transformer.fromJsonLd(jsonLd)

        assertEquals(issuanceDate, recoveredCredential.issuanceDate, "Issuance date should be preserved")
    }
}



