package com.trustweave.examples.national

import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.core.util.DigestUtils
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.proof.ProofType
import com.trustweave.services.IssuanceConfig
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.anchor.DefaultBlockchainAnchorRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.Instant

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
    val trustweave = TrustWeave.create {
        blockchains {
            chainId to anchorClient
        }
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

    val authorityDid = trustweave.dids.create()

    println("\n📥 RESPONSE: Authority DID Created Successfully")
    println("  ✓ DID: ${authorityDid.id}")
    println("  ✓ Verification Methods: ${authorityDid.verificationMethod.size}")
    println("  ✓ Role: National Education Authority (Ministry of Higher Education)")
    val authorityKeyId = authorityDid.verificationMethod.first().id
    println("  ✓ Authority Key ID: $authorityKeyId")
    println()

    // Step 3: Create Educational Institution DID
    println("Step 3: Creating Educational Institution DID...")
    println("\n📤 REQUEST: Create DID for Educational Institution")
    println("  Purpose: Create decentralized identifier for University of Algiers")
    println("  Role: Recognized educational institution")
    println("  Institution: University of Algiers (UA-001)")

    val institutionDid = trustweave.dids.create()

    println("\n📥 RESPONSE: Institution DID Created Successfully")
    println("  ✓ DID: ${institutionDid.id}")
    println("  ✓ Verification Methods: ${institutionDid.verificationMethod.size}")
    println("  ✓ Institution: University of Algiers")
    println("  ✓ Institution Code: UA-001")
    val institutionKeyId = institutionDid.verificationMethod.first().id
    println("  ✓ Institution Key ID: $institutionKeyId")
    println()

    // Step 4: Create Student DID
    println("Step 4: Creating Student DID...")
    println("\n📤 REQUEST: Create DID for Student")
    println("  Purpose: Create decentralized identifier for student")
    println("  Role: Credential holder and owner")
    println("  Student ID: STU-2024-001234")
    println("  National ID: 1234567890123")

    val studentDid = trustweave.dids.create()

    println("\n📥 RESPONSE: Student DID Created Successfully")
    println("  ✓ DID: ${studentDid.id}")
    println("  ✓ Verification Methods: ${studentDid.verificationMethod.size}")
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
    println("    - Issuer: ${authorityDid.id} (National Authority)")
    println("    - Subject: ${studentDid.id} (Student)")
    println("    - Institution: University of Algiers (${institutionDid.id})")
    println("    - Program: Computer Science (Bachelor)")
    println("    - Academic Year: 2024-2025")

    val enrollmentSubject = buildJsonObject {
        put("id", studentDid.id)
        put("algeroPass", buildJsonObject {
            put("credentialType", "enrollment")
            put("studentId", "STU-2024-001234")
            put("nationalId", "1234567890123")
            put("institution", buildJsonObject {
                put("institutionDid", institutionDid.id)
                put("institutionName", "University of Algiers")
                put("institutionCode", "UA-001")
            })
            put("program", buildJsonObject {
                put("programName", "Computer Science")
                put("programCode", "CS-BS")
                put("degreeLevel", "Bachelor")
            })
            put("enrollmentDate", "2024-09-01")
            put("status", "active")
            put("academicYear", "2024-2025")
        })
    }

    println("  Credential Subject:")
    val subjectJson = Json { prettyPrint = true; ignoreUnknownKeys = true }
    println(subjectJson.encodeToString(JsonObject.serializer(), enrollmentSubject))

    val enrollmentCredential = trustweave.credentials.issue(
        issuer = authorityDid.id,
        subject = enrollmentSubject,
        config = IssuanceConfig(
            proofType = ProofType.Ed25519Signature2020,
            keyId = authorityKeyId,
            issuerDid = authorityDid.id
        ),
        types = listOf("VerifiableCredential", "AlgeroPassCredential", "EnrollmentCredential", "EducationCredential")
    )

    println("\n📥 RESPONSE: Enrollment Credential Issued Successfully")
    println("  ✓ Credential ID: ${enrollmentCredential.id}")
    println("  ✓ Issuer: ${enrollmentCredential.issuer}")
    println("  ✓ Types: ${enrollmentCredential.type.joinToString(", ")}")
    println("  ✓ Issuance Date: ${enrollmentCredential.issuanceDate}")
    println("  ✓ Has Proof: ${enrollmentCredential.proof != null}")
    val proof = enrollmentCredential.proof
    if (proof != null) {
        println("  ✓ Proof Type: ${proof.type}")
        println("  ✓ Proof Purpose: ${proof.proofPurpose}")
    }
    println("  ✓ Student ID: STU-2024-001234")
    println("  ✓ Institution: University of Algiers")
    println("  ✓ Program: Computer Science (Bachelor)")
    println("  ✓ Status: active")
    println("\n  Full Credential Document:")
    val credentialJson = Json { prettyPrint = true; ignoreUnknownKeys = true }
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

    val enrollmentVerification = trustweave.credentials.verify(enrollmentCredential)

    println("\n📥 RESPONSE: Enrollment Credential Verification Result")
    if (enrollmentVerification.valid) {
        println("  ✓ Overall Status: VALID")
        println("  ✓ Proof Valid: ${enrollmentVerification.proofValid}")
        println("  ✓ Issuer Valid: ${enrollmentVerification.issuerValid}")
        println("  ✓ Not Expired: ${enrollmentVerification.notExpired}")
        println("  ✓ Not Revoked: ${enrollmentVerification.notRevoked}")
        if (enrollmentVerification.warnings.isNotEmpty()) {
            println("  ⚠ Warnings:")
            enrollmentVerification.warnings.forEach { warning ->
                println("    - $warning")
            }
        }
    } else {
        println("  ✗ Overall Status: INVALID")
        println("  ✗ Errors:")
        enrollmentVerification.errors.forEach { error ->
            println("    - $error")
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
    println("    - Issuer: ${authorityDid.id} (National Authority)")
    println("    - Subject: ${studentDid.id} (Student)")
    println("    - Institution: University of Algiers")
    println("    - Academic Year: 2024-2025")
    println("    - Semester: Fall 2024")

    val achievementSubject = buildJsonObject {
        put("id", studentDid.id)
        put("algeroPass", buildJsonObject {
            put("credentialType", "achievement")
            put("studentId", "STU-2024-001234")
            put("institution", buildJsonObject {
                put("institutionDid", institutionDid.id)
                put("institutionName", "University of Algiers")
                put("institutionCode", "UA-001")
            })
            put("academicYear", "2024-2025")
            put("semester", "Fall 2024")
            put("grades", buildJsonArray {
                add(buildJsonObject {
                    put("courseCode", "CS101")
                    put("courseName", "Introduction to Computer Science")
                    put("credits", 3)
                    put("grade", "A")
                    put("gpa", 4.0)
                })
                add(buildJsonObject {
                    put("courseCode", "MATH101")
                    put("courseName", "Calculus I")
                    put("credits", 4)
                    put("grade", "B+")
                    put("gpa", 3.5)
                })
                add(buildJsonObject {
                    put("courseCode", "ENG101")
                    put("courseName", "English Composition")
                    put("credits", 3)
                    put("grade", "A-")
                    put("gpa", 3.7)
                })
            })
            put("totalCredits", 10)
            put("gpa", 3.73)
        })
    }

    println("  Credential Subject:")
    println(subjectJson.encodeToString(JsonObject.serializer(), achievementSubject))

    val achievementCredential = trustweave.credentials.issue(
        issuer = authorityDid.id,
        subject = achievementSubject,
        config = IssuanceConfig(
            proofType = ProofType.Ed25519Signature2020,
            keyId = authorityKeyId,
            issuerDid = authorityDid.id
        ),
        types = listOf("VerifiableCredential", "AlgeroPassCredential", "AchievementCredential", "EducationCredential")
    )

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
    val achievementCredentialJsonFormatter = Json { prettyPrint = true; ignoreUnknownKeys = true }
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
    }

    val enrollmentCredentialJson = anchorJson.encodeToJsonElement(VerifiableCredential.serializer(), enrollmentCredential)
    val achievementCredentialJson = anchorJson.encodeToJsonElement(VerifiableCredential.serializer(), achievementCredential)

    // Compute digests
    val enrollmentDigest = DigestUtils.sha256DigestMultibase(enrollmentCredentialJson)
    val achievementDigest = DigestUtils.sha256DigestMultibase(achievementCredentialJson)

    // Create AlgeroPass record for enrollment
    val enrollmentRecord = buildJsonObject {
        put("studentDid", studentDid.id)
        put("studentId", "STU-2024-001234")
        put("credentialType", "enrollment")
        put("institutionDid", institutionDid.id)
        put("credentialDigest", enrollmentDigest)
        put("credentialId", enrollmentCredential.id)
        put("timestamp", Instant.now().toString())
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
        put("studentDid", studentDid.id)
        put("studentId", "STU-2024-001234")
        put("credentialType", "achievement")
        put("institutionDid", institutionDid.id)
        put("credentialDigest", achievementDigest)
        put("credentialId", achievementCredential.id)
        put("academicYear", "2024-2025")
        put("semester", "Fall 2024")
        put("timestamp", Instant.now().toString())
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
    println("✓ National Authority DID: ${authorityDid.id}")
    println("✓ Institution DID: ${institutionDid.id}")
    println("✓ Student DID: ${studentDid.id}")
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
