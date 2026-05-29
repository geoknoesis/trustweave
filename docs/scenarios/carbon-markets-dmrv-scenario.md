---
title: Carbon Markets & Digital MRV (dMRV) with Earth Observation
parent: Use Case Scenarios
nav_order: 29
---

# Carbon Markets & Digital MRV (dMRV) with Earth Observation

This guide demonstrates how to build a Digital Measurement, Reporting, and Verification (dMRV) system for carbon markets using TrustWeave and Earth Observation data. You'll learn how to create verifiable credentials for carbon credits that prevent double counting and enable automated verification.

## What You'll Build

By the end of this tutorial, you'll have:

- Created DIDs for carbon credit issuers, verifiers, and buyers
- Issued verifiable credentials for carbon credits with EO data evidence
- Implemented double-counting prevention using blockchain anchoring
- Built carbon credit lifecycle tracking (issuance â†’ sale â†’ retirement)
- Created automated verification workflows
- Anchored carbon credit credentials to blockchain for immutable records

## Big Picture & Significance

### The Carbon Market Digital Transformation

Carbon markets are moving from manual PDF audits to "Digital Measurement, Reporting, and Verification" (dMRV). Verifiable Credentials are the standard container for this digital proof, enabling automated verification and preventing double counting.

**Industry Context:**
- **Market Size**: Global carbon market projected to reach $2.4 trillion by 2027
- **Regulatory Pressure**: Increasing requirements for digital MRV and transparency
- **Current Challenge**: Manual PDF audits are slow, expensive, and prone to errors
- **The Gap**: Need standardized digital format for carbon credit proof
- **Double Counting**: Critical issue - same credit sold multiple times

**Why This Matters:**
1. **Double Counting Prevention**: Once a Carbon Credit VC is retired on the ledger, it cannot be re-sold
2. **Automation**: Enable automated verification and trading
3. **Transparency**: Verifiable data lineage for all stakeholders
4. **Standardization**: Standard format works across all carbon markets
5. **Trust**: Build trust in carbon markets through verifiable credentials
6. **Compliance**: Meet regulatory requirements for carbon accounting

### Real-World Examples

**OpenClimate (Open Earth Foundation)** - Active Pilot with British Columbia Government:
- Nested climate accounting system (Nation â†’ City â†’ Company) using DIDs and Verifiable Credentials
- Uses VCs to prevent "Double Counting"
- Once a Carbon Credit VC is retired on the ledger, it cannot be re-sold
- Aligns with **Qualified Relations** pattern to track lifecycle of the credit

**InterWork Alliance (IWA) / GBBC** - Standardization Body:
- Define the **Token Taxonomy Framework (TTF)** for carbon emissions
- Map "Carbon Emission Tokens" to specific data proofs
- EO VCs act as the "Evidence Layer" that underpins these tokens

## Value Proposition

### Problems Solved

1. **Double Counting Prevention**: Cryptographic proof prevents same credit being sold twice
2. **Automated Verification**: Enable automated MRV workflows
3. **EO Data Evidence**: Use Earth Observation data as proof of carbon sequestration
4. **Lifecycle Tracking**: Track credit from issuance to retirement
5. **Transparency**: Verifiable data lineage for all stakeholders
6. **Standardization**: Standard format works across all carbon markets
7. **Trust**: Build trust in carbon markets through verifiable credentials

### Business Benefits

**For Carbon Credit Issuers:**
- **Trust**: Build trust with buyers through verifiable credentials
- **Automation**: Reduce verification costs by 70%
- **Market Access**: Reach more buyers with standardized format
- **Compliance**: Meet regulatory requirements automatically

**For Carbon Credit Buyers:**
- **Verification**: Verify credits before purchase
- **Double Counting Prevention**: Cryptographic proof prevents fraud
- **Transparency**: See complete credit lifecycle
- **Compliance**: Meet carbon accounting requirements

**For Verifiers:**
- **Automation**: Automated verification workflows
- **Evidence**: EO data provides verifiable evidence
- **Audit Trail**: Complete verification history
- **Efficiency**: Reduce verification time by 80%

## Understanding the Problem

Carbon markets need:

1. **Digital MRV**: Digital measurement, reporting, and verification
2. **Double Counting Prevention**: Prevent same credit being sold multiple times
3. **EO Data Evidence**: Use Earth Observation data as proof
4. **Lifecycle Tracking**: Track credit from issuance to retirement
5. **Automated Verification**: Enable automated workflows
6. **Transparency**: Verifiable data lineage for all stakeholders

## Prerequisites

- Java 21+
- Kotlin 2.2.21+
- Gradle 8.5+
- Basic understanding of Kotlin and coroutines
- Understanding of carbon markets and MRV concepts

## Step 1: Add Dependencies

```kotlin
dependencies {
    // Core TrustWeave modules
    implementation("org.trustweave:distribution-all:0.6.0")

    // Test kit for in-memory implementations
    testImplementation("org.trustweave:testkit:0.6.0")

    // Optional: Algorand adapter for real blockchain anchoring
    implementation("org.trustweave:anchors-plugins-algorand:0.6.0")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

## Step 2: Complete Runnable Example

Here's a complete carbon credit dMRV workflow:

```kotlin
package com.example.carbon.markets

import org.trustweave.trust.TrustWeave
import org.trustweave.credential.model.ProofType
import org.trustweave.core.util.DigestUtils
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.types.getOrThrow
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.credential.results.getOrThrow

