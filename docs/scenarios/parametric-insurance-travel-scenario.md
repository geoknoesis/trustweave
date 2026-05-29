---
title: Parametric Insurance for Travel Disruptions
parent: Use Case Scenarios
nav_order: 25
---

# Parametric Insurance for Travel Disruptions

This guide demonstrates how to build a parametric travel insurance system using TrustWeave, similar to Chubb Travel Pro. You'll learn how to create verifiable credentials for travel disruption data (flight delays, weather events, baggage tracking) that trigger automatic insurance payouts, solving the "Oracle Problem" by enabling standardized, multi-provider data ecosystems for travel insurance.

## What You'll Build

By the end of this tutorial, you'll have:

- Created DIDs for travel insurance companies, airlines, weather services, and baggage tracking providers
- Issued verifiable credentials for travel disruption data (flight delays, weather events, baggage status)
- Built a standardized data oracle system using VCs that accepts data from multiple providers
- Implemented parametric trigger verification for automatic payouts
- Created multi-provider data acceptance workflows
- Anchored travel data credentials to blockchain for tamper-proof triggers

## Big Picture & Significance

### The Travel Insurance Oracle Problem

Parametric travel insurance pays out automatically when specific conditions are met (e.g., flight delay > 3 hours, weather event at destination, lost baggage > 24 hours). Currently, insurers rely on proprietary, siloed data sources to trigger payouts, creating vendor lock-in and limiting data source options.

**Industry Context:**
- **Market Size**: Travel insurance market projected to reach $50+ billion by 2030
- **Active Players**: Chubb Travel Pro, Allianz Travel, AXA Travel Insurance
- **Current Challenge**: Each insurer builds custom API integrations for each data provider (airlines, weather services, baggage systems)
- **The Gap**: No standardized way to accept travel disruption data from multiple certified providers (airlines, weather services, IATA)
- **Trust Issue**: Need cryptographic proof that data used for automatic payout is authentic and hasn't been tampered with

**Why This Matters:**
1. **Standardization**: Accept travel data from any certified provider without custom integrations
2. **Trust**: Cryptographic proof prevents fraud and data manipulation
3. **Multi-Provider**: Enable competition and redundancy in data sources
4. **Automation**: Enable automatic payouts based on verifiable travel data
5. **Cost Reduction**: Eliminate custom API integrations for each provider
6. **Transparency**: Verifiable data lineage for regulatory compliance

### Real-World Examples

**Chubb Travel Pro** - Digital travel protection:
- Provides automatic payouts for flight delays, weather disruptions, and baggage issues
- Currently relies on proprietary data integrations
- **Solution**: Adopting VC pattern allows accepting data from any certified provider (airlines, IATA, weather services) without custom API integrations

**Allianz Travel Insurance** - Global travel insurance:
- Uses flight delay data and weather data for automatic claims
- Claims processing relies on trusted data sources
- **Solution**: Wrapping travel disruption data in VCs ensures data used for automatic payout is authentic and prevents fraud

## Value Proposition

### Problems Solved

1. **Oracle Standardization**: Standard format for travel disruption data from any provider
2. **Multi-Provider Support**: Accept data from airlines, IATA, weather services, baggage systems without custom integrations
3. **Data Integrity**: Cryptographic proof prevents tampering and fraud
4. **Automated Triggers**: Enable automatic insurance payouts based on verifiable travel data
5. **Regulatory Compliance**: Verifiable data lineage for audit trails
6. **Cost Reduction**: Eliminate custom API integrations
7. **Trust**: Build trust in travel insurance through verifiable data

### Business Benefits

**For Insurance Companies:**
- **Cost Reduction**: No custom integrations needed for each data provider
- **Flexibility**: Switch between data providers easily
- **Trust**: Cryptographic proof of data integrity
- **Compliance**: Automated audit trails
- **Competition**: Enable multiple data providers to compete

**For Travel Data Providers (Airlines, Weather Services, IATA):**
- **Standardization**: One format works for all insurers
- **Market Access**: Reach all insurance companies with standard format
- **Trust**: Build trust through verifiable credentials
- **Differentiation**: Stand out with verifiable data quality

