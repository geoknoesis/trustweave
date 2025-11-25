---
title: Employee Onboarding and Background Verification Scenario
---

# Employee Onboarding and Background Verification Scenario

This guide demonstrates how to build a complete employee onboarding system using TrustWeave. You'll learn how employers can verify candidate credentials (education, certifications, work history), how background check providers can issue verification credentials, and how the entire onboarding process can be streamlined with verifiable credentials.

## What You'll Build

By the end of this tutorial, you'll have:

- ✅ Created DIDs for employer, candidate, educational institutions, and background check provider
- ✅ Issued Verifiable Credentials for education, certifications, and work history
- ✅ Created background check verification credentials
- ✅ Built candidate credential wallet with organized credentials
- ✅ Created comprehensive presentation for employer verification
- ✅ Verified all credentials cryptographically
- ✅ Implemented selective disclosure for privacy

## Big Picture & Significance

### The Employee Onboarding Challenge

Employee onboarding is a critical business process that involves verifying multiple credentials, conducting background checks, and ensuring compliance. Traditional onboarding is slow, expensive, and often requires manual verification of each credential.

**Industry Context:**
- **Market Size**: Global HR technology market projected to reach $35 billion by 2028
- **Onboarding Time**: Average onboarding takes 2-4 weeks
- **Verification Costs**: Companies spend $500-2000 per hire on background checks
- **Fraud Impact**: Resume fraud affects 40-50% of job applications
- **Compliance**: Strict regulations require thorough verification

**Why This Matters:**
1. **Speed**: Reduce onboarding time from weeks to days
2. **Cost Reduction**: Eliminate expensive manual verification
3. **Fraud Prevention**: Cryptographic proof prevents credential fraud
4. **Privacy**: Candidates control what information is shared
5. **Compliance**: Automated compliance with employment regulations
6. **Efficiency**: Streamlined hiring process for all parties

### The Onboarding Verification Problem

Traditional employee onboarding faces critical issues:
- **Slow Process**: Manual verification takes weeks
- **High Costs**: Background checks and verification are expensive
- **Fraud Vulnerability**: Fake credentials are common
- **No Privacy**: Candidates must share all information
- **Not Portable**: Credentials tied to specific employers
- **Compliance Risk**: Manual processes prone to errors

## Value Proposition

### Problems Solved

1. **Instant Verification**: Cryptographic proof without contacting institutions
2. **Fraud Prevention**: Tamper-proof credentials cannot be forged
3. **Privacy Control**: Selective disclosure shows only necessary information
4. **Cost Reduction**: Eliminates expensive verification processes
5. **Compliance**: Automated compliance with employment regulations
6. **Portability**: Credentials work across employers
7. **Efficiency**: 10x faster onboarding process

### Business Benefits

**For Employers:**
- **Cost Savings**: 70-80% reduction in verification costs
- **Speed**: Reduce onboarding time by 80%
- **Trust**: Cryptographic proof of authenticity
- **Compliance**: Automated regulatory compliance
- **Efficiency**: Streamlined hiring process

**For Candidates:**
- **Control**: Own and control credentials
- **Privacy**: Share only necessary information
- **Portability**: Credentials work across employers
- **Speed**: Faster job application process
- **Transparency**: Clear verification status

**For Background Check Providers:**
- **Efficiency**: Automated credential verification
- **Trust**: Cryptographic proof of verification
- **Scalability**: Handle more verifications
- **Compliance**: Automated compliance reporting

### ROI Considerations

- **Verification Costs**: 70-80% reduction in verification costs
- **Onboarding Time**: 80% reduction in onboarding time
- **Fraud Prevention**: Eliminates credential fraud
- **Compliance**: Automated regulatory compliance
- **Candidate Experience**: Improved candidate satisfaction

## Understanding the Problem

Traditional employee onboarding has several problems:

1. **Verification is slow**: Manual verification takes weeks
2. **Fraud is common**: Fake credentials are prevalent
3. **High costs**: Background checks are expensive
4. **No privacy**: Candidates must share all information
5. **Compliance risk**: Manual processes prone to errors

TrustWeave solves this by enabling:

- **Instant verification**: Cryptographic proof without contacting institutions
- **Tamper-proof**: Credentials are cryptographically signed
- **Privacy-preserving**: Selective disclosure shows only what's needed
- **Self-sovereign**: Candidates control their own credentials
- **Compliance**: Automated compliance with regulations

