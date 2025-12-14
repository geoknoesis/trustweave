---
title: "Smart Contract: Parametric Insurance Scenario"
parent: Use Case Scenarios
nav_order: 35
---

# Smart Contract: Parametric Insurance Scenario

> **Building Parametric Insurance with TrustWeave Smart Contracts**

This guide demonstrates how to build a parametric insurance system using TrustWeave's Smart Contract abstraction. You'll learn how to create contracts that automatically execute based on Earth Observation (EO) data triggers, with verifiable credentials and blockchain anchoring for trust and auditability.

## What You'll Build

By the end of this tutorial, you'll have:

- ✅ Created a parametric insurance contract using SmartContract
- ✅ Bound the contract with verifiable credentials
- ✅ Anchored the contract to blockchain
- ✅ Executed the contract based on EO data triggers
- ✅ Generated automatic payouts when conditions are met

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│              Parametric Insurance Contract                  │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ EO Provider │  │  Insurance   │  │  Blockchain  │       │
│  │   (DID)     │  │   (DID)     │  │   Anchor     │       │
│  └──────┬───────┘  └──────┬──────┘  └──────┬───────┘       │
│         │                 │                 │                │
│         │ Issues VC       │ Issues VC       │ Anchors        │
│         │                 │                 │                │
│  ┌──────▼─────────────────▼─────────────────▼──────┐       │
│  │         TrustWeave Smart Contract Service          │       │
│  │  ┌──────────────────────────────────────────┐   │       │
│  │  │  Contract Lifecycle Management            │   │       │
│  │  │  - Create Draft                           │   │       │
│  │  │  - Bind (Issue VC + Anchor)               │   │       │
│  │  │  - Activate                               │   │       │
│  │  │  - Execute (Evaluate Conditions)          │   │       │
│  │  └──────────────────────────────────────────┘   │       │
│  │  ┌──────────────────────────────────────────┐   │       │
│  │  │  Condition Evaluation                     │   │       │
│  │  │  - Parametric Triggers                    │   │       │
│  │  │  - Threshold Checks                       │   │       │
│  │  │  - Payout Calculation                    │   │       │
│  │  └──────────────────────────────────────────┘   │       │
│  └──────────────────────────────────────────────────┘       │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

## Step 1: Setup TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.contract.models.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

// Initialize TrustWeave with blockchain anchoring
val TrustWeave = TrustWeave.create {
    blockchains {
        "algorand:mainnet" to algorandClient
    }
}

// Create DIDs for parties
val insurerDid = trustWeave.createDid { method("key") }
val insuredDid = trustWeave.createDid { method("key") }
val eoProviderDid = trustWeave.createDid { method("key") }
```

## Step 2: Create Contract Draft

```kotlin
suspend fun createFloodInsuranceContract(
    TrustWeave: TrustWeave,
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
                penalties = null,
                rewards = null,
                jurisdiction = "US",
                governingLaw = "State of North Carolina"
            ),
            effectiveDate = Instant.now().toString(),
            expirationDate = Instant.now().plusSeconds(365 * 24 * 60 * 60).toString(),
            contractData = buildJsonObject {
                put("productType", "SarFlood")
                put("coverageAmount", coverageAmount)
                put("location", buildJsonObject {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("address", location.address)
                    put("region", location.region)
                })
                put("thresholds", buildJsonObject {
                    put("tier1Cm", 20.0)
                    put("tier2Cm", 50.0)
                    put("tier3Cm", 100.0)
                })
                put("payoutTiers", buildJsonObject {
                    put("tier1", 0.25)  // 25% of coverage
                    put("tier2", 0.50)  // 50% of coverage
                    put("tier3", 1.0)   // 100% of coverage
                })
            }
        )
    ).getOrThrow()

    println("✅ Contract draft created: ${contract.id}")
    println("   Contract Number: ${contract.contractNumber}")
    println("   Status: ${contract.status}")

    return contract
}

