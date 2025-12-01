package com.trustweave.examples.trust

import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.types.ProofType
import com.trustweave.trust.types.VerificationResult
import com.trustweave.trust.dsl.credential.CredentialTypes
import com.trustweave.trust.types.CredentialType
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.wallet.CredentialOrganization
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
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
    val universityDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("University DID: ${universityDid.value}")

    val companyDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Company DID: ${companyDid.value}")

    val hrDeptDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("HR Department DID: ${hrDeptDid.value}")

    val studentDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Student DID: ${studentDid.value}")

    val verifierDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Verifier DID: ${verifierDid.value}\n")

    // Step 3: Set up trust anchors
    println("Step 3: Setting up trust anchors...")
    trustWeave.trust {
        // Add university as trusted anchor for education credentials
        addAnchor(universityDid.value) {
            credentialTypes("EducationCredential", "DegreeCredential")
            description("Trusted university for education credentials")
        }
        println("✓ Added university as trust anchor for education credentials")

        // Add company as trusted anchor for employment credentials
        addAnchor(companyDid.value) {
            credentialTypes("EmploymentCredential")
            description("Trusted company for employment credentials")
        }
        println("✓ Added company as trust anchor for employment credentials")
    }
    println()

    // Step 4: Set up delegation (company delegates to HR department)
    println("Step 4: Setting up capability delegation...")

    // Update company DID document to delegate capability to HR department
    trustWeave.updateDid {
        did(companyDid.value)
        method(DidMethods.KEY)
        addCapabilityDelegation("${hrDeptDid.value}#key-1")
    }
    println("✓ Company delegated credential issuance capability to HR department")

    // Verify delegation chain
    val delegationResult = trustWeave.delegate {
        from(companyDid.value)
        to(hrDeptDid.value)
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
    val degreeCredential = trustWeave.issue {
        credential {
            id("https://university.edu/credentials/degree-${studentDid.value.substringAfterLast(":")}")
            type(CredentialTypes.EDUCATION, CredentialType.Degree)
            issuer(universityDid.value)
            subject {
                id(studentDid.value)
                "degree" {
                    "type" to "Bachelor"
                    "field" to "Computer Science"
                    "institution" to "Tech University"
                    "year" to "2023"
                }
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = universityDid.value, keyId = "key-1")
    }
    println("✓ Issued degree credential from university")

    // Issue employment credential from HR department (delegated)
    val employmentCredential = trustWeave.issue {
        credential {
            id("https://company.com/credentials/employment-${studentDid.value.substringAfterLast(":")}")
            type("EmploymentCredential")
            issuer(hrDeptDid.value)
            subject {
                id(studentDid.value)
                "employment" {
                    "company" to "Tech Corp"
                    "role" to "Software Engineer"
                    "startDate" to "2024-01-01"
                }
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = hrDeptDid.value, keyId = "key-1")
    }
    println("✓ Issued employment credential from HR department (delegated)\n")

    // Step 6: Verify credentials with trust registry
    println("Step 6: Verifying credentials with trust registry...")

    // Verify degree credential
    val degreeVerification = trustWeave.verify {
        credential(degreeCredential)
        checkExpiration()
    }

    println("Degree credential verification:")
    when (degreeVerification) {
        is VerificationResult.Valid -> {
            println("  Valid: true")
            println("  Errors: None")
        }
        is VerificationResult.Invalid -> {
            println("  Valid: false")
            val errorList = when (degreeVerification) {
                is VerificationResult.Invalid.Expired -> degreeVerification.errors
                is VerificationResult.Invalid.Revoked -> degreeVerification.errors
                is VerificationResult.Invalid.InvalidProof -> degreeVerification.errors
                is VerificationResult.Invalid.IssuerResolutionFailed -> degreeVerification.errors
                is VerificationResult.Invalid.UntrustedIssuer -> degreeVerification.errors
                is VerificationResult.Invalid.SchemaValidationFailed -> degreeVerification.errors
                is VerificationResult.Invalid.MultipleFailures -> degreeVerification.errors
                is VerificationResult.Invalid.Other -> degreeVerification.errors
            }
            println("  Errors: ${errorList.joinToString(", ")}")
        }
    }

    // Check trust registry separately
    trustWeave.trust {
        val isTrusted = isTrusted(degreeCredential.issuer, degreeCredential.type.firstOrNull { it != "VerifiableCredential" })
        println("  Trust Registry Valid: $isTrusted")
    }

    // Verify employment credential (should check delegation)
    val employmentVerification = trustWeave.verify {
        credential(employmentCredential)
        checkExpiration()
    }

    println("\nEmployment credential verification:")
    when (employmentVerification) {
        is VerificationResult.Valid -> {
            println("  Valid: true")
            println("  Errors: None")
        }
        is VerificationResult.Invalid -> {
            println("  Valid: false")
            val errorList = when (employmentVerification) {
                is VerificationResult.Invalid.Expired -> employmentVerification.errors
                is VerificationResult.Invalid.Revoked -> employmentVerification.errors
                is VerificationResult.Invalid.InvalidProof -> employmentVerification.errors
                is VerificationResult.Invalid.IssuerResolutionFailed -> employmentVerification.errors
                is VerificationResult.Invalid.UntrustedIssuer -> employmentVerification.errors
                is VerificationResult.Invalid.SchemaValidationFailed -> employmentVerification.errors
                is VerificationResult.Invalid.MultipleFailures -> employmentVerification.errors
                is VerificationResult.Invalid.Other -> employmentVerification.errors
            }
            println("  Errors: ${errorList.joinToString(", ")}")
        }
    }

    // Check trust registry and delegation separately
    trustWeave.trust {
        val isTrusted = isTrusted(employmentCredential.issuer, employmentCredential.type.firstOrNull { it != "VerifiableCredential" })
        println("  Trust Registry Valid: $isTrusted")
    }

    val delegationCheck = trustWeave.delegate {
        from(companyDid.value)
        to(hrDeptDid.value)
    }
    println("  Delegation Valid: ${delegationCheck.valid}")
    println()

    // Step 7: Find trust paths
    println("Step 7: Finding trust paths...")
    trustWeave.trust {
        val trustPath = findTrustPath(
            com.trustweave.trust.types.VerifierIdentity(verifierDid),
            com.trustweave.trust.types.IssuerIdentity.from(universityDid.value, "key-1")
        )
        when (trustPath) {
            is com.trustweave.trust.types.TrustPath.Verified -> {
                println("Trust path from verifier to university:")
                println("  Path: ${trustPath.fullPath.joinToString(" -> ") { it.value }}")
                println("  Trust Score: ${trustPath.trustScore}")
                println("  Verified: ${trustPath.verified}")
            }
            is com.trustweave.trust.types.TrustPath.NotFound -> {
                println("No trust path found from verifier to university: ${trustPath.reason}")
            }
        }

        val trustPath2 = findTrustPath(
            com.trustweave.trust.types.VerifierIdentity(verifierDid),
            com.trustweave.trust.types.IssuerIdentity.from(companyDid.value, "key-1")
        )
        when (trustPath2) {
            is com.trustweave.trust.types.TrustPath.Verified -> {
                println("\nTrust path from verifier to company:")
                println("  Path: ${trustPath2.fullPath.joinToString(" -> ") { it.value }}")
                println("  Trust Score: ${trustPath2.trustScore}")
                println("  Verified: ${trustPath2.verified}")
            }
            is com.trustweave.trust.types.TrustPath.NotFound -> {
                println("No trust path found from verifier to company: ${trustPath2.reason}")
            }
        }
    }
    println()

    // Step 8: Demonstrate DID document updates with new fields
    println("Step 8: Updating DID document with capability relationships...")
    trustWeave.updateDid {
        did(studentDid.value)
        method(DidMethods.KEY)
        addCapabilityInvocation("${studentDid.value}#key-1")
        context("https://www.w3.org/ns/did/v1", "https://example.com/context/v1")
    }
    println("✓ Updated student DID document with capability invocation and context\n")

    // Step 9: Get trusted issuers
    println("Step 9: Getting trusted issuers...")
    trustWeave.trust {
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

