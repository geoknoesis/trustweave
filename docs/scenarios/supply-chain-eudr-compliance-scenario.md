---
title: Supply Chain & Regulatory Compliance (EUDR) with Earth Observation
parent: Use Case Scenarios
nav_order: 23
---

# Supply Chain & Regulatory Compliance (EUDR) with Earth Observation

This guide demonstrates how to build a supply chain compliance system for the EU Deforestation Regulation (EUDR) using TrustWeave and Earth Observation data. You'll learn how to create verifiable credentials that prove geospatial non-deforestation for every shipment.

## What You'll Build

By the end of this tutorial, you'll have:

- ✅ Created DIDs for importers, exporters, and verifiers
- ✅ Issued verifiable credentials for geospatial non-deforestation proof
- ✅ Built Digital Product Passport (DPP) using VCs
- ✅ Implemented automated compliance verification
- ✅ Created EO data evidence for deforestation monitoring
- ✅ Anchored compliance credentials to blockchain for audit trails

## Big Picture & Significance

### The EU Deforestation Regulation (EUDR)

By 2025, importers must prove geospatial non-deforestation for every shipment. Current systems rely on self-declared PDFs, creating a trust gap. Verifiable Credentials provide cryptographic proof of compliance.

**Industry Context:**
- **Regulatory Requirement**: EUDR mandatory by 2025
- **Scope**: All imports of commodities (coffee, cocoa, palm oil, etc.)
- **Current Gap**: Self-declared PDFs are not verifiable
- **Solution**: W3C Verifiable Credentials to model farm identity and compliance status
- **EO Data**: Earth Observation data provides verifiable evidence of non-deforestation

**Why This Matters:**
1. **Regulatory Compliance**: Meet EUDR requirements with verifiable proof
2. **Geospatial Proof**: EO data provides verifiable evidence of non-deforestation
3. **Digital Product Passport**: VCs are leading candidate for DPP implementation
4. **Trust**: Cryptographic proof prevents fraud and false declarations
5. **Automation**: Enable automated compliance verification
6. **Audit Trails**: Complete compliance history for regulators

### Real-World Examples

**EU Deforestation Regulation (EUDR)**:
- Requirement: By 2025, importers must prove geospatial non-deforestation
- Current Gap: Self-declared PDFs
- Solution: AgrospAI and similar Agri-food data spaces testing W3C Verifiable Credentials

**Climate TRACE**:
- Independent tracking of GHG emissions using satellites and AI
- Acts as "Global Verifier"
- Emitter could issue VC claiming "Low Emissions"
- Verifier checks against Climate TRACE data to validate/refute automatically

## Value Proposition

### Problems Solved

1. **Regulatory Compliance**: Meet EUDR requirements with verifiable proof
2. **Geospatial Proof**: EO data provides verifiable evidence
3. **Digital Product Passport**: Standard format for DPP implementation
4. **Automated Verification**: Enable automated compliance checks
5. **Fraud Prevention**: Cryptographic proof prevents false declarations
6. **Audit Trails**: Complete compliance history
7. **Trust**: Build trust through verifiable credentials

### Business Benefits

**For Importers:**
- **Compliance**: Meet EUDR requirements automatically
- **Risk Reduction**: Reduce risk of non-compliance penalties
- **Efficiency**: Automated verification reduces costs
- **Trust**: Build trust with regulators and consumers

**For Exporters:**
- **Market Access**: Access EU market with verifiable compliance
- **Differentiation**: Stand out with verifiable credentials
- **Efficiency**: Reduce compliance documentation costs
- **Trust**: Build trust with importers

**For Regulators:**
- **Verification**: Automated compliance verification
- **Audit Trails**: Complete compliance history
- **Transparency**: Verifiable data lineage
- **Efficiency**: Reduce manual verification costs

## Understanding the Problem

EUDR compliance needs:

1. **Geospatial Proof**: Prove non-deforestation for specific locations
2. **Digital Product Passport**: Standard format for product information
3. **Automated Verification**: Enable automated compliance checks
4. **Audit Trails**: Complete compliance history
5. **EO Data Evidence**: Use Earth Observation data as proof
6. **Trust**: Cryptographic proof prevents fraud

