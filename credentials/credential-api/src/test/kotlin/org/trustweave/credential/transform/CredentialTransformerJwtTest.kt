package org.trustweave.credential.transform

import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.transform.toJwt
import org.trustweave.credential.transform.fromJwt
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Comprehensive tests for CredentialTransformer JWT operations.
 * 
 * Uses the elegant extension function API for credential transformations.
 */
class CredentialTransformerJwtTest {

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
    fun `test toJwt creates valid JWT format`() = runBlocking {
        val credential = createTestCredential()

        val jwt = credential.toJwt()

        assertNotNull(jwt, "JWT should be created")
        // JWT format: header.payload.signature (unsigned JWTs have empty signature)
        val parts = jwt.split(".")
        assertTrue(parts.size >= 2, "JWT should have at least header and payload")
        assertTrue(parts.size <= 3, "JWT should have at most 3 parts (unsigned can have 2)")
    }

    @Test
    fun `test fromJwt recovers credential structure`() = runBlocking {
        val originalCredential = createTestCredential(
            claims = mapOf(
                "name" to JsonPrimitive("John Doe"),
                "age" to JsonPrimitive(30)
            )
        )

        val jwt = originalCredential.toJwt()
        val recoveredCredential = jwt.fromJwt()

        assertNotNull(recoveredCredential, "Credential should be recovered")
        assertEquals(originalCredential.type, recoveredCredential.type, "Type should match")
        assertEquals(originalCredential.issuer.id.value, recoveredCredential.issuer.id.value, "Issuer should match")
        assertEquals(originalCredential.credentialSubject.id.value, recoveredCredential.credentialSubject.id.value, "Subject should match")
    }

    @Test
    fun `test JWT round trip preserves all claims`() = runBlocking {
        val originalCredential = createTestCredential(
            claims = mapOf(
                "name" to JsonPrimitive("Jane Smith"),
                "email" to JsonPrimitive("jane@example.com"),
                "age" to JsonPrimitive(28),
                "isActive" to JsonPrimitive(true),
                "score" to JsonPrimitive(95.5)
            )
        )

        val jwt = originalCredential.toJwt()
        val recoveredCredential = jwt.fromJwt()

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
    fun `test toJwt handles empty claims`() = runBlocking {
        val credential = createTestCredential(claims = emptyMap())

        val jwt = credential.toJwt()

        assertNotNull(jwt, "JWT should be created even with empty claims")
        val recoveredCredential = jwt.fromJwt()
        assertEquals(0, recoveredCredential.credentialSubject.claims.size, "Recovered credential should have empty claims")
    }

    @Test
    fun `test toJwt handles multiple credential types`() = runBlocking {
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

        val jwt = credential.toJwt()
        val recoveredCredential = jwt.fromJwt()

        assertEquals(credential.type.size, recoveredCredential.type.size, "Type count should match")
        assertTrue(recoveredCredential.type.any { it.value == "UniversityDegreeCredential" }, 
            "Should contain UniversityDegreeCredential type")
    }

    @Test
    fun `test fromJwt handles invalid JWT format`() = runBlocking {
        val invalidJwt = "not.a.valid.jwt"

        assertFailsWith<IllegalArgumentException> {
            invalidJwt.fromJwt()
        }
    }

    @Test
    fun `test fromJwt handles empty JWT`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            "".fromJwt()
        }
    }

    @Test
    fun `test fromJwt handles malformed JWT payload`() = runBlocking {
        // Create a JWT with invalid base64 payload
        val invalidJwt = "eyJ0eXAiOiJKV1QifQ.invalid.payload"

        assertFailsWith<IllegalArgumentException> {
            invalidJwt.fromJwt()
        }
    }

    @Test
    fun `test JWT with DID issuer`() = runBlocking {
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

        val jwt = credential.toJwt()
        val recoveredCredential = jwt.fromJwt()

        assertEquals(credential.issuer.id.value, recoveredCredential.issuer.id.value, "DID issuer should be preserved")
    }

    @Test
    fun `test JWT with complex nested claims`() = runBlocking {
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

        val jwt = credential.toJwt()
        val recoveredCredential = jwt.fromJwt()

        assertTrue(recoveredCredential.credentialSubject.claims.containsKey("profile"), 
            "Should contain nested profile claim")
        val recoveredProfile = recoveredCredential.credentialSubject.claims["profile"]
        assertNotNull(recoveredProfile, "Profile should not be null")
        assertTrue(recoveredProfile is JsonObject, "Profile should be JsonObject")
    }
}



