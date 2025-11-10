package io.geoknoesis.vericore.examples.academic

import io.geoknoesis.vericore.credential.dsl.*
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.wallet.CredentialOrganization
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    println("=== Academic Credentials Scenario ===\n")
    
    // Step 1: Configure Trust Layer
    println("Step 1: Configuring trust layer...")
    val trustLayer = trustLayer {
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }
        
        did {
            method("key") {
                algorithm("Ed25519")
            }
        }
        
        anchor {
            chain("algorand:testnet") {
                inMemory()
            }
        }
        
        credentials {
            defaultProofType("Ed25519Signature2020")
            autoAnchor(false)
        }
    }
    println("✓ Trust layer configured")
    
    // Step 2: Create DIDs
    println("\nStep 2: Creating DIDs...")
    val kms = trustLayer.dsl().getKms() as? InMemoryKeyManagementService
        ?: InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    
    val universityDid = didMethod.createDid()
    println("University DID: ${universityDid.id}")
    
    val studentDid = didMethod.createDid()
    println("Student DID: ${studentDid.id}")
    
    // Step 3: Create student wallet using DSL
    println("\nStep 3: Creating student wallet...")
    val studentWallet = trustLayer.wallet {
        id("student-wallet-${studentDid.id.substringAfterLast(":")}")
        holder(studentDid.id)
        enableOrganization()
        enablePresentation()
    }
    println("Wallet created with ID: ${studentWallet.walletId}")
    
    // Step 4: University issues degree credential using DSL
    println("\nStep 4: University issues degree credential...")
    val issuerKey = kms.generateKey("Ed25519")
    
    val issuedCredential = trustLayer.issue {
        credential {
            id("https://example.edu/credentials/degree-${studentDid.id.substringAfterLast(":")}")
            type("DegreeCredential", "BachelorDegreeCredential")
            issuer(universityDid.id)
            subject {
                id(studentDid.id)
                "degree" {
                    "type" to "BachelorDegree"
                    "name" to "Bachelor of Science in Computer Science"
                    "university" to "Example University"
                    "graduationDate" to "2023-05-15"
                    "gpa" to "3.8"
                }
            }
            issued(Instant.now())
            expires(365 * 10, ChronoUnit.DAYS) // Valid for 10 years
        }
        by(issuerDid = universityDid.id, keyId = issuerKey.id)
    }
    
    println("Credential issued:")
    println("  - Type: ${issuedCredential.type}")
    println("  - Issuer: ${issuedCredential.issuer}")
    println("  - Has proof: ${issuedCredential.proof != null}")
    
    // Step 5: Student stores credential in wallet
    println("\nStep 5: Student stores credential in wallet...")
    val credentialId = studentWallet.store(issuedCredential)
    println("Credential stored with ID: $credentialId")
    
    // Step 6: Organize credentials
    println("\nStep 6: Organizing credentials...")
    if (studentWallet is CredentialOrganization) {
        val educationCollection = studentWallet.createCollection(
            name = "Education Credentials",
            description = "Academic degrees and certificates"
        )
        studentWallet.addToCollection(credentialId, educationCollection)
        studentWallet.tagCredential(credentialId, setOf("degree", "bachelor", "computer-science", "verified"))
        
        println("Created collection: $educationCollection")
        println("Added tags: degree, bachelor, computer-science, verified")
    }
    
    // Step 7: Query credentials
    println("\nStep 7: Querying credentials...")
    val degrees = studentWallet.query {
        byType("DegreeCredential")
        valid()
    }
    println("Found ${degrees.size} valid degree credentials")
    
    // Step 8: Create presentation using DSL
    println("\nStep 8: Creating presentation for job application...")
    val presentation = presentation {
        credentials(issuedCredential)
        holder(studentDid.id)
        challenge("job-application-12345")
        proofType("Ed25519Signature2020")
    }
    
    println("Presentation created:")
    println("  - Holder: ${presentation.holder}")
    println("  - Credentials: ${presentation.verifiableCredential.size}")
    println("  - Challenge: ${presentation.challenge}")
    
    // Step 9: Verify credential using DSL
    println("\nStep 9: Verifying credential...")
    val verificationResult = trustLayer.verify {
        credential(issuedCredential)
        checkRevocation()
        checkExpiration()
    }
    
    if (verificationResult.valid) {
        println("✅ Credential is valid!")
        println("  - Proof valid: ${verificationResult.proofValid}")
        println("  - Issuer valid: ${verificationResult.issuerValid}")
        println("  - Not expired: ${verificationResult.notExpired}")
        println("  - Not revoked: ${verificationResult.notRevoked}")
    } else {
        println("❌ Credential verification failed:")
        verificationResult.errors.forEach { println("  - $it") }
    }
    
    // Step 10: Get wallet statistics
    println("\nStep 10: Wallet statistics...")
    val stats = studentWallet.getStatistics()
    println("""
        Total credentials: ${stats.totalCredentials}
        Valid credentials: ${stats.validCredentials}
        Collections: ${stats.collectionsCount}
        Tags: ${stats.tagsCount}
    """.trimIndent())
    
    println("\n=== Scenario Complete ===")
}

/**
 * Helper function to create a degree credential.
 * Used by tests and can be used for programmatic credential creation.
 */
fun createDegreeCredential(
    issuerDid: String,
    studentDid: String,
    degreeName: String,
    universityName: String,
    graduationDate: String,
    gpa: String
): VerifiableCredential {
    return credential {
        id("https://example.edu/credentials/degree-${studentDid.substringAfterLast(":")}")
        type("VerifiableCredential", "DegreeCredential")
        issuer(issuerDid)
        subject {
            id(studentDid)
            "degree" {
                "type" to "BachelorDegree"
                "name" to degreeName
                "university" to universityName
                "graduationDate" to graduationDate
                "gpa" to gpa
            }
        }
        issued(Instant.now())
        expires(365 * 10, ChronoUnit.DAYS) // Valid for 10 years
    }
}

