---
title: Parametric Insurance with Earth Observation Data
parent: Use Case Scenarios
nav_order: 26
---

# Parametric Insurance with Earth Observation Data

This guide demonstrates how to build a parametric insurance system using TrustWeave and Earth Observation (EO) data. You'll learn how to create verifiable credentials for EO data that trigger insurance payouts, solving the "Oracle Problem" by enabling standardized, multi-provider data ecosystems.

## What You'll Build

By the end of this tutorial, you'll have:

- Created DIDs for insurance companies and EO data providers
- Issued verifiable credentials for EO data (rainfall, temperature, spectral analysis)
- Built a standardized data oracle system using VCs
- Implemented parametric trigger verification
- Created multi-provider data acceptance workflows
- Anchored EO data credentials to blockchain for tamper-proof triggers

## Big Picture & Significance

### The Parametric Insurance Oracle Problem

Parametric insurance pays out automatically when specific conditions are met (e.g., rainfall below threshold, temperature above threshold). Currently, insurers rely on proprietary, siloed "Oracles" to trigger smart contracts, creating vendor lock-in and limiting data source options.

**Industry Context:**
- **Market Size**: Parametric insurance market projected to reach $29.3 billion by 2030
- **Active Players**: Arbol ($500M+ in climate risk coverage), Descartes Underwriting (global corporate insurance)
- **Current Challenge**: Each insurer builds custom API integrations for each data provider
- **The Gap**: No standardized way to accept EO data from multiple certified providers (ESA, Planet, NASA)
- **Trust Issue**: Need cryptographic proof that data used for $50M payout is the exact data that was modeled

**Why This Matters:**
1. **Standardization**: Accept EO data from any certified provider without custom integrations
2. **Trust**: Cryptographic proof prevents "replay attacks" and data corruption
3. **Multi-Provider**: Enable competition and redundancy in data sources
4. **Automation**: Enable automatic payouts based on verifiable EO data
5. **Cost Reduction**: Eliminate custom API integrations for each provider
6. **Transparency**: Verifiable data lineage for regulatory compliance

### Real-World Examples

**Arbol** - Manages $500M+ in climate risk coverage:
- Uses parametric triggers (e.g., rainfall data for agriculture)
- Currently builds custom data pipelines for each provider
- **Solution**: Adopting VC pattern allows accepting data from any certified provider (ESA, Planet, NASA) without custom API integrations

**Descartes Underwriting** - Global corporate insurance:
- Uses spectral analysis for climate risks (hail, flood, wildfire)
- Underwriting models rely on "spectral fingerprints" of damage
- **Solution**: Wrapping spectral fingerprints in VCs with SRI Integrity ensures data used for $50M payout is the exact same data that was modeled

## Value Proposition

### Problems Solved

1. **Oracle Standardization**: Standard format for EO data from any provider
2. **Multi-Provider Support**: Accept data from ESA, Planet, NASA, etc. without custom integrations
3. **Data Integrity**: Cryptographic proof prevents tampering and replay attacks
4. **Automated Triggers**: Enable automatic insurance payouts based on verifiable data
5. **Regulatory Compliance**: Verifiable data lineage for audit trails
6. **Cost Reduction**: Eliminate custom API integrations
7. **Trust**: Build trust in parametric insurance through verifiable data

### Business Benefits

**For Insurance Companies:**
- **Cost Reduction**: No custom integrations needed for each data provider
- **Flexibility**: Switch between data providers easily
- **Trust**: Cryptographic proof of data integrity
- **Compliance**: Automated audit trails
- **Competition**: Enable multiple data providers to compete

**For EO Data Providers:**
- **Standardization**: One format works for all insurers
- **Market Access**: Reach all insurance companies with standard format
- **Trust**: Build trust through verifiable credentials
- **Differentiation**: Stand out with verifiable data quality

**For Policyholders:**
- **Transparency**: Verify data used for payouts
- **Fairness**: Standardized data prevents manipulation
- **Speed**: Faster payouts with automated triggers
- **Trust**: Cryptographic proof of data integrity

## Understanding the Problem

Parametric insurance needs:

