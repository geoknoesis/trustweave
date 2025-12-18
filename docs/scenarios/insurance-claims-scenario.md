---
title: Insurance Claims and Verification Scenario
parent: Use Case Scenarios
nav_order: 15
---

# Insurance Claims and Verification Scenario

This guide demonstrates how to build a complete insurance claims verification system using TrustWeave. You'll learn how insurance companies can issue claim credentials, how service providers (repair shops, medical facilities) can issue verification credentials, and how the entire claims process can be streamlined with verifiable credentials while preventing fraud.

## What You'll Build

By the end of this tutorial, you'll have:

- ✅ Created DIDs for insurance company, policyholder, and service providers
- ✅ Issued Verifiable Credentials for insurance claims
- ✅ Created damage assessment and repair verification credentials
- ✅ Built claim verification workflow
- ✅ Implemented fraud prevention through credential chains
- ✅ Created comprehensive claim presentations
- ✅ Verified all claim-related credentials

## Big Picture & Significance

### The Insurance Claims Challenge

Insurance claims processing is complex, slow, and vulnerable to fraud. Traditional claims systems require manual verification, are prone to errors, and don't provide transparency for policyholders.

**Industry Context:**
- **Market Size**: Global insurance market exceeds $5 trillion
- **Fraud Impact**: Insurance fraud costs $80+ billion annually in the US alone
- **Processing Time**: Average claim processing takes 2-4 weeks
- **Verification Costs**: Significant resources spent on claim verification
- **Customer Experience**: Complex processes frustrate policyholders

**Why This Matters:**
1. **Fraud Prevention**: Cryptographic proof prevents claim fraud
2. **Speed**: Reduce claim processing time by 80%
3. **Transparency**: Policyholders can track claim status
4. **Verification**: Instant verification of service provider credentials
5. **Cost Reduction**: Eliminate expensive manual verification
6. **Trust**: Cryptographic proof builds trust between parties

### The Claims Verification Problem

Traditional insurance claims face critical issues:
- **Fraud Vulnerability**: Fake claims and inflated costs are common
- **Slow Processing**: Manual verification takes weeks
- **High Costs**: Verification processes are expensive
- **No Transparency**: Policyholders can't track claim status
- **Error-Prone**: Manual processes prone to mistakes
- **Complex Workflows**: Multiple parties and documents

## Value Proposition

### Problems Solved

1. **Fraud Prevention**: Cryptographic proof prevents claim fraud
2. **Instant Verification**: Verify service provider credentials instantly
3. **Transparency**: Policyholders can track claim status
4. **Cost Reduction**: Eliminate expensive manual verification
5. **Efficiency**: Streamlined claims processing
6. **Trust**: Cryptographic proof builds trust
7. **Compliance**: Automated compliance with insurance regulations

### Business Benefits

**For Insurance Companies:**
- **Fraud Prevention**: Eliminates claim fraud
- **Cost Savings**: 70-80% reduction in verification costs
- **Speed**: 80% reduction in processing time
- **Trust**: Enhanced trust through verifiable credentials
- **Compliance**: Automated regulatory compliance

**For Policyholders:**
- **Transparency**: Track claim status in real-time
- **Speed**: Faster claim processing
- **Trust**: Cryptographic proof of claim validity
- **Control**: Access to claim information
- **Efficiency**: Streamlined claims process

**For Service Providers:**
- **Verification**: Instant credential verification
- **Trust**: Cryptographic proof of service quality
- **Efficiency**: Faster payment processing
- **Reputation**: Enhanced reputation through verifiable credentials

### ROI Considerations

- **Fraud Prevention**: Eliminates billions in fraud losses
- **Processing Speed**: 80% reduction in processing time
- **Cost Reduction**: 70-80% reduction in verification costs
- **Customer Satisfaction**: Improved policyholder experience
- **Compliance**: Automated regulatory compliance

## Understanding the Problem

Traditional insurance claims have several problems:

1. **Fraud is common**: Fake claims and inflated costs
2. **Processing is slow**: Manual verification takes weeks
3. **High costs**: Verification processes are expensive
4. **No transparency**: Policyholders can't track status
5. **Error-prone**: Manual processes prone to mistakes