## Prerequisites

- Java 21+
- Kotlin 2.2.21+
- Gradle 8.5+
- Basic understanding of Kotlin and coroutines
- Understanding of EUDR requirements

## Step 1: Add Dependencies

```kotlin
dependencies {
    // Core TrustWeave modules
    implementation("com.trustweave:distribution-all:1.0.0-SNAPSHOT")

    // Test kit for in-memory implementations
    testImplementation("com.trustweave:testkit:1.0.0-SNAPSHOT")

    // Optional: Algorand adapter for real blockchain anchoring
    implementation("com.trustweave.chains:algorand:1.0.0-SNAPSHOT")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

## Step 2: Complete Runnable Example

Here's a complete EUDR compliance workflow:

```kotlin
package com.example.eudr.compliance

import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.json.DigestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.Instant

fun main() = runBlocking {
    println("=".repeat(70))
    println("EUDR Compliance with EO Data - Complete Example")
    println("=".repeat(70))

    // Step 1: Create TrustWeave instance
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),
            didMethodFactory = TestkitDidMethodFactory()
        )
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
        credentials { defaultProofSuite(ProofSuiteId.VC_LD) }
    }
    println("\n✅ TrustWeave initialized")

    // Step 2: Create DIDs for exporter, importer, and verifier
    import com.trustweave.trust.types.DidCreationResult
    
    val exporterDidResult = trustWeave.createDid { method("key") }
    val exporterDid = when (exporterDidResult) {
        is DidCreationResult.Success -> {
            println("✅ Exporter DID: ${exporterDidResult.did.value}")
            exporterDidResult.did
        }
        else -> {
            println("Failed to create exporter DID: ${exporterDidResult.reason}")
            return@runBlocking
        }
    }
    
    val importerDidResult = trustWeave.createDid { method("key") }
    val importerDid = when (importerDidResult) {
        is DidCreationResult.Success -> {
            println("✅ Importer DID: ${importerDidResult.did.value}")
            importerDidResult.did
        }
        else -> {
            println("Failed to create importer DID: ${importerDidResult.reason}")
            return@runBlocking
        }
    }
    
    val verifierDidResult = trustWeave.createDid { method("key") }
    val verifierDid = when (verifierDidResult) {
        is DidCreationResult.Success -> {
            println("✅ Verifier DID: ${verifierDidResult.did.value}")
            verifierDidResult.did
        }
        else -> {
            println("Failed to create verifier DID: ${verifierDidResult.reason}")
            return@runBlocking
        }
    }

    // Step 3: Create farm/production site DID
    val farmDidResult = trustWeave.createDid { method("key") }
    val farmDid = when (farmDidResult) {
        is DidCreationResult.Success -> {
            println("✅ Farm DID: ${farmDidResult.did.value}")
            farmDidResult.did
        }
        else -> {
            println("Failed to create farm DID: ${farmDidResult.reason}")
            return@runBlocking
        }
    }

    // Step 4: Create EO data evidence (non-deforestation proof)
    val eoDeforestationProof = buildJsonObject {
        put("id", "eo-deforestation-proof-2024")
        put("type", "NonDeforestationProof")
        put("location", buildJsonObject {
            put("latitude", -3.4653)
            put("longitude", -62.2159)
            put("region", "Amazon Rainforest, Brazil")
            put("farmId", farmDid.id)
            put("polygon", buildJsonArray {
                // Farm boundary coordinates
                add(buildJsonArray { add(-62.22); add(-3.47) })
                add(buildJsonArray { add(-62.21); add(-3.47) })
                add(buildJsonArray { add(-62.21); add(-3.46) })
                add(buildJsonArray { add(-62.22); add(-3.46) })
                add(buildJsonArray { add(-62.22); add(-3.47) })
            })
        })
        put("analysis", buildJsonObject {
            put("method", "Sentinel-2 L2A Time Series Analysis")
            put("analysisPeriod", buildJsonObject {
                put("startDate", "2020-01-01")
                put("endDate", "2024-12-31")
            })
            put("deforestationDetected", false)
            put("forestCoverChange", 0.02)  // 2% increase (reforestation)
            put("confidence", 0.95)
            put("verificationDate", Instant.now().toString())
        })
        put("timestamp", Instant.now().toString())
    }

    val eoProofDigest = DigestUtils.sha256DigestMultibase(eoDeforestationProof)

    // Step 5: Verifier issues compliance credential
    val verifierResolution = trustWeave.resolveDid(verifierDid)
    val verifierDoc = when (verifierResolution) {
        is DidResolutionResult.Success -> verifierResolution.document
        else -> throw IllegalStateException("Failed to resolve verifier DID")
    }
    val verifierKeyId = verifierDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    val complianceCredentialResult = trustWeave.issue {
        credential {
            id("eudr-compliance-2024-001")
            type("VerifiableCredential", "EUDRComplianceCredential")
            issuer(verifierDid.value)
            subject {
                id("eudr-compliance-2024-001")
                "complianceType" to "EUDR"
                "farm" {
                    "id" to farmDid.value
                    "name" to "Sustainable Coffee Farm"
                    "location" {
                        "latitude" to -3.4653
                        "longitude" to -62.2159
                        "country" to "Brazil"
                    }
                }
                "eoEvidence" to eoDeforestationProof
                "eoEvidenceDigest" to eoProofDigest
                "complianceStatus" to "compliant"
                "verificationDate" to Instant.now().toString()
                "validUntil" to Instant.now().plus(365, java.time.temporal.ChronoUnit.DAYS).toString()
                "verifier" to verifierDid.value
            }
            issued(Instant.now())
            expires(365, java.time.temporal.ChronoUnit.DAYS)
        }
        signedBy(issuerDid = verifierDid.value, keyId = verifierKeyId)
    }
    
    val complianceCredential = when (complianceCredentialResult) {
        is IssuanceResult.Success -> complianceCredentialResult.credential
        else -> {
            println("❌ Failed to issue compliance credential: ${complianceCredentialResult.allErrors.joinToString()}")
            return@runBlocking
        }
    }

    println("✅ Compliance Credential issued: ${complianceCredential.id}")
    println("   Status: compliant")
    println("   Farm: ${farmDid.id}")

    // Step 6: Create Digital Product Passport (DPP)
    val exporterResolution = trustWeave.resolveDid(exporterDid)
    val exporterDoc = when (exporterResolution) {
        is DidResolutionResult.Success -> exporterResolution.document
        else -> throw IllegalStateException("Failed to resolve exporter DID")
    }
    val exporterKeyId = exporterDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    val dppCredentialResult = trustWeave.issue {
        credential {
            id("dpp-coffee-shipment-2024-001")
            type("VerifiableCredential", "DigitalProductPassport", "EUDRProductCredential")
            issuer(exporterDid.value)
            subject {
                id("dpp-coffee-shipment-2024-001")
                "productType" to "Coffee"
                "commodity" to "Coffee Beans"
                "quantity" to 10000.0  // kg
                "unit" to "kg"
                "farm" to farmDid.value
                "complianceCredentialId" to complianceCredential.id
                "eoEvidenceDigest" to eoProofDigest
                "harvestDate" to "2024-06-15"
                "exportDate" to Instant.now().toString()
                "exporter" to exporterDid.value
                "destination" to "EU"
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = exporterDid.value, keyId = exporterKeyId)
    }
    
    val dppCredential = when (dppCredentialResult) {
        is IssuanceResult.Success -> dppCredentialResult.credential
        else -> {
            println("❌ Failed to issue DPP: ${dppCredentialResult.allErrors.joinToString()}")
            return@runBlocking
        }
    }

    println("✅ Digital Product Passport issued: ${dppCredential.id}")
    println("   Product: Coffee Beans")
    println("   Quantity: 10,000 kg")

    // Step 7: Importer verifies compliance before import
    import com.trustweave.trust.types.VerificationResult
    
    val dppVerification = trustWeave.verify {
        credential(dppCredential)
    }

    when (dppVerification) {
        is VerificationResult.Invalid -> {
            println("❌ DPP verification failed: ${dppVerification.allErrors.joinToString()}")
            return@runBlocking
        }
        is VerificationResult.Valid -> {
            // Continue processing
        }
    }

    println("✅ DPP verified")

    // Step 8: Verify compliance credential
    val complianceVerification = trustWeave.verify {
        credential(complianceCredential)
    }

    when (complianceVerification) {
        is VerificationResult.Invalid -> {
            println("❌ Compliance verification failed: ${complianceVerification.allErrors.joinToString()}")
            return@runBlocking
        }
        is VerificationResult.Valid -> {
            // Continue processing
        }
    }

    println("✅ Compliance verified")

    // Step 9: Verify EO evidence integrity
    val currentProofDigest = DigestUtils.sha256DigestMultibase(eoDeforestationProof)
    val credentialProofDigest = complianceCredential.credentialSubject
        .jsonObject["eoEvidenceDigest"]?.jsonPrimitive?.content ?: ""

    if (currentProofDigest == credentialProofDigest) {
        println("✅ EO Evidence integrity verified")
        println("   No tampering detected")
    } else {
        println("❌ EO Evidence integrity FAILED")
        println("   Evidence may have been tampered with")
        return@runBlocking
    }

    // Step 10: Check against Climate TRACE (global verifier)
    val climateTraceVerification = verifyAgainstClimateTrace(
        location = buildJsonObject {
            put("latitude", -3.4653)
            put("longitude", -62.2159)
        },
        eoEvidence = eoDeforestationProof
    )

    if (climateTraceVerification) {
        println("✅ Verified against Climate TRACE")
        println("   Global verification confirms compliance")
    } else {
        println("⚠️ Climate TRACE verification inconclusive")
    }

    // Step 11: Anchor to blockchain for audit trail
    val anchorResult = trustWeave.blockchains.anchor(
        data = dppCredential,
        serializer = VerifiableCredential.serializer(),
        chainId = "algorand:testnet"
    ).fold(
        onSuccess = { anchor ->
            println("✅ DPP anchored: ${anchor.ref.txHash}")
            anchor
        },
        onFailure = { error ->
            println("❌ Anchoring failed: ${error.message}")
            null
        }
    )

    println("\n📊 EUDR Compliance Summary:")
    println("   Farm: ${farmDid.id}")
    println("   Compliance Status: compliant")
    println("   EO Evidence: verified")
    println("   DPP: issued and verified")
    println("   Blockchain Anchor: ${anchorResult?.ref?.txHash}")
    println("   ✅ Ready for EU import")

    println("\n" + "=".repeat(70))
    println("✅ EUDR Compliance Scenario Complete!")
    println("=".repeat(70))
}

