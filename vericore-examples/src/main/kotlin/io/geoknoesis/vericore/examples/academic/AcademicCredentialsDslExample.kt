package io.geoknoesis.vericore.examples.academic

import io.geoknoesis.vericore.credential.dsl.*
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.wallet.CredentialOrganization
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Academic Credentials Example using DSL.
 * 
 * This example demonstrates how to use the Credential DSL API to:
 * 1. Configure a trust layer
 * 2. Create credentials using the fluent DSL
 * 3. Issue credentials with automatic proof generation
 * 4. Verify credentials
 * 5. Create presentations
 */
fun main() = runBlocking {
    println("=== Academic Credentials Scenario (DSL) ===\n")
    
    // Step 1: Configure Trust Layer
    println("Step 1: Configuring trust layer...")
    val trustLayer = trustLayer {
        keys {
            provider("inMemory")
            algorithm(KeyAlgorithms.ED25519)
        }
        
        did {
            method(DidMethods.KEY) {
                algorithm(KeyAlgorithms.ED25519)
            }
        }
        
        anchor {
            chain("algorand:testnet") {
                inMemory()
            }
        }
        
        credentials {
            defaultProofType(ProofTypes.ED25519)
            autoAnchor(false) // Set to true to auto-anchor credentials
        }
        
        revocation {
            provider("inMemory")
        }
        
        schemas {
            autoValidate(false)
            defaultFormat(io.geoknoesis.vericore.spi.SchemaFormat.JSON_SCHEMA)
        }
        
        // Configure trust registry
        trust {
            provider("inMemory")
        }
    }
    println("✓ Trust layer configured")
    
    // Step 2: Create DIDs using new DSL
    println("\nStep 2: Creating DIDs...")
    val universityDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("University DID: $universityDid")
    
    val studentDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Student DID: $studentDid")
    
    // Step 3: Create student wallet using DSL
    println("\nStep 3: Creating student wallet...")
    val studentWallet = trustLayer.wallet {
        id("student-wallet-${studentDid.substringAfterLast(":")}")
        holder(studentDid)
        enableOrganization()
        enablePresentation()
    }
    println("Wallet created with ID: ${studentWallet.walletId}")
    
    // Step 4: University issues degree credential using DSL
    println("\nStep 4: University issues degree credential using DSL...")
    val kms = trustLayer.dsl().getKms() as? InMemoryKeyManagementService
        ?: InMemoryKeyManagementService()
    val issuerKey = kms.generateKey("Ed25519")
    
    val issuedCredential = trustLayer.issue {
        credential {
            id("https://example.edu/credentials/degree-${studentDid.substringAfterLast(":")}")
            type("DegreeCredential", "BachelorDegreeCredential")
            issuer(universityDid)
            subject {
                id(studentDid)
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
        by(issuerDid = universityDid, keyId = issuerKey.id)
        withRevocation() // Auto-create status list
    }
    
    println("Credential issued:")
    println("  - Type: ${issuedCredential.type}")
    println("  - Issuer: ${issuedCredential.issuer}")
    println("  - Has proof: ${issuedCredential.proof != null}")
    if (issuedCredential.proof != null) {
        println("  - Proof type: ${issuedCredential.proof?.type}")
    }
    if (issuedCredential.credentialStatus != null) {
        println("  - Revocation status: ${issuedCredential.credentialStatus?.id}")
    }
    
    // Step 5: Student stores credential in wallet using lifecycle DSL
    println("\nStep 5: Student stores credential in wallet...")
    val stored = issuedCredential.storeIn(studentWallet)
    println("Credential stored with ID: ${stored.credentialId}")
    
    // Step 6: Organize credentials using new DSL
    println("\nStep 6: Organizing credentials...")
    if (studentWallet is CredentialOrganization) {
        val result = studentWallet.organize {
            collection("Education Credentials", "Academic degrees and certificates") {
                add(stored.credentialId)
                tag(stored.credentialId, "degree", "bachelor", "computer-science", "verified")
            }
        }
        println("Created ${result.collectionsCreated} collection(s)")
        println("Added tags: degree, bachelor, computer-science, verified")
    } else {
        println("Wallet does not support organization features")
    }
    
    // Step 7: Query credentials using enhanced query DSL
    println("\nStep 7: Querying credentials...")
    val degrees = studentWallet.queryEnhanced {
        byType("DegreeCredential")
        valid()
    }
    println("Found ${degrees.size} valid degree credentials")
    
    // Step 8: Create presentation using wallet presentation DSL
    println("\nStep 8: Creating presentation using wallet presentation DSL...")
    val presentation = studentWallet.presentation {
        fromWallet(stored.credentialId)
        holder(studentDid)
        challenge("job-application-12345")
        proofType(ProofTypes.ED25519)
    }
    
    println("Presentation created:")
    println("  - Holder: ${presentation.holder}")
    println("  - Credentials: ${presentation.verifiableCredential.size}")
    println("  - Challenge: ${presentation.challenge}")
    
    // Step 9: Verify credential using lifecycle DSL
    println("\nStep 9: Verifying credential using lifecycle DSL...")
    val verificationResult = stored.verify(trustLayer) {
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
    
    // Step 11: Demonstrate trust registry
    println("\nStep 11: Demonstrating trust registry...")
    trustLayer.trust {
        // Add university as trusted anchor
        addAnchor(universityDid) {
            credentialTypes("DegreeCredential", "EducationCredential")
            description("Trusted university for academic credentials")
        }
        println("✓ Added university as trust anchor")
        
        // Verify credential with trust registry
        val isTrusted = isTrusted(universityDid, "DegreeCredential")
        println("University trusted for DegreeCredential: $isTrusted")
    }
    
    // Step 12: Demonstrate DID document updates with new fields
    println("\nStep 12: Demonstrating DID document updates...")
    try {
        trustLayer.updateDid {
            did(universityDid)
            method(DidMethods.KEY)
            addCapabilityDelegation("$universityDid#key-1")
            context("https://www.w3.org/ns/did/v1")
        }
        println("✓ Updated university DID document with capability delegation and context")
    } catch (e: Exception) {
        println("DID document update skipped (${e.message})")
    }
    
    println("\n=== Scenario Complete ===")
    println("\nKey Benefits of DSL:")
    println("  ✓ Single trust layer configuration")
    println("  ✓ Fluent credential creation (no manual buildJsonObject)")
    println("  ✓ Automatic proof generation")
    println("  ✓ Simplified issuance and verification")
    println("  ✓ Type-safe operations")
}