data class Location(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val region: String
)
```

## Step 3: Bind Contract

Binding issues a verifiable credential and anchors to blockchain:

```kotlin
suspend fun bindInsuranceContract(
    TrustWeave: TrustWeave,
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

    println("✅ Contract bound:")
    println("   Credential ID: ${bound.credentialId}")
    println("   Anchor TX: ${bound.anchorRef.txHash}")
    println("   Chain: ${bound.anchorRef.chainId}")
    println("   Status: ${bound.contract.status}")

    return bound
}
```

## Step 4: Activate Contract

```kotlin
suspend fun activateContract(
    TrustWeave: TrustWeave,
    contractId: String
): SmartContract {

    val active = trustWeave.contracts.activateContract(contractId).getOrThrow()

    println("✅ Contract activated: ${active.id}")
    println("   Status: ${active.status}")
    println("   Effective: ${active.effectiveDate}")
    println("   Expires: ${active.expirationDate}")

    return active
}
```

## Step 5: Process EO Data and Execute Contract

When EO data arrives (e.g., SAR flood data), process it and execute the contract:

```kotlin
suspend fun processFloodDataAndExecute(
    TrustWeave: TrustWeave,
    contract: SmartContract,
    floodDepthCm: Double,
    eoDataCredential: VerifiableCredential
): ExecutionResult {

    // Create execution context with trigger data
    val executionContext = ExecutionContext(
        triggerData = buildJsonObject {
            put("floodDepthCm", floodDepthCm)
            put("credentialId", eoDataCredential.id)
            put("timestamp", Instant.now().toString())
        }
    )

    // Execute contract
    val result = trustWeave.contracts.executeContract(
        contract = contract,
        executionContext = executionContext
    ).getOrThrow()

    if (result.executed) {
        println("✅ Contract executed!")
        println("   Execution Type: ${result.executionType}")
        println("   Outcomes: ${result.outcomes.size}")

        result.outcomes.forEach { outcome ->
            println("   - ${outcome.description}")
            outcome.monetaryImpact?.let {
                println("     Amount: ${it.amount} ${it.currency}")
            }
        }
    } else {
        println("⚠️  Contract conditions not met")
        result.outcomes.forEach { outcome ->
            println("   - ${outcome.description}")
        }
    }

    return result
}
```

## Step 6: Complete Workflow

```kotlin
suspend fun completeParametricInsuranceWorkflow() {
    val TrustWeave = TrustWeave.create {
        blockchains {
            "algorand:mainnet" to algorandClient
        }
    }

    // Step 1: Create DIDs
    val insurerDidResult = trustWeave.createDid { method("key") }
    val insurerDid = when (insurerDidResult) {
        is DidCreationResult.Success -> insurerDidResult.did
        else -> throw IllegalStateException("Failed to create insurer DID: ${insurerDidResult.reason}")
    }
    
    val insuredDidResult = trustWeave.createDid { method("key") }
    val insuredDid = when (insuredDidResult) {
        is DidCreationResult.Success -> insuredDidResult.did
        else -> throw IllegalStateException("Failed to create insured DID: ${insuredDidResult.reason}")
    }
    
    val eoProviderDidResult = trustWeave.createDid { method("key") }
    val eoProviderDid = when (eoProviderDidResult) {
        is DidCreationResult.Success -> eoProviderDidResult.did
        else -> throw IllegalStateException("Failed to create EO provider DID: ${eoProviderDidResult.reason}")
    }
    
    val insurerResolution = trustWeave.resolveDid(insurerDid)
    val insurerDoc = when (insurerResolution) {
        is DidResolutionResult.Success -> insurerResolution.document
        else -> throw IllegalStateException("Failed to resolve insurer DID")
    }
    val insurerKeyId = insurerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No key found")
    
    val eoProviderResolution = trustWeave.resolveDid(eoProviderDid)
    val eoProviderDoc = when (eoProviderResolution) {
        is DidResolutionResult.Success -> eoProviderResolution.document
        else -> throw IllegalStateException("Failed to resolve EO provider DID")
    }
    val eoProviderKeyId = eoProviderDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No key found")

    // Step 2: Create contract draft
    val contract = createFloodInsuranceContract(
        TrustWeave = trustWeave,
        insurerDid = insurerDid.value,
        insuredDid = insuredDid.value,
        coverageAmount = 1_000_000.0,
        location = Location(
            latitude = 35.2271,
            longitude = -80.8431,
            address = "Charlotte, NC",
            region = "North Carolina"
        )
    )

    // Step 3: Bind contract
    val bound = bindInsuranceContract(
        TrustWeave = trustWeave,
        contract = contract,
        insurerDid = insurerDid.value,
        insurerKeyId = insurerKeyId
    )

    // Step 4: Activate contract
    val active = activateContract(trustWeave, bound.contract.id)

    // Step 5: Simulate flood event
    // In production, this would come from EO data provider
    val floodDepth = 75.0 // cm

    // Issue EO data credential (simplified - in production, EO provider issues this)
    import com.trustweave.trust.types.IssuanceResult
    
    val eoDataIssuanceResult = trustWeave.issue {
        credential {
            type("EarthObservationCredential")
            issuer(eoProviderDid.value)
            subject {
                "floodDepthCm" to floodDepth
                "timestamp" to Instant.now().toString()
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = eoProviderDid.value, keyId = eoProviderKeyId)
    }
    
    val eoDataCredential = when (eoDataIssuanceResult) {
        is IssuanceResult.Success -> eoDataIssuanceResult.credential
        else -> throw IllegalStateException("Failed to issue EO data credential: ${eoDataIssuanceResult.reason}")
    }

    // Step 6: Execute contract
    val executionResult = processFloodDataAndExecute(
        TrustWeave = TrustWeave,
        contract = active,
        floodDepthCm = floodDepth,
        eoDataCredential = eoDataCredential
    )

    // Step 7: Process payout (application-specific)
    if (executionResult.executed) {
        processPayout(executionResult)
    }
}
```

## Advanced: Custom Condition Evaluation

For production use, implement custom condition evaluators:

```kotlin
class FloodConditionEvaluator : ConditionEvaluator {
    override suspend fun evaluate(
        condition: ContractCondition,
        inputData: JsonElement,
        contract: SmartContract
    ): Boolean {
        when (condition.conditionType) {
            ConditionType.THRESHOLD -> {
                val threshold = extractThreshold(condition.expression)
                val value = extractValue(inputData, "floodDepthCm")
                return value >= threshold
            }
            else -> throw UnsupportedOperationException(
                "Condition type ${condition.conditionType} not supported"
            )
        }
    }

    private fun extractThreshold(expression: String): Double {
        // Parse expression like "$.floodDepthCm >= 50"
        val match = Regex(">= (\\d+\\.?\\d*)").find(expression)
        return match?.groupValues?.get(1)?.toDouble() ?: 0.0
    }

    private fun extractValue(data: JsonElement, key: String): Double {
        return data.jsonObject[key]?.jsonPrimitive?.content?.toDouble() ?: 0.0
    }
}
```

## Benefits of Using Smart Contracts

1. **Standardization**: W3C-compliant format works across all contract types
2. **Trust**: Cryptographic proof of contract terms prevents disputes
3. **Automation**: Parametric execution enables instant payouts
4. **Auditability**: Blockchain anchoring provides immutable audit trails
5. **Multi-Party**: DIDs enable complex multi-party contracts
6. **Extensibility**: Pluggable execution models for different use cases

## Next Steps

- Review [Smart Contracts Core Concepts](../core-concepts/smart-contracts.md) for detailed API documentation
- Explore [Parametric Insurance MGA Guide](parametric-insurance-mga-implementation-guide.md) for complete MGA implementation
- See [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) for anchoring details
- Check [API Reference](../api-reference/core-api.md) for complete API documentation

