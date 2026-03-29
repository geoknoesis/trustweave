package org.trustweave.examples.academic

import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.credential.presentationResult
import org.trustweave.trust.types.PresentationResult
import org.trustweave.trust.dsl.wallet.QueryBuilder
import org.trustweave.trust.dsl.wallet.organize
import org.trustweave.trust.types.*
import org.trustweave.credential.results.VerificationResult
import org.trustweave.wallet.CredentialOrganization
import org.trustweave.wallet.Wallet
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

fun main() = runBlocking {
    println("=== Academic Credentials Scenario ===\n")

    // Step 1: Configure TrustWeave (simplified with smart defaults)
    println("Step 1: Configuring TrustWeave...")
    val kms = InMemoryKeyManagementService()

    val trustWeave = TrustWeave.build {
        keys { custom(kms) }  // Auto-signer created from KMS
        did { method(KEY) {} }   // Algorithm defaults to ED25519
        anchor { chain("algorand:testnet") { inMemory() } }
        credentials { defaultProofType(ProofType.Ed25519Signature2020) }
    }
    println("✓ TrustWeave configured")

    // Step 2: Create DIDs (simplified with auto key extraction)
    println("\nStep 2: Creating DIDs...")
    val (universityDid, issuerKeyId) = trustWeave.createDidWithKey().getOrThrow()
    println("University DID: ${universityDid.value}")
    println("✓ Key ID: $issuerKeyId")

    val studentDid = trustWeave.createDid().getOrThrowDid()
    println("Student DID: ${studentDid.value}")

    // Step 3: Create student wallet using DSL
    println("\nStep 3: Creating student wallet...")
    val studentWallet: Wallet = trustWeave.wallet {
        id("student-wallet-${studentDid.value.substringAfterLast(":")}")
        holder(studentDid.value)
        enableOrganization()
        enablePresentation()
    }.getOrThrow()
    println("Wallet created with ID: ${studentWallet.walletId}")

    // Step 4: University issues degree credential using DSL
    println("\nStep 4: University issues degree credential...")
    // CRITICAL: The key used for signing MUST match what's in the DID document
    // This ensures proof verification succeeds because the DID document contains the correct verification method
    // Reuse the issuerKeyId extracted earlier from the verification method
    // The IssuanceDsl will construct verificationMethodId as "$issuerDid#$keyId" which matches the DID document

    val issuedCredential = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/degree-${studentDid.value.substringAfterLast(":")}")
            type("DegreeCredential", "BachelorDegreeCredential")
            issuer(universityDid)  // Accept Did type directly
            subject {
                id(studentDid)  // Accept Did type directly
                "degree" {
                    "type" to "BachelorDegree"
                    "name" to "Bachelor of Science in Computer Science"
                    "university" to "Example University"
                    "graduationDate" to "2023-05-15"
                    "gpa" to "3.8"
                }
            }
            issued(Clock.System.now())
            expires((365 * 10).days) // Valid for 10 years
        }
        signedBy(universityDid)  // Auto-extract key ID
    }.getOrThrow()

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
        val result = studentWallet.organize {
            collection("Education Credentials", "Academic degrees and certificates") {
                add(credentialId)
                tag(credentialId, "degree", "bachelor", "computer-science", "verified")
            }
        }
        println("Created ${result.collectionsCreated} collection(s)")
        println("Added tags: degree, bachelor, computer-science, verified")
    }

    // Step 7: Query credentials
    println("\nStep 7: Querying credentials...")
    val degrees = studentWallet.query {
        (this as QueryBuilder).type("DegreeCredential")
        (this as QueryBuilder).valid()
    }
    println("Found ${degrees.size} valid degree credentials")

    // Step 8: Build verifiable presentation via TrustWeave (sealed PresentationResult)
    println("\nStep 8: Creating presentation for job application...")
    when (val pr = trustWeave.presentationResult {
        holder(studentDid)
        credentials(issuedCredential)
        challenge("job-application-12345")
    }) {
        is PresentationResult.Success -> {
            val presentation = pr.presentation
            println("Presentation created:")
            println("  - Holder: ${presentation.holder}")
            println("  - Credentials: ${presentation.verifiableCredential.size}")
            println("  - Challenge: ${presentation.challenge}")
        }
        is PresentationResult.Failure -> {
            println("Presentation not created: ${pr.errors.joinToString()}")
        }
    }

    // Step 9: Verify credential using DSL
    println("\nStep 9: Verifying credential...")
    val verificationResult = trustWeave.verify {
        credential(issuedCredential)
        checkRevocation()
        checkExpiration()
    }

    when (verificationResult) {
        is VerificationResult.Valid -> {
            println("✅ Credential is valid!")
            println("  - Proof valid: ${verificationResult.proofValid}")
            println("  - Issuer valid: ${verificationResult.issuerValid}")
            println("  - Not expired: true")
            println("  - Not revoked: true")
        }
        else -> {
            println("❌ Credential verification failed: ${verificationResult.allErrors.joinToString("; ")}")
        }
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
    issuerDid: org.trustweave.did.identifiers.Did,
    studentDid: org.trustweave.did.identifiers.Did,
    degreeName: String,
    universityName: String,
    graduationDate: String,
    gpa: String
): VerifiableCredential {
    return credential {
        id("https://example.edu/credentials/degree-${studentDid.value.substringAfterLast(":")}")
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
        issued(Clock.System.now())
        expires(365.days * 10) // Valid for 10 years
    }
}