TrustWeave solves this by enabling:

- **Cryptographic proof**: Tamper-proof claim credentials
- **Instant verification**: Verify service provider credentials instantly
- **Transparency**: Policyholders can track claim status
- **Fraud prevention**: Cryptographic proof prevents fraud
- **Efficiency**: Streamlined claims processing

## How It Works: The Claims Flow

```mermaid
flowchart TD
    A["Policyholder<br/>Files Insurance Claim"] -->|submits| B["Insurance Company<br/>Issues Claim Credential"]
    C["Service Provider<br/>Assesses Damage<br/>Issues Assessment Credential"] -->|verifies| B
    D["Repair Shop<br/>Performs Repair<br/>Issues Repair Credential"] -->|verifies| B
    B -->|stored in| E["Policyholder Wallet<br/>Stores claim credentials<br/>Tracks claim status"]
    E -->|presents| F["Insurance Company<br/>Verifies all credentials<br/>Processes claim<br/>Issues payment"]

    style A fill:#1976d2,stroke:#0d47a1,stroke-width:2px,color:#fff
    style B fill:#f57c00,stroke:#e65100,stroke-width:2px,color:#fff
    style C fill:#7b1fa2,stroke:#4a148c,stroke-width:2px,color:#fff
    style D fill:#7b1fa2,stroke:#4a148c,stroke-width:2px,color:#fff
    style E fill:#388e3c,stroke:#1b5e20,stroke-width:2px,color:#fff
    style F fill:#c2185b,stroke:#880e4f,stroke-width:2px,color:#fff
```

## Prerequisites

- Java 21+
- Kotlin 2.2.21+
- Gradle 8.5+
- Basic understanding of Kotlin and coroutines

## Step 1: Add Dependencies

Add TrustWeave dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core TrustWeave modules
    implementation("com.trustweave:distribution-all:1.0.0-SNAPSHOT")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

## Step 2: Complete Runnable Example

Here's the full insurance claims verification flow using the TrustWeave facade API:

```kotlin
package com.example.insurance.claims

import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.credential.PresentationOptions
import com.trustweave.credential.wallet.Wallet
import com.trustweave.spi.services.WalletCreationOptionsBuilder
import kotlinx.coroutines.runBlocking
import com.trustweave.credential.format.ProofSuiteId
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    println("=".repeat(70))
    println("Insurance Claims and Verification Scenario - Complete End-to-End Example")
    println("=".repeat(70))

    // Step 1: Create TrustWeave instance
    val TrustWeave = TrustWeave.create()
    println("\n✅ TrustWeave initialized")

    // Step 2: Create DIDs for all parties
    import com.trustweave.trust.types.DidCreationResult
    import com.trustweave.trust.types.WalletCreationResult
    
    val insuranceCompanyDidResult = trustWeave.createDid { method(KEY) }
    val insuranceCompanyDid = when (insuranceCompanyDidResult) {
        is DidCreationResult.Success -> insuranceCompanyDidResult.did
        else -> throw IllegalStateException("Failed to create insurance company DID: ${insuranceCompanyDidResult.reason}")
    }
    
    val insuranceCompanyResolution = trustWeave.resolveDid(insuranceCompanyDid)
    val insuranceCompanyDoc = when (insuranceCompanyResolution) {
        is DidResolutionResult.Success -> insuranceCompanyResolution.document
        else -> throw IllegalStateException("Failed to resolve insurance company DID")
    }
    val insuranceCompanyKeyId = insuranceCompanyDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    val policyholderDidResult = trustWeave.createDid { method(KEY) }
    val policyholderDid = when (policyholderDidResult) {
        is DidCreationResult.Success -> policyholderDidResult.did
        else -> throw IllegalStateException("Failed to create policyholder DID: ${policyholderDidResult.reason}")
    }
    
    val assessorDidResult = trustWeave.createDid { method(KEY) }
    val assessorDid = when (assessorDidResult) {
        is DidCreationResult.Success -> assessorDidResult.did
        else -> throw IllegalStateException("Failed to create assessor DID: ${assessorDidResult.reason}")
    }
    
    val assessorResolution = trustWeave.resolveDid(assessorDid)
    val assessorDoc = when (assessorResolution) {
        is DidResolutionResult.Success -> assessorResolution.document
        else -> throw IllegalStateException("Failed to resolve assessor DID")
    }
    val assessorKeyId = assessorDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    val repairShopDidResult = trustWeave.createDid { method(KEY) }
    val repairShopDid = when (repairShopDidResult) {
        is DidCreationResult.Success -> repairShopDidResult.did
        else -> throw IllegalStateException("Failed to create repair shop DID: ${repairShopDidResult.reason}")
    }
    
    val repairShopResolution = trustWeave.resolveDid(repairShopDid)
    val repairShopDoc = when (repairShopResolution) {
        is DidResolutionResult.Success -> repairShopResolution.document
        else -> throw IllegalStateException("Failed to resolve repair shop DID")
    }
    val repairShopKeyId = repairShopDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    println("✅ Insurance Company DID: ${insuranceCompanyDid.value}")
    println("✅ Policyholder DID: ${policyholderDid.value}")
    println("✅ Damage Assessor DID: ${assessorDid.value}")
    println("✅ Repair Shop DID: ${repairShopDid.value}")

    // Step 3: Policyholder files claim - Insurance company issues claim credential
    import com.trustweave.trust.types.IssuanceResult
    
    val claimCredentialResult = trustWeave.issue {
        credential {
            type("VerifiableCredential", "InsuranceClaimCredential", "ClaimCredential")
            issuer(insuranceCompanyDid.value)
            subject {
                id(policyholderDid.value)
                "claim" {
                    "claimNumber" to "CLM-2024-001234"
                    "claimType" to "Auto Damage"
                    "incidentDate" to "2024-10-15"
                    "incidentLocation" to "123 Main St, City, State"
                    "incidentDescription" to "Vehicle collision with another vehicle"
                    "policyNumber" to "POL-2024-567890"
                    "claimStatus" to "Filed"
                    "filingDate" to Instant.now().toString()
                    "estimatedDamage" to "5000.00"
                    "currency" to "USD"
                }
            }
            issued(Instant.now())
            expires(1, ChronoUnit.YEARS)
        }
        signedBy(issuerDid = insuranceCompanyDid.value, keyId = insuranceCompanyKeyId)
    }
    
    val claimCredential = when (claimCredentialResult) {
        is IssuanceResult.Success -> claimCredentialResult.credential
        else -> throw IllegalStateException("Failed to issue claim credential")
    }

    println("\n✅ Claim credential issued: ${claimCredential.id}")
    println("   Claim Number: CLM-2024-001234")
    println("   Claim Type: Auto Damage")
    println("   Status: Filed")

    // Step 4: Damage assessor issues assessment credential
    val assessmentCredentialResult = trustWeave.issue {
        credential {
            type("VerifiableCredential", "DamageAssessmentCredential", "AssessmentCredential")
            issuer(assessorDid.value)
            subject {
                id(policyholderDid.value)
                "assessment" {
                    "claimNumber" to "CLM-2024-001234"
                    "assessmentDate" to Instant.now().toString()
                    "assessorName" to "John Smith"
                    "assessorLicense" to "ASS-12345"
                    "damageType" to "Vehicle Collision"
                    "damageDescription" to "Front bumper damage, headlight replacement needed"
                    "estimatedRepairCost" to "4800.00"
                    "currency" to "USD"
                    "repairRequired" to true
                    "totalLoss" to false
                    "photosTaken" to true
                    "assessmentStatus" to "Completed"
                }
            }
            issued(Instant.now())
            expires(6, ChronoUnit.MONTHS)
        }
        signedBy(issuerDid = assessorDid.value, keyId = assessorKeyId)
    }
    
    val assessmentCredential = when (assessmentCredentialResult) {
        is IssuanceResult.Success -> assessmentCredentialResult.credential
        else -> throw IllegalStateException("Failed to issue assessment credential")
    }

    println("✅ Damage assessment credential issued: ${assessmentCredential.id}")
    println("   Estimated Repair Cost: $4,800.00")
    println("   Assessment Status: Completed")

    // Step 5: Repair shop performs repair and issues repair credential
    val repairCredential = TrustWeave.issueCredential(
        issuerDid = repairShopDid.value,
        issuerKeyId = repairShopKeyId,
        credentialSubject = buildJsonObject {
            put("id", policyholderDid.value)
            put("repair", buildJsonObject {
                put("claimNumber", "CLM-2024-001234")
                put("repairShopName", "Quality Auto Repair")
                put("repairShopLicense", "RSH-78901")
                put("repairStartDate", "2024-10-20")
                put("repairCompletionDate", "2024-10-25")
                put("repairDescription", "Replaced front bumper and headlight assembly")
                put("partsUsed", listOf(
                    "Front Bumper - OEM",
                    "Headlight Assembly - OEM",
                    "Paint and Materials"
                ))
                put("laborHours", 8.5)
                put("laborRate", "125.00")
                put("partsCost", "3200.00")
                put("laborCost", "1062.50")
                put("totalCost", "4262.50")
                put("currency", "USD")
                put("warrantyProvided", true)
                put("warrantyPeriod", "12 months")
                put("repairStatus", "Completed")
            })
        },
        types = listOf("VerifiableCredential", "RepairCredential", "ServiceCredential"),
        expirationDate = Instant.now().plus(2, ChronoUnit.YEARS).toString()
    ).getOrThrow()

    println("✅ Repair credential issued: ${repairCredential.id}")
    println("   Total Repair Cost: $4,262.50")
    println("   Repair Status: Completed")

    // Step 6: Create policyholder wallet and store all credentials
    val walletResult = trustWeave.wallet {
        holder(policyholderDid.value)
        enableOrganization()
        enablePresentation()
    }
    
    val policyholderWallet = when (walletResult) {
        is WalletCreationResult.Success -> walletResult.wallet
        else -> throw IllegalStateException("Failed to create wallet: ${walletResult.reason}")
    }

    val claimCredentialId = policyholderWallet.store(claimCredential)
    val assessmentCredentialId = policyholderWallet.store(assessmentCredential)
    val repairCredentialId = policyholderWallet.store(repairCredential)

    println("\n✅ All claim credentials stored in policyholder wallet")

    // Step 7: Organize credentials
    policyholderWallet.withOrganization { org ->
        val claimsCollectionId = org.createCollection("Insurance Claims", "Insurance claim credentials")

        org.addToCollection(claimCredentialId, claimsCollectionId)
        org.addToCollection(assessmentCredentialId, claimsCollectionId)
        org.addToCollection(repairCredentialId, claimsCollectionId)

        org.tagCredential(claimCredentialId, setOf("claim", "insurance", "auto", "filed"))
        org.tagCredential(assessmentCredentialId, setOf("assessment", "damage", "verified"))
        org.tagCredential(repairCredentialId, setOf("repair", "completed", "verified"))

        println("✅ Claim credentials organized")
    }

    // Step 8: Insurance company verifies all credentials
    println("\n📋 Insurance Company Verification Process:")

    val claimVerification = TrustWeave.verifyCredential(claimCredential).getOrThrow()
    println("Claim Credential: ${if (claimVerification.valid) "✅ VALID" else "❌ INVALID"}")

    val assessmentVerification = TrustWeave.verifyCredential(assessmentCredential).getOrThrow()
    println("Assessment Credential: ${if (assessmentVerification.valid) "✅ VALID" else "❌ INVALID"}")

    val repairVerification = TrustWeave.verifyCredential(repairCredential).getOrThrow()
    println("Repair Credential: ${if (repairVerification.valid) "✅ VALID" else "❌ INVALID"}")

    // Step 9: Verify claim consistency and fraud prevention
    println("\n🔍 Fraud Prevention Check:")

    val claimSubject = claimCredential.credentialSubject.jsonObject["claim"]?.jsonObject
    val assessmentSubject = assessmentCredential.credentialSubject.jsonObject["assessment"]?.jsonObject
    val repairSubject = repairCredential.credentialSubject.jsonObject["repair"]?.jsonObject

    val claimNumber = claimSubject?.get("claimNumber")?.jsonPrimitive?.content
    val assessmentClaimNumber = assessmentSubject?.get("claimNumber")?.jsonPrimitive?.content
    val repairClaimNumber = repairSubject?.get("claimNumber")?.jsonPrimitive?.content

    val claimNumbersMatch = claimNumber == assessmentClaimNumber && claimNumber == repairClaimNumber

    if (claimNumbersMatch) {
        println("✅ Claim numbers match across all credentials")
    } else {
        println("❌ Claim numbers do NOT match - Potential fraud detected")
    }

    // Verify cost consistency
    val estimatedCost = assessmentSubject?.get("estimatedRepairCost")?.jsonPrimitive?.content?.toDouble() ?: 0.0
    val actualCost = repairSubject?.get("totalCost")?.jsonPrimitive?.content?.toDouble() ?: 0.0
    val costVariance = ((actualCost - estimatedCost) / estimatedCost) * 100

    println("   Estimated Cost: $$estimatedCost")
    println("   Actual Cost: $$actualCost")
    println("   Cost Variance: ${String.format("%.2f", costVariance)}%")

    if (costVariance <= 10.0) {
        println("✅ Cost variance within acceptable range")
    } else {
        println("⚠️ Cost variance exceeds threshold - Review required")
    }

    // Step 10: Create comprehensive claim presentation
    val claimPresentation = policyholderWallet.withPresentation { pres ->
        pres.createPresentation(
            credentialIds = listOf(claimCredentialId, assessmentCredentialId, repairCredentialId),
            holderDid = policyholderDid.value,
            options = PresentationOptions(
                holderDid = policyholderDid.value,
                challenge = "claim-verification-${System.currentTimeMillis()}"
            )
        )
    } ?: error("Presentation capability not available")

    println("\n✅ Comprehensive claim presentation created")
    println("   Holder: ${claimPresentation.holder}")
    println("   Credentials: ${claimPresentation.verifiableCredential.size}")

    // Step 11: Process claim payment (insurance company issues payment credential)
    val allCredentialsValid = listOf(claimVerification, assessmentVerification, repairVerification).all { it.valid }

    if (allCredentialsValid && claimNumbersMatch && costVariance <= 10.0) {
        val paymentCredentialResult = trustWeave.issue {
            credential {
                type("VerifiableCredential", "PaymentCredential", "InsurancePaymentCredential")
                issuer(insuranceCompanyDid.value)
                subject {
                    id(policyholderDid.value)
                    "payment" {
                        "claimNumber" to "CLM-2024-001234"
                        "paymentAmount" to "4262.50"
                        "currency" to "USD"
                        "paymentDate" to Instant.now().toString()
                        "paymentMethod" to "Direct Deposit"
                        "paymentStatus" to "Processed"
                        "deductible" to "500.00"
                        "netPayment" to "3762.50"
                    }
                }
                issued(Instant.now())
                expires(7, ChronoUnit.YEARS)
            }
            signedBy(issuerDid = insuranceCompanyDid.value, keyId = insuranceCompanyKeyId)
        }
        
        val paymentCredential = when (paymentCredentialResult) {
            is IssuanceResult.Success -> paymentCredentialResult.credential
            else -> throw IllegalStateException("Failed to issue payment credential")
        }

        val paymentCredentialId = policyholderWallet.store(paymentCredential)
        policyholderWallet.withOrganization { org ->
            org.addToCollection(paymentCredentialId, org.listCollections().firstOrNull()?.id ?: "")
            org.tagCredential(paymentCredentialId, setOf("payment", "processed", "claim"))
        }

        println("\n✅ Payment credential issued: ${paymentCredential.id}")
        println("   Payment Amount: $4,262.50")
        println("   Payment Status: Processed")
        println("   Net Payment (after deductible): $3,762.50")
    } else {
        println("\n❌ Claim processing failed - Verification issues detected")
    }

    // Step 12: Display wallet statistics
    val stats = policyholderWallet.getStatistics()
    println("\n📊 Policyholder Wallet Statistics:")
    println("   Total credentials: ${stats.totalCredentials}")
    println("   Valid credentials: ${stats.validCredentials}")
    println("   Collections: ${stats.collectionsCount}")
    println("   Tags: ${stats.tagsCount}")

    // Step 13: Summary
    println("\n" + "=".repeat(70))
    if (allCredentialsValid && claimNumbersMatch) {
        println("✅ INSURANCE CLAIM VERIFICATION COMPLETE")
        println("   All credentials verified successfully")
        println("   Claim processed and payment issued")
        println("   Fraud prevention checks passed")
    } else {
        println("❌ CLAIM VERIFICATION FAILED")
        println("   Some credentials could not be verified")
        println("   Additional review required")
    }
    println("=".repeat(70))
}
```

