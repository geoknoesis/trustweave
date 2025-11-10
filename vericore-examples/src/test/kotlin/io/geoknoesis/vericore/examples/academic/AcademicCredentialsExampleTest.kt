package io.geoknoesis.vericore.examples.academic

import io.geoknoesis.vericore.credential.CredentialIssuanceOptions
import io.geoknoesis.vericore.credential.CredentialVerificationOptions
import io.geoknoesis.vericore.credential.PresentationOptions
import io.geoknoesis.vericore.credential.issuer.CredentialIssuer
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import io.geoknoesis.vericore.credential.verifier.CredentialVerifier
import io.geoknoesis.vericore.credential.wallet.CredentialQueryBuilder
import io.geoknoesis.vericore.credential.did.CredentialDidResolver
import io.geoknoesis.vericore.anchor.BlockchainRegistry
import io.geoknoesis.vericore.did.DidRegistry
import io.geoknoesis.vericore.did.toCredentialDidResolution
import io.geoknoesis.vericore.testkit.credential.InMemoryWallet
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for Academic Credentials scenario.
 * 
 * Verifies that the academic credentials workflow behaves correctly:
 * - DID creation for university and student
 * - Credential issuance with proof
 * - Credential storage in wallet
 * - Credential organization (collections, tags)
 * - Credential querying
 * - Presentation creation
 * - Credential verification
 */
class AcademicCredentialsExampleTest {

