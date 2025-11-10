package io.geoknoesis.vericore.credential.did

import io.geoknoesis.vericore.credential.CredentialIssuanceOptions
import io.geoknoesis.vericore.credential.issuer.CredentialIssuer
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.did.DidMethod
import io.geoknoesis.vericore.did.DidMethodRegistry
import io.geoknoesis.vericore.did.DidResolutionResult
import io.geoknoesis.vericore.did.DefaultDidMethodRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for DidLinkedCredentialService.
 * Tests all methods, branches, and edge cases.
 */
class DidLinkedCredentialServiceBranchCoverageTest {

    @BeforeEach
    fun setup() {
        ProofGeneratorRegistry.clear()
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        ProofGeneratorRegistry.register(generator)
    }

    @Test
    fun `test DidLinkedCredentialService issueCredentialForDid with string claims`() = runBlocking {
        val issuer = CredentialIssuer()
        val service = DidLinkedCredentialService(didRegistry = createDidRegistry(), credentialIssuer = issuer)
        
        val claims = mapOf<String, Any>(
            "name" to "John Doe",
            "email" to "john@example.com"
        )
        
        val credential = service.issueCredentialForDid(
            subjectDid = "did:key:subject",
            credentialType = "PersonCredential",
            claims = claims,
            issuerDid = "did:key:issuer",
            keyId = "key-1"
        )
        
        assertNotNull(credential)
        assertEquals("PersonCredential", credential.type.last())
        assertEquals("did:key:subject", credential.credentialSubject.jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test DidLinkedCredentialService issueCredentialForDid with number claims`() = runBlocking {
        val issuer = CredentialIssuer()
        val service = DidLinkedCredentialService(didRegistry = createDidRegistry(), credentialIssuer = issuer)
        
        val claims = mapOf<String, Any>(
            "age" to 30,
            "score" to 95.5
        )
        
        val credential = service.issueCredentialForDid(
            subjectDid = "did:key:subject",
            credentialType = "PersonCredential",
            claims = claims,
            issuerDid = "did:key:issuer",
            keyId = "key-1"
        )
        
        assertNotNull(credential)
    }

    @Test
    fun `test DidLinkedCredentialService issueCredentialForDid with boolean claims`() = runBlocking {
        val issuer = CredentialIssuer()
        val service = DidLinkedCredentialService(didRegistry = createDidRegistry(), credentialIssuer = issuer)
        
        val claims = mapOf<String, Any>(
            "verified" to true,
            "active" to false
        )
        
        val credential = service.issueCredentialForDid(
            subjectDid = "did:key:subject",
            credentialType = "PersonCredential",
            claims = claims,
            issuerDid = "did:key:issuer",
            keyId = "key-1"
        )
        
        assertNotNull(credential)
    }

    @Test
    fun `test DidLinkedCredentialService issueCredentialForDid with mixed claims`() = runBlocking {
        val issuer = CredentialIssuer()
        val service = DidLinkedCredentialService(didRegistry = createDidRegistry(), credentialIssuer = issuer)
        
        val claims = mapOf<String, Any>(
            "name" to "John Doe",
            "age" to 30,
            "verified" to true,
            "custom" to mapOf("nested" to "value")
        )
        
        val credential = service.issueCredentialForDid(
            subjectDid = "did:key:subject",
            credentialType = "PersonCredential",
            claims = claims,
            issuerDid = "did:key:issuer",
            keyId = "key-1"
        )
        
        assertNotNull(credential)
    }

    @Test
    fun `test DidLinkedCredentialService issueCredentialForDid with options`() = runBlocking {
        val issuer = CredentialIssuer()
        val service = DidLinkedCredentialService(didRegistry = createDidRegistry(), credentialIssuer = issuer)
        
        val claims = mapOf<String, Any>("name" to "John Doe")
        val options = CredentialIssuanceOptions(
            proofType = "Ed25519Signature2020",
            challenge = "challenge-123"
        )
        
        val credential = service.issueCredentialForDid(
            subjectDid = "did:key:subject",
            credentialType = "PersonCredential",
            claims = claims,
            issuerDid = "did:key:issuer",
            keyId = "key-1",
            options = options
        )
        
        assertNotNull(credential)
        assertNotNull(credential.proof)
    }

    @Test
    fun `test DidLinkedCredentialService resolveCredentialSubject with DID subject`() = runBlocking {
        val issuer = CredentialIssuer()
        val service = DidLinkedCredentialService(didRegistry = createDidRegistry(), credentialIssuer = issuer)
        
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            },
            issuanceDate = java.time.Instant.now().toString()
        )
        
        val subjectDid = service.resolveCredentialSubject(credential)
        
        assertEquals("did:key:subject", subjectDid)
    }

    @Test
    fun `test DidLinkedCredentialService resolveCredentialSubject with non-DID subject`() = runBlocking {
        val issuer = CredentialIssuer()
        val service = DidLinkedCredentialService(didRegistry = createDidRegistry(), credentialIssuer = issuer)
        
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "not-a-did")
                put("name", "John Doe")
            },
            issuanceDate = java.time.Instant.now().toString()
        )
        
        val subjectDid = service.resolveCredentialSubject(credential)
        
        assertNull(subjectDid)
    }

    @Test
    fun `test DidLinkedCredentialService resolveCredentialSubject with missing id`() = runBlocking {
        val issuer = CredentialIssuer()
        val service = DidLinkedCredentialService(didRegistry = createDidRegistry(), credentialIssuer = issuer)
        
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("name", "John Doe")
            },
            issuanceDate = java.time.Instant.now().toString()
        )
        
        val subjectDid = service.resolveCredentialSubject(credential)
        
        assertNull(subjectDid)
    }

    @Test
    fun `test DidLinkedCredentialService verifyIssuerDid returns true`() = runBlocking {
        val issuer = CredentialIssuer()
        val service = DidLinkedCredentialService(didRegistry = createDidRegistry(), credentialIssuer = issuer)
        
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = java.time.Instant.now().toString()
        )
        
        val valid = service.verifyIssuerDid(credential)
        
        assertTrue(valid) // Current implementation always returns true
    }

    @Test
    fun `test DidLinkedCredentialService verifyIssuerDid with invalid DID`() = runBlocking {
        val issuer = CredentialIssuer()
        val service = DidLinkedCredentialService(didRegistry = createDidRegistry(), credentialIssuer = issuer)
        
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "invalid-did",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = java.time.Instant.now().toString()
        )
        
        val valid = service.verifyIssuerDid(credential)
        
        assertFalse(valid)
    }
}

private fun createDidRegistry(): DidMethodRegistry {
    val registry = DefaultDidMethodRegistry()
    registry.register(object : DidMethod {
        override val method: String = "key"

        override suspend fun createDid(options: Map<String, Any?>): DidDocument {
            val id = options["id"] as? String ?: "did:key:${java.util.UUID.randomUUID()}"
            return DidDocument(id = id)
        }

        override suspend fun resolveDid(did: String): DidResolutionResult = DidResolutionResult(
            document = DidDocument(id = did)
        )

        override suspend fun updateDid(
            did: String,
            updater: (DidDocument) -> DidDocument
        ): DidDocument = updater(DidDocument(id = did))

        override suspend fun deactivateDid(did: String): Boolean = true
    })
    return registry
}