// Helper function to verify against Climate TRACE
suspend fun verifyAgainstClimateTrace(
    location: JsonObject,
    eoEvidence: JsonObject
): Boolean {
    // In production, query Climate TRACE API
    // Compare EO evidence with Climate TRACE data
    // Return true if evidence matches Climate TRACE data
    return true  // Placeholder
}
```

**Expected Output:**
```
======================================================================
EUDR Compliance with EO Data - Complete Example
======================================================================

✅ TrustWeave initialized
✅ Exporter DID: did:key:z6Mk...
✅ Importer DID: did:key:z6Mk...
✅ Verifier DID: did:key:z6Mk...
✅ Farm DID: did:key:z6Mk...
✅ Compliance Credential issued: urn:uuid:...
   Status: compliant
   Farm: did:key:z6Mk...
✅ Digital Product Passport issued: urn:uuid:...
   Product: Coffee Beans
   Quantity: 10,000 kg
✅ DPP verified
✅ Compliance verified
✅ EO Evidence integrity verified
   No tampering detected
✅ Verified against Climate TRACE
   Global verification confirms compliance
✅ DPP anchored: tx_...

📊 EUDR Compliance Summary:
   Farm: did:key:z6Mk...
   Compliance Status: compliant
   EO Evidence: verified
   DPP: issued and verified
   Blockchain Anchor: tx_...
   ✅ Ready for EU import

