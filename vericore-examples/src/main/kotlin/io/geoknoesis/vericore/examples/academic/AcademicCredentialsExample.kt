package io.geoknoesis.vericore.examples.academic

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import io.geoknoesis.vericore.credential.CredentialIssuanceOptions
import io.geoknoesis.vericore.credential.CredentialVerificationOptions
import io.geoknoesis.vericore.credential.PresentationOptions
import io.geoknoesis.vericore.credential.issuer.CredentialIssuer
import io.geoknoesis.vericore.credential.verifier.CredentialVerifier
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import io.geoknoesis.vericore.credential.wallet.CredentialQueryBuilder
import io.geoknoesis.vericore.testkit.credential.InMemoryWallet
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import io.geoknoesis.vericore.did.DidRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    println("=== Academic Credentials Scenario ===\n")
    
    // Step 1: Setup - Create KMS and DID methods
    println("Step 1: Setting up services...")
    val universityKms = InMemoryKeyManagementService()
    val studentKms = InMemoryKeyManagementService()
    
    val didMethod = DidKeyMockMethod(universityKms)
    DidRegistry.register(didMethod)
    
    // Step 2: Create DIDs
    println("\nStep 2: Creating DIDs...")
    val universityDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
    println("University DID: ${universityDid.id}")
    
    val studentDid = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
    println("Student DID: ${studentDid.id}")
    
    // Step 3: Create student wallet
    println("\nStep 3: Creating student wallet...")
    val studentWallet = InMemoryWallet(
        walletDid = studentDid.id,
        holderDid = studentDid.id
    )
    println("Wallet created with ID: ${studentWallet.walletId}")
    
    // Step 4: University issues degree credential
    println("\nStep 4: University issues degree credential...")
    val degreeCredential = createDegreeCredential(
        issuerDid = universityDid.id,
        studentDid = studentDid.id,
        degreeName = "Bachelor of Science in Computer Science",
        universityName = "Example University",
        graduationDate = "2023-05-15",
        gpa = "3.8"
    )
    
    // Issue credential with proof
    val issuerKey = universityKms.generateKey("Ed25519")
    val proofGenerator = Ed25519ProofGenerator(
        signer = { data, keyId -> universityKms.sign(keyId, data) },
        getPublicKeyId = { keyId -> issuerKey.id }
    )
    ProofGeneratorRegistry.register(proofGenerator)
    
    val credentialIssuer = CredentialIssuer(
        proofGenerator = proofGenerator,
        resolveDid = { did -> DidRegistry.resolve(did).document != null }
    )
    
    val issuedCredential = credentialIssuer.issue(
        credential = degreeCredential,
        issuerDid = universityDid.id,
        keyId = issuerKey.id,
        options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
    )
    
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
    val educationCollection = studentWallet.createCollection(
        name = "Education Credentials",
        description = "Academic degrees and certificates"
    )
    studentWallet.addToCollection(credentialId, educationCollection)
    studentWallet.tagCredential(credentialId, setOf("degree", "bachelor", "computer-science", "verified"))
    
    println("Created collection: $educationCollection")
    println("Added tags: degree, bachelor, computer-science, verified")
    
    // Step 7: Query credentials
    println("\nStep 7: Querying credentials...")
    val degrees = studentWallet.query {
        byType("DegreeCredential")
        valid()
    }
    println("Found ${degrees.size} valid degree credentials")
    
    // Step 8: Create presentation for job application
    println("\nStep 8: Creating presentation for job application...")
    val presentation = studentWallet.createPresentation(
        credentialIds = listOf(credentialId),
        holderDid = studentDid.id,
        options = PresentationOptions(
            holderDid = studentDid.id,
            proofType = "Ed25519Signature2020",
            challenge = "job-application-12345"
        )
    )
    
    println("Presentation created:")
    println("  - Holder: ${presentation.holder}")
    println("  - Credentials: ${presentation.verifiableCredential.size}")
    println("  - Challenge: ${presentation.challenge}")
    
    // Step 9: Verify credential
    println("\nStep 9: Verifying credential...")
    val verifier = CredentialVerifier(
        resolveDid = { did -> DidRegistry.resolve(did).document != null }
    )
    
    val verificationResult = verifier.verify(
        credential = issuedCredential,
        options = CredentialVerificationOptions(
            checkRevocation = true,
            checkExpiration = true
        )
    )
    
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

fun createDegreeCredential(
    issuerDid: String,
    studentDid: String,
    degreeName: String,
    universityName: String,
    graduationDate: String,
    gpa: String
): VerifiableCredential {
    val now = Instant.now()
    val expirationDate = now.plus(365 * 10, ChronoUnit.DAYS) // Valid for 10 years
    
    return VerifiableCredential(
        id = "https://example.edu/credentials/degree-${studentDid.substringAfterLast(":")}",
        type = listOf("VerifiableCredential", "DegreeCredential", "BachelorDegreeCredential"),
        issuer = issuerDid,
        credentialSubject = buildJsonObject {
            put("id", studentDid)
            put("degree", buildJsonObject {
                put("type", "BachelorDegree")
                put("name", degreeName)
                put("university", universityName)
                put("graduationDate", graduationDate)
                put("gpa", gpa)
            })
        },
        issuanceDate = now.toString(),
        expirationDate = expirationDate.toString(),
        credentialSchema = null // Schema validation skipped for example
    )
}