## How It Works: The Onboarding Flow

```mermaid
flowchart TD
    A["Educational Institution<br/>Issues Degree Credential"] -->|stores| B["Candidate Wallet<br/>Stores all credentials<br/>Organizes by category"]
    C["Previous Employer<br/>Issues Work History Credential"] -->|stores| B
    D["Certification Body<br/>Issues Certification Credential"] -->|stores| B
    E["Background Check Provider<br/>Verifies & Issues Verification Credential"] -->|stores| B
    B -->|presents| F["Employer Verifier<br/>Receives comprehensive presentation<br/>Verifies all credentials<br/>Checks background check status"]
    
    style A fill:#1976d2,stroke:#0d47a1,stroke-width:2px,color:#fff
    style C fill:#1976d2,stroke:#0d47a1,stroke-width:2px,color:#fff
    style D fill:#1976d2,stroke:#0d47a1,stroke-width:2px,color:#fff
    style E fill:#f57c00,stroke:#e65100,stroke-width:2px,color:#fff
    style B fill:#388e3c,stroke:#1b5e20,stroke-width:2px,color:#fff
    style F fill:#c2185b,stroke:#880e4f,stroke-width:2px,color:#fff
```

## Prerequisites

- Java 21+
- Kotlin 2.2.0+
- Gradle 8.5+
- Basic understanding of Kotlin and coroutines

## Step 1: Add Dependencies

Add TrustWeave dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core TrustWeave modules
    implementation("com.trustweave:trustweave-all:1.0.0-SNAPSHOT")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

**Result:** After syncing, you can run every snippet below without adding more modules.

## Step 2: Complete Runnable Example

Here's the full employee onboarding flow using the TrustWeave facade API. This complete, copy-paste ready example demonstrates the entire workflow from credential issuance to employer verification.