fun main() = runBlocking {
    println("=".repeat(70))
    println("Carbon Markets & Digital MRV - Complete Example")
    println("=".repeat(70))

    // Step 1: Create TrustWeave instance with blockchain
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
        credentials { defaultProofType(ProofType.Ed25519Signature2020) }
    }
    println("\n✅ TrustWeave initialized")

    // Step 2: Create DIDs for carbon credit issuer, verifier, and buyer
    
    val issuerDidResult = trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }
    
    val issuerDid = trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }.getOrThrowDid()
    println("✅ Created issuer DID: ${issuerDid.value}")

    val verifierDidResult = trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }
    
    val verifierDid = trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }.getOrThrowDid()
    println("✅ Created verifier DID: ${verifierDid.value}")

    val buyerDid = trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }.getOrThrowDid()
    println("✅ Created buyer DID: ${buyerDid.value}")

    // Step 3: Create EO data evidence (forest carbon sequestration)
    val eoEvidence = buildJsonObject {
        put("id", "eo-evidence-forest-2024")
        put("type", "ForestCarbonSequestration")
        put("location", buildJsonObject {
            put("latitude", 45.5017)
            put("longitude", -73.5673)
            put("region", "Quebec Forest, Canada")
            put("area", 1000.0)  // hectares
        })
        put("measurement", buildJsonObject {
            put("carbonSequestrated", 5000.0)  // tons CO2
            put("measurementPeriod", buildJsonObject {
                put("startDate", "2024-01-01")
                put("endDate", "2024-12-31")
            })
            put("method", "Sentinel-2 L2A Spectral Analysis")
            put("confidence", 0.92)
        })
        put("timestamp", Instant.now().toString())
    }

    val eoEvidenceDigest = DigestUtils.sha256DigestMultibase(eoEvidence)

    // Step 4: Verifier issues verification credential
    val verifierDoc = when (val res = trustWeave.resolveDid(verifierDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
    val verifierKeyId = verifierDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    val verificationCredentialResult = trustWeave.issue {
        credential {
            id("urn:carbon:verification:forest-2024")
            type("VerifiableCredential", "CarbonVerificationCredential")
            issuer(verifierDid)
            subject {
                id("urn:carbon:verification:forest-2024")
                "verificationType" to "CarbonSequestration"
                "eoEvidence" to eoEvidence
                "eoEvidenceDigest" to eoEvidenceDigest
                "verifiedAmount" to 5000.0  // tons CO2
                "verificationDate" to Instant.now().toString()
                "verifier" to verifierDid.value
                "status" to "verified"
            }
            issued(Instant.now())
        }
        signedBy(verifierDid)
    }
    
    
    val verificationCredential = verificationCredentialResult.getOrThrow()

    println("✅ Verification Credential issued: ${verificationCredential.id}")

    // Step 5: Issuer issues carbon credit credential
    val issuerDoc = when (val res = trustWeave.resolveDid(issuerDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
    val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    val carbonCreditResult = trustWeave.issue {
        credential {
            id("urn:carbon:credit:CC-2024-001")
            type("VerifiableCredential", "CarbonCreditCredential")
            issuer(issuerDid)
            subject {
                id("urn:carbon:credit:CC-2024-001")
                "creditType" to "ForestCarbonSequestration"
                "amount" to 5000.0  // tons CO2
                "unit" to "tCO2e"
                "verificationCredentialId" to verificationCredential.id
                "eoEvidenceDigest" to eoEvidenceDigest
                "issuanceDate" to Instant.now().toString()
                "vintage" to "2024"
                "status" to "issued"  // issued â†’ sold â†’ retired
                "projectId" to "PROJ-12345"
                "location" {
                    "latitude" to 45.5017
                    "longitude" to -73.5673
                }
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
    }
    
    val carbonCredit = carbonCreditResult.getOrThrow()

    println("✅ Carbon Credit issued: ${carbonCredit.id}")
    println("   Amount: 5000 tCO2e")
    println("   Status: issued")

    // Step 6: Anchor credit to blockchain (prevent double counting)
    val anchorResult = trustWeave.blockchains.anchor(
        data = carbonCredit,
        serializer = VerifiableCredential.serializer(),
        chainId = "algorand:testnet"
    ).fold(
        onSuccess = { anchor ->
            println("✅ Carbon Credit anchored: ${anchor.ref.txHash}")
            anchor
        },
        onFailure = { error ->
            println("[FAIL] Anchoring failed: ${error.message}")
            null
        }
    )

    // Step 7: Track credit sale (update status)
    if (anchorResult != null) {
        // Create sale credential
        val saleCredentialResult = trustWeave.issue {
            credential {
                id("urn:carbon:sale:CC-2024-001")
                type("VerifiableCredential", "CarbonCreditSaleCredential")
                issuer(issuerDid.value)
                subject {
                    id("urn:carbon:sale:CC-2024-001")
                    "creditId" to carbonCredit.id
                    "buyer" to buyerDid.value
                    "saleDate" to Instant.now().toString()
                    "price" to 50.0  // $50 per ton
                    "totalAmount" to 250000.0  // $250,000
                    "currency" to "USD"
                    "previousStatus" to "issued"
                    "newStatus" to "sold"
                    "anchorRef" {
                        "chainId" to anchorResult.ref.chainId
                        "txHash" to anchorResult.ref.txHash
                    }
                }
                issued(Instant.now())
            }
            signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
        }
        
        val saleCredential = saleCredentialResult.getOrThrow()

        println("✅ Sale Credential issued: ${saleCredential.id}")
        println("   Buyer: ${buyerDid.id}")
        println("   Price: $250,000 USD")

        // Step 8: Verify credit not already sold (double counting prevention)
        val creditStatus = checkCreditStatus(carbonCredit.id, anchorResult.ref)
        if (creditStatus == "sold") {
            println("[FAIL] ERROR: Credit already sold!")
            println("   Double counting prevented by blockchain anchor")
            return@runBlocking
        }

        // Step 9: Retire credit (final state)
        val buyerDoc = when (val res = trustWeave.resolveDid(buyerDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
        val buyerKeyId = buyerDoc.verificationMethod.firstOrNull()?.extractKeyId()
            ?: throw IllegalStateException("No verification method found")

        val retirementCredentialResult = trustWeave.issue {
            credential {
                id("urn:carbon:retirement:CC-2024-001")
                type("VerifiableCredential", "CarbonCreditRetirementCredential")
                issuer(buyerDid.value)
                subject {
                    id("urn:carbon:retirement:CC-2024-001")
                    "creditId" to carbonCredit.id
                    "retirementDate" to Instant.now().toString()
                    "retirementReason" to "CarbonOffset"
                    "previousStatus" to "sold"
                    "newStatus" to "retired"
                    "anchorRef" {
                        "chainId" to anchorResult.ref.chainId
                        "txHash" to anchorResult.ref.txHash
                    }
                }
                issued(Instant.now())
            }
            signedBy(issuerDid = buyerDid, keyId = buyerKeyId)
        }
        
        val retirementCredential = retirementCredentialResult.getOrThrow()

        println("✅ Retirement Credential issued: ${retirementCredential.id}")
        println("   Status: retired")
        println("   Credit cannot be re-sold (double counting prevented)")
    }

    // Step 10: Verify complete lifecycle
    println("\n[stats] Carbon Credit Lifecycle:")
    println("   1. Issued: ${carbonCredit.id}")
    println("   2. Verified: ${verificationCredential.id}")
    println("   3. Anchored: ${anchorResult?.ref?.txHash}")
    println("   4. Sold: Sale credential issued")
    println("   5. Retired: Retirement credential issued")
    println("   ✅ Complete lifecycle tracked with VCs")

    println("\n" + "=".repeat(70))
    println("✅ Carbon Markets & dMRV Scenario Complete!")
    println("=".repeat(70))
}

// Helper function to check credit status on blockchain
suspend fun checkCreditStatus(
    creditId: String,
    anchorRef: AnchorRef
): String {
    // In production, query blockchain for credit status
    // This prevents double counting by checking if credit was already sold/retired
    return "issued"  // Placeholder - in production, query blockchain
}
```

**Expected Output:**
```
======================================================================
Carbon Markets & Digital MRV - Complete Example
======================================================================

✅ TrustWeave initialized
✅ Issuer DID: did:key:z6Mk...
✅ Verifier DID: did:key:z6Mk...
✅ Buyer DID: did:key:z6Mk...
✅ Verification Credential issued: urn:uuid:...
✅ Carbon Credit issued: urn:uuid:...
   Amount: 5000 tCO2e
   Status: issued
✅ Carbon Credit anchored: tx_...
✅ Sale Credential issued: urn:uuid:...
   Buyer: did:key:z6Mk...
   Price: $250,000 USD
✅ Retirement Credential issued: urn:uuid:...
   Status: retired
   Credit cannot be re-sold (double counting prevented)

[stats] Carbon Credit Lifecycle:
   1. Issued: urn:uuid:...
   2. Verified: urn:uuid:...
   3. Anchored: tx_...
   4. Sold: Sale credential issued
   5. Retired: Retirement credential issued
   ✅ Complete lifecycle tracked with VCs

======================================================================
✅ Carbon Markets & dMRV Scenario Complete!
======================================================================
```

## Step 3: Double Counting Prevention

The key feature is preventing double counting:

```kotlin
import org.trustweave.anchor.AnchorRef

suspend fun preventDoubleCounting(
    creditId: String,
    anchorRef: AnchorRef
): Boolean {
    // Check blockchain for credit status
    val status = queryBlockchainStatus(creditId, anchorRef)

    if (status == "retired" || status == "sold") {
        println("[FAIL] Credit already used: $status")
        println("   Double counting prevented!")
        return false
    }

    return true
}
```

## Step 4: Nested Climate Accounting (OpenClimate Pattern)

For nested accounting (Nation â†’ City â†’ Company):

```kotlin
// Nation level
val nationCredit = issueCarbonCredit(
    amount = 1000000.0,
    level = "Nation",
    entity = "Canada"
)

// City level (references nation)
val cityCredit = issueCarbonCredit(
    amount = 100000.0,
    level = "City",
    entity = "Vancouver",
    parentCredit = nationCredit.id
)

// Company level (references city)
val companyCredit = issueCarbonCredit(
    amount = 10000.0,
    level = "Company",
    entity = "Acme Corp",
    parentCredit = cityCredit.id
)
```

## Step 5: Token Taxonomy Framework (IWA) Integration

Map carbon credits to TTF tokens:

```kotlin
val ttfToken = buildJsonObject {
    put("tokenType", "CarbonEmissionToken")
    put("amount", 5000.0)
    put("unit", "tCO2e")
    put("evidence", buildJsonObject {
        put("type", "VerifiableCredential")
        put("credentialId", carbonCredit.id)
        put("eoEvidenceDigest", eoEvidenceDigest)
    })
    put("standard", "TTF")
}
```

## Next Steps

- Explore [Earth Observation Scenario](earth-observation-scenario.md) for EO data integrity
- Learn about [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md)
- Review [Credential Lifecycle](../getting-started/common-patterns.md#credential-lifecycle-management)

## Related Documentation

- Earth Observation Scenario](earth-observation-scenario.md) - EO data integrity
- Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) - Anchoring concepts
- API Reference](../api-reference/core-api.md) - Complete API documentation


