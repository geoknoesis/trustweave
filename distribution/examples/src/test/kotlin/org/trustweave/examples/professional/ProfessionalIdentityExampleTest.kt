package org.trustweave.examples.professional

import org.trustweave.credential.proof.ProofOptions
import org.trustweave.testkit.credential.InMemoryWallet
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.did.identifiers.Did
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
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val professionalDidDoc = didMethod.createDid()
        val professionalDid = professionalDidDoc.id

        val wallet = InMemoryWallet(
            walletDidObj = professionalDid,
            holderDidObj = professionalDid
        )

        // Store education credentials
        val bachelorDegree = createEducationCredential(
            issuerDid = Did("did:key:university"),
            holderDid = professionalDid,
            degreeType = "Bachelor",
            field = "Computer Science",
            institution = "Tech University",
            year = "2018"
        )
        val bachelorId = wallet.store(bachelorDegree)

        val masterDegree = createEducationCredential(
            issuerDid = Did("did:key:university"),
            holderDid = professionalDid,
            degreeType = "Master",
            field = "Software Engineering",
            institution = "Tech University",
            year = "2020"
        )
        val masterId = wallet.store(masterDegree)

        // Store employment credential
        val job1 = createEmploymentCredential(
            issuerDid = Did("did:key:company1"),
            holderDid = professionalDid,
            company = "Tech Corp",
            role = "Software Engineer",
            startDate = "2020-06-01",
            endDate = "2022-12-31",
            achievements = listOf("Led development")
        )
        val job1Id = wallet.store(job1)

        // Store certification
        val awsCert = createCertificationCredential(
            issuerDid = Did("did:key:aws"),
            holderDid = professionalDid,
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
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val professionalDidDoc = didMethod.createDid()
        val professionalDid = professionalDidDoc.id

        val wallet = InMemoryWallet(
            walletDidObj = professionalDid,
            holderDidObj = professionalDid
        )

        // Store credentials
        val bachelorDegree = createEducationCredential(
            issuerDid = Did("did:key:university"),
            holderDid = professionalDid,
            degreeType = "Bachelor",
            field = "Computer Science",
            institution = "Tech University",
            year = "2018"
        )
        val bachelorId = wallet.store(bachelorDegree)

        val job1 = createEmploymentCredential(
            issuerDid = Did("did:key:company1"),
            holderDid = professionalDid,
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
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val professionalDidDoc = didMethod.createDid()
        val professionalDid = professionalDidDoc.id

        val wallet = InMemoryWallet(
            walletDidObj = professionalDid,
            holderDidObj = professionalDid
        )

        // Store credential
        val bachelorDegree = createEducationCredential(
            issuerDid = Did("did:key:university"),
            holderDid = professionalDid,
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
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val professionalDidDoc = didMethod.createDid()
        val professionalDid = professionalDidDoc.id

        val wallet = InMemoryWallet(
            walletDidObj = professionalDid,
            holderDidObj = professionalDid
        )

        // Store multiple credential types
        val bachelorDegree = createEducationCredential(
            issuerDid = Did("did:key:university"),
            holderDid = professionalDid,
            degreeType = "Bachelor",
            field = "Computer Science",
            institution = "Tech University",
            year = "2018"
        )
        wallet.store(bachelorDegree)

        val awsCert = createCertificationCredential(
            issuerDid = Did("did:key:aws"),
            holderDid = professionalDid,
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
        assertTrue(certifications.first().type.any { it.value == "CertificationCredential" })
    }

    @Test
    fun `test selective disclosure presentation`() = runBlocking {
        // Setup
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val professionalDidDoc = didMethod.createDid()
        val professionalDid = professionalDidDoc.id

        val wallet = InMemoryWallet(
            walletDidObj = professionalDid,
            holderDidObj = professionalDid
        )

        // Store credentials
        val masterDegree = createEducationCredential(
            issuerDid = Did("did:key:university"),
            holderDid = professionalDid,
            degreeType = "Master",
            field = "Software Engineering",
            institution = "Tech University",
            year = "2020"
        )
        val masterId = wallet.store(masterDegree)

        val job1 = createEmploymentCredential(
            issuerDid = Did("did:key:company1"),
            holderDid = professionalDid,
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
            holderDid = professionalDid.value,
            options = org.trustweave.credential.proof.ProofOptions(
                challenge = "job-application-12345"
            )
        )

        assertNotNull(presentation)
        assertEquals(professionalDid.value, presentation.holder.value)
        assertEquals(2, presentation.verifiableCredential.size)
        assertEquals("job-application-12345", presentation.challenge)
    }

    @Test
    fun `test full presentation creation`() = runBlocking {
        // Setup
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val professionalDidDoc = didMethod.createDid()
        val professionalDid = professionalDidDoc.id

        val wallet = InMemoryWallet(
            walletDidObj = professionalDid,
            holderDidObj = professionalDid
        )

        // Store credentials
        val masterDegree = createEducationCredential(
            issuerDid = Did("did:key:university"),
            holderDid = professionalDid,
            degreeType = "Master",
            field = "Software Engineering",
            institution = "Tech University",
            year = "2020"
        )
        val masterId = wallet.store(masterDegree)

        val awsCert = createCertificationCredential(
            issuerDid = Did("did:key:aws"),
            holderDid = professionalDid,
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
            holderDid = professionalDid.value,
            options = org.trustweave.credential.proof.ProofOptions()
        )

        assertNotNull(presentation)
        assertEquals(professionalDid.value, presentation.holder.value)
        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test querying active certifications`() = runBlocking {
        // Setup
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val professionalDidDoc = didMethod.createDid()
        val professionalDid = professionalDidDoc.id

        val wallet = InMemoryWallet(
            walletDidObj = professionalDid,
            holderDidObj = professionalDid
        )

        // Use fixed dates that are clearly expired and active
        // Expired: January 1, 2021 (definitely in the past)
        val expiredIssueDate = "2020-01-01T00:00:00Z"
        val expiredExpirationDate = "2021-01-01T00:00:00Z"
        
        // Active: Use a date far in the future (2099) to ensure it's always active
        val activeIssueDate = "2024-01-01T00:00:00Z"
        val activeExpirationDate = "2099-12-31T23:59:59Z"

        // Store expired certification
        val expiredCert = createCertificationCredential(
            issuerDid = Did("did:key:aws"),
            holderDid = professionalDid,
            certificationName = "Expired Cert",
            issuer = "Test Issuer",
            issueDate = expiredIssueDate,
            expirationDate = expiredExpirationDate,
            credentialId = "EXPIRED-1"
        )
        wallet.store(expiredCert)

        // Store active certification
        val activeCert = createCertificationCredential(
            issuerDid = Did("did:key:aws"),
            holderDid = professionalDid,
            certificationName = "Active Cert",
            issuer = "Test Issuer",
            issueDate = activeIssueDate,
            expirationDate = activeExpirationDate,
            credentialId = "ACTIVE-1"
        )
        wallet.store(activeCert)

        // First, verify we can find credentials by type
        val allCerts = wallet.query {
            byType("CertificationCredential")
        }
        assertEquals(2, allCerts.size, "Should find 2 certifications")
        
        // Verify expirationDate is set on credentials
        val expiredStored = allCerts.find { it.id?.value?.contains("EXPIRED-1") == true }
        val activeStored = allCerts.find { it.id?.value?.contains("ACTIVE-1") == true }
        assertNotNull(expiredStored?.expirationDate, "Expired cert should have expirationDate")
        assertNotNull(activeStored?.expirationDate, "Active cert should have expirationDate")
        
        // Query active certifications
        val activeCerts = wallet.query {
            byType("CertificationCredential")
            notExpired()
        }
        assertEquals(1, activeCerts.size, "Should find exactly 1 active certification")
        assertTrue(activeCerts.first().id?.value?.contains("ACTIVE-1") == true, 
            "Active certification should have ACTIVE-1 in its ID")
    }
}
