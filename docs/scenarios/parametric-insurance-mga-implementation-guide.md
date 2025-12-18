---
title: Parametric Insurance MGA Implementation Guide with TrustWeave
parent: Use Case Scenarios
nav_order: 36
---

# Parametric Insurance MGA Implementation Guide with TrustWeave

> **Building "Atlas Parametric" - An EO-Driven Parametric Insurance MGA**

This comprehensive guide shows you how to build your parametric insurance MGA solution using TrustWeave as the trust and integrity foundation. This implementation covers SAR flood, heatwave, solar attenuation, hurricane, and drought parametric products with instant payouts and objective EO triggers.

## Executive Summary

**What You're Building:**
- A parametric insurance MGA (Managing General Agent) platform
- EO-driven triggers (SAR, NDVI, AOD, LST, InSAR)
- Automated 24-72 hour payouts
- Multi-provider EO data acceptance
- Tamper-proof trigger verification
- Regulatory-compliant audit trails

**Why TrustWeave:**
- **Trust Foundation**: Verifiable Credentials for EO data integrity
- **Multi-Provider Support**: Accept data from ESA, Planet, NASA, NOAA without custom integrations
- **Blockchain Anchoring**: Tamper-proof trigger records for regulatory compliance
- **Standardization**: W3C-compliant format works across all providers
- **Automation**: Enable instant payouts with verifiable triggers

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Atlas Parametric MGA Platform                │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────────┐  │
│  │  EO Data        │  │  Pricing        │  │  Payout        │  │
│  │  Ingestion      │  │  Engine         │  │  Automation    │  │
│  │  (SAR, NDVI,    │  │  (Actuarial)    │  │  (Banking API) │  │
│  │   AOD, LST)     │  │                 │  │                │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬───────┘  │
│           │                    │                     │          │
│           └────────────────────┼─────────────────────┘          │
│                                │                                 │
│                    ┌───────────▼────────────┐                   │
│                    │   TrustWeave Trust Layer │                   │
│                    │  ┌──────────────────┐  │                   │
│                    │  │ DID Management   │  │                   │
│                    │  │ VC Issuance      │  │                   │
│                    │  │ VC Verification │  │                   │
│                    │  │ Blockchain Anchor│  │                   │
│                    │  │ Data Integrity  │  │                   │
│                    │  └──────────────────┘  │                   │
│                    └───────────┬────────────┘                   │
│                                │                                 │
│  ┌──────────────────┐  ┌────────▼────────┐  ┌──────────────────┐  │
│  │  Broker Portal  │  │  Trigger       │  │  Reinsurer      │  │
│  │  (Distribution)  │  │  Engine        │  │  Dashboard      │  │
│  └──────────────────┘  └────────────────┘  └──────────────────┘  │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. TrustWeave Trust Layer

TrustWeave provides:
- **DID Management**: Identity for insurers, EO providers, reinsurers, brokers
- **Verifiable Credentials**: EO data wrapped in VCs for integrity
- **Blockchain Anchoring**: Tamper-proof trigger records
- **Multi-Provider Support**: Accept EO data from any certified provider

### 2. EO Data Ingestion Pipeline

Processes:
- Sentinel-1 SAR (flood detection)
- MODIS/VIIRS LST (heatwave)
- AOD + irradiance (solar attenuation)
- NDVI (drought/agriculture)
- GPM/IMERG (rainfall)
- InSAR (deformation)

### 3. Trigger Engine

Evaluates parametric triggers:
- Real-time EO data ingestion
- Threshold evaluation
- Tiered payout calculation
- Automatic trigger verification

### 4. Pricing Engine

Actuarial pricing:
- EO climate archive (20-40 years)
- Hazard-frequency modeling
- Geographic risk scoring
- Reinsurer-approved rates

### 5. Payout Automation

Automated payouts:
- KYC/AML integration
- Banking API integration
- Reinsurer notification
- Audit trail generation

## Implementation: Product Suite

### Product 1: SAR-Based Flood Parametric

**Data Sources:** Sentinel-1 SAR + DEM
**Markets:** US (NC, SC, FL, GA)
**Payout Range:** $25k - $5M
**Triggers:** Depth thresholds (20cm, 50cm, 1m)

#### Implementation

