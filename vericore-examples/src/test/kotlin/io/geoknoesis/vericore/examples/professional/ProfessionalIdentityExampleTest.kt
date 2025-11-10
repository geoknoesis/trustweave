package io.geoknoesis.vericore.examples.professional

import io.geoknoesis.vericore.credential.PresentationOptions
import io.geoknoesis.vericore.anchor.BlockchainRegistry
import io.geoknoesis.vericore.did.DidRegistry
import io.geoknoesis.vericore.testkit.credential.InMemoryWallet
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for Professional Identity scenario.
 * 
 * Verifies that the professional identity wallet workflow behaves correctly:
 * - Multiple credential types (education, employment, certifications)
 * - Credential organization (collections, tags)
 * - Credential querying by type and tags
 * - Selective disclosure presentations
 * - Full presentations
 */
class ProfessionalIdentityExampleTest {

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
            assertTrue(outputString.contains("Professional Identity Wallet Scenario"), "Should print scenario title")
            assertTrue(outputString.contains("Setting up services"), "Should print setup step")
            assertTrue(outputString.contains("Professional DID:"), "Should print professional DID")
            assertTrue(outputString.contains("Wallet created"), "Should print wallet creation")
            assertTrue(outputString.contains("Storing") || outputString.contains("credentials"), "Should print credential storage")
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `test storing multiple credential types`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        val professionalDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val wallet = InMemoryWallet(
            walletDid = professionalDid.id,
            holderDid = professionalDid.id
        )
        
        // Store education credentials
        val bachelorDegree = createEducationCredential(
            issuerDid = "did:key:university",
            holderDid = professionalDid.id,
            degreeType = "Bachelor",
            field = "Computer Science",
            institution = "Tech University",
            year = "2018"
        )
        val bachelorId = wallet.store(bachelorDegree)

        val masterDegree = createEducationCredential(
            issuerDid = "did:key:university",
            holderDid = professionalDid.id,
            degreeType = "Master",
            field = "Software Engineering",
            institution = "Tech University",
            year = "2020"
        )
        val masterId = wallet.store(masterDegree)

        // Store employment credential
        val job1 = createEmploymentCredential(
            issuerDid = "did:key:company1",
            holderDid = professionalDid.id,
            company = "Tech Corp",
            role = "Software Engineer",
            startDate = "2020-06-01",
            endDate = "2022-12-31",
            achievements = listOf("Led development")
        )
        val job1Id = wallet.store(job1)

        // Store certification
        val awsCert = createCertificationCredential(
            issuerDid = "did:key:aws",
            holderDid = professionalDid.id,
            certificationName = "AWS Certified Solutions Architect",
            issuer = "Amazon Web Services",
            issueDate = "2021-03-15",
            expirationDate = "2024-03-15",
            credentialId = "AWS-12345"
        )
        val awsCertId = wallet.store(awsCert)