======================================================================
✅ EUDR Compliance Scenario Complete!
======================================================================
```

## Step 3: Automated Compliance Verification

Enable automated verification:

```kotlin
suspend fun automatedEUDRVerification(
    trustWeave: TrustWeave,
    dppCredential: VerifiableCredential
): ComplianceResult {
    // Verify DPP credential
    val dppVerification = trustWeave.verify {
        credential(dppCredential)
    }
    
    when (dppVerification) {
        is VerificationResult.Invalid -> {
            return ComplianceResult.NonCompliant("DPP verification failed: ${dppVerification.allErrors.joinToString()}")
        }
        is VerificationResult.Valid -> {
            // Continue
        }
    }

    // Extract compliance credential ID
    val complianceCredentialId = extractComplianceCredentialId(dppCredential)

    // Verify compliance credential
    val complianceCredential = fetchCredential(complianceCredentialId)
    val complianceVerification = trustWeave.verify {
        credential(complianceCredential)
    }
    
    when (complianceVerification) {
        is VerificationResult.Invalid -> {
            return ComplianceResult.NonCompliant("Compliance verification failed: ${complianceVerification.allErrors.joinToString()}")
        }
        is VerificationResult.Valid -> {
            // Continue
        }
    }

    // Verify EO evidence
    val eoEvidence = extractEOEvidence(complianceCredential)
    val eoVerification = verifyEOEvidence(eoEvidence)
    when (eoVerification) {
        is VerificationResult.Invalid -> {
            return ComplianceResult.NonCompliant("EO evidence verification failed: ${eoVerification.allErrors.joinToString()}")
        }
        is VerificationResult.Valid -> {
            // Continue
        }
    }

    // Check against Climate TRACE
    val climateTraceCheck = verifyAgainstClimateTrace(eoEvidence)
    if (!climateTraceCheck) {
        return ComplianceResult.NonCompliant("Climate TRACE verification failed")
    }

    return ComplianceResult.Compliant("All checks passed")
}

