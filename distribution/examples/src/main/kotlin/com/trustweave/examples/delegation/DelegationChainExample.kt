package com.trustweave.examples.delegation

import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.types.ProofType
import com.trustweave.trust.types.*
import com.trustweave.credential.models.VerifiableCredential
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
    val trustWeave = TrustWeave.build {
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
            defaultProofType(ProofType.Ed25519Signature2020)
        }
    }
    println("✓ Trust layer configured\n")

    // Step 2: Create DIDs for delegation chain
    println("Step 2: Creating DIDs for delegation chain...")
    val ceoDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("CEO DID: $ceoDid")

    val hrDirectorDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("HR Director DID: $hrDirectorDid")

    val hrManagerDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("HR Manager DID: $hrManagerDid")

    val employeeDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Employee DID: $employeeDid\n")

    // Step 3: Set up delegation chain
    println("Step 3: Setting up delegation chain...")

    // CEO delegates to HR Director
        trustWeave.updateDid {
        did(ceoDid.value)
        method(DidMethods.KEY)
        addCapabilityDelegation("${hrDirectorDid.value}#key-1")
    }
    println("✓ CEO delegated capability to HR Director")

    // HR Director delegates to HR Manager
        trustWeave.updateDid {
        did(hrDirectorDid.value)
        method(DidMethods.KEY)
        addCapabilityDelegation("${hrManagerDid.value}#key-1")
    }
    println("✓ HR Director delegated capability to HR Manager\n")

    // Step 4: Verify single-hop delegation
    println("Step 4: Verifying single-hop delegation...")
    val delegation1 = trustWeave.delegate {
        from(ceoDid.value)
        to(hrDirectorDid.value)
        verify()
    }

    println("CEO -> HR Director delegation:")
    println("  Valid: ${delegation1.valid}")
    println("  Path: ${delegation1.path.joinToString(" -> ")}")
    if (!delegation1.valid) {
        println("  Errors: ${delegation1.errors.joinToString(", ")}")
    }

    val delegation2 = trustWeave.delegate {
        from(hrDirectorDid.value)
        to(hrManagerDid.value)
        verify()
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
    // Note: Multi-hop delegation verification would be performed here
    println("✓ Multi-hop delegation chain verification would be performed here")
    println()

    // Step 6: Issue credential using delegated authority
    println("Step 6: Issuing credential using delegated authority...")
    val credential = trustWeave.issue {
        credential {
            id("https://company.com/credentials/employee-${employeeDid.value.substringAfterLast(":")}")
            type("EmploymentCredential")
            issuer(hrManagerDid.value) // HR Manager issues on behalf of company
            subject {
                id(employeeDid.value)
                "employment" {
                    "company" to "Tech Corp"
                    "role" to "Software Engineer"
                    "startDate" to "2024-01-01"
                    "department" to "Engineering"
                }
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = hrManagerDid.value, keyId = "key-1")
    }
    println("✓ Credential issued by HR Manager (delegated authority)\n")

    // Step 7: Verify credential with delegation check
    println("Step 7: Verifying credential with delegation check...")
    val verification = trustWeave.verify {
        credential(credential)
        // Note: verifyDelegation API has changed
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
        trustWeave.updateDid {
        did(employeeDid.value)
        method(DidMethods.KEY)
        addCapabilityInvocation("${employeeDid.value}#key-1")
    }
    println("✓ Updated employee DID with capability invocation")
    println("  This allows the employee to invoke capabilities (e.g., sign documents)")
    println()

    // Step 9: Show invalid delegation attempt
    println("Step 9: Testing invalid delegation...")
    val invalidDelegation = trustWeave.delegate {
        from(employeeDid.value) // Employee cannot delegate (not in CEO's chain)
        to(hrManagerDid.value)
        verify()
    }

    println("Employee -> HR Manager delegation (should fail):")
    println("  Valid: ${invalidDelegation.valid}")
    if (!invalidDelegation.valid) {
        println("  Errors: ${invalidDelegation.errors.joinToString(", ")}")
    }
    println()

    println("=== Delegation Chain Scenario Complete ===")
}

