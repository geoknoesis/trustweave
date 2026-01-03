package org.trustweave.examples.academic

import org.trustweave.credential.model.SchemaFormat
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.storeIn
import org.trustweave.trust.dsl.wallet.QueryBuilder
import org.trustweave.trust.dsl.wallet.organize
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.types.*
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.wallet.CredentialOrganization
import org.trustweave.wallet.Wallet
import org.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import org.trustweave.credential.model.ProofType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    val trustWeave = TrustWeave.build {
        keys {
            provider(IN_MEMORY)
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
            defaultProofType(ProofType.Ed25519Signature2020)
            autoAnchor(false) // Set to true to auto-anchor credentials
        }

        revocation {
            provider(IN_MEMORY)
        }

        schemas {
            autoValidate(false)
            defaultFormat(SchemaFormat.JSON_SCHEMA)
        }

        // Configure trust registry
        trust {
            provider(IN_MEMORY)
        }
    }
    println("✓ Trust layer configured")

    // Step 2: Create DIDs using new DSL
    println("\nStep 2: Creating DIDs...")
    val universityDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }.getOrFail()
    println("University DID: $universityDid")

    val studentDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }.getOrFail()
    println("Student DID: $studentDid")

    // Step 3: Create student wallet using DSL
    println("\nStep 3: Creating student wallet...")
    val studentWallet: Wallet = trustWeave.wallet {
        id("student-wallet-${studentDid.value.substringAfterLast(":")}")
        holder(studentDid)
        enableOrganization()
        enablePresentation()
    }.getOrFail()
    println("Wallet created with ID: ${studentWallet.walletId}")

    // Step 4: University issues degree credential using DSL
    println("\nStep 4: University issues degree credential using DSL...")
    val kms = trustWeave.configuration.kmsService as? InMemoryKeyManagementService
        ?: InMemoryKeyManagementService()
    val issuerKeyResult = kms.generateKey(org.trustweave.kms.Algorithm.Ed25519)
    val issuerKey = when (issuerKeyResult) {
        is org.trustweave.kms.results.GenerateKeyResult.Success -> issuerKeyResult.keyHandle
        else -> throw IllegalStateException("Failed to generate key")
    }

    val issuedCredential = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/degree-${studentDid.value.substringAfterLast(":")}")
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
            issued(Clock.System.now())
            expires((365 * 10).days) // Valid for 10 years
        }
        signedBy(universityDid)
        withRevocation() // Auto-create status list
    }.getOrFail()

    println("Credential issued:")
    println("  - Type: ${issuedCredential.type.map { it.value }}")
    println("  - Issuer: ${issuedCredential.issuer}")
    println("  - Has proof: ${issuedCredential.proof != null}")
    if (issuedCredential.proof != null) {
        val proofType = when (val proof = issuedCredential.proof) {
            is org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof -> proof.type
            is org.trustweave.credential.model.vc.CredentialProof.JwtProof -> "JWT"
            is org.trustweave.credential.model.vc.CredentialProof.SdJwtVcProof -> "SD-JWT"
            null -> null
        }
        println("  - Proof type: $proofType")
    }
    if (issuedCredential.credentialStatus != null) {
        println("  - Revocation status: ${issuedCredential.credentialStatus?.id}")
    }

    // Step 5: Student stores credential in wallet using lifecycle DSL
    println("\nStep 5: Student stores credential in wallet...")
    val stored = issuedCredential.storeIn(studentWallet)
    println("Credential stored with ID: ${stored.id}")

    // Step 6: Organize credentials using new DSL
    println("\nStep 6: Organizing credentials...")
    if (studentWallet is CredentialOrganization) {
        val result = studentWallet.organize {
            collection("Education Credentials", "Academic degrees and certificates") {
                add(stored.id?.value ?: throw IllegalStateException("Credential must have ID"))
                tag(stored.id?.value ?: throw IllegalStateException("Credential must have ID"), "degree", "bachelor", "computer-science", "verified")
            }
        }
        println("Created ${result.collectionsCreated} collection(s)")
        println("Added tags: degree, bachelor, computer-science, verified")
    } else {
        println("Wallet does not support organization features")
    }

    // Step 7: Query credentials using enhanced query DSL
    println("\nStep 7: Querying credentials...")
    val degrees = studentWallet.query {
        (this as QueryBuilder).type("DegreeCredential")
        (this as QueryBuilder).valid()
    }
    println("Found ${degrees.size} valid degree credentials")

    // Step 8: Create presentation using presentation DSL
    println("\nStep 8: Creating presentation using presentation DSL...")
    val retrievedCredential = studentWallet.get(stored.id?.value ?: throw IllegalStateException("Credential must have ID"))
        ?: throw IllegalStateException("Credential not found in wallet")
    // Note: Presentation creation requires a PresentationService which is typically configured in TrustWeave
    // For this example, we'll create a simple presentation without proof
    val presentation = VerifiablePresentation(
        id = CredentialId("urn:example:presentation:${System.currentTimeMillis()}"),
        type = listOf(CredentialType.fromString("VerifiablePresentation")),
        verifiableCredential = listOf(retrievedCredential),
        holder = Iri(studentDid.value),
        challenge = "job-application-12345"
    )

    println("Presentation created:")
    println("  - Holder: ${presentation.holder}")
    println("  - Credentials: ${presentation.verifiableCredential.size}")
    println("  - Challenge: ${presentation.challenge}")

    // Step 9: Verify credential using lifecycle DSL
    println("\nStep 9: Verifying credential using lifecycle DSL...")
    val verificationResult = trustWeave.verify {
        credential(stored)
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
        is VerificationResult.Invalid -> {
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

    // Step 11: Demonstrate trust registry
    println("\nStep 11: Demonstrating trust registry...")
    trustWeave.trust {
        // Add university as trusted anchor
        addAnchor(universityDid.value) {
            credentialTypes("DegreeCredential", "EducationCredential")
            description("Trusted university for academic credentials")
        }
        println("✓ Added university as trust anchor")

        // Verify credential with trust registry
        val isTrusted = isTrusted(universityDid.value, "DegreeCredential")
        println("University trusted for DegreeCredential: $isTrusted")
    }

    // Step 12: Demonstrate DID document updates with new fields
    println("\nStep 12: Demonstrating DID document updates...")
    try {
        trustWeave.updateDid {
            did(universityDid.value)
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