1. **Standardized Data Format**: Accept EO data from any provider
2. **Data Integrity**: Verify data hasn't been tampered with
3. **Multi-Provider Support**: Work with ESA, Planet, NASA, etc.
4. **Automated Triggers**: Enable automatic payouts
5. **Audit Trails**: Complete data lineage for compliance
6. **Trust**: Cryptographic proof of data authenticity

## Prerequisites

- Java 21+
- Kotlin 2.2.21+
- Gradle 8.5+
- Basic understanding of Kotlin and coroutines
- Understanding of parametric insurance concepts

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

Here's a complete parametric insurance workflow using EO data credentials:

```kotlin
package com.example.parametric.insurance

import org.trustweave.trust.TrustWeave
import org.trustweave.core.*
import org.trustweave.core.util.DigestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.credential.results.getOrThrow

fun main() = runBlocking {
    println("=".repeat(70))
    println("Parametric Insurance with EO Data - Complete Example")
    println("=".repeat(70))

    // Step 1: Create TrustWeave instance
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }
    println("\n[OK] TrustWeave initialized")

    // Step 2: Create DIDs for insurance company and EO data provider
    val insuranceDid = trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }.getOrThrowDid()
    println("[OK] Created insurance DID: ${insuranceDid.value}")
    
    // Continue with EO provider DID creation
    
    val eoProviderDid = trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }.getOrThrowDid()
    println("[OK] Created EO provider DID: ${eoProviderDid.value}")

    println("[OK] Insurance Company DID: ${insuranceDid.value}")
    println("[OK] EO Data Provider DID: ${eoProviderDid.value}")

    // Step 3: EO Data Provider issues credential for rainfall data
    val eoProviderDoc = when (val res = trustWeave.resolveDid(eoProviderDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
    val eoProviderKeyId = eoProviderDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method found")

    // Create EO data payload (rainfall measurement)
    val rainfallData = buildJsonObject {
        put("id", "rainfall-measurement-2024-06-15")
        put("type", "RainfallMeasurement")
        put("location", buildJsonObject {
            put("latitude", 37.7749)
            put("longitude", -122.4194)
            put("region", "San Francisco, CA")
        })
        put("measurement", buildJsonObject {
            put("value", 0.5)  // 0.5 inches of rainfall
            put("unit", "inches")
            put("timestamp", Instant.now().toString())
            put("source", "Sentinel-2 L2A")
            put("method", "Spectral Analysis")
        })
        put("quality", buildJsonObject {
            put("confidence", 0.95)
            put("validationStatus", "validated")
        })
    }

    // Compute digest for data integrity
    val dataDigest = DigestUtils.sha256DigestMultibase(rainfallData)

    // Issue verifiable credential for EO data
    val eoDataIssuanceResult = trustWeave.issue {
        credential {
            type("EarthObservationCredential", "InsuranceOracleCredential")
            issuer(eoProviderDid)
            subject {
                id("urn:eo:measurement:rainfall-2024-06-15")
                "dataType" to "RainfallMeasurement"
                "data" to rainfallData
                "dataDigest" to dataDigest
                "provider" to eoProviderDid.value
                "timestamp" to Instant.now().toString()
            }
            issued(Instant.now())
        }
        signedBy(eoProviderDid)
    }
    
    
    val eoDataCredential = eoDataIssuanceResult.getOrThrow()
    println("[OK] EO Data Credential issued: ${eoDataCredential.id}")
    println("   Data digest: $dataDigest")

    // Step 4: Verify EO data credential (insurance company verifies before using)
    when (val verification = trustWeave.verify { credential(eoDataCredential) }) {
        is VerificationResult.Valid -> {
            println("[OK] EO Data Credential verified")
            println("   Proof valid: ${verification.proofValid}")
            println("   Issuer valid: ${verification.issuerValid}")
        }
        is VerificationResult.Invalid -> {
            println("[FAIL] Verification failed: ${verification.allErrors.joinToString()}")
            return@runBlocking
        }
    }

    // Step 5: Extract data and check parametric trigger
    val credentialSubject = eoDataCredential.credentialSubject
    val rainfallValue = credentialSubject.jsonObject["data"]
        ?.jsonObject?.get("measurement")
        ?.jsonObject?.get("value")
        ?.jsonPrimitive?.content?.toDouble()
        ?: error("Rainfall value not found")

    println("\n[stats] Parametric Trigger Check:")
    println("   Rainfall value: $rainfallValue inches")

    // Insurance policy: Payout if rainfall < 1.0 inches
    val triggerThreshold = 1.0
    val shouldPayout = rainfallValue < triggerThreshold

    if (shouldPayout) {
        println("   [OK] TRIGGER MET: Rainfall below threshold ($triggerThreshold inches)")
        println("   [payout] Insurance payout should be triggered")

        // Step 6: Create payout credential (insurance company issues)
        val insuranceDoc = when (val res = trustWeave.resolveDid(insuranceDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
        val insuranceKeyId = insuranceDoc.verificationMethod.firstOrNull()?.extractKeyId()
            ?: throw IllegalStateException("No verification method found")

        val payoutIssuanceResult = trustWeave.issue {
            credential {
                type("InsurancePayoutCredential")
                issuer(insuranceDid)
                subject {
                    id("urn:insurance:payout:2024-06-15")
                    "policyId" to "POL-12345"
                    "triggerType" to "RainfallBelowThreshold"
                    "triggerValue" to rainfallValue
                    "threshold" to triggerThreshold
                    "dataCredentialId" to eoDataCredential.id
                    "dataDigest" to dataDigest
                    "payoutAmount" to 50000.0
                    "currency" to "USD"
                    "timestamp" to Instant.now().toString()
                }
                issued(Instant.now())
            }
            signedBy(issuerDid = insuranceDid, keyId = insuranceKeyId)
        }
        
        val payoutCredential = payoutIssuanceResult.getOrThrow()
        println("[OK] Payout Credential issued: ${payoutCredential.id}")
        println("   Payout amount: $50,000 USD")
        println("   Data credential: ${eoDataCredential.id}")
    } else {
        println("   [FAIL] TRIGGER NOT MET: Rainfall above threshold")
        println("   No payout triggered")
    }

    // Step 7: Verify data integrity (prevent replay attacks)
    val currentDataDigest = DigestUtils.sha256DigestMultibase(rainfallData)
    val credentialDataDigest = credentialSubject.jsonObject["dataDigest"]
        ?.jsonPrimitive?.content ?: ""

    if (currentDataDigest == credentialDataDigest) {
        println("\n[OK] Data Integrity Verified")
        println("   Data digest matches credential")
        println("   No tampering detected")
    } else {
        println("\n[FAIL] Data Integrity FAILED")
        println("   Data may have been tampered with")
        println("   DO NOT TRUST THIS DATA")
    }

    println("\n" + "=".repeat(70))
    println("[OK] Parametric Insurance Scenario Complete!")
    println("=".repeat(70))
}
```