```kotlin
package com.atlasparametric.products.flood

import com.trustweave.TrustWeave
import com.trustweave.contract.models.*
import com.trustweave.core.*
import com.trustweave.json.DigestUtils
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * SAR Flood Parametric Product using Smart Contracts
 *
 * Uses Sentinel-1 SAR data to detect flood depth and trigger automatic payouts
 */
class SarFloodProduct(
    private val TrustWeave: TrustWeave,
    private val eoProviderDid: String
) {

    /**
     * Create a flood insurance contract
     */
    suspend fun createFloodContract(
        insurerDid: String,
        insuredDid: String,
        coverageAmount: Double,
        location: Location
    ): SmartContract {

        val contract = trustWeave.contracts.draft(
            request = ContractDraftRequest(
                contractType = ContractType.Insurance,
                executionModel = ExecutionModel.Parametric(
                    triggerType = TriggerType.EarthObservation,
                    evaluationEngine = "parametric-insurance"
                ),
                parties = ContractParties(
                    primaryPartyDid = insurerDid,
                    counterpartyDid = insuredDid
                ),
                terms = ContractTerms(
                    obligations = listOf(
                        Obligation(
                            id = "payout-obligation",
                            partyDid = insurerDid,
                            description = "Pay out based on flood depth tier",
                            obligationType = ObligationType.PAYMENT
                        )
                    ),
                    conditions = listOf(
                        ContractCondition(
                            id = "flood-threshold-20cm",
                            description = "Flood depth >= 20cm (Tier 1)",
                            conditionType = ConditionType.THRESHOLD,
                            expression = "$.floodDepthCm >= 20"
                        ),
                        ContractCondition(
                            id = "flood-threshold-50cm",
                            description = "Flood depth >= 50cm (Tier 2)",
                            conditionType = ConditionType.THRESHOLD,
                            expression = "$.floodDepthCm >= 50"
                        ),
                        ContractCondition(
                            id = "flood-threshold-100cm",
                            description = "Flood depth >= 100cm (Tier 3)",
                            conditionType = ConditionType.THRESHOLD,
                            expression = "$.floodDepthCm >= 100"
                        )
                    ),
                    jurisdiction = "US",
                    governingLaw = "State of North Carolina"
                ),
                effectiveDate = Instant.now().toString(),
                expirationDate = Instant.now().plusSeconds(365 * 24 * 60 * 60).toString(),
                contractData = buildJsonObject {
                    "productType" to "SarFlood"
                    "coverageAmount" to coverageAmount
                    "location" {
                        "latitude" to location.latitude
                        "longitude" to location.longitude
                        "address" to location.address
                        "region" to location.region
                    }
                    "thresholds" {
                        "tier1Cm" to 20.0
                        "tier2Cm" to 50.0
                        "tier3Cm" to 100.0
                    }
                    "payoutTiers" {
                        "tier1" to 0.25  // 25% of coverage
                        "tier2" to 0.50  // 50% of coverage
                        "tier3" to 1.0   // 100% of coverage
                    }
                }
            )
        ).getOrThrow()

        println("✅ Flood contract draft created: ${contract.id}")
        return contract
    }

    /**
     * Bind contract (issue VC and anchor to blockchain)
     */
    suspend fun bindFloodContract(
        contract: SmartContract,
        insurerDid: String,
        insurerKeyId: String
    ): BoundContract {

        val bound = trustWeave.contracts.bindContract(
            contractId = contract.id,
            issuerDid = insurerDid,
            issuerKeyId = insurerKeyId,
            chainId = "algorand:mainnet"
        ).getOrThrow()

        println("✅ Contract bound: ${bound.credentialId}, anchored: ${bound.anchorRef.txHash}")
        return bound
    }

    /**
     * Process SAR flood data and issue verifiable credential
     */
    suspend fun processSarFloodData(
        location: Location,
        sarData: SarFloodMeasurement,
        timestamp: Instant
    ): Result<VerifiableCredential> = trustweaveCatching {

        // Step 1: Create EO data payload
        val floodData = buildJsonObject {
            put("id", "sar-flood-${location.id}-${timestamp.toEpochMilli()}")
            put("type", "SarFloodMeasurement")
            put("location", buildJsonObject {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("address", location.address)
                put("region", location.region)
            })
            put("measurement", buildJsonObject {
                put("floodDepthCm", sarData.floodDepthCm)
                put("floodAreaSqKm", sarData.floodAreaSqKm)
                put("confidence", sarData.confidence)
                put("source", "Sentinel-1 SAR")
                put("processingMethod", "SAR Flood Extraction")
                put("demUsed", sarData.demUsed)
                put("timestamp", timestamp.toString())
            })
            put("quality", buildJsonObject {
                put("validationStatus", "validated")
                put("dataQuality", sarData.quality)
            })
        }

        // Step 2: Compute data digest for integrity
        val dataDigest = DigestUtils.sha256DigestMultibase(floodData)

        // Step 3: Issue verifiable credential for EO data
        // Note: eoProviderDid is a String (DID value), so we need to create a Did object for resolveDid
        import com.trustweave.core.Did
        val eoProviderDidObj = Did(eoProviderDid)
        val resolution = trustWeave.resolveDid(eoProviderDidObj)
        val eoProviderDoc = when (resolution) {
            is DidResolutionResult.Success -> resolution.document
            else -> throw IllegalStateException("Failed to resolve EO provider DID")
        }
        val eoProviderKeyId = eoProviderDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method found")

        val floodIssuanceResult = trustWeave.issue {
            credential {
                id("urn:eo:sar-flood:${location.id}-${timestamp.toEpochMilli()}")
                type("VerifiableCredential", "EarthObservationCredential", "InsuranceOracleCredential", "SarFloodCredential")
                issuer(eoProviderDid)
                subject {
                    id("urn:eo:sar-flood:${location.id}-${timestamp.toEpochMilli()}")
                    "dataType" to "SarFloodMeasurement"
                    "data" to floodData
                    "dataDigest" to dataDigest
                    "provider" to eoProviderDid
                    "timestamp" to timestamp.toString()
                }
                issued(Instant.now())
            }
            by(issuerDid = eoProviderDid, keyId = eoProviderKeyId)
        }
        
        val floodCredential = when (floodIssuanceResult) {
            is IssuanceResult.Success -> floodIssuanceResult.credential
            else -> throw IllegalStateException("Failed to issue flood credential")
        }

        // Step 4: Anchor to blockchain for tamper-proof record
        val anchorResult = trustWeave.blockchains.anchor(
            data = floodCredential,
            serializer = VerifiableCredential.serializer(),
            chainId = "algorand:mainnet"
        )
        
        anchorResult.fold(
            onSuccess = { anchor ->
                println("✅ SAR Flood Credential issued and anchored: ${anchor.ref.txHash}")
            },
            onFailure = { error ->
                throw IllegalStateException("Failed to anchor credential: ${error.message}")
            }
        )

        floodCredential
    }

    /**
     * Execute contract with flood data
     */
    suspend fun executeFloodContract(
        contract: SmartContract,
        floodCredential: VerifiableCredential
    ): ExecutionResult {

        // Extract flood depth from credential
        val credentialSubject = floodCredential.credentialSubject
        val floodDepthCm = credentialSubject.jsonObject["data"]
            ?.jsonObject?.get("measurement")
            ?.jsonObject?.get("floodDepthCm")
            ?.jsonPrimitive?.content?.toDouble()
            ?: error("Flood depth not found in credential")

        // Create execution context with trigger data
        val executionContext = ExecutionContext(
            triggerData = buildJsonObject {
                put("floodDepthCm", floodDepthCm)
                put("credentialId", floodCredential.id)
                put("timestamp", Instant.now().toString())
            }
        )

        // Execute contract
        val result = trustWeave.contracts.executeContract(
            contract = contract,
            executionContext = executionContext
        ).getOrThrow()

        if (result.executed) {
            println("✅ Contract executed! Payout triggered for flood depth: ${floodDepthCm}cm")
            result.outcomes.forEach { outcome ->
                println("   Outcome: ${outcome.description}")
                outcome.monetaryImpact?.let {
                    println("   Amount: ${it.amount} ${it.currency}")
                }
            }
        } else {
            println("⚠️  Contract conditions not met (flood depth: ${floodDepthCm}cm)")
        }

        return result
    }
}

// Data Models
data class Location(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val region: String
)

data class SarFloodMeasurement(
    val floodDepthCm: Double,
    val floodAreaSqKm: Double,
    val confidence: Double,
    val demUsed: String,
    val quality: String
)
```

