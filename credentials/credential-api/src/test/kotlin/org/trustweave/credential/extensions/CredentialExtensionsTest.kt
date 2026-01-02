package org.trustweave.credential.extensions

import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.extensions.*
import org.trustweave.core.identifiers.Iri
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Tests for CredentialExtensions extension functions.
 */
class CredentialExtensionsTest {
    
    private fun createTestCredential(
        expirationDate: Instant? = null,
        types: List<String> = listOf("VerifiableCredential"),
        claims: Map<String, JsonElement> = emptyMap()
    ): VerifiableCredential {
        return VerifiableCredential(
            type = types.map { CredentialType.fromString(it) },
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = Clock.System.now(),
            expirationDate = expirationDate,
            credentialSubject = CredentialSubject(
                id = Iri("did:key:holder"),
                claims = claims
            ),
            proof = null
        )
    }
    
    @Test
    fun `test isExpired returns true for expired credential`() {
        val pastDate = Clock.System.now().minus(1.days)
        val credential = createTestCredential(expirationDate = pastDate)
        
        assertTrue(credential.isExpired(), "Credential should be expired")
    }
    
    @Test
    fun `test isExpired returns false for non-expired credential`() {
        val futureDate = Clock.System.now().plus(1.days)
        val credential = createTestCredential(expirationDate = futureDate)
        
        assertFalse(credential.isExpired(), "Credential should not be expired")
    }
    
    @Test
    fun `test isExpired returns false for credential without expiration`() {
        val credential = createTestCredential(expirationDate = null)
        
        assertFalse(credential.isExpired(), "Credential without expiration should not be expired")
    }
    
    @Test
    fun `test isExpiredAt checks expiration at specific time`() {
        val expirationDate = Clock.System.now().plus(1.days)
        val credential = createTestCredential(expirationDate = expirationDate)
        
        val beforeExpiration = Clock.System.now().plus(12.hours)
        val afterExpiration = Clock.System.now().plus(2.days)
        
        assertFalse(credential.isExpiredAt(beforeExpiration), "Credential should not be expired before expiration date")
        assertTrue(credential.isExpiredAt(afterExpiration), "Credential should be expired after expiration date")
    }
    
    @Test
    fun `test isValid returns true for valid credential`() {
        val futureDate = Clock.System.now().plus(1.days)
        val credential = createTestCredential(expirationDate = futureDate)
        
        assertTrue(credential.isValid(), "Credential should be valid")
    }
    
    @Test
    fun `test isValid returns false for expired credential`() {
        val pastDate = Clock.System.now().minus(1.days)
        val credential = createTestCredential(expirationDate = pastDate)
        
        assertFalse(credential.isValid(), "Credential should not be valid")
    }
    
    @Test
    fun `test isValidAt checks validity at specific time`() {
        val expirationDate = Clock.System.now().plus(1.days)
        val credential = createTestCredential(expirationDate = expirationDate)
        
        val beforeExpiration = Clock.System.now().plus(12.hours)
        val afterExpiration = Clock.System.now().plus(2.days)
        
        assertTrue(credential.isValidAt(beforeExpiration), "Credential should be valid before expiration")
        assertFalse(credential.isValidAt(afterExpiration), "Credential should not be valid after expiration")
    }
    
    @Test
    fun `test typeStrings returns list of type strings`() {
        val credential = createTestCredential(
            types = listOf("VerifiableCredential", "UniversityDegreeCredential")
        )
        
        val types = credential.typeStrings()
        
        assertEquals(2, types.size)
        assertTrue(types.contains("VerifiableCredential"))
        assertTrue(types.contains("UniversityDegreeCredential"))
    }
    
    @Test
    fun `test hasType returns true for existing type`() {
        val credential = createTestCredential(
            types = listOf("VerifiableCredential", "UniversityDegreeCredential")
        )
        
        assertTrue(credential.hasType("UniversityDegreeCredential"))
        assertTrue(credential.hasType("VerifiableCredential"))
    }
    
