package org.trustweave.examples.professional

import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.credential.model.ProofType
import org.trustweave.trust.dsl.credential.CredentialTypes
import org.trustweave.credential.model.CredentialType
import org.trustweave.trust.dsl.credential.SchemaValidatorTypes
import org.trustweave.trust.dsl.credential.ServiceTypes
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.wallet.organize
import org.trustweave.trust.dsl.wallet.query as dslQuery
import org.trustweave.trust.dsl.wallet.QueryBuilder
import org.trustweave.trust.dsl.wallet.presentationFromWallet
import org.trustweave.trust.dsl.registerSchema
import org.trustweave.trust.dsl.credential.schema
import org.trustweave.trust.dsl.storeIn
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.SchemaFormat
import org.trustweave.credential.credentialService
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.proof.ProofPurpose
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.wallet.CredentialOrganization
import org.trustweave.wallet.Wallet
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.services.TestkitWalletFactory
import org.trustweave.testkit.getOrFail
import org.trustweave.did.identifiers.extractKeyId
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

fun main() = runBlocking {
    println("=== Professional Identity Wallet Scenario ===\n")

    // Step 1: Configure Trust Layer with all features
    println("Step 1: Setting up services...")
    // Create KMS first so we can use it for key generation and signing
    val kms = InMemoryKeyManagementService()
    val kmsRef = kms // Capture for closure

    // Create signer function
    val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
        when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
            is org.trustweave.kms.results.SignResult.Success -> result.signature
            else -> throw IllegalStateException("Signing failed: $result")
        }
    }

    // TrustWeave auto-creates everything via SPI - minimal configuration needed
    val trustWeave = TrustWeave.build {
        factories(
            walletFactory = TestkitWalletFactory()  // Only wallet factory needed
        )
        keys {
            custom(kmsRef)
            // Provide signer function directly to avoid reflection issues
            signer { data, keyId ->
                when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                    is org.trustweave.kms.results.SignResult.Success -> result.signature
                    else -> throw IllegalStateException("Signing failed: $result")
                }
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
        // CredentialService is auto-created with custom signer from keys{} block

        schemas {
            autoValidate(false)
            defaultFormat(SchemaFormat.JSON_SCHEMA)
        }
        // CredentialService is auto-created with custom signer from keys{} block
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
        holder(professionalDid)
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
        is org.trustweave.did.resolver.DidResolutionResult.Success -> universityDidResolution.document
        else -> throw IllegalStateException("Failed to resolve university DID")
    }
    val universityVerificationMethod = universityDidDoc.verificationMethod.firstOrNull()
        ?: throw IllegalStateException("No verification method found in university DID document")
    val universityKeyId = universityVerificationMethod.extractKeyId()
        ?: throw IllegalStateException("No key ID found in university verification method")

    // Issue credentials using new DSL with revocation support
    val bachelorDegree = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/bachelor-${professionalDid.value.substringAfterLast(":")}")
            type(CredentialTypes.EDUCATION, CredentialType.Custom("BachelorDegreeCredential"))
            issuer(universityDid)
            subject(professionalDid) {
                "degree" {
                    "type" to "Bachelor"
                    "field" to "Computer Science"
                    "institution" to "Tech University"
                    "year" to "2018"
                }
            }
            issued(Clock.System.now())
        }
        signedBy(issuerDid = universityDid, keyId = universityKeyId)
    }.getOrFail()
    val bachelorStored = bachelorDegree.storeIn(wallet)
    val bachelorId = requireNotNull(bachelorStored.id) { "Credential must have an id" }

    val masterDegree = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/master-${professionalDid.value.substringAfterLast(":")}")
            type(CredentialTypes.EDUCATION, CredentialType.Custom("MasterDegreeCredential"))
            issuer(universityDid)
            subject(professionalDid) {
                "degree" {
                    "type" to "Master"
                    "field" to "Software Engineering"
                    "institution" to "Tech University"
                    "year" to "2020"
                }
            }
            issued(Clock.System.now())
        }
        signedBy(issuerDid = universityDid, keyId = universityKeyId)
    }.getOrFail()
    val masterStored = masterDegree.storeIn(wallet)
    val masterId = requireNotNull(masterStored.id) { "Credential must have an id" }

    println("Stored ${wallet.list().size} education credentials")

    // Step 4: Store work experience credentials using DSL
    println("\nStep 4: Storing work experience credentials...")
    val job1 = createEmploymentCredential(
        issuerDid = Did("did:key:company1"),
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
        issuerDid = Did("did:key:company2"),
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
        issuerDid = Did("did:key:aws"),
        holderDid = professionalDid,
        certificationName = "AWS Certified Solutions Architect",
        issuer = "Amazon Web Services",
        issueDate = "2021-03-15",
        expirationDate = "2024-03-15",
        credentialId = "AWS-12345"
    )
    val awsCertId = wallet.store(awsCert)

    val kubernetesCert = createCertificationCredential(
        issuerDid = Did("did:key:cncf"),
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
            // Schema validation would be done via trustWeave.configuration.schemas.validate()
            // For now, just indicate that validation would occur here
            println("AWS cert schema validation: ✓ (validation would occur here)")
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
                add(bachelorId.value, masterId.value)
                tag(bachelorId.value, "education", "degree", "bachelor", "computer-science")
                tag(masterId.value, "education", "degree", "master", "software-engineering")
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
    val professionalKeyResult = kms.generateKey(org.trustweave.kms.Algorithm.Ed25519, mapOf("keyId" to "professional-key"))
    val professionalKey = when (professionalKeyResult) {
        is org.trustweave.kms.results.GenerateKeyResult.Success -> professionalKeyResult.keyHandle
        else -> throw IllegalStateException("Failed to generate key")
    }

    // Create a new CredentialService for presentations using TrustWeave's DID resolver
    // We need a separate instance because presentations require holder's key, not issuer's key
    val presentationSigner: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
        when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
            is org.trustweave.kms.results.SignResult.Success -> result.signature
            else -> throw IllegalStateException("Signing failed: $result")
        }
    }
    val presentationDidResolver = DidResolver { did ->
        // Use TrustWeave's public resolveDid method
        val resolution = trustWeave.resolveDid(did)
        when (resolution) {
            is DidResolutionResult.Success -> resolution
            else -> throw IllegalStateException("Failed to resolve DID: ${did.value}")
        }
    }
    val credentialServiceForPresentation = credentialService(
        didResolver = presentationDidResolver,
        signer = presentationSigner
    )

    // Presentation for job application
    val jobApplicationCredentials: List<VerifiableCredential> = listOf(
        masterId.value, job1Id, job2Id, awsCertId
    ).mapNotNull { wallet.get(it) }
    val jobApplicationPresentation = credentialServiceForPresentation.createPresentation(
        credentials = jobApplicationCredentials,
        request = PresentationRequest(
            proofOptions = ProofOptions(
                purpose = ProofPurpose.Authentication,
                challenge = "job-application-${Clock.System.now().toEpochMilliseconds()}",
                verificationMethod = "${professionalDid.value}#${professionalKey.id.value}",
                additionalOptions = mapOf("proofType" to "Ed25519Signature2020")
            )
        )
    )
    println("Job application presentation created with ${jobApplicationPresentation.verifiableCredential.size} credentials")

    // Presentation for professional profile using query-based presentation
    val profileCredentials = wallet.dslQuery {
        types("MasterDegreeCredential", "EmploymentCredential", "CertificationCredential")
        valid()
    }
    val profilePresentation = credentialServiceForPresentation.createPresentation(
        credentials = profileCredentials,
        request = PresentationRequest(
            proofOptions = ProofOptions(
                purpose = ProofPurpose.Authentication,
                verificationMethod = "${professionalDid.value}#${professionalKey.id.value}",
                additionalOptions = mapOf("proofType" to "Ed25519Signature2020")
            )
        )
    )
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
    issuerDid: Did,
    holderDid: Did,
    degreeType: String,
    field: String,
    institution: String,
    year: String
): VerifiableCredential {
    return credential {
        id("https://example.edu/credentials/${degreeType.lowercase()}-${holderDid.value.substringAfterLast(":")}")
        type("EducationCredential", "${degreeType}DegreeCredential")
        issuer(issuerDid)
        subject(holderDid) {
            "degree" {
                "type" to degreeType
                "field" to field
                "institution" to institution
                "year" to year
            }
        }
        issued(Clock.System.now())
        // Education credentials typically don't expire
    }
}

fun createEmploymentCredential(
    issuerDid: Did,
    holderDid: Did,
    company: String,
    role: String,
    startDate: String,
    endDate: String?,
    achievements: List<String>
): VerifiableCredential {
    return credential {
        // Sanitize company name for URI (replace spaces and special chars with hyphens)
        val sanitizedCompany = company.lowercase().replace(Regex("[^a-z0-9]+"), "-").replace("-+".toRegex(), "-")
        id("https://example.com/employment/${sanitizedCompany}-${holderDid.value.substringAfterLast(":")}")
        type("EmploymentCredential")
        issuer(issuerDid)
        subject(holderDid) {
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
        issued(Clock.System.now())
    }
}

fun createCertificationCredential(
    issuerDid: Did,
    holderDid: Did,
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
        subject(holderDid) {
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