### Product 2: Heatwave Parametric

**Data Sources:** MODIS LST + ERA5
**Markets:** GCC (Saudi Arabia, UAE)
**Triggers:** > X°C for Y days
**Clients:** Construction, energy, government

#### Implementation

```kotlin
package com.atlasparametric.products.heatwave

import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.json.DigestUtils
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.Duration

class HeatwaveProduct(
    private val TrustWeave: TrustWeave,
    private val eoProviderDid: String
) {

    /**
     * Process heatwave data from MODIS LST
     */
    suspend fun processHeatwaveData(
        location: Location,
        lstData: List<LstMeasurement>,
        threshold: HeatwaveThreshold
    ): Result<VerifiableCredential> = trustweaveCatching {

        // Calculate consecutive days above threshold
        val consecutiveDays = calculateConsecutiveDays(lstData, threshold.temperatureC)

        val heatwaveData = buildJsonObject {
            put("id", "heatwave-${location.id}-${Instant.now().toEpochMilli()}")
            put("type", "HeatwaveMeasurement")
            put("location", buildJsonObject {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("region", location.region)
            })
            put("measurement", buildJsonObject {
                put("maxTemperatureC", lstData.maxOf { it.temperatureC })
                put("avgTemperatureC", lstData.average { it.temperatureC })
                put("consecutiveDays", consecutiveDays)
                put("thresholdC", threshold.temperatureC)
                put("minDaysRequired", threshold.minDays)
                put("source", "MODIS LST + ERA5")
                put("timestamp", Instant.now().toString())
            })
        }

        val dataDigest = DigestUtils.sha256DigestMultibase(heatwaveData)

        // Resolve DID from string (eoProviderDid is a DID string)
        import com.trustweave.core.Did
        val eoProviderDidObj = Did(eoProviderDid)
        val eoProviderResolution = trustWeave.resolveDid(eoProviderDidObj)
        val eoProviderDoc = when (eoProviderResolution) {
            is DidResolutionResult.Success -> eoProviderResolution.document
            else -> throw IllegalStateException("Failed to resolve EO provider DID")
        }
        val eoProviderKeyId = eoProviderDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method found")

        val heatwaveIssuanceResult = trustWeave.issue {
            credential {
                id("urn:eo:heatwave:${location.id}-${Instant.now().toEpochMilli()}")
                type("EarthObservationCredential", "InsuranceOracleCredential", "HeatwaveCredential")
                issuer(eoProviderDid)
                subject {
                    id("urn:eo:heatwave:${location.id}-${Instant.now().toEpochMilli()}")
                    "dataType" to "HeatwaveMeasurement"
                    "data" to heatwaveData
                    "dataDigest" to dataDigest
                    "provider" to eoProviderDid
                    "timestamp" to Instant.now().toString()
                }
                issued(Instant.now())
            }
            by(issuerDid = eoProviderDid, keyId = eoProviderKeyId)
        }
        
        val heatwaveCredential = when (heatwaveIssuanceResult) {
            is IssuanceResult.Success -> heatwaveIssuanceResult.credential
            else -> throw IllegalStateException("Failed to issue heatwave credential")
        }

        // Anchor to blockchain
        val anchorResult = trustWeave.blockchains.anchor(
            data = heatwaveCredential,
            serializer = VerifiableCredential.serializer(),
            chainId = "algorand:mainnet"
        )
        
        anchorResult.fold(
            onSuccess = { anchor ->
                // Credential anchored successfully
            },
            onFailure = { error ->
                throw IllegalStateException("Failed to anchor credential: ${error.message}")
            }
        )

        heatwaveCredential
    }

    /**
     * Create heatwave insurance contract
     */
    suspend fun createHeatwaveContract(
        insurerDid: String,
        insuredDid: String,
        basePayout: Double,
        threshold: HeatwaveThreshold,
        location: Location
    ): SmartContract {

        val contract = trustWeave.contracts.draft(
            request = ContractDraftRequest(
                contractType = ContractType.Insurance,
                executionModel = ExecutionModel.Parametric(
                    triggerType = TriggerType.EarthObservation,
                    evaluationEngine = "parametric-insurance"
                ),
                parties = ContractParties(
                    primaryPartyDid = insurerDid,
                    counterpartyDid = insuredDid
                ),
                terms = ContractTerms(
                    obligations = listOf(
                        Obligation(
                            id = "heatwave-payout",
                            partyDid = insurerDid,
                            description = "Pay out for consecutive days above threshold",
                            obligationType = ObligationType.PAYMENT
                        )
                    ),
                    conditions = listOf(
                        ContractCondition(
                            id = "heatwave-threshold",
                            description = "Temperature >= ${threshold.temperatureC}°C for ${threshold.minDays} days",
                            conditionType = ConditionType.COMPOSITE,
                            expression = "$.consecutiveDays >= ${threshold.minDays} && $.maxTemperatureC >= ${threshold.temperatureC}"
                        )
                    )
                ),
                effectiveDate = Instant.now().toString(),
                expirationDate = Instant.now().plusSeconds(365 * 24 * 60 * 60).toString(),
                contractData = buildJsonObject {
                    put("productType", "Heatwave")
                    put("basePayout", basePayout)
                    put("threshold", buildJsonObject {
                        put("temperatureC", threshold.temperatureC)
                        put("minDays", threshold.minDays)
                    })
                    put("location", buildJsonObject {
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("region", location.region)
                    })
                }
            )
        ).getOrThrow()

        return contract
    }

    /**
     * Execute heatwave contract
     */
    suspend fun executeHeatwaveContract(
        contract: SmartContract,
        heatwaveCredential: VerifiableCredential
    ): ExecutionResult {

        val credentialSubject = heatwaveCredential.credentialSubject
        val consecutiveDays = credentialSubject.jsonObject["data"]
            ?.jsonObject?.get("measurement")
            ?.jsonObject?.get("consecutiveDays")
            ?.jsonPrimitive?.content?.toInt()
            ?: error("Consecutive days not found")

        val maxTemp = credentialSubject.jsonObject["data"]
            ?.jsonObject?.get("measurement")
            ?.jsonObject?.get("maxTemperatureC")
            ?.jsonPrimitive?.content?.toDouble()
            ?: error("Max temperature not found")

        val executionContext = ExecutionContext(
            triggerData = buildJsonObject {
                put("consecutiveDays", consecutiveDays)
                put("maxTemperatureC", maxTemp)
                put("credentialId", heatwaveCredential.id)
            }
        )

        return trustWeave.contracts.executeContract(
            contract = contract,
            executionContext = executionContext
        ).getOrThrow()
    }

    private fun calculateConsecutiveDays(
        lstData: List<LstMeasurement>,
        thresholdC: Double
    ): Int {
        var maxConsecutive = 0
        var currentConsecutive = 0

        for (measurement in lstData.sortedBy { it.timestamp }) {
            if (measurement.temperatureC > thresholdC) {
                currentConsecutive++
                maxConsecutive = maxOf(maxConsecutive, currentConsecutive)
            } else {
                currentConsecutive = 0
            }
        }

        return maxConsecutive
    }
}

data class LstMeasurement(
    val temperatureC: Double,
    val timestamp: Instant
)

data class HeatwaveThreshold(
    val temperatureC: Double,
    val minDays: Int
)

data class HeatwavePolicy(
    val id: String,
    val location: Location,
    val threshold: HeatwaveThreshold,
    val basePayout: Double,
    val dailyIncrement: Double
)
```