**For Travelers:**
- **Transparency**: Verify data used for payouts
- **Fairness**: Standardized data prevents manipulation
- **Speed**: Faster payouts with automated triggers
- **Trust**: Cryptographic proof of data integrity
- **Convenience**: Automatic claims without paperwork

## Understanding the Problem

Travel parametric insurance needs:

1. **Standardized Data Format**: Accept travel disruption data from any provider
2. **Data Integrity**: Verify data hasn't been tampered with
3. **Multi-Provider Support**: Work with airlines, IATA, weather services, baggage systems
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

Here's a complete travel parametric insurance workflow covering flight delays, weather guarantees, and baggage tracking:

```kotlin
package com.example.travel.insurance

import org.trustweave.trust.TrustWeave
import org.trustweave.core.*
import org.trustweave.core.util.DigestUtils
import kotlinx.coroutines.runBlocking
import org.trustweave.core.json.jsonData
import java.time.Instant
import java.time.Duration
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.types.getOrThrow
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.credential.results.getOrThrow

fun main() = runBlocking {
    println("=".repeat(70))
    println("Parametric Travel Insurance - Complete Example")
    println("=".repeat(70))

    // Step 1: Create TrustWeave instance
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }
    println("\n[OK] TrustWeave initialized")

    // Step 2: Create DIDs for insurance company, airline, weather service, and baggage system
    
    val insuranceDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
    val airlineDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
    
    val weatherServiceDidResult = trustWeave.createDid { method(KEY) }
    
    val weatherServiceDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
    val baggageSystemDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()

    println("[OK] Insurance Company DID: ${insuranceDid.value}")
    println("[OK] Airline DID: ${airlineDid.value}")
    println("[OK] Weather Service DID: ${weatherServiceDid.value}")
    println("[OK] Baggage System DID: ${baggageSystemDid.value}")

    // ============================================
    // Scenario 1: Flight Delay Automatic Payout
    // ============================================
    println("\n" + "-".repeat(70))
    println("Scenario 1: Flight Delay Automatic Payout")
    println("-".repeat(70))

    val airlineDoc = when (val res = trustWeave.resolveDid(airlineDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
    val airlineKeyId = airlineDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: error("No verification method found")

    // Create flight delay data (issued by airline)
    val flightDelayData = jsonData {
        "id" to "flight-delay-AA1234-2024-10-08"
        "type" to "FlightDelay"
        "flight" {
            "flightNumber" to "AA1234"
            "departure" {
                "airport" to "JFK"
                "scheduled" to "2024-10-08T14:00:00Z"
                "actual" to "2024-10-08T17:30:00Z"
            }
            "arrival" {
                "airport" to "LHR"
                "scheduled" to "2024-10-08T23:00:00Z"
            }
            "aircraft" to "Boeing 777-300ER"
        }
        "delay" {
            "durationMinutes" to 210  // 3.5 hours delay
            "reason" to "Weather"
            "timestamp" to Instant.now().toString()
        }
    }

    val delayDigest = DigestUtils.sha256DigestMultibase(flightDelayData)

    // Airline issues verifiable credential for flight delay
    val flightDelayIssuanceResult = trustWeave.issue {
        credential {
            id("urn:travel:flight-delay:AA1234-2024-10-08")
            type("VerifiableCredential", "FlightDelayCredential", "TravelOracleCredential")
            issuer(airlineDid)
            subject {
                id("urn:travel:flight-delay:AA1234-2024-10-08")
                "dataType" to "FlightDelay"
                "data" to flightDelayData
                "dataDigest" to delayDigest
                "provider" to airlineDid.value
                "timestamp" to Instant.now().toString()
            }
        }
        signedBy(airlineDid)
    }
    
    
    val flightDelayCredential = flightDelayIssuanceResult.getOrThrow()

    println("[OK] Flight Delay Credential issued: ${flightDelayCredential.id}")

    // Verify credential
    val delayVerificationResult = trustWeave.verify {
        credential(flightDelayCredential)
    }
    
    when (delayVerificationResult) {
        is VerificationResult.Valid -> {
            println("[OK] Flight Delay Credential verified")
        }
        is VerificationResult.Invalid -> {
            println("[FAIL] Flight delay credential invalid")
            return@runBlocking
        }
    }

    // Check parametric trigger (policy: payout if delay > 3 hours)
    val delayMinutes = flightDelayData.jsonObject["delay"]
        ?.jsonObject?.get("durationMinutes")
        ?.jsonPrimitive?.content?.toInt() ?: 0

    val delayThresholdMinutes = 180  // 3 hours
    val shouldPayoutDelay = delayMinutes > delayThresholdMinutes

    println("\n[stats] Flight Delay Trigger Check:")
    println("   Delay: $delayMinutes minutes (${delayMinutes / 60.0} hours)")
    println("   Threshold: $delayThresholdMinutes minutes")

    if (shouldPayoutDelay) {
        println("   [OK] TRIGGER MET: Delay exceeds threshold")
        println("   [payout] Automatic payout should be triggered")

        val insuranceDoc = when (val res = trustWeave.resolveDid(insuranceDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
        val insuranceKeyId = insuranceDoc.verificationMethod.firstOrNull()?.extractKeyId()
            ?: error("No verification method found")

        val delayPayoutIssuanceResult = trustWeave.issue {
            credential {
                id("urn:travel:payout:delay-AA1234-2024-10-08")
                type("VerifiableCredential", "TravelInsurancePayoutCredential")
                issuer(insuranceDid)
                subject {
                    id("urn:travel:payout:delay-AA1234-2024-10-08")
                    "policyId" to "TRAVEL-POL-12345"
                    "triggerType" to "FlightDelay"
                    "delayMinutes" to delayMinutes
                    "thresholdMinutes" to delayThresholdMinutes
                    "dataCredentialId" to flightDelayCredential.id
                    "payoutAmount" to 250.0
                    "currency" to "USD"
                    "payoutMethod" to "virtual-card"
                    "timestamp" to Instant.now().toString()
                }
            }
            signedBy(insuranceDid)
        }
        
        val delayPayoutCredential = delayPayoutIssuanceResult.getOrThrow()

        println("[OK] Delay Payout Credential issued: ${delayPayoutCredential.id}")
        println("   Payout amount: $250 USD via virtual card")
    } else {
        println("   [FAIL] TRIGGER NOT MET: Delay below threshold")
    }

    // ============================================
    // Scenario 2: Weather Guarantee Automatic Payout
    // ============================================
    println("\n" + "-".repeat(70))
    println("Scenario 2: Weather Guarantee Automatic Payout")
    println("-".repeat(70))

    val weatherDoc = when (val res = trustWeave.resolveDid(weatherServiceDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
    val weatherKeyId = weatherDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: error("No verification method found")

    // Create weather event data (issued by weather service)
    val weatherData = jsonData {
        "id" to "weather-event-LHR-2024-10-08"
        "type" to "WeatherEvent"
        "location" {
            "airport" to "LHR"
            "city" to "London"
            "country" to "UK"
            "coordinates" {
                "latitude" to 51.4700
                "longitude" to -0.4543
            }
        }
        "event" {
            "type" to "Severe Storm"
            "severity" to "High"
            "description" to "Thunderstorms with heavy rain and strong winds"
            "startTime" to "2024-10-08T12:00:00Z"
            "endTime" to "2024-10-08T18:00:00Z"
            "windSpeed" to 45  // mph
            "visibility" to 0.5  // miles
        }
        "impact" {
            "travelDisruption" to true
            "flightsCancelled" to 50
            "flightsDelayed" to 120
            "timestamp" to Instant.now().toString()
        }
    }

    val weatherDigest = DigestUtils.sha256DigestMultibase(weatherData)

    // Weather service issues verifiable credential
    val weatherIssuanceResult = trustWeave.issue {
        credential {
            id("urn:travel:weather-event:LHR-2024-10-08")
            type("VerifiableCredential", "WeatherEventCredential", "TravelOracleCredential")
            issuer(weatherServiceDid)
            subject {
                id("urn:travel:weather-event:LHR-2024-10-08")
                "dataType" to "WeatherEvent"
                "data" to weatherData
                "dataDigest" to weatherDigest
                "provider" to weatherServiceDid.value
                "timestamp" to Instant.now().toString()
            }
        }
        signedBy(weatherServiceDid)
    }
    
    val weatherCredential = weatherIssuanceResult.getOrThrow()

    println("[OK] Weather Event Credential issued: ${weatherCredential.id}")

    // Verify credential
    val weatherVerificationResult = trustWeave.verify {
        credential(weatherCredential)
    }
    
    when (weatherVerificationResult) {
        is VerificationResult.Valid -> {
            println("[OK] Weather Event Credential verified")
        }
        is VerificationResult.Invalid -> {
            println("[FAIL] Weather credential invalid")
            return@runBlocking
        }
    }

    // Check parametric trigger (policy: payout for severe weather at destination)
    val isSevereWeather = weatherData.jsonObject["event"]
        ?.jsonObject?.get("severity")
        ?.jsonPrimitive?.content == "High"

    val causesTravelDisruption = weatherData.jsonObject["impact"]
        ?.jsonObject?.get("travelDisruption")
        ?.jsonPrimitive?.content?.toBoolean() == true

    val shouldPayoutWeather = isSevereWeather && causesTravelDisruption

    println("\n[stats] Weather Guarantee Trigger Check:")
    println("   Severity: High")
    println("   Travel Disruption: $causesTravelDisruption")

    if (shouldPayoutWeather) {
        println("   [OK] TRIGGER MET: Severe weather causes travel disruption")
        println("   [payout] Automatic payout should be triggered")

        val weatherPayoutIssuanceResult = trustWeave.issue {
            credential {
                id("urn:travel:payout:weather-LHR-2024-10-08")
                type("VerifiableCredential", "TravelInsurancePayoutCredential")
                issuer(insuranceDid)
                subject {
                    id("urn:travel:payout:weather-LHR-2024-10-08")
                    "policyId" to "TRAVEL-POL-12345"
                    "triggerType" to "WeatherGuarantee"
                    "location" to "LHR"
                    "severity" to "High"
                    "dataCredentialId" to weatherCredential.id
                    "payoutAmount" to 500.0
                    "currency" to "USD"
                    "payoutMethod" to "airline-miles"
                    "timestamp" to Instant.now().toString()
                }
            }
            signedBy(insuranceDid)
        }
        
        val weatherPayoutCredential = weatherPayoutIssuanceResult.getOrThrow()

        println("[OK] Weather Payout Credential issued: ${weatherPayoutCredential.id}")
        println("   Payout amount: $500 USD in airline miles")
    } else {
        println("   [FAIL] TRIGGER NOT MET: Weather conditions don't meet criteria")
    }

    // ============================================
    // Scenario 3: Baggage Delay Automatic Payout
    // ============================================
    println("\n" + "-".repeat(70))
    println("Scenario 3: Baggage Delay Automatic Payout")
    println("-".repeat(70))

    val baggageDoc = when (val res = trustWeave.resolveDid(baggageSystemDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
    val baggageKeyId = baggageDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: error("No verification method found")

    // Create baggage delay data (issued by baggage tracking system)
    val baggageData = jsonData {
        "id" to "baggage-delay-ABC123-2024-10-08"
        "type" to "BaggageDelay"
        "baggage" {
            "tagNumber" to "ABC123"
            "flightNumber" to "AA1234"
            "destination" to "LHR"
        }
        "status" {
            "status" to "Delayed"
            "location" to "JFK"
            "lastSeen" to "2024-10-08T14:00:00Z"
            "currentTime" to "2024-10-09T15:00:00Z"  // 25 hours later
        }
        "delay" {
            "durationHours" to 25
            "estimatedArrival" to "2024-10-09T20:00:00Z"
        }
    }

    val baggageDigest = DigestUtils.sha256DigestMultibase(baggageData)

    // Baggage system issues verifiable credential
    val baggageIssuanceResult = trustWeave.issue {
        credential {
            id("urn:travel:baggage-delay:ABC123-2024-10-08")
            type("VerifiableCredential", "BaggageDelayCredential", "TravelOracleCredential")
            issuer(baggageSystemDid)
            subject {
                id("urn:travel:baggage-delay:ABC123-2024-10-08")
                "dataType" to "BaggageDelay"
                "data" to baggageData
                "dataDigest" to baggageDigest
                "provider" to baggageSystemDid.value
                "timestamp" to Instant.now().toString()
            }
        }
        signedBy(baggageSystemDid)
    }
    
    val baggageCredential = baggageIssuanceResult.getOrThrow()

    println("[OK] Baggage Delay Credential issued: ${baggageCredential.id}")

    // Verify credential
    val baggageVerificationResult = trustWeave.verify {
        credential(baggageCredential)
    }
    
    when (baggageVerificationResult) {
        is VerificationResult.Valid -> {
            println("[OK] Baggage Delay Credential verified")
        }
        is VerificationResult.Invalid -> {
            println("[FAIL] Baggage credential invalid")
            return@runBlocking
        }
    }

    // Check parametric trigger (policy: payout if baggage delayed > 24 hours)
    val delayHours = baggageData.jsonObject["delay"]
        ?.jsonObject?.get("durationHours")
        ?.jsonPrimitive?.content?.toInt() ?: 0

    val baggageThresholdHours = 24
    val shouldPayoutBaggage = delayHours > baggageThresholdHours

    println("\n[stats] Baggage Delay Trigger Check:")
    println("   Delay: $delayHours hours")
    println("   Threshold: $baggageThresholdHours hours")

    if (shouldPayoutBaggage) {
        println("   [OK] TRIGGER MET: Baggage delay exceeds threshold")
        println("   [payout] Automatic payout should be triggered")

        val baggagePayoutIssuanceResult = trustWeave.issue {
            credential {
                id("urn:travel:payout:baggage-ABC123-2024-10-08")
                type("VerifiableCredential", "TravelInsurancePayoutCredential")
                issuer(insuranceDid)
                subject {
                    id("urn:travel:payout:baggage-ABC123-2024-10-08")
                    "policyId" to "TRAVEL-POL-12345"
                    "triggerType" to "BaggageDelay"
                    "delayHours" to delayHours
                    "thresholdHours" to baggageThresholdHours
                    "dataCredentialId" to baggageCredential.id
                    "payoutAmount" to 200.0
                    "currency" to "USD"
                    "payoutMethod" to "e-voucher"
                    "timestamp" to Instant.now().toString()
                }
            }
            signedBy(insuranceDid)
        }
        
        val baggagePayoutCredential = baggagePayoutIssuanceResult.getOrThrow()

        println("[OK] Baggage Payout Credential issued: ${baggagePayoutCredential.id}")
        println("   Payout amount: $200 USD via e-voucher")
    } else {
        println("   [FAIL] TRIGGER NOT MET: Baggage delay below threshold")
    }

    // ============================================
    // Data Integrity Verification
    // ============================================
    println("\n" + "-".repeat(70))
    println("Data Integrity Verification")
    println("-".repeat(70))

    // Verify flight delay data integrity
    val currentDelayDigest = DigestUtils.sha256DigestMultibase(flightDelayData)
    val credentialDelayDigest = flightDelayCredential.credentialSubject.jsonObject["dataDigest"]
        ?.jsonPrimitive?.content ?: ""

    if (currentDelayDigest == credentialDelayDigest) {
        println("[OK] Flight Delay Data Integrity Verified")
    } else {
        println("[FAIL] Flight Delay Data Integrity FAILED")
    }

    // Verify weather data integrity
    val currentWeatherDigest = DigestUtils.sha256DigestMultibase(weatherData)
    val credentialWeatherDigest = weatherCredential.credentialSubject.jsonObject["dataDigest"]
        ?.jsonPrimitive?.content ?: ""

    if (currentWeatherDigest == credentialWeatherDigest) {
        println("[OK] Weather Data Integrity Verified")
    } else {
        println("[FAIL] Weather Data Integrity FAILED")
    }

    // Verify baggage data integrity
    val currentBaggageDigest = DigestUtils.sha256DigestMultibase(baggageData)
    val credentialBaggageDigest = baggageCredential.credentialSubject.jsonObject["dataDigest"]
        ?.jsonPrimitive?.content ?: ""

    if (currentBaggageDigest == credentialBaggageDigest) {
        println("[OK] Baggage Data Integrity Verified")
    } else {
        println("[FAIL] Baggage Data Integrity FAILED")
    }

    println("\n" + "=".repeat(70))
    println("[OK] Parametric Travel Insurance Scenario Complete!")
    println("=".repeat(70))
}
```