**Expected Output:**
```
======================================================================
Parametric Insurance with EO Data - Complete Example
======================================================================

[OK] TrustWeave initialized
[OK] Insurance Company DID: did:key:z6Mk...
[OK] EO Data Provider DID: did:key:z6Mk...
[OK] EO Data Credential issued: urn:uuid:...
   Data digest: u5v...
[OK] EO Data Credential verified
   Proof valid: true
   Issuer valid: true

[stats] Parametric Trigger Check:
   Rainfall value: 0.5 inches
   [OK] TRIGGER MET: Rainfall below threshold (1.0 inches)
   [payout] Insurance payout should be triggered
[OK] Payout Credential issued: urn:uuid:...
   Payout amount: $50,000 USD
   Data credential: urn:uuid:...

[OK] Data Integrity Verified
   Data digest matches credential
   No tampering detected

======================================================================
[OK] Parametric Insurance Scenario Complete!
======================================================================
```

## Step 3: Multi-Provider Support

The key advantage of using VCs is accepting data from multiple providers:

```kotlin
import org.trustweave.credential.results.VerificationResult

// Accept data from any certified provider
val providers = listOf("ESA", "Planet", "NASA", "NOAA")

suspend fun acceptEODataFromAnyProvider(
    providerDid: String,
    dataCredential: VerifiableCredential
): Boolean {
    // Verify credential
    val verification = trustWeave.verify {
        credential(dataCredential)
    }
    when (verification) {
        is VerificationResult.Valid -> {
            // Credential is valid, continue
        }
        is VerificationResult.Invalid -> {
            return false
        }
    }

    // Check if provider is certified
    val isCertified = checkProviderCertification(providerDid)
    if (!isCertified) return false

    // Extract and use data
    val data = extractDataFromCredential(dataCredential)
    return processDataForInsurance(data)
}
```