### Product 3: Solar Attenuation Parametric

**Data Sources:** AOD (MODIS, VIIRS) + Irradiance (CERES)
**Markets:** GCC (Solar farms)
**Triggers:** >30% irradiance drop
**Clients:** ACWA Power, NEOM, UAE utilities

#### Implementation

```kotlin
package com.atlasparametric.products.solar

import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.json.DigestUtils
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class SolarAttenuationProduct(
    private val TrustWeave: TrustWeave,
    private val eoProviderDid: String
) {

    /**
     * Process solar attenuation data
     */
    suspend fun processSolarAttenuation(
        location: Location,
        aodData: AodMeasurement,
        irradianceData: IrradianceMeasurement
    ): Result<VerifiableCredential> = trustweaveCatching {

        // Calculate attenuation percentage
        val baselineIrradiance = irradianceData.baselineWattPerSqM
        val currentIrradiance = irradianceData.currentWattPerSqM
        val attenuationPercent = ((baselineIrradiance - currentIrradiance) / baselineIrradiance) * 100.0

        val solarData = buildJsonObject {
            put("id", "solar-attenuation-${location.id}-${Instant.now().toEpochMilli()}")
            put("type", "SolarAttenuationMeasurement")
            put("location", buildJsonObject {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("solarFarmId", location.solarFarmId)
            })
            put("measurement", buildJsonObject {
                put("aod", aodData.aodValue)
                put("baselineIrradiance", baselineIrradiance)
                put("currentIrradiance", currentIrradiance)
                put("attenuationPercent", attenuationPercent)
                put("source", "MODIS/VIIRS AOD + CERES Irradiance")
                put("timestamp", Instant.now().toString())
            })
        }

        val dataDigest = DigestUtils.sha256DigestMultibase(solarData)

        // Resolve DID from string (eoProviderDid is a DID string)
        import com.trustweave.core.Did
        val eoProviderDidObj = Did(eoProviderDid)
        val eoProviderResolution = trustWeave.resolveDid(eoProviderDidObj)
        val eoProviderDoc = when (eoProviderResolution) {
            is DidResolutionResult.Success -> eoProviderResolution.document
            else -> throw IllegalStateException("Failed to resolve EO provider DID")
        }
        val eoProviderKeyId = eoProviderDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method found")

        val solarIssuanceResult = trustWeave.issue {
            credential {
                id("urn:eo:solar-attenuation:${location.id}-${Instant.now().toEpochMilli()}")
                type("EarthObservationCredential", "InsuranceOracleCredential", "SolarAttenuationCredential")
                issuer(eoProviderDid)
                subject {
                    id("urn:eo:solar-attenuation:${location.id}-${Instant.now().toEpochMilli()}")
                    "dataType" to "SolarAttenuationMeasurement"
                    "data" to solarData
                    "dataDigest" to dataDigest
                    "provider" to eoProviderDid
                    "timestamp" to Instant.now().toString()
                }
                issued(Instant.now())
            }
            by(issuerDid = eoProviderDid, keyId = eoProviderKeyId)
        }
        
        val solarCredential = when (solarIssuanceResult) {
            is IssuanceResult.Success -> solarIssuanceResult.credential
            else -> throw IllegalStateException("Failed to issue solar credential")
        }

        // Anchor to blockchain
        val anchorResult = trustWeave.blockchains.anchor(
            data = solarCredential,
            serializer = VerifiableCredential.serializer(),
            chainId = "algorand:mainnet"
        )
        
        anchorResult.fold(
            onSuccess = { anchor ->
                // Credential anchored successfully
            },
            onFailure = { error ->
                throw IllegalStateException("Failed to anchor credential: ${error.message}")
            }
        )

        solarCredential
    }

    /**
     * Evaluate solar attenuation trigger
     */
    suspend fun evaluateSolarTrigger(
        policy: SolarPolicy,
        solarCredential: VerifiableCredential
    ): TriggerResult {

        val verification = trustWeave.verify {
            credential(solarCredential)
        }
        when (verification) {
            is VerificationResult.Valid -> {
                // Credential is valid, continue
            }
            is VerificationResult.Invalid -> {
                return TriggerResult(triggered = false, reason = "Credential invalid: ${verification.reason}")
            }
        }

        val credentialSubject = solarCredential.credentialSubject
        val attenuationPercent = credentialSubject.jsonObject["data"]
            ?.jsonObject?.get("measurement")
            ?.jsonObject?.get("attenuationPercent")
            ?.jsonPrimitive?.content?.toDouble()
            ?: return TriggerResult(triggered = false, reason = "Attenuation not found")

        val thresholdPercent = policy.thresholdPercent

        if (attenuationPercent < thresholdPercent) {
            return TriggerResult(
                triggered = false,
                reason = "Attenuation ($attenuationPercent%) below threshold ($thresholdPercent%)"
            )
        }

        // Calculate payout based on attenuation severity
        val excessAttenuation = attenuationPercent - thresholdPercent
        val payoutAmount = when {
            excessAttenuation >= 20 -> policy.coverageAmount * 1.0  // Full payout
            excessAttenuation >= 10 -> policy.coverageAmount * 0.75
            else -> policy.coverageAmount * 0.50
        }

        return TriggerResult(
            triggered = true,
            attenuationPercent = attenuationPercent,
            payoutAmount = payoutAmount,
            dataCredentialId = solarCredential.id
        )
    }
}

data class AodMeasurement(
    val aodValue: Double,
    val timestamp: Instant
)

data class IrradianceMeasurement(
    val baselineWattPerSqM: Double,
    val currentWattPerSqM: Double,
    val timestamp: Instant
)

data class SolarPolicy(
    val id: String,
    val location: Location,
    val thresholdPercent: Double = 30.0,
    val coverageAmount: Double
)
```