**Expected Output:**
```
======================================================================
Parametric Travel Insurance - Complete Example
======================================================================

[OK] TrustWeave initialized
[OK] Insurance Company DID: did:key:z6Mk...
[OK] Airline DID: did:key:z6Mk...
[OK] Weather Service DID: did:key:z6Mk...
[OK] Baggage System DID: did:key:z6Mk...

----------------------------------------------------------------------
Scenario 1: Flight Delay Automatic Payout
----------------------------------------------------------------------
[OK] Flight Delay Credential issued: urn:uuid:...
[OK] Flight Delay Credential verified

[stats] Flight Delay Trigger Check:
   Delay: 210 minutes (3.5 hours)
   Threshold: 180 minutes
   [OK] TRIGGER MET: Delay exceeds threshold
   [payout] Automatic payout should be triggered
[OK] Delay Payout Credential issued: urn:uuid:...
   Payout amount: $250 USD via virtual card

----------------------------------------------------------------------
Scenario 2: Weather Guarantee Automatic Payout
----------------------------------------------------------------------
[OK] Weather Event Credential issued: urn:uuid:...
[OK] Weather Event Credential verified

[stats] Weather Guarantee Trigger Check:
   Severity: High
   Travel Disruption: true
   [OK] TRIGGER MET: Severe weather causes travel disruption
   [payout] Automatic payout should be triggered
[OK] Weather Payout Credential issued: urn:uuid:...
   Payout amount: $500 USD in airline miles

----------------------------------------------------------------------
Scenario 3: Baggage Delay Automatic Payout
----------------------------------------------------------------------
[OK] Baggage Delay Credential issued: urn:uuid:...
[OK] Baggage Delay Credential verified

[stats] Baggage Delay Trigger Check:
   Delay: 25 hours
   Threshold: 24 hours
   [OK] TRIGGER MET: Baggage delay exceeds threshold
   [payout] Automatic payout should be triggered
[OK] Baggage Payout Credential issued: urn:uuid:...
   Payout amount: $200 USD via e-voucher

----------------------------------------------------------------------
Data Integrity Verification
----------------------------------------------------------------------
[OK] Flight Delay Data Integrity Verified
[OK] Weather Data Integrity Verified
[OK] Baggage Data Integrity Verified

======================================================================
[OK] Parametric Travel Insurance Scenario Complete!
======================================================================
```