sealed class ComplianceResult {
    data class Compliant(val message: String) : ComplianceResult()
    data class NonCompliant(val reason: String) : ComplianceResult()
}
```

## Step 4: Digital Product Passport (DPP) Structure

DPP using VCs:

```kotlin
val dpp = buildJsonObject {
    put("id", "dpp-product-001")
    put("product", buildJsonObject {
        put("type", "Coffee")
        put("quantity", 10000.0)
        put("unit", "kg")
    })
    put("compliance", buildJsonObject {
        put("eudrCompliant", true)
        put("complianceCredentialId", complianceCredential.id)
        put("verificationDate", Instant.now().toString())
    })
    put("provenance", buildJsonObject {
        put("farm", farmDid.id)
        put("harvestDate", "2024-06-15")
        put("exportDate", Instant.now().toString())
    })
    put("eoEvidence", buildJsonObject {
        put("digest", eoProofDigest)
        put("verificationStatus", "verified")
    })
}
```

## Step 5: Climate TRACE Integration

Verify against Climate TRACE as global verifier:

```kotlin
suspend fun verifyAgainstClimateTrace(
    location: JsonObject,
    eoEvidence: JsonObject
): Boolean {
    // Query Climate TRACE API
    val climateTraceData = queryClimateTraceAPI(location)

    // Compare with EO evidence
    val matches = compareWithClimateTrace(eoEvidence, climateTraceData)

    return matches
}
```

## Next Steps

- Explore [Supply Chain Traceability Scenario](supply-chain-traceability-scenario.md)
- Learn about [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md)
- Review [Verification Policies](../advanced/verification-policies.md)

## Related Documentation

- [Supply Chain Traceability Scenario](supply-chain-traceability-scenario.md) - Supply chain workflows
- [Earth Observation Scenario](earth-observation-scenario.md) - EO data integrity
- [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) - Anchoring concepts
- [API Reference](../api-reference/core-api.md) - Complete API documentation