        // Verify all credentials stored
        assertEquals(4, wallet.list().size)
        assertNotNull(wallet.get(bachelorId))
        assertNotNull(wallet.get(masterId))
        assertNotNull(wallet.get(job1Id))
        assertNotNull(wallet.get(awsCertId))
    }

    @Test
    fun `test credential organization with collections`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        val professionalDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val wallet = InMemoryWallet(
            walletDid = professionalDid.id,
            holderDid = professionalDid.id
        )
        
        // Store credentials
        val bachelorDegree = createEducationCredential(
            issuerDid = "did:key:university",
            holderDid = professionalDid.id,
            degreeType = "Bachelor",
            field = "Computer Science",
            institution = "Tech University",
            year = "2018"
        )
        val bachelorId = wallet.store(bachelorDegree)

        val job1 = createEmploymentCredential(
            issuerDid = "did:key:company1",
            holderDid = professionalDid.id,
            company = "Tech Corp",
            role = "Software Engineer",
            startDate = "2020-06-01",
            endDate = "2022-12-31",
            achievements = emptyList()
        )
        val job1Id = wallet.store(job1)

        // Create collections
        val educationCollection = wallet.createCollection(
            name = "Education",
            description = "Academic degrees"
        )
        val workCollection = wallet.createCollection(
            name = "Work Experience",
            description = "Employment history"
        )

        // Add credentials to collections
        wallet.addToCollection(bachelorId, educationCollection)
        wallet.addToCollection(job1Id, workCollection)

        // Verify collections
        val collections = wallet.listCollections()
        assertEquals(2, collections.size)
        
        val education = wallet.getCollection(educationCollection)
        assertNotNull(education)
        assertTrue(education?.credentialCount ?: 0 > 0, "Education collection should contain credentials")
    }

    @Test
    fun `test credential tagging`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        val professionalDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val wallet = InMemoryWallet(
            walletDid = professionalDid.id,
            holderDid = professionalDid.id
        )
        
        // Store credential
        val bachelorDegree = createEducationCredential(
            issuerDid = "did:key:university",
            holderDid = professionalDid.id,
            degreeType = "Bachelor",
            field = "Computer Science",
            institution = "Tech University",
            year = "2018"
        )
        val bachelorId = wallet.store(bachelorDegree)

        // Add tags
        wallet.tagCredential(bachelorId, setOf("education", "degree", "bachelor", "computer-science"))

        // Verify tags
        val metadata = wallet.getMetadata(bachelorId)
        assertNotNull(metadata)
        assertTrue(metadata?.tags?.contains("education") == true)
        assertTrue(metadata?.tags?.contains("degree") == true)
        assertTrue(metadata?.tags?.contains("bachelor") == true)

        // Find by tag
        val educationCreds = wallet.findByTag("education")
        assertEquals(1, educationCreds.size)
    }

    @Test
    fun `test credential querying by type`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        val professionalDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val wallet = InMemoryWallet(
            walletDid = professionalDid.id,
            holderDid = professionalDid.id
        )
        
        // Store multiple credential types
        val bachelorDegree = createEducationCredential(
            issuerDid = "did:key:university",
            holderDid = professionalDid.id,
            degreeType = "Bachelor",
            field = "Computer Science",
            institution = "Tech University",
            year = "2018"
        )
        wallet.store(bachelorDegree)

        val awsCert = createCertificationCredential(
            issuerDid = "did:key:aws",
            holderDid = professionalDid.id,
            certificationName = "AWS Certified",
            issuer = "AWS",
            issueDate = "2021-03-15",
            expirationDate = "2024-03-15",
            credentialId = "AWS-12345"
        )
        wallet.store(awsCert)

        // Query by type
        val certifications = wallet.query {
            byType("CertificationCredential")
        }
        assertEquals(1, certifications.size)
        assertTrue(certifications.first().type.contains("CertificationCredential"))
    }

    @Test
    fun `test selective disclosure presentation`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        val professionalDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val wallet = InMemoryWallet(
            walletDid = professionalDid.id,
            holderDid = professionalDid.id
        )
        
        // Store credentials
        val masterDegree = createEducationCredential(
            issuerDid = "did:key:university",
            holderDid = professionalDid.id,
            degreeType = "Master",
            field = "Software Engineering",
            institution = "Tech University",
            year = "2020"
        )
        val masterId = wallet.store(masterDegree)

        val job1 = createEmploymentCredential(
            issuerDid = "did:key:company1",
            holderDid = professionalDid.id,
            company = "Tech Corp",
            role = "Software Engineer",
            startDate = "2020-06-01",
            endDate = "2022-12-31",
            achievements = emptyList()
        )
        val job1Id = wallet.store(job1)

        // Create selective disclosure presentation
        val presentation = wallet.createSelectiveDisclosure(
            credentialIds = listOf(masterId, job1Id),
            disclosedFields = listOf(
                "degree.field",
                "degree.institution",
                "employment.company",
                "employment.role"
            ),
            holderDid = professionalDid.id,
            options = PresentationOptions(
                holderDid = professionalDid.id,
                proofType = "Ed25519Signature2020",
                challenge = "job-application-12345"
            )
        )

        assertNotNull(presentation)
        assertEquals(professionalDid.id, presentation.holder)
        assertEquals(2, presentation.verifiableCredential.size)
        assertEquals("job-application-12345", presentation.challenge)
    }

    @Test
    fun `test full presentation creation`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        val professionalDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val wallet = InMemoryWallet(
            walletDid = professionalDid.id,
            holderDid = professionalDid.id
        )
        
        // Store credentials
        val masterDegree = createEducationCredential(
            issuerDid = "did:key:university",
            holderDid = professionalDid.id,
            degreeType = "Master",
            field = "Software Engineering",
            institution = "Tech University",
            year = "2020"
        )
        val masterId = wallet.store(masterDegree)

        val awsCert = createCertificationCredential(
            issuerDid = "did:key:aws",
            holderDid = professionalDid.id,
            certificationName = "AWS Certified",
            issuer = "AWS",
            issueDate = "2021-03-15",
            expirationDate = "2024-03-15",
            credentialId = "AWS-12345"
        )
        val awsCertId = wallet.store(awsCert)

        // Create full presentation
        val presentation = wallet.createPresentation(
            credentialIds = listOf(masterId, awsCertId),
            holderDid = professionalDid.id,
            options = PresentationOptions(
                holderDid = professionalDid.id,
                proofType = "Ed25519Signature2020"
            )
        )

        assertNotNull(presentation)
        assertEquals(professionalDid.id, presentation.holder)
        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test querying active certifications`() = runBlocking {
        // Setup
        DidRegistry.clear()
        BlockchainRegistry.clear()
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        DidRegistry.register(didMethod)
        val professionalDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        
        val wallet = InMemoryWallet(
            walletDid = professionalDid.id,
            holderDid = professionalDid.id
        )
        
        // Store expired certification
        val expiredCert = createCertificationCredential(
            issuerDid = "did:key:aws",
            holderDid = professionalDid.id,
            certificationName = "Expired Cert",
            issuer = "Test Issuer",
            issueDate = "2020-01-01T00:00:00Z",
            expirationDate = "2021-01-01T00:00:00Z", // Expired
            credentialId = "EXPIRED-1"
        )
        wallet.store(expiredCert)

        // Store active certification
        val activeCert = createCertificationCredential(
            issuerDid = "did:key:aws",
            holderDid = professionalDid.id,
            certificationName = "Active Cert",
            issuer = "Test Issuer",
            issueDate = "2023-01-01T00:00:00Z",
            expirationDate = "2026-01-01T00:00:00Z", // Active
            credentialId = "ACTIVE-1"
        )
        wallet.store(activeCert)

        // Query active certifications
        val activeCerts = wallet.query {
            byType("CertificationCredential")
            notExpired()
        }
        assertEquals(1, activeCerts.size)
        assertTrue(activeCerts.first().id?.contains("ACTIVE-1") == true)
    }
}