## Step 3: Multi-Provider Support

The key advantage of using VCs is accepting data from multiple providers:

```kotlin
// Accept data from any certified provider
val providers = listOf("IATA", "FlightStats", "OpenWeather", "Weather.com", "SITA")

suspend fun acceptTravelDataFromAnyProvider(
    trustWeave: TrustWeave,
    providerDid: String,
    dataCredential: VerifiableCredential
): Boolean {
    // Verify credential
    val verificationResult = trustWeave.verify {
        credential(dataCredential)
    }
    
    when (verificationResult) {
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

## Step 4: Medical Emergency Example

For medical emergency coverage with quick payouts:

```kotlin
// Medical service provider issues emergency credential
val medicalData = jsonData {
    "id" to "medical-emergency-2024-10-08"
    "type" to "MedicalEmergency"
    "patient" {
        "policyNumber" to "TRAVEL-POL-12345"
        // No PII - privacy preserving
    }
    "emergency" {
        "type" to "Medical Treatment"
        "location" {
            "country" to "UK"
            "city" to "London"
        }
        "timestamp" to Instant.now().toString()
        "amount" to 1500.0
        "currency" to "USD"
    }
}

val medicalDigest = DigestUtils.sha256DigestMultibase(medicalData)

val medicalIssuanceResult = trustWeave.issue {
    credential {
        id("urn:travel:medical-emergency:2024-10-08")
        type("VerifiableCredential", "MedicalEmergencyCredential", "TravelOracleCredential")
        issuer(medicalProviderDid.value)
        subject {
            id("urn:travel:medical-emergency:2024-10-08")
            "dataType" to "MedicalEmergency"
            "data" to medicalData
            "dataDigest" to medicalDigest
            "provider" to medicalProviderDid.value
        }
    }
    signedBy(medicalProviderDid)
}

