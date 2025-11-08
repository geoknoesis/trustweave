package io.geoknoesis.vericore.examples.professional

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import io.geoknoesis.vericore.credential.PresentationOptions
import io.geoknoesis.vericore.testkit.credential.InMemoryWallet
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import io.geoknoesis.vericore.did.DidRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import java.time.Instant

fun main() = runBlocking {
    println("=== Professional Identity Wallet Scenario ===\n")
    
    // Step 1: Setup
    println("Step 1: Setting up services...")
    val kms = InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    DidRegistry.register(didMethod)
    
    val professionalDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
    println("Professional DID: ${professionalDid.id}")
    
    // Step 2: Create professional wallet
    println("\nStep 2: Creating professional wallet...")
    val wallet = InMemoryWallet(
        walletDid = professionalDid.id,
        holderDid = professionalDid.id
    )
    println("Wallet created: ${wallet.walletId}")
    
    // Step 3: Store education credentials
    println("\nStep 3: Storing education credentials...")
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
    
    println("Stored ${wallet.list().size} education credentials")
    
    // Step 4: Store work experience credentials
    println("\nStep 4: Storing work experience credentials...")
    val job1 = createEmploymentCredential(
        issuerDid = "did:key:company1",
        holderDid = professionalDid.id,
        company = "Tech Corp",
        role = "Software Engineer",
        startDate = "2020-06-01",
        endDate = "2022-12-31",
        achievements = listOf(
            "Led development of microservices architecture",
            "Mentored 3 junior developers",
            "Increased system performance by 40%"
        )
    )
    val job1Id = wallet.store(job1)
    
    val job2 = createEmploymentCredential(
        issuerDid = "did:key:company2",
        holderDid = professionalDid.id,
        company = "Innovation Labs",
        role = "Senior Software Engineer",
        startDate = "2023-01-01",
        endDate = null, // Current position
        achievements = listOf(
            "Architected cloud-native platform",
            "Reduced infrastructure costs by 30%"
        )
    )
    val job2Id = wallet.store(job2)
    
    println("Stored ${wallet.list().size} total credentials")
    
    // Step 5: Store certifications
    println("\nStep 5: Storing certifications...")
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
    
    val kubernetesCert = createCertificationCredential(
        issuerDid = "did:key:cncf",
        holderDid = professionalDid.id,
        certificationName = "Certified Kubernetes Administrator",
        issuer = "Cloud Native Computing Foundation",
        issueDate = "2022-06-20",
        expirationDate = "2025-06-20",
        credentialId = "CKA-67890"
    )
    val k8sCertId = wallet.store(kubernetesCert)
    
    println("Stored ${wallet.list().size} total credentials")
    
    // Step 6: Organize credentials
    println("\nStep 6: Organizing credentials...")
    
    // Create collections
    val educationCollection = wallet.createCollection(
        name = "Education",
        description = "Academic degrees and certificates"
    )
    val workCollection = wallet.createCollection(
        name = "Work Experience",
        description = "Employment history and achievements"
    )
    val certificationsCollection = wallet.createCollection(
        name = "Certifications",
        description = "Professional licenses and certifications"
    )
    
    // Add credentials to collections
    wallet.addToCollection(bachelorId, educationCollection)
    wallet.addToCollection(masterId, educationCollection)
    wallet.addToCollection(job1Id, workCollection)
    wallet.addToCollection(job2Id, workCollection)
    wallet.addToCollection(awsCertId, certificationsCollection)
    wallet.addToCollection(k8sCertId, certificationsCollection)
    
    // Add tags
    wallet.tagCredential(bachelorId, setOf("education", "degree", "bachelor", "computer-science"))
    wallet.tagCredential(masterId, setOf("education", "degree", "master", "software-engineering"))
    wallet.tagCredential(job1Id, setOf("work", "employment", "software-engineer", "completed"))
    wallet.tagCredential(job2Id, setOf("work", "employment", "senior-engineer", "current"))
    wallet.tagCredential(awsCertId, setOf("certification", "cloud", "aws", "active"))
    wallet.tagCredential(k8sCertId, setOf("certification", "kubernetes", "cncf", "active"))
    
    println("Created ${wallet.listCollections().size} collections")
    println("Total tags: ${wallet.getAllTags().size}")
    
    // Step 7: Query credentials
    println("\nStep 7: Querying credentials...")
    
    // Find all active certifications
    val activeCerts = wallet.query {
        byType("CertificationCredential")
        notExpired()
        valid()
    }
    println("Active certifications: ${activeCerts.size}")
    
    // Find credentials by tag
    val cloudCredentials = wallet.findByTag("cloud")
    println("Cloud-related credentials: ${cloudCredentials.size}")
    
    // Step 8: Create targeted presentations
    println("\nStep 8: Creating targeted presentations...")
    
    // Presentation for job application (selective disclosure)
    val jobApplicationPresentation = wallet.createSelectiveDisclosure(
        credentialIds = listOf(masterId, job1Id, job2Id, awsCertId),
        disclosedFields = listOf(
            "degree.field",
            "degree.institution",
            "degree.year",
            "employment.company",
            "employment.role",
            "employment.startDate",
            "certification.name",
            "certification.issuer"
        ),
        holderDid = professionalDid.id,
        options = PresentationOptions(
            holderDid = professionalDid.id,
            proofType = "Ed25519Signature2020",
            challenge = "job-application-${Instant.now().toEpochMilli()}"
        )
    )
    println("Job application presentation created with ${jobApplicationPresentation.verifiableCredential.size} credentials")
    
    // Presentation for professional profile (public)
    val profilePresentation = wallet.createPresentation(
        credentialIds = listOf(masterId, job2Id, awsCertId, k8sCertId),
        holderDid = professionalDid.id,
        options = PresentationOptions(
            holderDid = professionalDid.id,
            proofType = "Ed25519Signature2020"
        )
    )
    println("Professional profile presentation created")
    
    // Step 9: Wallet statistics
    println("\nStep 9: Wallet statistics...")
    val stats = wallet.getStatistics()
    println("""
        Total credentials: ${stats.totalCredentials}
        Valid credentials: ${stats.validCredentials}
        Expired credentials: ${stats.expiredCredentials}
        Collections: ${stats.collectionsCount}
        Tags: ${stats.tagsCount}
    """.trimIndent())
    
    println("\n=== Scenario Complete ===")
}