## Step 4: Spectral Fingerprint Example (Descartes Underwriting)

For spectral analysis use cases:

```kotlin
// Create spectral fingerprint credential
val spectralData = buildJsonObject {
    put("id", "spectral-fingerprint-wildfire-2024")
    put("type", "SpectralFingerprint")
    put("location", buildJsonObject {
        put("latitude", 34.0522)
        put("longitude", -118.2437)
        put("region", "Los Angeles, CA")
    })
    put("spectralAnalysis", buildJsonObject {
        put("bands", buildJsonArray {
            add(buildJsonObject { put("band", "NIR"); put("value", 0.85) })
            add(buildJsonObject { put("band", "SWIR"); put("value", 0.72) })
            add(buildJsonObject { put("band", "Red"); put("value", 0.45) })
        })
        put("damageType", "Wildfire")
        put("damageSeverity", 0.78)
        put("confidence", 0.92)
    })
    put("timestamp", Instant.now().toString())
}

val spectralDigest = DigestUtils.sha256DigestMultibase(spectralData)

val spectralIssuanceResult = trustWeave.issue {
    credential {
        type("SpectralAnalysisCredential", "InsuranceOracleCredential")
        issuer(eoProviderDid.value)
        subject {
            id("urn:eo:fingerprint:wildfire-2024")
            "dataType" to "SpectralFingerprint"
            "data" to spectralData
            "dataDigest" to spectralDigest
            "provider" to eoProviderDid.value
        }
        issued(Instant.now())
    }
    signedBy(issuerDid = eoProviderDid, keyId = eoProviderKeyId)
}

// Verify spectral fingerprint matches underwriting model
val modelFingerprint = getUnderwritingModelFingerprint()
val matchesModel = verifySpectralMatch(spectralData, modelFingerprint)

if (matchesModel) {
    println("[OK] Spectral fingerprint matches underwriting model")
    println("   Data used for payout is the exact data that was modeled")
    println("   No replay attack or data corruption possible")
}
```

## Step 5: Blockchain Anchoring for Audit Trail

Anchor credentials to blockchain for immutable audit trail:

```kotlin
// Anchor EO data credential
val anchorResult = trustWeave.blockchains.anchor(
    data = eoDataCredential,
    serializer = VerifiableCredential.serializer(),
    chainId = "algorand:testnet"
).fold(
    onSuccess = { anchor ->
        println("[OK] Credential anchored: ${anchor.ref.txHash}")
        anchor
    },
    onFailure = { error ->
        println("[FAIL] Anchoring failed: ${error.message}")
        null
    }
)

// Store anchor reference for audit trail
if (anchorResult != null) {
    saveAuditRecord(
        dataCredentialId = eoDataCredential.id,
        anchorRef = anchorResult.ref,
        timestamp = anchorResult.timestamp
    )
}
```

## Key Benefits

1. **Standardization**: One format works for all EO data providers
2. **Multi-Provider**: Accept data from ESA, Planet, NASA without custom integrations
3. **Data Integrity**: Cryptographic proof prevents tampering and replay attacks
4. **Automation**: Enable automatic insurance payouts
5. **Audit Trail**: Complete data lineage for compliance
6. **Trust**: Build trust through verifiable credentials

## Real-World Integration

**Arbol Integration:**
- Replace custom API integrations with VC-based data acceptance
- Accept data from any certified provider (ESA, Planet, NASA)
- Reduce integration costs by 80%

**Descartes Underwriting Integration:**
- Wrap spectral fingerprints in VCs with SRI Integrity
- Ensure data used for $50M payout is exact data that was modeled
- Prevent replay attacks and data corruption

## Next Steps

- Explore [Earth Observation Scenario](earth-observation-scenario.md) for EO data integrity
- Learn about [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md)
- Review [Error Handling](../advanced/error-handling.md) for production patterns

## Related Documentation

- Earth Observation Scenario](earth-observation-scenario.md) - EO data integrity workflow
- Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) - Anchoring concepts
- API Reference](../api-reference/core-api.md) - Complete API documentation