```kotlin
package com.example.employee.onboarding

import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.credential.PresentationOptions
import com.trustweave.credential.wallet.Wallet
import com.trustweave.spi.services.WalletCreationOptionsBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    println("=".repeat(70))
    println("Employee Onboarding Scenario - Complete End-to-End Example")
    println("=".repeat(70))
    
    // Step 1: Create TrustWeave instance
    val TrustWeave = TrustWeave.create()
    println("\n✅ TrustWeave initialized")
    
    // Step 2: Create DIDs for all parties
    val universityDidDoc = TrustWeave.dids.create()
    val universityDid = universityDidDoc.id
    val universityKeyId = universityDidDoc.verificationMethod.firstOrNull()?.id
        ?: error("No verification method found")
    
    val previousEmployerDidDoc = TrustWeave.dids.create()
    val previousEmployerDid = previousEmployerDidDoc.id
    val previousEmployerKeyId = previousEmployerDidDoc.verificationMethod.firstOrNull()?.id
        ?: error("No verification method found")
    
    val certificationBodyDidDoc = TrustWeave.dids.create()
    val certificationBodyDid = certificationBodyDidDoc.id
    val certificationBodyKeyId = certificationBodyDidDoc.verificationMethod.firstOrNull()?.id
        ?: error("No verification method found")
    
    val backgroundCheckProviderDidDoc = TrustWeave.dids.create()
    val backgroundCheckProviderDid = backgroundCheckProviderDidDoc.id
    val backgroundCheckProviderKeyId = backgroundCheckProviderDidDoc.verificationMethod.firstOrNull()?.id
        ?: error("No verification method found")
    
    val candidateDidDoc = TrustWeave.dids.create()
    val candidateDid = candidateDidDoc.id
    
    val employerDidDoc = TrustWeave.dids.create()
    val employerDid = employerDidDoc.id
    
    println("✅ University DID: $universityDid")
    println("✅ Previous Employer DID: $previousEmployerDid")
    println("✅ Certification Body DID: $certificationBodyDid")
    println("✅ Background Check Provider DID: $backgroundCheckProviderDid")
    println("✅ Candidate DID: $candidateDid")
    println("✅ Employer DID: $employerDid")
    
    // Step 3: Issue education credential
    val educationCredential = TrustWeave.issueCredential(
        issuerDid = universityDid,
        issuerKeyId = universityKeyId,
        credentialSubject = buildJsonObject {
            put("id", candidateDid)
            put("degree", buildJsonObject {
                put("type", "MasterDegree")
                put("name", "Master of Business Administration")
                put("university", "Business School University")
                put("graduationDate", "2020-06-15")
                put("gpa", "3.9")
            })
        },
        types = listOf("VerifiableCredential", "EducationCredential", "DegreeCredential"),
        expirationDate = Instant.now().plus(50, ChronoUnit.YEARS).toString()
    ).getOrThrow()
    
    println("\n✅ Education credential issued: ${educationCredential.id}")
    
    // Step 4: Issue work history credential
    val workHistoryCredential = TrustWeave.issueCredential(
        issuerDid = previousEmployerDid,
        issuerKeyId = previousEmployerKeyId,
        credentialSubject = buildJsonObject {
            put("id", candidateDid)
            put("employment", buildJsonObject {
                put("company", "Tech Corp Inc")
                put("position", "Senior Software Engineer")
                put("startDate", "2020-07-01")
                put("endDate", "2023-12-31")
                put("responsibilities", listOf(
                    "Led development of microservices architecture",
                    "Mentored junior developers",
                    "Improved system performance by 40%"
                ))
                put("performanceRating", "Exceeds Expectations")
            })
        },
        types = listOf("VerifiableCredential", "EmploymentCredential", "WorkHistoryCredential"),
        expirationDate = Instant.now().plus(10, ChronoUnit.YEARS).toString()
    ).getOrThrow()
    
    println("✅ Work history credential issued: ${workHistoryCredential.id}")
    
    // Step 5: Issue certification credential
    val certificationCredential = TrustWeave.issueCredential(
        issuerDid = certificationBodyDid,
        issuerKeyId = certificationBodyKeyId,
        credentialSubject = buildJsonObject {
            put("id", candidateDid)
            put("certification", buildJsonObject {
                put("name", "AWS Certified Solutions Architect")
                put("issuingOrganization", "Amazon Web Services")
                put("issueDate", "2021-03-15")
                put("expirationDate", "2024-03-15")
                put("certificationNumber", "AWS-SA-12345")
                put("level", "Professional")
            })
        },
        types = listOf("VerifiableCredential", "CertificationCredential", "ProfessionalCertificationCredential"),
        expirationDate = "2024-03-15T00:00:00Z"
    ).getOrThrow()
    
    println("✅ Certification credential issued: ${certificationCredential.id}")
    
    // Step 6: Create candidate wallet and store all credentials
    val candidateWallet = TrustWeave.createWallet(
        holderDid = candidateDid,
        options = WalletCreationOptionsBuilder().apply {
            enableOrganization = true
            enablePresentation = true
        }.build()
    ).getOrThrow()
    
    val educationCredentialId = candidateWallet.store(educationCredential)
    val workHistoryCredentialId = candidateWallet.store(workHistoryCredential)
    val certificationCredentialId = candidateWallet.store(certificationCredential)
    
    println("\n✅ All credentials stored in candidate wallet")
    
    // Step 7: Organize credentials with collections and tags
    candidateWallet.withOrganization { org ->
        val educationCollectionId = org.createCollection("Education", "Educational credentials")
        val employmentCollectionId = org.createCollection("Employment", "Work history and employment credentials")
        val certificationCollectionId = org.createCollection("Certifications", "Professional certifications")
        
        org.addToCollection(educationCredentialId, educationCollectionId)
        org.addToCollection(workHistoryCredentialId, employmentCollectionId)
        org.addToCollection(certificationCredentialId, certificationCollectionId)
        
        org.tagCredential(educationCredentialId, setOf("education", "mba", "degree", "verified"))
        org.tagCredential(workHistoryCredentialId, setOf("employment", "work-history", "verified"))
        org.tagCredential(certificationCredentialId, setOf("certification", "aws", "professional", "verified"))
        
        println("✅ Credentials organized into collections")
    }
    
    // Step 8: Background check provider verifies credentials and issues verification credential
    val backgroundCheckCredential = TrustWeave.issueCredential(
        issuerDid = backgroundCheckProviderDid,
        issuerKeyId = backgroundCheckProviderKeyId,
        credentialSubject = buildJsonObject {
            put("id", candidateDid)
            put("backgroundCheck", buildJsonObject {
                put("checkType", "Comprehensive")
                put("checkDate", Instant.now().toString())
                put("status", "Passed")
                put("verifiedCredentials", listOf(
                    "Education: Verified",
                    "Employment: Verified",
                    "Certification: Verified",
                    "Criminal Record: Clear",
                    "Credit Check: Passed"
                ))
                put("checkProvider", "Trusted Background Checks Inc")
                put("reportId", "BC-2024-001234")
            })
        },
        types = listOf("VerifiableCredential", "BackgroundCheckCredential", "VerificationCredential"),
        expirationDate = Instant.now().plus(1, ChronoUnit.YEARS).toString()
    ).getOrThrow()
    
    val backgroundCheckCredentialId = candidateWallet.store(backgroundCheckCredential)
    candidateWallet.withOrganization { org ->
        val verificationCollectionId = org.createCollection("Verifications", "Background checks and verifications")
        org.addToCollection(backgroundCheckCredentialId, verificationCollectionId)
        org.tagCredential(backgroundCheckCredentialId, setOf("background-check", "verification", "passed"))
    }
    
    println("✅ Background check credential issued and stored")
    
    // Step 9: Create comprehensive presentation for employer
    val presentation = candidateWallet.withPresentation { pres ->
        pres.createPresentation(
            credentialIds = listOf(
                educationCredentialId,
                workHistoryCredentialId,
                certificationCredentialId,
                backgroundCheckCredentialId
            ),
            holderDid = candidateDid,
            options = PresentationOptions(
                holderDid = candidateDid,
                challenge = "job-application-${System.currentTimeMillis()}"
            )
        )
    } ?: error("Presentation capability not available")
    
    println("\n✅ Presentation created for employer")
    println("   Holder: ${presentation.holder}")
    println("   Credentials: ${presentation.verifiableCredential.size}")
    
    // Step 10: Employer verifies all credentials
    println("\n📋 Employer Verification Process:")
    
    val educationVerification = TrustWeave.verifyCredential(educationCredential).getOrThrow()
    println("Education Credential: ${if (educationVerification.valid) "✅ VALID" else "❌ INVALID"}")
    
    val workHistoryVerification = TrustWeave.verifyCredential(workHistoryCredential).getOrThrow()
    println("Work History Credential: ${if (workHistoryVerification.valid) "✅ VALID" else "❌ INVALID"}")
    
    val certificationVerification = TrustWeave.verifyCredential(certificationCredential).getOrThrow()
    println("Certification Credential: ${if (certificationVerification.valid) "✅ VALID" else "❌ INVALID"}")
    
    val backgroundCheckVerification = TrustWeave.verifyCredential(backgroundCheckCredential).getOrThrow()
    println("Background Check Credential: ${if (backgroundCheckVerification.valid) "✅ VALID" else "❌ INVALID"}")
    
    // Step 11: Display wallet statistics
    val stats = candidateWallet.getStatistics()
    println("\n📊 Candidate Wallet Statistics:")
    println("   Total credentials: ${stats.totalCredentials}")
    println("   Valid credentials: ${stats.validCredentials}")
    println("   Collections: ${stats.collectionsCount}")
    println("   Tags: ${stats.tagsCount}")
    
    // Step 12: Summary
    val allValid = listOf(
        educationVerification,
        workHistoryVerification,
        certificationVerification,
        backgroundCheckVerification
    ).all { it.valid }
    
    if (allValid) {
        println("\n" + "=".repeat(70))
        println("✅ EMPLOYEE ONBOARDING VERIFICATION COMPLETE")
        println("   All credentials verified successfully")
        println("   Candidate is eligible for employment")
        println("=".repeat(70))
    } else {
        println("\n" + "=".repeat(70))
        println("❌ VERIFICATION FAILED")
        println("   Some credentials could not be verified")
        println("=".repeat(70))
    }
}
```