## Complete Workflow Example

### Complete Flood Insurance Workflow with Smart Contracts

```kotlin
suspend fun completeFloodInsuranceWorkflow() {
    // Step 1: Initialize TrustWeave
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),
            didMethodFactory = TestkitDidMethodFactory()
        )
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
        blockchains {
            "algorand:mainnet" to algorandClient
        }
    }

    // Step 2: Create DIDs for parties
    import com.trustweave.trust.types.DidCreationResult
    import com.trustweave.trust.types.DidResolutionResult
    import com.trustweave.trust.types.IssuanceResult
    import com.trustweave.trust.types.VerificationResult
    
    val insurerDidResult = trustWeave.createDid { method(KEY) }
    val insurerDid = when (insurerDidResult) {
        is DidCreationResult.Success -> insurerDidResult.did
        else -> throw IllegalStateException("Failed to create insurer DID")
    }
    
    val insuredDidResult = trustWeave.createDid { method(KEY) }
    val insuredDid = when (insuredDidResult) {
        is DidCreationResult.Success -> insuredDidResult.did
        else -> throw IllegalStateException("Failed to create insured DID")
    }
    
    val eoProviderDidResult = trustWeave.createDid { method(KEY) }
    val eoProviderDid = when (eoProviderDidResult) {
        is DidCreationResult.Success -> eoProviderDidResult.did
        else -> throw IllegalStateException("Failed to create EO provider DID")
    }
    
    val insurerResolution = trustWeave.resolveDid(insurerDid)
    val insurerDoc = when (insurerResolution) {
        is DidResolutionResult.Success -> insurerResolution.document
        else -> throw IllegalStateException("Failed to resolve insurer DID")
    }
    val insurerKeyId = insurerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No key found")

    // Step 3: Initialize product
    val floodProduct = SarFloodProduct(trustWeave, eoProviderDid.value)

    // Step 4: Create contract
    val contract = floodProduct.createFloodContract(
        insurerDid = insurerDid.value,
        insuredDid = insuredDid.value,
        coverageAmount = 1_000_000.0,
        location = Location(
            id = "loc-001",
            latitude = 35.2271,
            longitude = -80.8431,
            address = "Charlotte, NC",
            region = "North Carolina"
        )
    )

    // Step 5: Bind contract (issue VC and anchor)
    val bound = floodProduct.bindFloodContract(
        contract = contract,
        insurerDid = insurerDid.value,
        insurerKeyId = insurerKeyId
    )

    // Step 6: Activate contract
    val activeResult = trustWeave.contracts.activateContract(bound.contract.id)
    val active = activeResult.getOrThrow()

    // Step 7: Process EO data (in production, this comes from EO provider)
    val floodCredentialResult = floodProduct.processSarFloodData(
        location = Location(
            id = "loc-001",
            latitude = 35.2271,
            longitude = -80.8431,
            address = "Charlotte, NC",
            region = "North Carolina"
        ),
        sarData = SarFloodMeasurement(
            floodDepthCm = 75.0,
            floodAreaSqKm = 15.5,
            confidence = 0.95,
            demUsed = "SRTM",
            quality = "high"
        ),
        timestamp = Instant.now()
    )
    
    val floodCredential = floodCredentialResult.getOrThrow()

    // Step 8: Execute contract
    val executionResult = floodProduct.executeFloodContract(
        contract = active,
        floodCredential = floodCredential
    )

    // Step 9: Process payout (application-specific)
    if (executionResult.executed) {
        processPayout(executionResult)
    }
}
```

