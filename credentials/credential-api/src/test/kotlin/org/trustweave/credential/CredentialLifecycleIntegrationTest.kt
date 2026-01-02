package org.trustweave.credential

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.kms.KeyManagementService
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.credential.CredentialServices
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * Integration tests for the full credential lifecycle.
 * 
 * Tests the complete flow from credential issuance through verification,
 * ensuring all components work together correctly.
 * 
 * These tests verify:
 * - Credential issuance with VC-LD format
 * - Credential verification (including signature verification)
 * - Batch credential verification
 * - Expiration checking
 * - Error handling for invalid credentials
 */
class CredentialLifecycleIntegrationTest {
    
    @Test
    fun `test full credential lifecycle - issue and verify`() = runBlocking {
        // Setup: Create KMS and DID method
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        
        // Create issuer DID
        val issuerDocument = didMethod.createDid()
        val issuerDid = issuerDocument.id
        
        // Create holder DID
        val holderDocument = didMethod.createDid()
        val holderDid = holderDocument.id
        
        // Create DID resolver
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return when (did.value) {
                    issuerDid.value -> DidResolutionResult.Success(issuerDocument)
                    holderDid.value -> DidResolutionResult.Success(holderDocument)
                    else -> DidResolutionResult.Failure.NotFound(did, "DID not found")
                }
            }
        }
        
        // Create credential service with VC-LD engine
        val credentialService = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD)
        )
        
        // Get issuer key ID
        val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
            ?: throw IllegalStateException("No verification method found")
        
        // Step 1: Issue credential
        val issuanceRequest = IssuanceRequest(
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
            issuerKeyId = issuerKeyId,
            credentialSubject = CredentialSubject(
                id = Iri(holderDid.value),
                claims = mapOf(
                    "degree" to buildJsonObject {
                        put("type", "BachelorDegree")
                        put("name", "Bachelor of Science in Computer Science")
                        put("university", "Example University")
                    }
                )
            ),
            type = listOf(
                CredentialType.fromString("VerifiableCredential"),
                CredentialType.fromString("DegreeCredential")
            ),
            issuedAt = Clock.System.now(),
            validUntil = Clock.System.now().plus(3650.days)
        )
        
        val issuanceResult = credentialService.issue(issuanceRequest)
        
        // Step 2: Verify issuance succeeded
        assertTrue(issuanceResult is IssuanceResult.Success, "Credential issuance should succeed")
        val credential = (issuanceResult as IssuanceResult.Success).credential
        
        assertNotNull(credential.proof, "Credential should have a proof")
        assertEquals(issuerDid.value, credential.issuer.id.value, "Issuer should match")
        assertEquals(holderDid.value, credential.credentialSubject.id.value, "Subject should match")
        
        // Step 3: Verify credential
        val verificationOptions = VerificationOptions(
            checkExpiration = true,
            checkRevocation = false
        )
        
        val verificationResult = credentialService.verify(credential, null, verificationOptions)
        
        // Step 4: Verify verification succeeded
        assertTrue(verificationResult is VerificationResult.Valid, "Credential verification should succeed")
        val validResult = verificationResult as VerificationResult.Valid
        
        assertEquals(issuerDid.value, validResult.issuerIri.value, "Verified issuer should match")
        assertEquals(holderDid.value, validResult.subjectIri?.value, "Verified subject should match")
        assertNotNull(validResult.issuedAt, "Issued at should be set")
    }
    
    @Test
    fun `test credential lifecycle with expiration check`() = runBlocking {
        // Setup
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        
        val issuerDocument = didMethod.createDid()
        val issuerDid = issuerDocument.id
        
        val holderDocument = didMethod.createDid()
        val holderDid = holderDocument.id
        
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return when (did.value) {
                    issuerDid.value -> DidResolutionResult.Success(issuerDocument)
                    holderDid.value -> DidResolutionResult.Success(holderDocument)
                    else -> DidResolutionResult.Failure.NotFound(did, "DID not found")
                }
            }
        }
        
        val credentialService = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD)
        )
        
        val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
            ?: throw IllegalStateException("No verification method found")
        
        // Issue credential with expiration in the past
        val pastExpiration = Clock.System.now().minus(1.days)
        
        val issuanceRequest = IssuanceRequest(
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
            issuerKeyId = issuerKeyId,
            credentialSubject = CredentialSubject(
                id = Iri(holderDid.value),
                claims = emptyMap()
            ),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuedAt = Clock.System.now().minus(365.days),
            validUntil = pastExpiration
        )
        
        val issuanceResult = credentialService.issue(issuanceRequest)
        assertTrue(issuanceResult is IssuanceResult.Success)
        val credential = (issuanceResult as IssuanceResult.Success).credential
        
        // Verify with expiration check - should fail
        val verificationOptions = VerificationOptions(
            checkExpiration = true,
            checkRevocation = false
        )
        
        val verificationResult = credentialService.verify(credential, null, verificationOptions)
        
        // Should be expired
        assertTrue(verificationResult is VerificationResult.Invalid.Expired, 
            "Credential should be expired, got: ${verificationResult::class.simpleName}")
    }
    
    @Test
    fun `test batch verification`() = runBlocking {
        // Setup
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        
        val issuerDocument = didMethod.createDid()
        val issuerDid = issuerDocument.id
        
        val holderDocument = didMethod.createDid()
        val holderDid = holderDocument.id
        
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return when (did.value) {
                    issuerDid.value -> DidResolutionResult.Success(issuerDocument)
                    holderDid.value -> DidResolutionResult.Success(holderDocument)
                    else -> DidResolutionResult.Failure.NotFound(did, "DID not found")
                }
            }
        }
        
        val credentialService = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD)
        )
        
        val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
            ?: throw IllegalStateException("No verification method found")
        
        // Create verification options
        val verificationOptions = VerificationOptions(checkExpiration = false)
        
        // Issue multiple credentials
        val credentials = (1..3).map { index ->
            val request = IssuanceRequest(
                format = ProofSuiteId.VC_LD,
                issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
                issuerKeyId = issuerKeyId,
                credentialSubject = CredentialSubject(
                    id = Iri(holderDid.value),
                    claims = mapOf("index" to JsonPrimitive(index.toString()))
                ),
                type = listOf(CredentialType.fromString("VerifiableCredential")),
                issuedAt = Clock.System.now()
            )
            
            val result = credentialService.issue(request)
            assertTrue(result is IssuanceResult.Success, "Credential $index should be issued successfully")
            (result as IssuanceResult.Success).credential
        }
        
        // Batch verify
        val results = credentialService.verify(credentials, null, verificationOptions)
        
        // All should be valid
        assertEquals(3, results.size, "Should have 3 verification results")
        results.forEachIndexed { index, result ->
            when (result) {
                is VerificationResult.Valid -> {
                    // Success
                }
                is VerificationResult.Invalid -> {
                    val reason = when (result) {
                        is VerificationResult.Invalid.InvalidProof -> result.reason
                        is VerificationResult.Invalid.InvalidIssuer -> result.reason
                        is VerificationResult.Invalid.Expired -> "Expired at: ${result.expiredAt}"
                        is VerificationResult.Invalid.Revoked -> "Revoked at: ${result.revokedAt}, reason: ${result.revocationReason}"
                        is VerificationResult.Invalid.NotYetValid -> "Not yet valid, valid from: ${result.validFrom}"
                        is VerificationResult.Invalid.UnsupportedFormat -> "Unsupported format: ${result.format.value}"
                        is VerificationResult.Invalid.UntrustedIssuer -> "Untrusted issuer: ${result.issuerDid.value}"
                        is VerificationResult.Invalid.SchemaValidationFailed -> "Schema validation failed: ${result.validationErrors.joinToString()}"
                        is VerificationResult.Invalid.MultipleFailures -> result.errors.joinToString()
                    }
                    assertTrue(false, 
                        "Credential $index should be valid, got: ${result::class.simpleName} - $reason")
                }
            }
        }
    }
    
    @Test
    fun `test issuance fails with unsupported format`() = runBlocking {
        // Setup
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val issuerDocument = didMethod.createDid()
        val issuerDid = issuerDocument.id
        
        val holderDocument = didMethod.createDid()
        val holderDid = holderDocument.id
        
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return when (did.value) {
                    issuerDid.value -> DidResolutionResult.Success(issuerDocument)
                    holderDid.value -> DidResolutionResult.Success(holderDocument)
                    else -> DidResolutionResult.Failure.NotFound(did, "DID not found")
                }
            }
        }
        
        // Create service with only VC-LD support
        val credentialService = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD)
        )
        
        val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
            ?: throw IllegalStateException("No verification method found")
        
        // Try to issue with unsupported format (assuming SD-JWT-VC is not registered)
        val request = IssuanceRequest(
            format = ProofSuiteId.SD_JWT_VC, // This should fail if not registered
            issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
            issuerKeyId = issuerKeyId,
            credentialSubject = CredentialSubject(
                id = Iri(holderDid.value),
                claims = emptyMap()
            ),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuedAt = Clock.System.now()
        )
        
        val result = credentialService.issue(request)
        
        // Should fail with UnsupportedFormat
        assertTrue(result is IssuanceResult.Failure.UnsupportedFormat,
            "Should fail with UnsupportedFormat, got: ${result::class.simpleName}")
    }
    
    @Test
    fun `test verification fails with invalid issuer DID`() = runBlocking {
        // Setup
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        
        val issuerDocument = didMethod.createDid()
        val issuerDid = issuerDocument.id
        
        val holderDocument = didMethod.createDid()
        val holderDid = holderDocument.id
        
        // DID resolver that fails to resolve issuer
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return when (did.value) {
                    holderDid.value -> DidResolutionResult.Success(holderDocument)
                    else -> DidResolutionResult.Failure.NotFound(did, "DID not found")
                }
            }
        }
        
        val credentialService = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD)
        )
        
        val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
            ?: throw IllegalStateException("No verification method found")
        
        // Issue credential (this will work because we have the DID document locally)
        val issuanceRequest = IssuanceRequest(
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
            issuerKeyId = issuerKeyId,
            credentialSubject = CredentialSubject(
                id = Iri(holderDid.value),
                claims = emptyMap()
            ),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuedAt = Clock.System.now()
        )
        
        val issuanceResult = credentialService.issue(issuanceRequest)
        assertTrue(issuanceResult is IssuanceResult.Success)
        val credential = (issuanceResult as IssuanceResult.Success).credential
        
        // Now verify - should fail because issuer DID cannot be resolved
        val verificationResult = credentialService.verify(credential, null, VerificationOptions())
        
        // Should fail with InvalidIssuer
        assertTrue(verificationResult is VerificationResult.Invalid.InvalidIssuer,
            "Should fail with InvalidIssuer, got: ${verificationResult::class.simpleName}")
    }
    
    @Test
    fun `test verification fails with credential missing proof`() = runBlocking {
        // Setup
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        
        val issuerDocument = didMethod.createDid()
        val issuerDid = issuerDocument.id
        
        val holderDocument = didMethod.createDid()
        val holderDid = holderDocument.id
        
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return when (did.value) {
                    issuerDid.value -> DidResolutionResult.Success(issuerDocument)
                    holderDid.value -> DidResolutionResult.Success(holderDocument)
                    else -> DidResolutionResult.Failure.NotFound(did, "DID not found")
                }
            }
        }
        
        val credentialService = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD)
        )
        
        // Create credential without proof
        val credentialWithoutProof = VerifiableCredential(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri(holderDid.value),
                claims = emptyMap()
            ),
            proof = null // No proof
        )
        
        // Verify should fail
        val verificationResult = credentialService.verify(
            credentialWithoutProof,
            null,
            VerificationOptions()
        )
        
        assertTrue(verificationResult is VerificationResult.Invalid.InvalidProof,
            "Should fail with InvalidProof, got: ${verificationResult::class.simpleName}")
    }
    
    @Test
    fun `test issuance fails with too many claims`() = runBlocking {
        // Setup
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        
        val issuerDocument = didMethod.createDid()
        val issuerDid = issuerDocument.id
        
        val holderDocument = didMethod.createDid()
        val holderDid = holderDocument.id
        
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return when (did.value) {
                    issuerDid.value -> DidResolutionResult.Success(issuerDocument)
                    holderDid.value -> DidResolutionResult.Success(holderDocument)
                    else -> DidResolutionResult.Failure.NotFound(did, "DID not found")
                }
            }
        }
        
        val credentialService = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = didResolver,
            formats = listOf(ProofSuiteId.VC_LD)
        )
        
        val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
            ?: throw IllegalStateException("No verification method found")
        
        // Create request with too many claims (exceeding MAX_CLAIMS_PER_CREDENTIAL)
        val tooManyClaims = (1..1001).associate { index ->
            "claim_$index" to JsonPrimitive("value_$index")
        }
        
        val request = IssuanceRequest(
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
            issuerKeyId = issuerKeyId,
            credentialSubject = CredentialSubject(
                id = Iri(holderDid.value),
                claims = tooManyClaims
            ),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuedAt = Clock.System.now()
        )
        
        val result = credentialService.issue(request)
        
        // Should fail with InvalidRequest
        assertTrue(result is IssuanceResult.Failure.InvalidRequest,
            "Should fail with InvalidRequest, got: ${result::class.simpleName}")
        
        val failure = result as IssuanceResult.Failure.InvalidRequest
        assertTrue(failure.reason.contains("exceeds maximum claims count"),
            "Error should mention claims count limit")
    }
}