**Expected Output:**
```
======================================================================
Employee Onboarding Scenario - Complete End-to-End Example
======================================================================

✅ TrustWeave initialized
✅ University DID: did:key:z6Mk...
✅ Previous Employer DID: did:key:z6Mk...
✅ Certification Body DID: did:key:z6Mk...
✅ Background Check Provider DID: did:key:z6Mk...
✅ Candidate DID: did:key:z6Mk...
✅ Employer DID: did:key:z6Mk...

✅ Education credential issued: urn:uuid:...
✅ Work history credential issued: urn:uuid:...
✅ Certification credential issued: urn:uuid:...

✅ All credentials stored in candidate wallet
✅ Credentials organized into collections
✅ Background check credential issued and stored

✅ Presentation created for employer
   Holder: did:key:z6Mk...
   Credentials: 4

📋 Employer Verification Process:
Education Credential: ✅ VALID
Work History Credential: ✅ VALID
Certification Credential: ✅ VALID
Background Check Credential: ✅ VALID

📊 Candidate Wallet Statistics:
   Total credentials: 4
   Valid credentials: 4
   Collections: 4
   Tags: 12

======================================================================
✅ EMPLOYEE ONBOARDING VERIFICATION COMPLETE
   All credentials verified successfully
   Candidate is eligible for employment
======================================================================
```