## Complete System Integration

### Main Application

```kotlin
package com.atlasparametric

import com.trustweave.TrustWeave
import com.trustweave.chains.algorand.AlgorandBlockchainAnchorClient
import com.atlasparametric.products.flood.SarFloodProduct
import com.atlasparametric.products.heatwave.HeatwaveProduct
import com.atlasparametric.products.solar.SolarAttenuationProduct
import kotlinx.coroutines.runBlocking

/**
 * Atlas Parametric MGA Platform
 *
 * Main application entry point
 */
class AtlasParametricPlatform {

    private val TrustWeave: TrustWeave
    private val sarFloodProduct: SarFloodProduct
    private val heatwaveProduct: HeatwaveProduct
    private val solarProduct: SolarAttenuationProduct

    init {
        // Initialize TrustWeave with blockchain anchoring
        TrustWeave = TrustWeave.create {
            blockchains {
                "algorand:mainnet" to AlgorandBlockchainAnchorClient(
                    chainId = "algorand:mainnet",
                    apiKey = System.getenv("ALGORAND_API_KEY")
                )
            }
        }

        // Create DIDs for EO providers
        val eoProviderDidResult = runBlocking {
            trustWeave.createDid { method(KEY) }
        }
        
        val eoProviderDid = when (eoProviderDidResult) {
            is DidCreationResult.Success -> eoProviderDidResult.did
            else -> throw IllegalStateException("Failed to create EO provider DID")
        }

        // Initialize products
        sarFloodProduct = SarFloodProduct(trustWeave, eoProviderDid.value)
        heatwaveProduct = HeatwaveProduct(trustWeave, eoProviderDid.value)
        solarProduct = SolarAttenuationProduct(trustWeave, eoProviderDid.value)
    }

    /**
     * Process EO data and execute contracts
     */
    suspend fun processEoDataAndExecute(
        productType: ProductType,
        eoData: Any,
        contracts: List<SmartContract>
    ): List<ExecutionResult> {

        return when (productType) {
            ProductType.SAR_FLOOD -> {
                val floodData = eoData as SarFloodMeasurement
                processFloodExecutions(floodData, contracts)
            }
            ProductType.HEATWAVE -> {
                val heatData = eoData as List<LstMeasurement>
                processHeatwaveExecutions(heatData, contracts)
            }
            ProductType.SOLAR_ATTENUATION -> {
                val solarData = eoData as Pair<AodMeasurement, IrradianceMeasurement>
                processSolarExecutions(solarData, contracts)
            }
        }
    }

    private suspend fun processFloodExecutions(
        floodData: SarFloodMeasurement,
        contracts: List<SmartContract>
    ): List<ExecutionResult> {

        val results = mutableListOf<ExecutionResult>()

        for (contract in contracts.filter {
            it.contractData.jsonObject["productType"]?.jsonPrimitive?.content == "SarFlood"
        }) {
            // Process SAR data and issue credential
            val location = extractLocation(contract)
            val floodCredentialResult = sarFloodProduct.processSarFloodData(
                location = location,
                sarData = floodData,
                timestamp = Instant.now()
            )
            
            val floodCredential = floodCredentialResult.getOrNull() ?: run {
                println("Failed to process flood data")
                continue
            }

            // Execute contract
            val executionResult = sarFloodProduct.executeFloodContract(
                contract = contract,
                floodCredential = floodCredential
            )

            if (executionResult.executed) {
                // Process payout
                processPayout(executionResult)
            }

            results.add(executionResult)
        }

        return results
    }

    private fun extractLocation(contract: SmartContract): Location {
        val locationData = contract.contractData.jsonObject["location"]?.jsonObject
            ?: error("Location not found in contract")

        return Location(
            id = locationData["address"]?.jsonPrimitive?.content ?: "unknown",
            latitude = locationData["latitude"]?.jsonPrimitive?.content?.toDouble() ?: 0.0,
            longitude = locationData["longitude"]?.jsonPrimitive?.content?.toDouble() ?: 0.0,
            address = locationData["address"]?.jsonPrimitive?.content ?: "",
            region = locationData["region"]?.jsonPrimitive?.content ?: ""
        )
    }

    private suspend fun processPayout(executionResult: ExecutionResult) {
        // Integrate with banking API (Stripe, Plaid, etc.)
        // Extract payout amount from executionResult.outcomes
        executionResult.outcomes.forEach { outcome ->
            outcome.monetaryImpact?.let { amount ->
                // Execute payout via banking API
                println("Processing payout: ${amount.amount} ${amount.currency}")
            }
        }
    }
}

enum class ProductType {
    SAR_FLOOD,
    HEATWAVE,
    SOLAR_ATTENUATION,
    HURRICANE,
    DROUGHT
}

data class PayoutResult(
    val policyId: String,
    val amount: Double,
    val status: String,
    val payoutCredentialId: String,
    val timestamp: Instant
)
```

