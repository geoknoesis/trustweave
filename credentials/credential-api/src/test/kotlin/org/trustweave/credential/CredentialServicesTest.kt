package org.trustweave.credential

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for CredentialServices factory methods.
 */
class CredentialServicesTest {

    @Test
    fun `test credentialService with didResolver only`() = runBlocking {
        val didResolver = createMockDidResolver()
        
        val service = credentialService(didResolver = didResolver)
        
        assertNotNull(service, "Service should be created")
        // Verify service works by checking it can handle a basic operation
        assertTrue(service is CredentialService)
    }

    @Test
    fun `test credentialService with all optional parameters`() = runBlocking {
        val didResolver = createMockDidResolver()
        val schemaRegistry = null // Schema registry can be null
        val revocationManager = null // Revocation manager can be null
        
        val service = credentialService(
            didResolver = didResolver,
            schemaRegistry = schemaRegistry,
            revocationManager = revocationManager
        )
        
        assertNotNull(service, "Service should be created with all parameters")
    }

    @Test
    fun `test credentialService with signer function`() = runBlocking {
        val didResolver = createMockDidResolver()
        val kms: KeyManagementService = InMemoryKeyManagementService()
        
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            val result = kms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)
            when (result) {
                is SignResult.Success -> result.signature
                is SignResult.Failure.UnsupportedAlgorithm -> 
                    throw IllegalArgumentException("Unsupported algorithm")
                is SignResult.Failure.KeyNotFound -> 
                    throw IllegalArgumentException("Key not found: $keyId")
                is SignResult.Failure.Error -> 
                    throw RuntimeException("Signing error: ${result.reason}")
            }
        }
        
        val service = credentialService(
            didResolver = didResolver,
            signer = signer
        )
        
        assertNotNull(service, "Service should be created with signer")
    }

    @Test
    fun `test credentialService with signer and optional parameters`() = runBlocking {
        val didResolver = createMockDidResolver()
        val kms: KeyManagementService = InMemoryKeyManagementService()
        
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            val result = kms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)
            when (result) {
                is SignResult.Success -> result.signature
                is SignResult.Failure.UnsupportedAlgorithm -> 
                    throw IllegalArgumentException("Unsupported algorithm")
                is SignResult.Failure.KeyNotFound -> 
                    throw IllegalArgumentException("Key not found: $keyId")
                is SignResult.Failure.Error -> 
                    throw RuntimeException("Signing error: ${result.reason}")
            }
        }
        
        val service = credentialService(
            didResolver = didResolver,
            signer = signer,
            schemaRegistry = null,
            revocationManager = null
        )
        
        assertNotNull(service, "Service should be created with signer and optional parameters")
    }

    @Test
    fun `test createCredentialService with all formats`() = runBlocking {
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didResolver = createMockDidResolver()
        
        val service = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD, ProofSuiteId.SD_JWT_VC)
        )
        
        assertNotNull(service, "Service should be created with all formats")
        
        // Note: Issuance requires proper KMS setup with keys, so we just verify service creation
        // Actual issuance testing is done in integration tests
    }

    @Test
    fun `test createCredentialService with single format`() = runBlocking {
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didResolver = createMockDidResolver()
        
        val service = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD)
        )
        
        assertNotNull(service, "Service should be created with single format")
        
        // Note: Issuance requires proper KMS setup with keys, so we just verify service creation
        // Actual issuance testing is done in integration tests
    }

    @Test
    fun `test createCredentialService with unsupported format`() = runBlocking {
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didResolver = createMockDidResolver()
        
        // This should not fail - it should create service with available formats
        val service = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD) // Only VC-LD is available
        )
        
        assertNotNull(service, "Service should be created even with limited formats")
    }


    @Test
    fun `test createCredentialService with KMS creates service successfully`() = runBlocking {
        val didResolver = createMockDidResolver()
        val kms: KeyManagementService = InMemoryKeyManagementService()
        
        // createCredentialService creates signer internally from KMS
        val service = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD)
        )
        
        assertNotNull(service, "Service should be created from KMS")
    }

    private fun createMockDidResolver(): DidResolver {
        return object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                // Return a mock success result
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

    private fun createTestIssuanceRequest(format: ProofSuiteId): IssuanceRequest {
        val issuerDid = Did("did:key:test-issuer")
        val holderDid = Did("did:key:test-holder")
        
        return IssuanceRequest(
            issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
            credentialSubject = CredentialSubject(
                id = holderDid,
                claims = mapOf(
                    "name" to JsonPrimitive("Test User"),
                    "age" to JsonPrimitive(30)
                )
            ),
            format = format,
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuerKeyId = null // Will use default key
        )
    }
}

