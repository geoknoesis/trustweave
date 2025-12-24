package org.trustweave.examples.academic

import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.services.DidMethodFactory
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.exception.KmsException
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.getOrFail
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.wallet.QueryBuilder
import org.trustweave.trust.dsl.wallet.organize
import org.trustweave.trust.types.*
import org.trustweave.wallet.CredentialOrganization
import org.trustweave.wallet.Wallet
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

fun main() = runBlocking {
    println("=== Academic Credentials Scenario ===\n")

    // Step 1: Configure Trust Layer
    println("Step 1: Configuring trust layer...")
    // Create KMS instance that will be shared across all operations
    val kms = InMemoryKeyManagementService()
    // Capture reference for closure
    val kmsRef = kms

    val trustWeave = TrustWeave.build {
        // Explicitly configure factories to ensure same KMS instance is used
        factories(
            didMethodFactory = object : DidMethodFactory {
                override suspend fun create(
                    methodName: String,
                    config: DidCreationOptions,
                    kms: KeyManagementService
                ): DidMethod? {
                    if (methodName == "key") {
                        // CRITICAL: Verify this is the same KMS instance we're using for signing
                        if (kms !== kmsRef) {
                            throw IllegalStateException(
                                "KMS instance mismatch: Factory received different KMS instance than signer. " +
                                "This will cause KeyNotFound errors during signing."
                            )
                        }
                        return DidKeyMockMethod(kms)
                    }
                    return null
                }
            }
        )
        
        keys {
            custom(kmsRef)
            // Ensure signer uses the same KMS instance that was used to create DIDs
            // Extract fragment if keyId is in format "did:key:xxx#key-id", otherwise use as-is
            signer { data, keyId ->
                // The keyId passed to the signer should match what's stored in KMS
                // If it's a full verification method ID, extract the fragment part
                val actualKeyId = if (keyId.contains("#")) {
                    keyId.substringAfter("#")
                } else {
                    keyId
                }
                when (val signResult = kmsRef.sign(org.trustweave.core.identifiers.KeyId(actualKeyId), data)) {
                    is org.trustweave.kms.results.SignResult.Success -> signResult.signature
                    is org.trustweave.kms.results.SignResult.Failure.KeyNotFound -> throw IllegalStateException("Signing failed: Key not found: ${signResult.keyId}")
                    is org.trustweave.kms.results.SignResult.Failure.UnsupportedAlgorithm -> throw IllegalStateException("Signing failed: Unsupported algorithm")
                    is org.trustweave.kms.results.SignResult.Failure.Error -> throw IllegalStateException("Signing failed: ${signResult.reason}")
                }
            }
            algorithm(ED25519)
        }

        did {
            method(KEY) {
                algorithm(ED25519)
            }
        }

        anchor {
            chain("algorand:testnet") {
                inMemory()
            }
        }

        credentials {
            defaultProofType(ProofType.Ed25519Signature2020)
            autoAnchor(false)
        }
    }
    println("✓ Trust layer configured")

    // Step 2: Create DIDs using TrustWeave (ensures keys are in the same KMS)
    println("\nStep 2: Creating DIDs...")
    val universityDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }.getOrFail()
    println("University DID: ${universityDid.value}")
    
    // Verify the key exists in KMS immediately after DID creation
    val universityDidResolution = trustWeave.configuration.registries.didRegistry.resolve(universityDid.value)
        ?: throw IllegalStateException("Failed to resolve university DID")
    val universityDidDoc = when (universityDidResolution) {
        is org.trustweave.did.resolver.DidResolutionResult.Success -> universityDidResolution.document
        else -> throw IllegalStateException("Failed to resolve university DID")
    }
    val verificationMethod = universityDidDoc.verificationMethod.firstOrNull()
        ?: throw IllegalStateException("No verification method found in university DID document")
    // Extract key ID from verification method ID (e.g., "did:key:xxx#key-1" -> "key-1")
    // This will be used later for signing the credential
    val issuerKeyId = verificationMethod.id.value.substringAfter("#")
    try {
        kmsRef.getPublicKey(org.trustweave.core.identifiers.KeyId(issuerKeyId))
        println("✓ Key verified in KMS: $issuerKeyId")
    } catch (e: KmsException.KeyNotFound) {
        throw IllegalStateException(
            "Key '$issuerKeyId' not found in KMS immediately after DID creation. " +
            "This indicates the DID method is using a different KMS instance.",
            e
        )
    }

    val studentDid = trustWeave.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }.getOrFail()
    println("Student DID: ${studentDid.value}")

    // Step 3: Create student wallet using DSL
    println("\nStep 3: Creating student wallet...")
    val studentWallet: Wallet = trustWeave.wallet {
        id("student-wallet-${studentDid.value.substringAfterLast(":")}")
        holder(studentDid.value)
        enableOrganization()
        enablePresentation()
    }.getOrFail()
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
            issuer(universityDid.value)
            subject {
                id(studentDid.value)
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
        signedBy(issuerDid = universityDid.value, keyId = issuerKeyId)
    }.getOrFail()

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

    // Step 8: Create presentation (for demonstration purposes)
    // Note: In a real scenario, this would be signed with the holder's key
    println("\nStep 8: Creating presentation for job application...")
    val presentation = VerifiablePresentation(
        id = CredentialId("urn:example:presentation:${System.currentTimeMillis()}"),
        type = listOf(CredentialType.fromString("VerifiablePresentation")),
        verifiableCredential = listOf(issuedCredential),
        holder = Iri(studentDid.value),
        challenge = "job-application-12345"
    )

    println("Presentation created:")
    println("  - Holder: ${presentation.holder}")
    println("  - Credentials: ${presentation.verifiableCredential.size}")
    println("  - Challenge: ${presentation.challenge}")

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
        is VerificationResult.Invalid.Expired -> {
            println("❌ Credential expired at ${verificationResult.expiredAt}")
            verificationResult.errors.forEach { println("  - $it") }
        }
        is VerificationResult.Invalid.Revoked -> {
            println("❌ Credential revoked")
            verificationResult.errors.forEach { println("  - $it") }
        }
        else -> {
            println("❌ Credential verification failed:")
            verificationResult.errors.forEach { println("  - $it") }
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
        issued(Clock.System.now())
        expires(365.days * 10) // Valid for 10 years
    }
}