## Multi-Provider EO Data Acceptance

One of TrustWeave's key advantages is accepting EO data from multiple providers:

```kotlin
/**
 * Accept EO data from any certified provider
 */
class MultiProviderEoDataService(
    private val TrustWeave: TrustWeave
) {

    private val certifiedProviders = setOf(
        "did:key:esa-provider",
        "did:key:planet-provider",
        "did:key:nasa-provider",
        "did:key:noaa-provider"
    )

    /**
     * Accept EO data credential from any certified provider
     */
    suspend fun acceptEoDataCredential(
        dataCredential: VerifiableCredential
    ): Result<EoData> = trustweaveCatching {

        // Step 1: Verify credential
        val verificationResult = trustWeave.verify {
            credential(dataCredential)
        }
        
        when (verificationResult) {
            is VerificationResult.Valid -> {
                // Credential is valid, continue
            }
            is VerificationResult.Invalid -> {
                error("Credential verification failed: ${verificationResult.errors}")
            }
        }

        // Step 2: Check if provider is certified
        val issuerDid = dataCredential.issuer.firstOrNull()?.id
            ?: error("No issuer found in credential")

        if (!certifiedProviders.contains(issuerDid)) {
            error("Provider $issuerDid is not certified")
        }

        // Step 3: Extract and return data
        val credentialSubject = dataCredential.credentialSubject
        val dataType = credentialSubject.jsonObject["dataType"]?.jsonPrimitive?.content
        val data = credentialSubject.jsonObject["data"]?.jsonObject

        EoData(
            type = dataType ?: "Unknown",
            data = data ?: buildJsonObject {},
            provider = issuerDid,
            credentialId = dataCredential.id
        )
    }
}

data class EoData(
    val type: String,
    val data: JsonObject,
    val provider: String,
    val credentialId: String
)
```