fun createEducationCredential(
    issuerDid: String,
    holderDid: String,
    degreeType: String,
    field: String,
    institution: String,
    year: String
): VerifiableCredential {
    return VerifiableCredential(
        id = "https://example.edu/credentials/${degreeType.lowercase()}-${holderDid.substringAfterLast(":")}",
        type = listOf("VerifiableCredential", "EducationCredential", "${degreeType}DegreeCredential"),
        issuer = issuerDid,
        credentialSubject = buildJsonObject {
            put("id", holderDid)
            put("degree", buildJsonObject {
                put("type", degreeType)
                put("field", field)
                put("institution", institution)
                put("year", year)
            })
        },
        issuanceDate = Instant.now().toString(),
        expirationDate = null // Education credentials typically don't expire
    )
}

fun createEmploymentCredential(
    issuerDid: String,
    holderDid: String,
    company: String,
    role: String,
    startDate: String,
    endDate: String?,
    achievements: List<String>
): VerifiableCredential {
    return VerifiableCredential(
        id = "https://example.com/employment/${company.lowercase()}-${holderDid.substringAfterLast(":")}",
        type = listOf("VerifiableCredential", "EmploymentCredential"),
        issuer = issuerDid,
        credentialSubject = buildJsonObject {
            put("id", holderDid)
            put("employment", buildJsonObject {
                put("company", company)
                put("role", role)
                put("startDate", startDate)
                if (endDate != null) {
                    put("endDate", endDate)
                } else {
                    put("current", true)
                }
                put("achievements", buildJsonArray {
                    achievements.forEach { add(it) }
                })
            })
        },
        issuanceDate = Instant.now().toString(),
        expirationDate = null
    )
}

fun createCertificationCredential(
    issuerDid: String,
    holderDid: String,
    certificationName: String,
    issuer: String,
    issueDate: String,
    expirationDate: String,
    credentialId: String
): VerifiableCredential {
    return VerifiableCredential(
        id = "https://example.com/certifications/$credentialId",
        type = listOf("VerifiableCredential", "CertificationCredential"),
        issuer = issuerDid,
        credentialSubject = buildJsonObject {
            put("id", holderDid)
            put("certification", buildJsonObject {
                put("name", certificationName)
                put("issuer", issuer)
                put("issueDate", issueDate)
                put("expirationDate", expirationDate)
                put("credentialId", credentialId)
            })
        },
        issuanceDate = issueDate,
        expirationDate = expirationDate
    )
}