**To run this example:**
1. Add the dependency (see Step 1)
2. Copy the code above into `src/main/kotlin/EmployeeOnboarding.kt`
3. Run with `./gradlew run` or execute in your IDE

## Step-by-Step Explanation

### Step 1: TrustWeave Initialization

**What this does:** Creates a TrustWeave instance with default configuration (in-memory KMS, did:key method).

**Why it matters:** This is the entry point for all TrustWeave operations. The default configuration is perfect for development and testing.

**Result:** You have a working TrustWeave instance ready to create DIDs and issue credentials.

### Step 2: DID Creation for All Parties

**What this does:** Creates DIDs for all parties involved in the onboarding process.

**Why it matters:** Each party needs a decentralized identifier to participate in the credential ecosystem. DIDs enable cryptographic verification without centralized authorities.

**Result:** All parties have unique, self-sovereign identifiers.

### Steps 3-5: Credential Issuance

**What this does:** Different issuers (university, previous employer, certification body) issue credentials to the candidate.

**Why it matters:** Each credential represents a verified claim about the candidate. The cryptographic signature proves authenticity without contacting the issuer.

**Result:** Candidate has verifiable credentials for education, work history, and certifications.

### Step 6: Wallet Creation and Storage

**What this does:** Creates a wallet for the candidate and stores all credentials.

**Why it matters:** The wallet provides secure storage and organization capabilities. The candidate owns and controls their credentials.

**Result:** All credentials are securely stored in the candidate's wallet.

### Step 7: Credential Organization

**What this does:** Organizes credentials into collections and adds tags for easy retrieval.

**Why it matters:** Organization makes it easy to find and present specific credentials. Collections and tags enable efficient credential management.

**Result:** Credentials are organized and easily retrievable.

### Step 8: Background Check Verification

**What this does:** Background check provider verifies all credentials and issues a verification credential.

**Why it matters:** This provides a trusted third-party verification of all candidate credentials. Employers can trust this single credential instead of verifying each one individually.

**Result:** Candidate has a comprehensive background check credential.

### Step 9: Presentation Creation

**What this does:** Creates a verifiable presentation containing all relevant credentials.

**Why it matters:** Presentations allow selective disclosure. The candidate can share only necessary credentials while maintaining privacy.

**Result:** Employer receives a comprehensive, verifiable presentation.

### Step 10: Credential Verification

**What this does:** Employer verifies each credential cryptographically.

**Why it matters:** Cryptographic verification proves authenticity without contacting issuers. This is instant and tamper-proof.

**Result:** All credentials are verified and the candidate is eligible for employment.

## Key Features Demonstrated

1. **Multi-Issuer Credentials**: Multiple parties issue credentials to the same holder
2. **Credential Organization**: Collections and tags for efficient management
3. **Background Check Integration**: Third-party verification credentials
4. **Comprehensive Presentations**: Multiple credentials in a single presentation
5. **Selective Disclosure**: Privacy-preserving credential sharing
6. **Cryptographic Verification**: Instant, tamper-proof verification

## Real-World Extensions

- **Credential Revocation**: Implement revocation lists for expired credentials
- **Blockchain Anchoring**: Anchor critical credentials to blockchain for permanent records
- **Multi-Chain Support**: Anchor to multiple blockchains for redundancy
- **Credential Refresh**: Automatic renewal of expiring credentials
- **Audit Trails**: Track all credential access and verification events
- **Compliance Reporting**: Automated compliance reports for regulators

## Related Documentation

- [Quick Start](../getting-started/quick-start.md) - Get started with TrustWeave
- [Common Patterns](../getting-started/common-patterns.md) - Reusable code patterns
- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Wallet API](../api-reference/wallet-api.md) - Wallet operations reference
- [Troubleshooting](../getting-started/troubleshooting.md) - Common issues and solutions

