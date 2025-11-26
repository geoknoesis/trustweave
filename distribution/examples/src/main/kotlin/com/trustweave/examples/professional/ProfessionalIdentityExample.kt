package com.trustweave.examples.professional

import com.trustweave.trust.dsl.*
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.presentation.PresentationService
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.wallet.CredentialOrganization
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

fun main() = runBlocking {
    println("=== Professional Identity Wallet Scenario ===\n")
    
    // Step 1: Configure Trust Layer with all features
    println("Step 1: Setting up services...")
    // Create KMS first so we can use it for key generation and signing
    val kms = InMemoryKeyManagementService()
    val kmsRef = kms // Capture for closure
    val presentationService = PresentationService(
        proofGenerator = Ed25519ProofGenerator(
            signer = { data, keyId -> kmsRef.sign(keyId, data) }
        )
    )
    
    val trustWeave = trustWeave {
        keys {
            custom(kmsRef)
            // Provide signer function directly to avoid reflection issues
            signer { data, keyId ->
                kmsRef.sign(keyId, data)
            }
        }
        
        did {
            method(DidMethods.KEY) {
                algorithm(KeyAlgorithms.ED25519)
            }
        }
        
        credentials {
            defaultProofType(ProofTypes.ED25519)
        }
        
        revocation {
            provider("inMemory")
        }
        
        schemas {
            autoValidate(false)
            defaultFormat(SchemaFormat.JSON_SCHEMA)
        }
        
        // Configure trust registry
        trust {
            provider("inMemory")
        }
    }
    
    // Create professional DID using new DSL
    val professionalDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Professional DID: $professionalDid")
    
    // Register schemas for credential validation
    println("\nStep 1.5: Registering credential schemas...")
    trustLayer.registerSchema {
        id("https://example.com/schemas/education")
        type(SchemaValidatorTypes.JSON_SCHEMA)
        jsonSchema {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("degree", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("type", buildJsonObject { put("type", "string") })
                        put("field", buildJsonObject { put("type", "string") })
                        put("institution", buildJsonObject { put("type", "string") })
                        put("year", buildJsonObject { put("type", "string") })
                    })
                })
            })
        }
    }
    println("✓ Education schema registered")
    
    trustLayer.registerSchema {
        id("https://example.com/schemas/certification")
        type(SchemaValidatorTypes.JSON_SCHEMA)
        jsonSchema {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("certification", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("name", buildJsonObject { put("type", "string") })
                        put("issuer", buildJsonObject { put("type", "string") })
                        put("issueDate", buildJsonObject { put("type", "string") })
                        put("expirationDate", buildJsonObject { put("type", "string") })
                    })
                })
            })
        }
    }
    println("✓ Certification schema registered")
    
    // Step 2: Create professional wallet using DSL
    println("\nStep 2: Creating professional wallet...")
    val wallet = trustLayer.wallet {
        id("professional-wallet-${professionalDid.substringAfterLast(":")}")
        holder(professionalDid)
        enableOrganization()
        enablePresentation()
    }
    println("Wallet created: ${wallet.walletId}")
    
    // Step 3: Store education credentials using DSL
    println("\nStep 3: Storing education credentials...")
    // Generate keys for issuers using the same KMS
    val universityKey = kms.generateKey("Ed25519")
    // Verify key exists
    kms.getPublicKey(universityKey.id)
    
    // Issue credentials using new DSL with revocation support
    val bachelorDegree = trustLayer.issue {
        credential {
            id("https://example.edu/credentials/bachelor-${professionalDid.substringAfterLast(":")}")
            type(CredentialTypes.EDUCATION, "BachelorDegreeCredential")
            issuer("did:key:university")
            subject {
                id(professionalDid)
                "degree" {
                    "type" to "Bachelor"
                    "field" to "Computer Science"
                    "institution" to "Tech University"
                    "year" to "2018"
                }
            }
            issued(Instant.now())
        }
        by(issuerDid = "did:key:university", keyId = universityKey.id)
    }
    val bachelorId = bachelorDegree.storeIn(wallet).credentialId
    
    val masterDegree = trustLayer.issue {
        credential {
            id("https://example.edu/credentials/master-${professionalDid.substringAfterLast(":")}")
            type(CredentialTypes.EDUCATION, "MasterDegreeCredential")
            issuer("did:key:university")
            subject {
                id(professionalDid)
                "degree" {
                    "type" to "Master"
                    "field" to "Software Engineering"
                    "institution" to "Tech University"
                    "year" to "2020"
                }
            }
            issued(Instant.now())
        }
        by(issuerDid = "did:key:university", keyId = universityKey.id)
    }
    val masterId = masterDegree.storeIn(wallet).credentialId
    
    println("Stored ${wallet.list().size} education credentials")
    
    // Step 4: Store work experience credentials using DSL
    println("\nStep 4: Storing work experience credentials...")
    val job1 = createEmploymentCredential(
        issuerDid = "did:key:company1",
        holderDid = professionalDid,
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
        holderDid = professionalDid,
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
    
    // Step 5: Store certifications using DSL
    println("\nStep 5: Storing certifications...")
    val awsCert = createCertificationCredential(
        issuerDid = "did:key:aws",
        holderDid = professionalDid,
        certificationName = "AWS Certified Solutions Architect",
        issuer = "Amazon Web Services",
        issueDate = "2021-03-15",
        expirationDate = "2024-03-15",
        credentialId = "AWS-12345"
    )
    val awsCertId = wallet.store(awsCert)
    
    val kubernetesCert = createCertificationCredential(
        issuerDid = "did:key:cncf",
        holderDid = professionalDid,
        certificationName = "Certified Kubernetes Administrator",
        issuer = "Cloud Native Computing Foundation",
        issueDate = "2022-06-20",
        expirationDate = "2025-06-20",
        credentialId = "CKA-67890"
    )
    val k8sCertId = wallet.store(kubernetesCert)
    
    println("Stored ${wallet.list().size} total credentials")
    
    // Step 5.5: Verify credentials against schemas
    println("\nStep 5.5: Validating credentials against schemas...")
    val awsCertStored = wallet.get(awsCertId)
    if (awsCertStored != null) {
        try {
            val schemaResult = awsCertStored.validateSchema(trustLayer.dsl(), "https://example.com/schemas/certification")
            println("AWS cert schema validation: ${if (schemaResult.valid) "✓ Valid" else "✗ Invalid"}")
        } catch (e: Exception) {
            println("Schema validation skipped (validator not registered)")
        }
    }
    
    // Step 6: Organize credentials using new DSL
    println("\nStep 6: Organizing credentials...")
    var certificationsCollectionId: String? = null
    if (wallet is CredentialOrganization) {
        val result = wallet.organize {
            collection("Education", "Academic degrees and certificates") {
                add(bachelorId, masterId)
                tag(bachelorId, "education", "degree", "bachelor", "computer-science")
                tag(masterId, "education", "degree", "master", "software-engineering")
            }
            
            collection("Work Experience", "Employment history and achievements") {
                add(job1Id, job2Id)
                tag(job1Id, "work", "employment", "software-engineer", "completed")
                tag(job2Id, "work", "employment", "senior-engineer", "current")
            }
            
            collection("Certifications", "Professional licenses and certifications") {
                add(awsCertId, k8sCertId)
                tag(awsCertId, "certification", "cloud", "aws", "active")
                tag(k8sCertId, "certification", "kubernetes", "cncf", "active")
            }
        }
        println("Organized credentials into ${result.collectionsCreated} collections")
        
        // Get collection ID for querying
        certificationsCollectionId = wallet.listCollections()
            .find { it.name == "Certifications" }?.id
    }
    
    // Step 7: Query credentials using enhanced query DSL
    println("\nStep 7: Querying credentials...")
    
    // Find all active certifications using query
    val activeCerts = wallet.query {
        type("CertificationCredential")
        notExpired()
        valid()
        tag("active")
    }
    println("Active certifications: ${activeCerts.size}")
    
    // Find cloud-related credentials by tag and collection
    if (certificationsCollectionId != null) {
        val cloudCredentials = wallet.query {
            tag("cloud")
            collection(certificationsCollectionId)
        }
        println("Cloud-related credentials: ${cloudCredentials.size}")
    }
    
    // Step 8: Create targeted presentations using wallet presentation DSL
    println("\nStep 8: Creating targeted presentations...")
    
    // Generate a key for the professional/holder to sign presentations
    val professionalKey = kms.generateKey("Ed25519", mapOf("keyId" to "professional-key"))
    
    // Presentation for job application using wallet presentation DSL
    val jobApplicationCredentials = listOf(masterId, job1Id, job2Id, awsCertId)
        .mapNotNull { wallet.get(it) }
    val jobApplicationPresentation = presentation(presentationService) {
        credentials(jobApplicationCredentials)
        holder(professionalDid)
        challenge("job-application-${Instant.now().toEpochMilli()}")
        proofType(ProofTypes.ED25519)
        keyId(professionalKey.id)
        selectiveDisclosure {
            reveal(
                "degree.field",
                "degree.institution",
                "degree.year",
                "employment.company",
                "employment.role",
                "employment.startDate",
                "certification.name",
                "certification.issuer"
            )
        }
    }
    println("Job application presentation created with ${jobApplicationPresentation.verifiableCredential.size} credentials")
    
    // Presentation for professional profile using query-based presentation
    val profileCredentials = wallet.query {
        types("MasterDegreeCredential", "EmploymentCredential", "CertificationCredential")
        valid()
    }
    val profilePresentation = presentation(presentationService) {
        credentials(profileCredentials)
        holder(professionalDid)
        proofType(ProofTypes.ED25519)
        keyId(professionalKey.id)
    }
    println("Professional profile presentation created with ${profilePresentation.verifiableCredential.size} credentials")
    
    // Step 8.5: Demonstrate key rotation
    println("\nStep 8.5: Demonstrating key rotation...")
    try {
        trustWeave.rotateKey {
            did(professionalDid)
            algorithm(KeyAlgorithms.ED25519)
        }
        println("✓ Key rotated successfully")
    } catch (e: Exception) {
        println("Key rotation skipped (${e.message})")
    }
    
    // Step 8.6: Demonstrate DID document updates
    println("\nStep 8.6: Demonstrating DID document updates...")
    try {
        trustWeave.updateDid {
            did(professionalDid)
            method(DidMethods.KEY)
            addService {
                id("$professionalDid#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://professional.example.com")
            }
            // Add capability invocation for signing documents
            addCapabilityInvocation("$professionalDid#key-1")
            // Add capability delegation for delegating to assistants
            addCapabilityDelegation("$professionalDid#key-2")
            // Set JSON-LD context
            context("https://www.w3.org/ns/did/v1", "https://example.com/context/v1")
        }
        println("✓ DID document updated with service endpoint, capability relationships, and context")
    } catch (e: Exception) {
        println("DID document update skipped (${e.message})")
    }
    
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
    return credential {
        id("https://example.edu/credentials/${degreeType.lowercase()}-${holderDid.substringAfterLast(":")}")
        type("EducationCredential", "${degreeType}DegreeCredential")
        issuer(issuerDid)
        subject {
            id(holderDid)
            "degree" {
                "type" to degreeType
                "field" to field
                "institution" to institution
                "year" to year
            }
        }
        issued(Instant.now())
        // Education credentials typically don't expire
    }
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
    return credential {
        id("https://example.com/employment/${company.lowercase()}-${holderDid.substringAfterLast(":")}")
        type("EmploymentCredential")
        issuer(issuerDid)
        subject {
            id(holderDid)
            "employment" {
                "company" to company
                "role" to role
                "startDate" to startDate
                if (endDate != null) {
                    "endDate" to endDate
                } else {
                    "current" to true
                }
                "achievements" to achievements
            }
        }
        issued(Instant.now())
    }
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
    // Parse dates - handle both ISO format with time and date-only format
    val parsedIssueDate = if (issueDate.contains("T")) {
        Instant.parse(issueDate)
    } else {
        Instant.parse("${issueDate}T00:00:00Z")
    }
    
    val parsedExpirationDate = if (expirationDate.contains("T")) {
        Instant.parse(expirationDate)
    } else {
        Instant.parse("${expirationDate}T00:00:00Z")
    }
    
    return credential {
        id("https://example.com/certifications/$credentialId")
        type("CertificationCredential")
        issuer(issuerDid)
        subject {
            id(holderDid)
            "certification" {
                "name" to certificationName
                "issuer" to issuer
                "issueDate" to issueDate
                "expirationDate" to expirationDate
                "credentialId" to credentialId
            }
        }
        issued(parsedIssueDate)
        expires(parsedExpirationDate)
    }
}
