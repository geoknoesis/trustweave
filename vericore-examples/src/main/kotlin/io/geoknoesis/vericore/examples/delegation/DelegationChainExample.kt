package io.geoknoesis.vericore.examples.delegation

import io.geoknoesis.vericore.credential.dsl.*
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * Delegation Chain Example Scenario.
 * 
 * Demonstrates delegation chain functionality:
 * - Creating DID with capability delegation
 * - Delegating authority to another DID
 * - Verifying delegation chain
 * - Using delegated credentials
 * 
 * This example shows a corporate hierarchy where:
 * - CEO delegates credential issuance to HR Director
 * - HR Director delegates to HR Manager
 * - HR Manager issues credentials on behalf of the company
 */
fun main() = runBlocking {
    println("=== Delegation Chain Scenario ===\n")
    
    // Step 1: Configure Trust Layer
    println("Step 1: Setting up trust layer...")
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
    }
    println("✓ Trust layer configured\n")
    
    // Step 2: Create DIDs for delegation chain
    println("Step 2: Creating DIDs for delegation chain...")
    val ceoDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("CEO DID: $ceoDid")
    
    val hrDirectorDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("HR Director DID: $hrDirectorDid")
    
    val hrManagerDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("HR Manager DID: $hrManagerDid")
    
    val employeeDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Employee DID: $employeeDid\n")
    
    // Step 3: Set up delegation chain
    println("Step 3: Setting up delegation chain...")
    
    // CEO delegates to HR Director
    trustLayer.updateDid {
        did(ceoDid)
        method(DidMethods.KEY)
        addCapabilityDelegation("$hrDirectorDid#key-1")
    }
    println("✓ CEO delegated capability to HR Director")
    
    // HR Director delegates to HR Manager
    trustLayer.updateDid {
        did(hrDirectorDid)
        method(DidMethods.KEY)
        addCapabilityDelegation("$hrManagerDid#key-1")
    }
    println("✓ HR Director delegated capability to HR Manager\n")
    
    // Step 4: Verify single-hop delegation
    println("Step 4: Verifying single-hop delegation...")
    val delegation1 = trustLayer.delegate {
        from(ceoDid)
        to(hrDirectorDid)
        capability("issueCredentials")
    }
    
    println("CEO -> HR Director delegation:")
    println("  Valid: ${delegation1.valid}")
    println("  Path: ${delegation1.path.joinToString(" -> ")}")
    if (!delegation1.valid) {
        println("  Errors: ${delegation1.errors.joinToString(", ")}")
    }
    
    val delegation2 = trustLayer.delegate {
        from(hrDirectorDid)
        to(hrManagerDid)
        capability("issueCredentials")
    }
    
    println("\nHR Director -> HR Manager delegation:")
    println("  Valid: ${delegation2.valid}")
    println("  Path: ${delegation2.path.joinToString(" -> ")}")
    if (!delegation2.valid) {
        println("  Errors: ${delegation2.errors.joinToString(", ")}")
    }
    println()
    
    // Step 5: Verify multi-hop delegation chain
    println("Step 5: Verifying multi-hop delegation chain...")
    val delegationBuilder = trustLayer.delegation {
        capability("issueCredentials")
    }
    val multiHopResult = delegationBuilder.verifyChain(listOf(ceoDid, hrDirectorDid, hrManagerDid))
    
    println("CEO -> HR Director -> HR Manager delegation chain:")
    println("  Valid: ${multiHopResult.valid}")
    println("  Path: ${multiHopResult.path.joinToString(" -> ")}")
    if (!multiHopResult.valid) {
        println("  Errors: ${multiHopResult.errors.joinToString(", ")}")
    }
    println()
    
    // Step 6: Issue credential using delegated authority
    println("Step 6: Issuing credential using delegated authority...")
    val credential = trustLayer.issue {
        credential {
            id("https://company.com/credentials/employee-${employeeDid.substringAfterLast(":")}")
            type("EmploymentCredential")
            issuer(hrManagerDid) // HR Manager issues on behalf of company
            subject {
                id(employeeDid)
                "employment" {
                    "company" to "Tech Corp"
                    "role" to "Software Engineer"
                    "startDate" to "2024-01-01"
                    "department" to "Engineering"
                }
            }
            issued(Instant.now())
        }
        by(issuerDid = hrManagerDid, keyId = "key-1")
    }
    println("✓ Credential issued by HR Manager (delegated authority)\n")
    
    // Step 7: Verify credential with delegation check
    println("Step 7: Verifying credential with delegation check...")
    val verification = trustLayer.verify {
        credential(credential)
        verifyDelegation()
        checkExpiration()
    }
    
    println("Credential verification:")
    println("  Valid: ${verification.valid}")
    println("  Delegation Valid: ${verification.delegationValid}")
    println("  Proof Valid: ${verification.proofValid}")
    println("  Errors: ${if (verification.errors.isEmpty()) "None" else verification.errors.joinToString(", ")}")
    println("  Warnings: ${if (verification.warnings.isEmpty()) "None" else verification.warnings.joinToString(", ")}")
    println()
    
    // Step 8: Demonstrate capability invocation
    println("Step 8: Demonstrating capability invocation...")
    trustLayer.updateDid {
        did(employeeDid)
        method(DidMethods.KEY)
        addCapabilityInvocation("$employeeDid#key-1")
    }
    println("✓ Updated employee DID with capability invocation")
    println("  This allows the employee to invoke capabilities (e.g., sign documents)")
    println()
    
    // Step 9: Show invalid delegation attempt
    println("Step 9: Testing invalid delegation...")
    val invalidDelegation = trustLayer.delegate {
        from(employeeDid) // Employee cannot delegate (not in CEO's chain)
        to(hrManagerDid)
        capability("issueCredentials")
    }
    
    println("Employee -> HR Manager delegation (should fail):")
    println("  Valid: ${invalidDelegation.valid}")
    if (!invalidDelegation.valid) {
        println("  Errors: ${invalidDelegation.errors.joinToString(", ")}")
    }
    println()
    
    println("=== Delegation Chain Scenario Complete ===")
}