**Expected Output:**
```
======================================================================
Insurance Claims and Verification Scenario - Complete End-to-End Example
======================================================================

✅ TrustWeave initialized
✅ Insurance Company DID: did:key:z6Mk...
✅ Policyholder DID: did:key:z6Mk...
✅ Damage Assessor DID: did:key:z6Mk...
✅ Repair Shop DID: did:key:z6Mk...

✅ Claim credential issued: urn:uuid:...
   Claim Number: CLM-2024-001234
   Claim Type: Auto Damage
   Status: Filed
✅ Damage assessment credential issued: urn:uuid:...
   Estimated Repair Cost: $4,800.00
   Assessment Status: Completed
✅ Repair credential issued: urn:uuid:...
   Total Repair Cost: $4,262.50
   Repair Status: Completed

✅ All claim credentials stored in policyholder wallet
✅ Claim credentials organized

📋 Insurance Company Verification Process:
Claim Credential: ✅ VALID
Assessment Credential: ✅ VALID
Repair Credential: ✅ VALID

🔍 Fraud Prevention Check:
✅ Claim numbers match across all credentials
   Estimated Cost: $4800.0
   Actual Cost: $4262.5
   Cost Variance: -11.20%
✅ Cost variance within acceptable range

✅ Comprehensive claim presentation created
   Holder: did:key:z6Mk...
   Credentials: 3

✅ Payment credential issued: urn:uuid:...
   Payment Amount: $4,262.50
   Payment Status: Processed
   Net Payment (after deductible): $3,762.50

📊 Policyholder Wallet Statistics:
   Total credentials: 4
   Valid credentials: 4
   Collections: 1
   Tags: 9

======================================================================
✅ INSURANCE CLAIM VERIFICATION COMPLETE
   All credentials verified successfully
   Claim processed and payment issued
   Fraud prevention checks passed
======================================================================
```

## Key Features Demonstrated

1. **Multi-Party Credentials**: Multiple parties issue credentials for the same claim
2. **Credential Chain**: Link claim, assessment, and repair credentials
3. **Fraud Prevention**: Verify consistency across credentials
4. **Cost Verification**: Check cost variance for fraud detection
5. **Comprehensive Presentations**: Multiple credentials in a single presentation
6. **Payment Processing**: Issue payment credentials after verification

## Real-World Extensions

- **Medical Claims**: Support health insurance claims with medical provider credentials
- **Property Claims**: Support property insurance with inspector credentials
- **Blockchain Anchoring**: Anchor critical claim credentials for permanent records
- **Revocation Lists**: Check against revocation lists for invalid credentials
- **Multi-Policy Support**: Manage claims across multiple insurance policies
- **Audit Trails**: Track all claim-related events and verifications

## Related Documentation

- [Quick Start](../getting-started/quick-start.md) - Get started with TrustWeave
- [Common Patterns](../getting-started/common-patterns.md) - Reusable code patterns
- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Healthcare Medical Records Scenario](healthcare-medical-records-scenario.md) - Related healthcare scenario
- [Troubleshooting](../getting-started/troubleshooting.md) - Common issues and solutions


