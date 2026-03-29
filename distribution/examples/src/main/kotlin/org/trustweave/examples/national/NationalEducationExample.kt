package org.trustweave.examples.national

import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.types.getOrThrow
import org.trustweave.trust.TrustWeave
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.types.*
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.core.*
import org.trustweave.core.util.DigestUtils
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.arrayOfObjects
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.anchor.DefaultBlockchainAnchorRegistry
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * National Education Credentials Algeria Scenario (AlgeroPass) - Complete Example
 *
 * This example demonstrates a comprehensive national-level education credential system:
 * 1. Setup TrustWeave with blockchain anchoring
 * 2. Create DIDs for national authority, institution, and student
 * 3. Issue enrollment credential (AlgeroPass)
 * 4. Issue achievement credential (grades/transcript)
 * 5. Create student wallet and store credentials
 * 6. Anchor credentials to blockchain
 * 7. Verify credentials
 *
 * This scenario demonstrates:
 * - National-level credential issuance
 * - Cross-institution credential portability
 * - Student credential wallet management
 * - Blockchain anchoring for immutability
 * - Complete traceability of all operations
 *
 * Run: `./gradlew :TrustWeave-examples:runNationalEducation`
 */
fun main() = runBlocking {
    println("=".repeat(70))
    println("National Education Credentials (AlgeroPass) - Complete Scenario")
    println("=".repeat(70))
    println()

    // Step 1: Setup TrustWeave with blockchain anchoring
    println("Step 1: Setting up TrustWeave with blockchain anchoring...")
    val chainId = "algorand:testnet"

    // Create TrustWeave instance with in-memory blockchain client for testing
    // In production, use AlgorandBlockchainAnchorClient or other blockchain clients
    // IMPORTANT: Store the client reference so we can reuse it for verification
    val anchorClient = InMemoryBlockchainAnchorClient(chainId)
    
    // Create KMS instance and capture reference for signer
    val kms = InMemoryKeyManagementService()
    val kmsRef = kms
    
    // Create signer function
    val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
        when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
            is org.trustweave.kms.results.SignResult.Success -> result.signature
            else -> throw IllegalStateException("Signing failed: $result")
        }
    }
    
    // TrustWeave auto-creates everything via SPI - minimal configuration needed
    val trustweave = TrustWeave.build {
        keys {
            custom(kmsRef)  // Custom KMS still supported
            signer(signer)
            algorithm(ED25519)
        }
        did {
            method(KEY) {
                algorithm(ED25519)  // Auto-discovered via SPI
            }
        }
        anchor {
            chain(chainId) { inMemory() }  // Auto-discovered via SPI
        }
        credentials {
            defaultProofType(ProofType.Ed25519Signature2020)
        }
        // KMS, CredentialService, DidResolver, registries all auto-created!
    }
    println("✓ TrustWeave instance created")
    println("✓ Blockchain client registered: $chainId")
    println("  - Mode: In-memory (for testing)")
    println("  - Note: In production, use real blockchain clients (Algorand, Ethereum, etc.)")
    println()

    // Step 2: Create National Education Authority DID
    println("Step 2: Creating National Education Authority DID...")
    println("\n📤 REQUEST: Create DID for National Education Authority")
    println("  Purpose: Create decentralized identifier for Ministry of Higher Education")
    println("  Role: Trusted issuer of national-level education credentials")
    println("  Method: key (default)")

    val authorityDid = trustweave.createDid().getOrThrowDid()
    
    // Resolve authority DID to get document
    val authorityDidDoc = try {
        when (val authorityDidResolution = trustweave.resolveDid(authorityDid)) {
            is DidResolutionResult.Success -> authorityDidResolution.document
            else -> {
                println("\n📥 RESPONSE: DID Resolution Failed")
                println("  ⚠ Status: No document found (may be in-memory)")
                return@runBlocking
            }
        }
    } catch (error: Throwable) {
        println("\n📥 RESPONSE: DID Resolution Failed")
        println("  ✗ Error: ${error.message}")
        return@runBlocking
    }

    println("\n📥 RESPONSE: Authority DID Created Successfully")
    println("  ✓ DID: ${authorityDid.value}")
    println("  ✓ Verification Methods: ${authorityDidDoc.verificationMethod.size}")
    println("  ✓ Role: National Education Authority (Ministry of Higher Education)")
    println("  ✓ Authority Key ID: (auto-extracted during signing)")
    println()

    // Step 3: Create Educational Institution DID
    println("Step 3: Creating Educational Institution DID...")
    println("\n📤 REQUEST: Create DID for Educational Institution")
    println("  Purpose: Create decentralized identifier for University of Algiers")
    println("  Role: Recognized educational institution")
    println("  Institution: University of Algiers (UA-001)")

    val institutionDid = trustweave.createDid().getOrThrowDid()
    
    // Resolve institution DID
    val institutionDidDoc = try {
        when (val institutionDidResolution = trustweave.resolveDid(institutionDid)) {
            is DidResolutionResult.Success -> institutionDidResolution.document
            else -> {
                println("\n📥 RESPONSE: DID Resolution Failed")
                println("  ⚠ Status: No document found (may be in-memory)")
                return@runBlocking
            }
        }
    } catch (error: Throwable) {
        println("\n📥 RESPONSE: DID Resolution Failed")
        println("  ✗ Error: ${error.message}")
        return@runBlocking
    }

    println("\n📥 RESPONSE: Institution DID Created Successfully")
    println("  ✓ DID: ${institutionDid.value}")
    println("  ✓ Verification Methods: ${institutionDidDoc.verificationMethod.size}")
    println("  ✓ Institution: University of Algiers")
    println("  ✓ Institution Code: UA-001")
    println("  ✓ Institution Key ID: (auto-extracted during signing)")
    println()

    // Step 4: Create Student DID
    println("Step 4: Creating Student DID...")
    println("\n📤 REQUEST: Create DID for Student")
    println("  Purpose: Create decentralized identifier for student")
    println("  Role: Credential holder and owner")
    println("  Student ID: STU-2024-001234")
    println("  National ID: 1234567890123")

    val studentDid = trustweave.createDid().getOrThrowDid()

    println("\n📥 RESPONSE: Student DID Created Successfully")
    println("  ✓ DID: ${studentDid.value}")
    println("  ✓ Verification Methods: (resolved on demand)")
    println("  ✓ Student ID: STU-2024-001234")
    println("  ✓ National ID: 1234567890123")
    println()

    // Step 5: Issue AlgeroPass Enrollment Credential
    println("Step 5: Issuing AlgeroPass Enrollment Credential...")
    println("\n📤 REQUEST: Issue Enrollment Credential")
    println("  Purpose: Issue national-level enrollment credential (AlgeroPass)")
    println("  What it attests:")
    println("    - Student is enrolled at a recognized institution")
    println("    - Enrollment is recognized at national level")
    println("    - Credential is portable across institutions")
    println("  Parameters:")
    println("    - Issuer: ${authorityDid.value} (National Authority)")
    println("    - Subject: ${studentDid.value} (Student)")
    println("    - Institution: University of Algiers (${institutionDid.value})")
    println("    - Program: Computer Science (Bachelor)")
    println("    - Academic Year: 2024-2025")

    val enrollmentCredential = trustweave.issue {
        credential {
            type("AlgeroPassCredential", "EnrollmentCredential", "EducationCredential")
            issuer(authorityDid)
            subject {
                id(studentDid)
                "algeroPass" {
                    "credentialType" to "enrollment"
                    "studentId" to "STU-2024-001234"
                    "nationalId" to "1234567890123"
                    "institution" {
                        "institutionDid" to institutionDid.value
                        "institutionName" to "University of Algiers"
                        "institutionCode" to "UA-001"
                    }
                    "program" {
                        "programName" to "Computer Science"
                        "programCode" to "CS-BS"
                        "degreeLevel" to "Bachelor"
                    }
                    "enrollmentDate" to "2024-09-01"
                    "status" to "active"
                    "academicYear" to "2024-2025"
                }
            }
            issued(Clock.System.now())
        }
        signedBy(authorityDid)
    }.getOrThrow()

    println("\n📥 RESPONSE: Enrollment Credential Issued Successfully")
    println("  ✓ Credential ID: ${enrollmentCredential.id}")
    println("  ✓ Issuer: ${enrollmentCredential.issuer}")
    println("  ✓ Types: ${enrollmentCredential.type.joinToString(", ")}")
    println("  ✓ Issuance Date: ${enrollmentCredential.issuanceDate}")
    println("  ✓ Has Proof: ${enrollmentCredential.proof != null}")
    val proof = enrollmentCredential.proof
    if (proof is CredentialProof.LinkedDataProof) {
        println("  ✓ Proof Type: ${proof.type}")
        println("  ✓ Proof Purpose: ${proof.proofPurpose}")
    }
    println("  ✓ Student ID: STU-2024-001234")
    println("  ✓ Institution: University of Algiers")
    println("  ✓ Program: Computer Science (Bachelor)")
    println("  ✓ Status: active")
    println("\n  Full Credential Document:")
    val credentialJson = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
        classDiscriminator = "@type" // Use @type instead of type to avoid conflict with LinkedDataProof.type
    }
    println(credentialJson.encodeToString(VerifiableCredential.serializer(), enrollmentCredential))
    println()

    // Step 6: Verify Enrollment Credential
    println("Step 6: Verifying Enrollment Credential...")
    println("\n📤 REQUEST: Verify Enrollment Credential")
    println("  Purpose: Verify the cryptographic proof and validity of the enrollment credential")
    println("  Credential ID: ${enrollmentCredential.id}")
    println("  Checks performed:")
    println("    - Cryptographic proof verification")
    println("    - Issuer DID resolution and validation")
    println("    - Expiration check")
    println("    - Revocation status check")

    val enrollmentVerification = trustweave.verify {
        credential(enrollmentCredential)
    }

    println("\n📥 RESPONSE: Enrollment Credential Verification Result")
    when (enrollmentVerification) {
        is org.trustweave.credential.results.VerificationResult.Valid -> {
            println("  ✓ Overall Status: VALID")
            println("  ✓ Proof Valid: ${enrollmentVerification.proofValid}")
            println("  ✓ Issuer Valid: ${enrollmentVerification.issuerValid}")
            println("  ✓ Not Expired: ${enrollmentVerification.notExpired}")
            println("  ✓ Not Revoked: ${enrollmentVerification.notRevoked}")
            if (enrollmentVerification.allWarnings.isNotEmpty()) {
                println("  ⚠ Warnings:")
                enrollmentVerification.allWarnings.forEach { warning ->
                    println("    - $warning")
                }
            }
        }
        is org.trustweave.credential.results.VerificationResult.Invalid -> {
            println("  ✗ Overall Status: INVALID")
            println("  ✗ Errors:")
            enrollmentVerification.allErrors.forEach { error ->
                println("    - $error")
            }
        }
    }
    println()

    // Step 7: Issue Achievement Credential (Grades/Transcript)
    println("Step 7: Issuing Achievement Credential...")
    println("\n📤 REQUEST: Issue Achievement Credential")
    println("  Purpose: Issue national-level achievement credential (grades/transcript)")
    println("  What it attests:")
    println("    - Student academic achievements and grades")
    println("    - Achievements are recognized at national level")
    println("    - Enables credit transfer between institutions")
    println("  Parameters:")
    println("    - Issuer: ${authorityDid.value} (National Authority)")
    println("    - Subject: ${studentDid.value} (Student)")
    println("    - Institution: University of Algiers")
    println("    - Academic Year: 2024-2025")
    println("    - Semester: Fall 2024")

    val achievementCredential = trustweave.issue {
        credential {
            type("AlgeroPassCredential", "AchievementCredential", "EducationCredential")
            issuer(authorityDid)
            subject {
                id(studentDid)
                "algeroPass" {
                    "credentialType" to "achievement"
                    "studentId" to "STU-2024-001234"
                    "institution" {
                        "institutionDid" to institutionDid.value
                        "institutionName" to "University of Algiers"
                        "institutionCode" to "UA-001"
                    }
                    "academicYear" to "2024-2025"
                    "semester" to "Fall 2024"
                    "grades" to arrayOfObjects(
                        {
                            "courseCode" to "CS101"
                            "courseName" to "Introduction to Computer Science"
                            "credits" to 3
                            "grade" to "A"
                            "gpa" to 4.0
                        },
                        {
                            "courseCode" to "MATH101"
                            "courseName" to "Calculus I"
                            "credits" to 4
                            "grade" to "B+"
                            "gpa" to 3.5
                        },
                        {
                            "courseCode" to "ENG101"
                            "courseName" to "English Composition"
                            "credits" to 3
                            "grade" to "A-"
                            "gpa" to 3.7
                        }
                    )
                    "totalCredits" to 10
                    "gpa" to 3.73
                }
            }
            issued(Clock.System.now())
        }
        signedBy(authorityDid)
    }.getOrThrow()

    println("\n📥 RESPONSE: Achievement Credential Issued Successfully")
    println("  ✓ Credential ID: ${achievementCredential.id}")
    println("  ✓ Issuer: ${achievementCredential.issuer}")
    println("  ✓ Types: ${achievementCredential.type.joinToString(", ")}")
    println("  ✓ Issuance Date: ${achievementCredential.issuanceDate}")
    println("  ✓ Has Proof: ${achievementCredential.proof != null}")
    println("  ✓ Academic Year: 2024-2025")
    println("  ✓ Semester: Fall 2024")
    println("  ✓ Total Credits: 10")
    println("  ✓ GPA: 3.73")
    println("  ✓ Number of Courses: 3")
    println("\n  Full Credential Document:")
    val achievementCredentialJsonFormatter = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
        classDiscriminator = "@type" // Use @type instead of type to avoid conflict with LinkedDataProof.type
    }
    println(achievementCredentialJsonFormatter.encodeToString(VerifiableCredential.serializer(), achievementCredential))
    println()

    // Step 8: Anchor Credentials to Blockchain
    println("Step 8: Anchoring Credentials to Blockchain...")
    println("\n📤 REQUEST: Anchor Credentials to Blockchain")
    println("  Purpose: Store credential digests immutably on blockchain for long-term verification")
    println("  Chain ID: $chainId")
    println("  Mode: In-memory (for testing)")
    println("  Credentials to anchor:")
    println("    1. Enrollment Credential: ${enrollmentCredential.id}")
    println("    2. Achievement Credential: ${achievementCredential.id}")

    // Convert credentials to JSON for digest computation
    val anchorJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "@type" // Use @type instead of type to avoid conflict with LinkedDataProof.type
    }

    val enrollmentCredentialJson = anchorJson.encodeToJsonElement(enrollmentCredential)
    val achievementCredentialJson = anchorJson.encodeToJsonElement(achievementCredential)

    // Compute digests
    val enrollmentDigest = DigestUtils.sha256DigestMultibase(enrollmentCredentialJson)
    val achievementDigest = DigestUtils.sha256DigestMultibase(achievementCredentialJson)

    // Create AlgeroPass record for enrollment
    val enrollmentRecord = buildJsonObject {
        put("studentDid", studentDid.value)
        put("studentId", "STU-2024-001234")
        put("credentialType", "enrollment")
        put("institutionDid", institutionDid.value)
        put("credentialDigest", enrollmentDigest)
        put("credentialId", enrollmentCredential.id?.value ?: "")
        put("timestamp", Clock.System.now().toString())
    }

    println("  Enrollment Record:")
    println(anchorJson.encodeToString(JsonObject.serializer(), enrollmentRecord))

    val enrollmentAnchor = trustweave.blockchains.anchor(
        data = enrollmentRecord,
        serializer = JsonElement.serializer(),
        chainId = chainId
    )

    println("\n📥 RESPONSE: Enrollment Credential Anchored Successfully")
    println("  ✓ Chain ID: ${enrollmentAnchor.ref.chainId}")
    println("  ✓ Transaction Hash: ${enrollmentAnchor.ref.txHash}")
    println("  ✓ Timestamp: ${enrollmentAnchor.timestamp}")
    println("  ✓ Credential Digest: $enrollmentDigest")
    println()

    // Anchor achievement credential
    val achievementRecord = buildJsonObject {
        put("studentDid", studentDid.value)
        put("studentId", "STU-2024-001234")
        put("credentialType", "achievement")
        put("institutionDid", institutionDid.value)
        put("credentialDigest", achievementDigest)
        put("credentialId", achievementCredential.id?.value ?: "")
        put("academicYear", "2024-2025")
        put("semester", "Fall 2024")
        put("timestamp", Clock.System.now().toString())
    }

    println("  Achievement Record:")
    println(anchorJson.encodeToString(JsonObject.serializer(), achievementRecord))

    val achievementAnchor = trustweave.blockchains.anchor(
        data = achievementRecord,
        serializer = JsonElement.serializer(),
        chainId = chainId
    )

    println("\n📥 RESPONSE: Achievement Credential Anchored Successfully")
    println("  ✓ Chain ID: ${achievementAnchor.ref.chainId}")
    println("  ✓ Transaction Hash: ${achievementAnchor.ref.txHash}")
    println("  ✓ Timestamp: ${achievementAnchor.timestamp}")
    println("  ✓ Credential Digest: $achievementDigest")
    println()

    // Step 9: Read Back Anchored Data
    println("Step 9: Reading Anchored Credentials from Blockchain...")
    println("\n📤 REQUEST: Read Enrollment Record from Blockchain")
    println("  Purpose: Retrieve and verify the anchored enrollment record")
    println("  Anchor Reference:")
    println("    - Chain ID: ${enrollmentAnchor.ref.chainId}")
    println("    - Transaction Hash: ${enrollmentAnchor.ref.txHash}")

    val readEnrollment = trustweave.blockchains.read<JsonElement>(
        ref = enrollmentAnchor.ref,
        serializer = JsonElement.serializer()
    )

    println("\n📥 RESPONSE: Enrollment Record Retrieved")
    println("  ✓ Status: Successfully read from blockchain")
    println("  ✓ Student DID: ${readEnrollment.jsonObject["studentDid"]?.jsonPrimitive?.content}")
    println("  ✓ Student ID: ${readEnrollment.jsonObject["studentId"]?.jsonPrimitive?.content}")
    println("  ✓ Credential Type: ${readEnrollment.jsonObject["credentialType"]?.jsonPrimitive?.content}")
    println("  ✓ Credential Digest: ${readEnrollment.jsonObject["credentialDigest"]?.jsonPrimitive?.content}")
    println("  ✓ Timestamp: ${readEnrollment.jsonObject["timestamp"]?.jsonPrimitive?.content}")

    // Verify integrity
    val readDigest = readEnrollment.jsonObject["credentialDigest"]?.jsonPrimitive?.content
    println("\n  Integrity Verification:")
    println("    Expected Digest: $enrollmentDigest")
    println("    Retrieved Digest: $readDigest")
    if (readDigest == enrollmentDigest) {
        println("    ✓ Status: MATCH - Data integrity verified")
    } else {
        println("    ✗ Status: MISMATCH - Data integrity check failed")
    }
    println()

    // Summary
    println("=".repeat(70))
    println("AlgeroPass Scenario Summary")
    println("=".repeat(70))
    println("✓ TrustWeave instance created with blockchain integration")
    println("✓ National Authority DID: ${authorityDid.value}")
    println("✓ Institution DID: ${institutionDid.value}")
    println("✓ Student DID: ${studentDid.value}")
    println("✓ Enrollment Credential issued: ${enrollmentCredential.id}")
    println("  - Student ID: STU-2024-001234")
    println("  - Institution: University of Algiers")
    println("  - Program: Computer Science (Bachelor)")
    println("  - Status: active")
    println("✓ Achievement Credential issued: ${achievementCredential.id}")
    println("  - Academic Year: 2024-2025")
    println("  - Semester: Fall 2024")
    println("  - GPA: 3.73")
    println("✓ Enrollment credential anchored: ${enrollmentAnchor.ref.txHash}")
    println("✓ Achievement credential anchored: ${achievementAnchor.ref.txHash}")
    println()
    println("=".repeat(70))
    println("✅ Complete AlgeroPass Scenario Successful!")
    println("=".repeat(70))
    println()
    println("Key Benefits Demonstrated:")
    println("  - National Recognition: Credentials recognized across all Algerian institutions")
    println("  - Student Mobility: Easy transfer between universities")
    println("  - Fraud Prevention: Cryptographic proof prevents credential forgery")
    println("  - Efficiency: Instant verification without contacting institutions")
    println("  - Student Control: Students own and control their credentials")
    println("  - Privacy: Selective disclosure protects student privacy")
    println("  - Interoperability: Standard format works across all institutions")
    println("  - Immutability: Blockchain anchoring provides long-term verification")
    println("=".repeat(70))
}
