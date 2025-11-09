package io.geoknoesis.vericore.examples.trust

import io.geoknoesis.vericore.credential.dsl.*
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.wallet.CredentialOrganization
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * Web of Trust Example Scenario.
 * 
 * Demonstrates complete web of trust functionality:
 * - Setting up trust anchors
 * - Issuing credentials with trust verification
 * - Delegating capabilities
 * - Verifying delegation chains
 * - Finding trust paths between DIDs
 * 
 * This example shows how to build a trust network where:
 * - Universities are trusted anchors for education credentials
 * - Companies can delegate credential issuance to HR departments
 * - Verifiers can check trust paths to validate issuer trustworthiness
 */
fun main() = runBlocking {
    println("=== Web of Trust Scenario ===\n")
    
    // Step 1: Configure Trust Layer with Trust Registry
    println("Step 1: Setting up trust layer with trust registry...")
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
        
        credentials {
            defaultProofType(ProofTypes.ED25519)
        }
        
        revocation {
            provider("inMemory")
        }
        
        schemas {
            autoValidate(false)
        }
        
        // Configure trust registry
        trust {
            provider("inMemory")
        }
    }
    println("✓ Trust layer configured with trust registry\n")
    
    // Step 2: Create DIDs for different entities
    println("Step 2: Creating DIDs for entities...")
    val universityDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("University DID: $universityDid")
    
    val companyDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Company DID: $companyDid")
    
    val hrDeptDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("HR Department DID: $hrDeptDid")
    
    val studentDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Student DID: $studentDid")
    
    val verifierDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Verifier DID: $verifierDid\n")
    
    // Step 3: Set up trust anchors
    println("Step 3: Setting up trust anchors...")
    trustLayer.trust {
        // Add university as trusted anchor for education credentials
        addAnchor(universityDid) {
            credentialTypes("EducationCredential", "DegreeCredential")
            description("Trusted university for education credentials")
        }
        println("✓ Added university as trust anchor for education credentials")
        
        // Add company as trusted anchor for employment credentials
        addAnchor(companyDid) {
            credentialTypes("EmploymentCredential")
            description("Trusted company for employment credentials")
        }
        println("✓ Added company as trust anchor for employment credentials")
    }
    println()
    
    // Step 4: Set up delegation (company delegates to HR department)
    println("Step 4: Setting up capability delegation...")
    
    // Update company DID document to delegate capability to HR department
    trustLayer.updateDid {
        did(companyDid)
        method(DidMethods.KEY)
        addCapabilityDelegation("$hrDeptDid#key-1")
    }
    println("✓ Company delegated credential issuance capability to HR department")
    
    // Verify delegation chain
    val delegationResult = trustLayer.delegate {
        from(companyDid)
        to(hrDeptDid)
        capability("issueCredentials")
    }
    
    if (delegationResult.valid) {
        println("✓ Delegation chain verified: ${delegationResult.path.joinToString(" -> ")}")
    } else {
        println("✗ Delegation chain verification failed: ${delegationResult.errors.joinToString(", ")}")
    }
    println()
    
    // Step 5: Issue credentials with trust verification
    println("Step 5: Issuing credentials...")
    
    // Issue education credential from university
    val degreeCredential = trustLayer.issue {
        credential {
            id("https://university.edu/credentials/degree-${studentDid.substringAfterLast(":")}")
            type(CredentialTypes.EDUCATION, "DegreeCredential")
            issuer(universityDid)
            subject {
                id(studentDid)
                "degree" {
                    "type" to "Bachelor"
                    "field" to "Computer Science"
                    "institution" to "Tech University"
                    "year" to "2023"
                }
            }
            issued(Instant.now())
        }
        by(issuerDid = universityDid, keyId = "key-1")
    }
    println("✓ Issued degree credential from university")
    
    // Issue employment credential from HR department (delegated)
    val employmentCredential = trustLayer.issue {
        credential {
            id("https://company.com/credentials/employment-${studentDid.substringAfterLast(":")}")
            type("EmploymentCredential")
            issuer(hrDeptDid)
            subject {
                id(studentDid)
                "employment" {
                    "company" to "Tech Corp"
                    "role" to "Software Engineer"
                    "startDate" to "2024-01-01"
                }
            }
            issued(Instant.now())
        }
        by(issuerDid = hrDeptDid, keyId = "key-1")
    }
    println("✓ Issued employment credential from HR department (delegated)\n")
    
    // Step 6: Verify credentials with trust registry
    println("Step 6: Verifying credentials with trust registry...")
    
    // Verify degree credential
    val degreeVerification = trustLayer.verify {
        credential(degreeCredential)
        checkTrustRegistry()
        checkExpiration()
    }
    
    println("Degree credential verification:")
    println("  Valid: ${degreeVerification.valid}")
    println("  Trust Registry Valid: ${degreeVerification.trustRegistryValid}")
    println("  Errors: ${if (degreeVerification.errors.isEmpty()) "None" else degreeVerification.errors.joinToString(", ")}")
    
    // Verify employment credential (should check delegation)
    val employmentVerification = trustLayer.verify {
        credential(employmentCredential)
        checkTrustRegistry()
        verifyDelegation()
        checkExpiration()
    }
    
    println("\nEmployment credential verification:")
    println("  Valid: ${employmentVerification.valid}")
    println("  Trust Registry Valid: ${employmentVerification.trustRegistryValid}")
    println("  Delegation Valid: ${employmentVerification.delegationValid}")
    println("  Errors: ${if (employmentVerification.errors.isEmpty()) "None" else employmentVerification.errors.joinToString(", ")}")
    println()
    
    // Step 7: Find trust paths
    println("Step 7: Finding trust paths...")
    trustLayer.trust {
        val trustPath = getTrustPath(verifierDid, universityDid)
        if (trustPath != null) {
            println("Trust path from verifier to university:")
            println("  Path: ${trustPath.path.joinToString(" -> ")}")
            println("  Trust Score: ${trustPath.trustScore}")
            println("  Valid: ${trustPath.valid}")
        } else {
            println("No trust path found from verifier to university")
        }
        
        val trustPath2 = getTrustPath(verifierDid, companyDid)
        if (trustPath2 != null) {
            println("\nTrust path from verifier to company:")
            println("  Path: ${trustPath2.path.joinToString(" -> ")}")
            println("  Trust Score: ${trustPath2.trustScore}")
            println("  Valid: ${trustPath2.valid}")
        } else {
            println("No trust path found from verifier to company")
        }
    }
    println()
    
    // Step 8: Demonstrate DID document updates with new fields
    println("Step 8: Updating DID document with capability relationships...")
    trustLayer.updateDid {
        did(studentDid)
        method(DidMethods.KEY)
        addCapabilityInvocation("$studentDid#key-1")
        context("https://www.w3.org/ns/did/v1", "https://example.com/context/v1")
    }
    println("✓ Updated student DID document with capability invocation and context\n")
    
    // Step 9: Get trusted issuers
    println("Step 9: Getting trusted issuers...")
    trustLayer.trust {
        val trustedForEducation = getTrustedIssuers("EducationCredential")
        println("Trusted issuers for EducationCredential:")
        trustedForEducation.forEach { println("  - $it") }
        
        val trustedForEmployment = getTrustedIssuers("EmploymentCredential")
        println("\nTrusted issuers for EmploymentCredential:")
        trustedForEmployment.forEach { println("  - $it") }
    }
    println()
    
    println("=== Web of Trust Scenario Complete ===")
}