    @Test
    fun `test hasType returns false for non-existing type`() {
        val credential = createTestCredential(
            types = listOf("VerifiableCredential")
        )
        
        assertFalse(credential.hasType("UniversityDegreeCredential"))
    }
    
    @Test
    fun `test getClaim returns claim value`() {
        val credential = createTestCredential(
            claims = mapOf(
                "name" to JsonPrimitive("John Doe"),
                "age" to JsonPrimitive(30)
            )
        )
        
        val name = credential.getClaim("name")
        val age = credential.getClaim("age")
        
        assertTrue(name is JsonPrimitive)
        assertEquals("John Doe", (name as JsonPrimitive).content)
        assertTrue(age is JsonPrimitive)
        assertEquals(30, (age as JsonPrimitive).longOrNull)
    }
    
    @Test
    fun `test getClaim returns null for non-existing claim`() {
        val credential = createTestCredential(claims = emptyMap())
        
        val claim = credential.getClaim("nonexistent")
        
        assertEquals(null, claim)
    }
    
    @Test
    fun `test hasClaim returns true for existing claim`() {
        val credential = createTestCredential(
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        )
        
        assertTrue(credential.hasClaim("name"))
    }
    
    @Test
    fun `test hasClaim returns false for non-existing claim`() {
        val credential = createTestCredential(claims = emptyMap())
        
        assertFalse(credential.hasClaim("nonexistent"))
    }
    
    @Test
    fun `test credentialCount returns correct count`() {
        val credential1 = createTestCredential()
        val credential2 = createTestCredential()
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:key:holder"),
            verifiableCredential = listOf(credential1, credential2),
            proof = null
        )
        
        assertEquals(2, presentation.credentialCount())
    }
    
    @Test
    fun `test isEmpty returns true for empty presentation`() {
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:key:holder"),
            verifiableCredential = emptyList(),
            proof = null
        )
        
        assertTrue(presentation.isEmpty())
    }
    
    @Test
    fun `test isEmpty returns false for non-empty presentation`() {
        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:key:holder"),
            verifiableCredential = listOf(credential),
            proof = null
        )
        
        assertFalse(presentation.isEmpty())
    }
    
    @Test
    fun `test isNotEmpty returns true for non-empty presentation`() {
        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:key:holder"),
            verifiableCredential = listOf(credential),
            proof = null
        )
        
        assertTrue(presentation.isNotEmpty())
    }
    
    @Test
    fun `test isNotEmpty returns false for empty presentation`() {
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:key:holder"),
            verifiableCredential = emptyList(),
            proof = null
        )
        
        assertFalse(presentation.isNotEmpty())
    }
    
    @Test
    fun `test allCredentialTypes returns all types from all credentials`() {
        val credential1 = createTestCredential(types = listOf("VerifiableCredential", "TypeA"))
        val credential2 = createTestCredential(types = listOf("VerifiableCredential", "TypeB"))
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:key:holder"),
            verifiableCredential = listOf(credential1, credential2),
            proof = null
        )
        
        val allTypes = presentation.allCredentialTypes()
        
        assertTrue(allTypes.contains("VerifiableCredential"))
        assertTrue(allTypes.contains("TypeA"))
        assertTrue(allTypes.contains("TypeB"))
        assertEquals(3, allTypes.size) // Distinct types
    }
    
    @Test
    fun `test credentialsByType filters credentials by type`() {
        val credential1 = createTestCredential(types = listOf("VerifiableCredential", "TypeA"))
        val credential2 = createTestCredential(types = listOf("VerifiableCredential", "TypeB"))
        val credential3 = createTestCredential(types = listOf("VerifiableCredential", "TypeA"))
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:key:holder"),
            verifiableCredential = listOf(credential1, credential2, credential3),
            proof = null
        )
        
        val typeACredentials = presentation.credentialsByType("TypeA")
        
        assertEquals(2, typeACredentials.size)
        assertTrue(typeACredentials.contains(credential1))
        assertTrue(typeACredentials.contains(credential3))
        assertFalse(typeACredentials.contains(credential2))
    }
}