    @Test
    fun `test main function executes successfully`() = runBlocking {
        // Capture output to verify execution
        val output = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        try {
            System.setOut(java.io.PrintStream(output))
            
            // Execute main function
            main()
            
            // Verify output contains expected content
            val outputString = output.toString()
            assertTrue(outputString.contains("Academic Credentials Scenario"), "Should print scenario title")
            assertTrue(outputString.contains("University DID:"), "Should print university DID")
            assertTrue(outputString.contains("Student DID:"), "Should print student DID")
            assertTrue(outputString.contains("Wallet created"), "Should print wallet creation")
            assertTrue(outputString.contains("Credential issued"), "Should print credential issuance")
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `test DID creation`() = runBlocking {
        // Setup
        ProofGeneratorRegistry.clear()
        DidRegistry.clear()
        val universityKms = InMemoryKeyManagementService()
        val studentKms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(universityKms)
        DidRegistry.register(didMethod)
        
        val universityDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val studentDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        // Verify DIDs are created
        assertNotNull(universityDid.id)
        assertTrue(universityDid.id.startsWith("did:key:"))
        
        assertNotNull(studentDid.id)
        assertTrue(studentDid.id.startsWith("did:key:"))
        
        assertNotEquals(universityDid.id, studentDid.id)
    }

    @Test
    fun `test wallet creation`() = runBlocking {
        // Setup
        DidRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        val studentDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val studentWallet = InMemoryWallet(
            walletDid = studentDid.id,
            holderDid = studentDid.id
        )
        
        // Verify wallet is created with correct DIDs
        assertNotNull(studentWallet.walletId)
        assertEquals(studentDid.id, studentWallet.walletDid)
        assertEquals(studentDid.id, studentWallet.holderDid)
    }

    @Test
    fun `test credential issuance`() = runBlocking {
        // Setup
        ProofGeneratorRegistry.clear()
        DidRegistry.clear()
        val universityKms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(universityKms)
        DidRegistry.register(didMethod)
        
        val universityDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val studentDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        // Create degree credential
        val degreeCredential = createDegreeCredential(
            issuerDid = universityDid.id,
            studentDid = studentDid.id,
            degreeName = "Bachelor of Science in Computer Science",
            universityName = "Example University",
            graduationDate = "2023-05-15",
            gpa = "3.8"
        )

        // Issue credential
        val issuerKey = universityKms.generateKey("Ed25519")
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> universityKms.sign(issuerKey.id, data) },
            getPublicKeyId = { issuerKey.id }
        )
        ProofGeneratorRegistry.register(proofGenerator)

        val credentialIssuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> DidRegistry.resolve(did)?.document != null }
        )

        val issuedCredential = credentialIssuer.issue(
            credential = degreeCredential,
            issuerDid = universityDid.id,
            keyId = issuerKey.id,
            options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        )

        // Verify credential has proof
        assertNotNull(issuedCredential.proof)
        assertEquals(universityDid.id, issuedCredential.issuer)
        assertTrue(issuedCredential.type.contains("VerifiableCredential"))
        assertTrue(issuedCredential.type.contains("DegreeCredential"))
    }

    @Test
    fun `test credential storage`() = runBlocking {
        // Setup
        ProofGeneratorRegistry.clear()
        DidRegistry.clear()
        val universityKms = InMemoryKeyManagementService()
        val studentKms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(universityKms)
        DidRegistry.register(didMethod)
        
        val universityDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val studentDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val studentWallet = InMemoryWallet(
            walletDid = studentDid.id,
            holderDid = studentDid.id
        )
        
        // Create and issue credential
        val degreeCredential = createDegreeCredential(
            issuerDid = universityDid.id,
            studentDid = studentDid.id,
            degreeName = "Bachelor of Science",
            universityName = "Test University",
            graduationDate = "2023-05-15",
            gpa = "3.8"
        )

        val issuerKey = universityKms.generateKey("Ed25519")
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> universityKms.sign(issuerKey.id, data) },
            getPublicKeyId = { issuerKey.id }
        )
        ProofGeneratorRegistry.register(proofGenerator)

        val credentialIssuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> DidRegistry.resolve(did)?.document != null }
        )

        val issuedCredential = credentialIssuer.issue(
            credential = degreeCredential,
            issuerDid = universityDid.id,
            keyId = issuerKey.id,
            options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        )

        // Store credential
        val credentialId = studentWallet.store(issuedCredential)
        assertNotNull(credentialId)

        // Verify credential can be retrieved
        val retrieved = studentWallet.get(credentialId)
        assertNotNull(retrieved)
        assertEquals(issuedCredential.id, retrieved?.id)
    }

    @Test
    fun `test credential organization`() = runBlocking {
        // Setup
        ProofGeneratorRegistry.clear()
        DidRegistry.clear()
        val universityKms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(universityKms)
        DidRegistry.register(didMethod)
        
        val universityDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val studentDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val studentWallet = InMemoryWallet(
            walletDid = studentDid.id,
            holderDid = studentDid.id
        )
        
        // Create and store credential
        val degreeCredential = createDegreeCredential(
            issuerDid = universityDid.id,
            studentDid = studentDid.id,
            degreeName = "Bachelor of Science",
            universityName = "Test University",
            graduationDate = "2023-05-15",
            gpa = "3.8"
        )

        val issuerKey = universityKms.generateKey("Ed25519")
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> universityKms.sign(issuerKey.id, data) },
            getPublicKeyId = { issuerKey.id }
        )
        ProofGeneratorRegistry.register(proofGenerator)

        val credentialIssuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> DidRegistry.resolve(did)?.document != null }
        )

        val issuedCredential = credentialIssuer.issue(
            credential = degreeCredential,
            issuerDid = universityDid.id,
            keyId = issuerKey.id,
            options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        )

        val credentialId = studentWallet.store(issuedCredential)

        // Create collection
        val collectionId = studentWallet.createCollection(
            name = "Education Credentials",
            description = "Academic degrees and certificates"
        )
        assertNotNull(collectionId)

        // Add credential to collection
        studentWallet.addToCollection(credentialId, collectionId)

        // Add tags
        studentWallet.tagCredential(credentialId, setOf("degree", "bachelor", "verified"))

        // Verify collection contains credential
        val collection = studentWallet.getCollection(collectionId)
        assertNotNull(collection)
        assertTrue(collection?.credentialCount ?: 0 > 0, "Collection should contain credentials")

        // Verify tags
        val metadata = studentWallet.getMetadata(credentialId)
        assertTrue(metadata?.tags?.contains("degree") == true)
        assertTrue(metadata?.tags?.contains("bachelor") == true)
    }

    @Test
    fun `test credential querying`() = runBlocking {
        // Setup
        ProofGeneratorRegistry.clear()
        DidRegistry.clear()
        val universityKms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(universityKms)
        DidRegistry.register(didMethod)
        
        val universityDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val studentDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val studentWallet = InMemoryWallet(
            walletDid = studentDid.id,
            holderDid = studentDid.id
        )
        
        // Create and store credential
        val degreeCredential = createDegreeCredential(
            issuerDid = universityDid.id,
            studentDid = studentDid.id,
            degreeName = "Bachelor of Science",
            universityName = "Test University",
            graduationDate = "2023-05-15",
            gpa = "3.8"
        )

        val issuerKey = universityKms.generateKey("Ed25519")
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> universityKms.sign(issuerKey.id, data) },
            getPublicKeyId = { issuerKey.id }
        )
        ProofGeneratorRegistry.register(proofGenerator)

        val credentialIssuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> DidRegistry.resolve(did)?.document != null }
        )

        val issuedCredential = credentialIssuer.issue(
            credential = degreeCredential,
            issuerDid = universityDid.id,
            keyId = issuerKey.id,
            options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        )

        val credentialId = studentWallet.store(issuedCredential)

        // Query by type
        val degrees = studentWallet.query {
            byType("DegreeCredential")
            valid()
        }
        assertEquals(1, degrees.size)
        assertEquals(credentialId, degrees.first().id)
    }

    @Test
    fun `test presentation creation`() = runBlocking {
        // Setup
        ProofGeneratorRegistry.clear()
        DidRegistry.clear()
        val universityKms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(universityKms)
        DidRegistry.register(didMethod)
        
        val universityDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val studentDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val studentWallet = InMemoryWallet(
            walletDid = studentDid.id,
            holderDid = studentDid.id
        )
        
        // Create and store credential
        val degreeCredential = createDegreeCredential(
            issuerDid = universityDid.id,
            studentDid = studentDid.id,
            degreeName = "Bachelor of Science",
            universityName = "Test University",
            graduationDate = "2023-05-15",
            gpa = "3.8"
        )

        val issuerKey = universityKms.generateKey("Ed25519")
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> universityKms.sign(issuerKey.id, data) },
            getPublicKeyId = { issuerKey.id }
        )
        ProofGeneratorRegistry.register(proofGenerator)

        val credentialIssuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> DidRegistry.resolve(did)?.document != null }
        )

        val issuedCredential = credentialIssuer.issue(
            credential = degreeCredential,
            issuerDid = universityDid.id,
            keyId = issuerKey.id,
            options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        )

        val credentialId = studentWallet.store(issuedCredential)

        // Create presentation
        val presentation = studentWallet.createPresentation(
            credentialIds = listOf(credentialId),
            holderDid = studentDid.id,
            options = PresentationOptions(
                holderDid = studentDid.id,
                proofType = "Ed25519Signature2020",
                challenge = "job-application-12345"
            )
        )

        assertNotNull(presentation)
        assertEquals(studentDid.id, presentation.holder)
        assertEquals(1, presentation.verifiableCredential.size)
        assertEquals("job-application-12345", presentation.challenge)
    }

    @Test
    fun `test credential verification`() = runBlocking {
        // Setup
        ProofGeneratorRegistry.clear()
        DidRegistry.clear()
        val universityKms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(universityKms)
        DidRegistry.register(didMethod)
        
        val universityDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val studentDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        // Create and issue credential
        val degreeCredential = createDegreeCredential(
            issuerDid = universityDid.id,
            studentDid = studentDid.id,
            degreeName = "Bachelor of Science",
            universityName = "Test University",
            graduationDate = "2023-05-15",
            gpa = "3.8"
        )

        val issuerKey = universityKms.generateKey("Ed25519")
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> universityKms.sign(issuerKey.id, data) },
            getPublicKeyId = { issuerKey.id }
        )
        ProofGeneratorRegistry.register(proofGenerator)

        val credentialIssuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> DidRegistry.resolve(did)?.document != null }
        )

        val issuedCredential = credentialIssuer.issue(
            credential = degreeCredential,
            issuerDid = universityDid.id,
            keyId = issuerKey.id,
            options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        )

        // Verify credential
        val credentialVerifier = CredentialVerifier(
            CredentialDidResolver { did ->
                DidRegistry.resolve(did)?.toCredentialDidResolution()
            }
        )
        
        val verificationResult = credentialVerifier.verify(
            credential = issuedCredential,
            options = CredentialVerificationOptions(
                checkRevocation = true,
                checkExpiration = true
            )
        )

        assertTrue(verificationResult.valid)
        assertTrue(verificationResult.proofValid)
        assertTrue(verificationResult.issuerValid)
        assertTrue(verificationResult.notExpired)
        assertTrue(verificationResult.notRevoked)
    }

    @Test
    fun `test wallet statistics`() = runBlocking {
        // Setup
        ProofGeneratorRegistry.clear()
        DidRegistry.clear()
        val universityKms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(universityKms)
        DidRegistry.register(didMethod)
        
        val universityDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val studentDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val studentWallet = InMemoryWallet(
            walletDid = studentDid.id,
            holderDid = studentDid.id
        )
        
        // Initially empty
        var stats = studentWallet.getStatistics()
        assertEquals(0, stats.totalCredentials)

        // Create and store credential
        val degreeCredential = createDegreeCredential(
            issuerDid = universityDid.id,
            studentDid = studentDid.id,
            degreeName = "Bachelor of Science",
            universityName = "Test University",
            graduationDate = "2023-05-15",
            gpa = "3.8"
        )

        val issuerKey = universityKms.generateKey("Ed25519")
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> universityKms.sign(issuerKey.id, data) },
            getPublicKeyId = { issuerKey.id }
        )
        ProofGeneratorRegistry.register(proofGenerator)

        val credentialIssuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> DidRegistry.resolve(did)?.document != null }
        )

        val issuedCredential = credentialIssuer.issue(
            credential = degreeCredential,
            issuerDid = universityDid.id,
            keyId = issuerKey.id,
            options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        )

        studentWallet.store(issuedCredential)

        // Verify statistics
        stats = studentWallet.getStatistics()
        assertEquals(1, stats.totalCredentials)
        assertEquals(1, stats.validCredentials)
    }
}

