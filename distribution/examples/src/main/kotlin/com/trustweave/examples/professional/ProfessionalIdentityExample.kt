package com.trustweave.examples.professional

import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.types.ProofType
import com.trustweave.trust.dsl.credential.CredentialTypes
import com.trustweave.trust.types.CredentialType
import com.trustweave.trust.dsl.credential.SchemaValidatorTypes
import com.trustweave.trust.dsl.credential.ServiceTypes
import com.trustweave.trust.dsl.credential.credential
import com.trustweave.trust.dsl.credential.presentation
import com.trustweave.trust.dsl.wallet.organize
import com.trustweave.trust.dsl.wallet.query as dslQuery
import com.trustweave.trust.dsl.wallet.QueryBuilder
import com.trustweave.trust.dsl.registerSchema
import com.trustweave.trust.dsl.credential.schema
import com.trustweave.trust.dsl.storeIn
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.SchemaFormat
import com.trustweave.credential.presentation.PresentationService
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.wallet.CredentialOrganization
import com.trustweave.wallet.Wallet
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.testkit.services.TestkitWalletFactory
import com.trustweave.testkit.getOrFail
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
            signer = { data, keyId -> kmsRef.sign(com.trustweave.core.types.KeyId(keyId), data) }
        )
    )

    val trustWeave = TrustWeave.build {
        factories(
            didMethodFactory = TestkitDidMethodFactory(),
            walletFactory = TestkitWalletFactory()
        )
        keys {
            custom(kmsRef)
            // Provide signer function directly to avoid reflection issues
            signer { data, keyId ->
                kmsRef.sign(com.trustweave.core.types.KeyId(keyId), data)
            }
        }

        did {
            method(DidMethods.KEY) {
                algorithm(KeyAlgorithms.ED25519)
            }
        }

        credentials {
            defaultProofType(ProofType.Ed25519Signature2020)
        }

        schemas {
            autoValidate(false)
            defaultFormat(SchemaFormat.JSON_SCHEMA)
        }
    }

    // Create professional DID using new DSL
    val professionalDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }.getOrFail()
    println("Professional DID: ${professionalDid.value}")

    // Register schemas for credential validation
    println("\nStep 1.5: Registering credential schemas...")
    trustWeave.configuration.registerSchema {
        id("https://example.com/schemas/education")
        type(SchemaValidatorTypes.JSON_SCHEMA)
        jsonSchema {
            "\$schema" to "http://json-schema.org/draft-07/schema#"
            "type" to "object"
            "properties" {
                "degree" {
                    "type" to "object"
                    "properties" {
                        "type" { "type" to "string" }
                        "field" { "type" to "string" }
                        "institution" { "type" to "string" }
                        "year" { "type" to "string" }
                    }
                }
            }
        }
    }
    println("✓ Education schema registered")

    trustWeave.configuration.registerSchema {
        id("https://example.com/schemas/certification")
        type(SchemaValidatorTypes.JSON_SCHEMA)
        jsonSchema {
            "\$schema" to "http://json-schema.org/draft-07/schema#"
            "type" to "object"
            "properties" {
                "certification" {
                    "type" to "object"
                    "properties" {
                        "name" { "type" to "string" }
                        "issuer" { "type" to "string" }
                        "issueDate" { "type" to "string" }
                        "expirationDate" { "type" to "string" }
                    }
                }
            }
        }
    }
    println("✓ Certification schema registered")

    // Step 2: Create professional wallet using DSL
    println("\nStep 2: Creating professional wallet...")
    val wallet: Wallet = trustWeave.wallet {
        id("professional-wallet-${professionalDid.value.substringAfterLast(":")}")
        holder(professionalDid.value)
        enableOrganization()
        enablePresentation()
    }.getOrFail()
    println("Wallet created: ${wallet.walletId}")

    // Step 3: Store education credentials using DSL
    println("\nStep 3: Storing education credentials...")
    // Create issuer DID for university
    val universityDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }.getOrFail()
    println("University DID: ${universityDid.value}")
    
    // Get the key ID from the university DID document
    val universityDidResolution = trustWeave.configuration.registries.didRegistry.resolve(universityDid.value)
        ?: throw IllegalStateException("Failed to resolve university DID")
    val universityDidDoc = when (universityDidResolution) {
        is com.trustweave.did.resolver.DidResolutionResult.Success -> universityDidResolution.document
        else -> throw IllegalStateException("Failed to resolve university DID")
    }
    val universityVerificationMethod = universityDidDoc.verificationMethod.firstOrNull()
        ?: throw IllegalStateException("No verification method found in university DID document")
    val universityKeyId = universityVerificationMethod.id.substringAfter("#")

    // Issue credentials using new DSL with revocation support
    val bachelorDegree = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/bachelor-${professionalDid.value.substringAfterLast(":")}")
            type(CredentialTypes.EDUCATION, CredentialType.Custom("BachelorDegreeCredential"))
            issuer(universityDid.value)
            subject {
                id(professionalDid.value)
                "degree" {
                    "type" to "Bachelor"
                    "field" to "Computer Science"
                    "institution" to "Tech University"
                    "year" to "2018"
                }
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = universityDid.value, keyId = universityKeyId)
    }.getOrFail()
    val bachelorStored = bachelorDegree.storeIn(wallet)
    val bachelorId = requireNotNull(bachelorStored.id) { "Credential must have an id" }

    val masterDegree = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/master-${professionalDid.value.substringAfterLast(":")}")
            type(CredentialTypes.EDUCATION, CredentialType.Custom("MasterDegreeCredential"))
            issuer(universityDid.value)
            subject {
                id(professionalDid.value)
                "degree" {
                    "type" to "Master"
                    "field" to "Software Engineering"
                    "institution" to "Tech University"
                    "year" to "2020"
                }
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = universityDid.value, keyId = universityKeyId)
    }.getOrFail()
    val masterStored = masterDegree.storeIn(wallet)
    val masterId = requireNotNull(masterStored.id) { "Credential must have an id" }

    println("Stored ${wallet.list().size} education credentials")

    // Step 4: Store work experience credentials using DSL
    println("\nStep 4: Storing work experience credentials...")
    val job1 = createEmploymentCredential(
        issuerDid = "did:key:company1",
        holderDid = professionalDid.value,
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
        holderDid = professionalDid.value,
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
        holderDid = professionalDid.value,
        certificationName = "AWS Certified Solutions Architect",
        issuer = "Amazon Web Services",
        issueDate = "2021-03-15",
        expirationDate = "2024-03-15",
        credentialId = "AWS-12345"
    )
    val awsCertId = wallet.store(awsCert)

    val kubernetesCert = createCertificationCredential(
        issuerDid = "did:key:cncf",
        holderDid = professionalDid.value,
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
            val schemaBuilder = schema("https://example.com/schemas/certification")
            val schemaResult = schemaBuilder.validate(awsCertStored)
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
    val activeCerts = wallet.dslQuery {
        type("CertificationCredential")
        notExpired()
        valid()
        tag("active")
    }
    println("Active certifications: ${activeCerts.size}")

    // Find cloud-related credentials by tag and collection
    if (certificationsCollectionId != null) {
        val cloudCredentials = wallet.dslQuery {
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
    val jobApplicationCredentials: List<VerifiableCredential> = listOf(masterId, job1Id, job2Id, awsCertId)
        .mapNotNull { wallet.get(it) }
    val jobApplicationPresentation = presentation(presentationService) {
        credentials(jobApplicationCredentials)
        holder(professionalDid.value)
        challenge("job-application-${Instant.now().toEpochMilli()}")
        proofType(ProofType.Ed25519Signature2020.value)
        keyId(professionalKey.id.value)
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
    val profileCredentials = wallet.dslQuery {
        types("MasterDegreeCredential", "EmploymentCredential", "CertificationCredential")
        valid()
    }
    val profilePresentation = presentation(presentationService) {
        credentials(profileCredentials)
        holder(professionalDid.value)
        proofType(ProofType.Ed25519Signature2020.value)
        keyId(professionalKey.id.value)
    }
    println("Professional profile presentation created with ${profilePresentation.verifiableCredential.size} credentials")

    // Step 8.5: Demonstrate key rotation
    println("\nStep 8.5: Demonstrating key rotation...")
    try {
        trustWeave.rotateKey {
            did(professionalDid.value)
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
            did(professionalDid.value)
            method(DidMethods.KEY)
            addService {
                id("${professionalDid.value}#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://professional.example.com")
            }
            // Add capability invocation for signing documents
            addCapabilityInvocation("${professionalDid.value}#key-1")
            // Add capability delegation for delegating to assistants
            addCapabilityDelegation("${professionalDid.value}#key-2")
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
        // Sanitize company name for URI (replace spaces and special chars with hyphens)
        val sanitizedCompany = company.lowercase().replace(Regex("[^a-z0-9]+"), "-").replace("-+".toRegex(), "-")
        id("https://example.com/employment/${sanitizedCompany}-${holderDid.substringAfterLast(":")}")
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