val medicalCredential = medicalIssuanceResult.getOrThrow()

// Automatic payout for medical emergencies
val medicalPayoutIssuanceResult = trustWeave.issue {
    credential {
        id("urn:travel:payout:medical-2024-10-08")
        type("VerifiableCredential", "TravelInsurancePayoutCredential")
        issuer(insuranceDid.value)
        subject {
            id("urn:travel:payout:medical-2024-10-08")
            "policyId" to "TRAVEL-POL-12345"
            "triggerType" to "MedicalEmergency"
            "dataCredentialId" to medicalCredential.id
            "payoutAmount" to 1500.0
            "currency" to "USD"
            "payoutMethod" to "direct-debit"
            "quickPayout" to true  // Fast-track for medical
        }
    }
    by(issuerDid = insuranceDid.value, keyId = insuranceKeyId)
}

val medicalPayoutCredential = medicalPayoutIssuanceResult.getOrThrow()
```

## Step 5: Embedding in Travel Booking Process

Integrate TrustWeave Pro into travel booking platforms:

```kotlin
// Embedded in airline booking system
suspend fun bookFlightWithInsurance(
    flightDetails: FlightDetails,
    insurancePolicy: InsurancePolicy
): BookingResult {
    // Book flight
    val booking = airline.bookFlight(flightDetails)

    // Create insurance policy credential
    val policyIssuanceResult = trustWeave.issue {
        credential {
            id(booking.id)
            type("VerifiableCredential", "TravelInsurancePolicyCredential")
            issuer(insuranceDid)
            subject {
                id(booking.id)
                "policyId" to insurancePolicy.id
                "coverage" {
                    "flightDelay" to true
                    "weatherGuarantee" to true
                    "baggageDelay" to true
                    "medicalEmergency" to true
                }
                "activeFrom" to booking.departureTime
                "activeUntil" to booking.returnTime
            }
        }
        by(issuerDid = insuranceDid.value, keyId = insuranceKeyId)
    }
    
    val policyCredential = policyIssuanceResult.getOrThrow()

    // Store policy credential with booking
    booking.storeCredential(policyCredential)

    return booking
}
```

## Step 6: Blockchain Anchoring for Audit Trail

Anchor travel credentials to blockchain for immutable audit trail:

```kotlin
// Anchor flight delay credential
val anchorResult = trustWeave.blockchains.anchor(
    data = flightDelayCredential,
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
        dataCredentialId = flightDelayCredential.id,
        anchorRef = anchorResult.ref,
        timestamp = anchorResult.timestamp
    )
}
```

## Key Benefits

1. **Standardization**: One format works for all travel data providers
2. **Multi-Provider**: Accept data from airlines, IATA, weather services, baggage systems without custom integrations
3. **Data Integrity**: Cryptographic proof prevents tampering and fraud
4. **Automation**: Enable automatic insurance payouts
5. **Audit Trail**: Complete data lineage for compliance
6. **Trust**: Build trust through verifiable credentials
7. **Speed**: Quick payouts without manual claims processing

## Real-World Integration

**Chubb Travel Pro Integration:**
- Replace custom API integrations with VC-based data acceptance
- Accept data from any certified provider (airlines, IATA, weather services)
- Reduce integration costs by 80%
- Enable automatic payouts for flight delays, weather disruptions, and baggage issues

**Travel Booking Platform Integration:**
- Embed TrustWeave Pro into booking process
- Issue insurance policy credentials at time of booking
- Enable automatic claims processing
- Provide transparency and trust to travelers

## Next Steps

- Explore [Parametric Insurance with Earth Observation](parametric-insurance-eo-scenario.md) for EO data use cases
- Learn about [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md)
- Review [Error Handling](../api-reference/advanced/error-handling.md) for production patterns

## Related Documentation

- [Parametric Insurance with Earth Observation](parametric-insurance-eo-scenario.md) - EO data insurance
- [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) - Anchoring concepts
- [API Reference](../api-reference/core-api.md) - Complete API documentation