## Broker Portal Integration

```kotlin
/**
 * Broker Portal API
 */
@RestController
@RequestMapping("/api/broker")
class BrokerPortalController(
    private val platform: AtlasParametricPlatform,
    private val pricingEngine: PricingEngine
) {

    /**
     * Get quote for parametric insurance
     */
    @PostMapping("/quote")
    suspend fun getQuote(
        @RequestBody request: QuoteRequest
    ): QuoteResponse {

        val premium = pricingEngine.calculatePremium(
            productType = request.productType,
            location = request.location,
            coverageAmount = request.coverageAmount
        )

        return QuoteResponse(
            premium = premium,
            coverageAmount = request.coverageAmount,
            productType = request.productType
        )
    }

    /**
     * Bind policy
     */
    @PostMapping("/bind")
    suspend fun bindPolicy(
        @RequestBody request: BindRequest
    ): PolicyResponse {

        // Create policy
        val policy = createPolicy(request)

        // Issue policy credential
        val policyCredential = issuePolicyCredential(policy)

        return PolicyResponse(
            policyId = policy.id,
            policyCredentialId = policyCredential.id,
            status = "BOUND"
        )
    }
}
```

## Regulatory Compliance & Audit Trails

```kotlin
/**
 * Audit Trail Service using TrustWeave blockchain anchoring
 */
class AuditTrailService(
    private val TrustWeave: TrustWeave
) {

    /**
     * Record audit event and anchor to blockchain
     */
    suspend fun recordAuditEvent(
        event: AuditEvent
    ): AuditRecord {

        // Create audit record
        val auditRecord = buildJsonObject {
            put("id", event.id)
            put("timestamp", event.timestamp.toString())
            put("eventType", event.type)
            put("actor", event.actor)
            put("resource", event.resource)
            put("details", event.details)
        }

        // Anchor to blockchain for immutability
        val anchorResult = trustWeave.blockchains.anchor(
            data = auditRecord,
            serializer = JsonObject.serializer(),
            chainId = "algorand:mainnet"
        ).getOrThrow()

        return AuditRecord(
            eventId = event.id,
            anchorRef = anchorResult.ref,
            timestamp = event.timestamp
        )
    }

    /**
     * Verify audit record integrity
     */
    suspend fun verifyAuditRecord(
        record: AuditRecord
    ): Boolean {

        // Read from blockchain
        val client = TrustWeave.getBlockchainClient(record.anchorRef.chainId)
            ?: return false

        val anchorResult = client.readPayload(record.anchorRef)

        // Verify integrity
        return anchorResult.payload.jsonObject["id"]?.jsonPrimitive?.content == record.eventId
    }
}
```

## Deployment Strategy

### Phase 1: MVP (Weeks 1-6)

1. **Setup TrustWeave**
   - Initialize TrustWeave with Algorand or Polygon
   - Create DIDs for EO providers
   - Setup blockchain anchoring

2. **Build SAR Flood Product**
   - Implement SAR flood detection
   - Create trigger evaluation logic
   - Build payout automation

3. **Broker Portal MVP**
   - Quote generation
   - Policy binding
   - Trigger dashboard

### Phase 2: Production (Months 2-12)

1. **Add Heatwave Product**
2. **Add Solar Attenuation Product**
3. **Multi-provider EO data acceptance**
4. **Regulatory compliance features**
5. **Reinsurer dashboard**

## Key Benefits of Using TrustWeave

1. **Standardization**: W3C-compliant format works with all EO providers
2. **Trust**: Cryptographic proof of data integrity
3. **Multi-Provider**: Accept data from ESA, Planet, NASA without custom integrations
4. **Regulatory Compliance**: Blockchain-anchored audit trails
5. **Automation**: Enable instant payouts with verifiable triggers
6. **Cost Reduction**: Eliminate custom API integrations

## Next Steps

1. **Review Existing Scenarios**:
   - [Parametric Insurance with EO Data](parametric-insurance-eo-scenario.md)
   - [Earth Observation Scenario](earth-observation-scenario.md)

2. **Explore TrustWeave APIs**:
   - [Core API Reference](../api-reference/core-api.md)
   - [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md)

3. **Start Building**:
   - Clone TrustWeave repository
   - Follow [Quick Start Guide](../getting-started/quick-start.md)
   - Implement SAR flood product first

## Related Documentation

- [Parametric Insurance with EO Data](parametric-insurance-eo-scenario.md) - EO data insurance patterns
- [Earth Observation Scenario](earth-observation-scenario.md) - EO data integrity
- [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) - Anchoring concepts
- [API Reference](../api-reference/core-api.md) - Complete API documentation


