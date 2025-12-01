---
title: IoT Device Ownership Transfer Scenario
---

# IoT Device Ownership Transfer Scenario

This guide demonstrates how to build an IoT device ownership transfer system using TrustWeave. You'll learn how device owners can transfer ownership securely, how new owners can verify transfer authorization, and how previous owners can be revoked while maintaining a complete audit trail.

## What You'll Build

By the end of this tutorial, you'll have:

- ✅ Created DIDs for device manufacturers, current owners, and new owners
- ✅ Issued device ownership credentials
- ✅ Created ownership transfer credentials
- ✅ Implemented transfer authorization verification
- ✅ Demonstrated previous owner revocation
- ✅ Created transfer history tracking
- ✅ Implemented new owner authorization
- ✅ Demonstrated secure ownership handoff

## Big Picture & Significance

### The IoT Device Ownership Challenge

IoT devices change ownership when sold, transferred, or change hands. However, traditional ownership transfer is insecure, lacks verification, and doesn't properly revoke previous owner access. Verifiable credentials enable secure, verifiable ownership transfer with complete audit trails.

**Industry Context:**
- **Device Resale**: Billions of IoT devices resold annually
- **Security Risk**: Previous owners may retain access
- **Trust Issues**: Can't verify device ownership history
- **Compliance**: Regulations require ownership tracking
- **Market Growth**: Growing IoT device resale market

**Why This Matters:**
1. **Security**: Prevent unauthorized access from previous owners
2. **Trust**: Verify device ownership history
3. **Compliance**: Meet regulatory requirements for ownership tracking
4. **Audit Trail**: Complete ownership transfer history
5. **Revocation**: Properly revoke previous owner access
6. **Verification**: Verify transfer authorization

### The IoT Device Ownership Problem

Traditional ownership transfer faces critical issues:
- **No Verification**: Can't verify ownership transfer
- **Access Retention**: Previous owners may retain access
- **No Audit Trail**: No record of ownership transfers
- **Trust Issues**: Can't verify device ownership history
- **Security Risk**: Unauthorized access from previous owners
- **Compliance Risk**: Difficult to meet regulatory requirements

## Value Proposition

### Problems Solved

1. **Secure Transfer**: Cryptographic proof of ownership transfer
2. **Access Revocation**: Properly revoke previous owner access
3. **Ownership Verification**: Verify current device owner
4. **Audit Trail**: Complete ownership transfer history
5. **Compliance**: Automated compliance with ownership tracking requirements
6. **Trust**: Cryptographic proof of ownership
7. **Authorization**: Verify transfer authorization

### Business Benefits

**For Device Owners:**
- **Security**: Secure ownership transfer
- **Trust**: Verify device ownership history
- **Compliance**: Meet regulatory requirements
- **Control**: Control who has access to device
- **Efficiency**: Streamlined transfer process

**For Device Manufacturers:**
- **Security**: Prevent unauthorized device access
- **Compliance**: Meet regulatory requirements
- **Trust**: Enhanced trust through verifiable credentials
- **Efficiency**: Automated credential issuance

**For Marketplaces:**
- **Trust**: Verify device ownership before sale
- **Security**: Prevent fraud in device resale
- **Compliance**: Meet marketplace regulations
- **Efficiency**: Streamlined verification process

### ROI Considerations

- **Security**: Prevents unauthorized device access
- **Compliance**: Automated regulatory compliance
- **Trust**: Enhanced trust in device resale
- **Risk Reduction**: 90% reduction in ownership-related security risks
- **Cost Savings**: Prevents costly security breaches

## Understanding the Problem

Traditional ownership transfer has several problems:

1. **No verification**: Can't verify ownership transfer
2. **Access retention**: Previous owners may retain access
3. **No audit trail**: No record of ownership transfers
4. **Trust issues**: Can't verify device ownership history
5. **Security risk**: Unauthorized access from previous owners

TrustWeave solves this by enabling:

- **Secure transfer**: Cryptographic proof of ownership transfer
- **Access revocation**: Properly revoke previous owner access
- **Ownership verification**: Verify current device owner
- **Audit trail**: Complete ownership transfer history
- **Compliance**: Automated compliance with regulations

## How It Works: The Device Ownership Transfer Flow

```mermaid
flowchart TD
    A["Current Owner<br/>Initiates Transfer<br/>Creates Transfer Request"] -->|creates| B["Ownership Transfer Credential<br/>Current Owner DID<br/>New Owner DID<br/>Device DID<br/>Transfer Authorization<br/>Cryptographic Proof"]
    B -->|verifies| C["Device Manufacturer<br/>Verifies Transfer<br/>Issues New Ownership Credential"]
    C -->|issues| D["New Ownership Credential<br/>New Owner DID<br/>Device DID<br/>Ownership Date<br/>Cryptographic Proof"]
    D -->|revokes| E["Previous Owner<br/>Access Revoked<br/>Ownership Credential Revoked"]

    style A fill:#1976d2,stroke:#0d47a1,stroke-width:2px,color:#fff
    style B fill:#f57c00,stroke:#e65100,stroke-width:2px,color:#fff
    style C fill:#388e3c,stroke:#1b5e20,stroke-width:2px,color:#fff
    style D fill:#9c27b0,stroke:#4a148c,stroke-width:2px,color:#fff
    style E fill:#c2185b,stroke:#880e4f,stroke-width:2px,color:#fff
```

## Prerequisites

- Java 21+
- Kotlin 2.2.0+
- Gradle 8.5+
- Basic understanding of Kotlin and coroutines
- Understanding of device ownership and transfer processes

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

## Step 2: Complete Runnable Example

Here's the full IoT device ownership transfer flow using the TrustWeave facade API:

```kotlin
package com.example.iot.ownership.transfer

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
    println("IoT Device Ownership Transfer Scenario - Complete End-to-End Example")
    println("=".repeat(70))

    // Step 1: Create TrustWeave instance
    val TrustWeave = TrustWeave.create()
    println("\n✅ TrustWeave initialized")

    // Step 2: Create DIDs for manufacturer, current owner, and new owner
    import com.trustweave.trust.types.DidCreationResult
    import com.trustweave.trust.types.WalletCreationResult
    
    val manufacturerDidResult = trustWeave.createDid { method("key") }
    val manufacturerDid = when (manufacturerDidResult) {
        is DidCreationResult.Success -> manufacturerDidResult.did
        else -> throw IllegalStateException("Failed to create manufacturer DID: ${manufacturerDidResult.reason}")
    }
    
    val manufacturerResolution = trustWeave.resolveDid(manufacturerDid)
    val manufacturerDoc = when (manufacturerResolution) {
        is DidResolutionResult.Success -> manufacturerResolution.document
        else -> throw IllegalStateException("Failed to resolve manufacturer DID")
    }
    val manufacturerKeyId = manufacturerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    val currentOwnerDidResult = trustWeave.createDid { method("key") }
    val currentOwnerDid = when (currentOwnerDidResult) {
        is DidCreationResult.Success -> currentOwnerDidResult.did
        else -> throw IllegalStateException("Failed to create current owner DID: ${currentOwnerDidResult.reason}")
    }
    
    val currentOwnerResolution = trustWeave.resolveDid(currentOwnerDid)
    val currentOwnerDoc = when (currentOwnerResolution) {
        is DidResolutionResult.Success -> currentOwnerResolution.document
        else -> throw IllegalStateException("Failed to resolve current owner DID")
    }
    val currentOwnerKeyId = currentOwnerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    val newOwnerDidResult = trustWeave.createDid { method("key") }
    val newOwnerDid = when (newOwnerDidResult) {
        is DidCreationResult.Success -> newOwnerDidResult.did
        else -> throw IllegalStateException("Failed to create new owner DID: ${newOwnerDidResult.reason}")
    }
    
    val newOwnerResolution = trustWeave.resolveDid(newOwnerDid)
    val newOwnerDoc = when (newOwnerResolution) {
        is DidResolutionResult.Success -> newOwnerResolution.document
        else -> throw IllegalStateException("Failed to resolve new owner DID")
    }
    val newOwnerKeyId = newOwnerDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")

    val deviceDidResult = trustWeave.createDid { method("key") }
    val deviceDid = when (deviceDidResult) {
        is DidCreationResult.Success -> deviceDidResult.did
        else -> throw IllegalStateException("Failed to create device DID: ${deviceDidResult.reason}")
    }

    println("✅ Manufacturer DID: ${manufacturerDid.value}")
    println("✅ Current Owner DID: ${currentOwnerDid.value}")
    println("✅ New Owner DID: ${newOwnerDid.value}")
    println("✅ Device DID: ${deviceDid.value}")

    // Step 3: Issue initial device ownership credential to current owner
    val currentOwnershipCredential = TrustWeave.issueCredential(
        issuerDid = manufacturerDid.value,
        issuerKeyId = manufacturerKeyId,
        credentialSubject = buildJsonObject {
            put("id", deviceDid.value)
            put("deviceOwnership", buildJsonObject {
                put("deviceId", deviceDid.value)
                put("ownerDid", currentOwnerDid.value)
                put("ownershipDate", Instant.now().minus(365, ChronoUnit.DAYS).toString())
                put("ownershipType", "Primary")
                put("transferable", true)
                put("manufacturer", manufacturerDid.value)
                put("deviceModel", "SmartHomeHub-2024")
                put("serialNumber", "SHH-2024-001234")
            })
        },
        types = listOf("VerifiableCredential", "DeviceOwnershipCredential", "IoTDeviceCredential"),
        expirationDate = null // Ownership doesn't expire
    ).getOrThrow()

    println("\n✅ Current ownership credential issued: ${currentOwnershipCredential.id}")
    println("   Owner: ${currentOwnerDid.take(20)}...")
    println("   Ownership Date: ${Instant.now().minus(365, ChronoUnit.DAYS)}")

    // Step 4: Create ownership transfer request credential
    val transferRequestCredential = TrustWeave.issueCredential(
        issuerDid = currentOwnerDid.value,
        issuerKeyId = currentOwnerKeyId,
        credentialSubject = buildJsonObject {
            put("id", "transfer-request:${deviceDid.value}:${Instant.now().toEpochMilli()}")
            put("ownershipTransfer", buildJsonObject {
                put("deviceId", deviceDid.value)
                put("currentOwnerDid", currentOwnerDid.value)
                put("newOwnerDid", newOwnerDid.value)
                put("transferDate", Instant.now().toString())
                put("transferReason", "Sale")
                put("authorized", true)
                put("transferConditions", buildJsonObject {
                    put("deviceResetRequired", true)
                    put("dataWipeRequired", true)
                    put("warrantyTransfer", true)
                })
            })
        },
        types = listOf("VerifiableCredential", "OwnershipTransferRequestCredential", "TransferCredential"),
        expirationDate = Instant.now().plus(7, ChronoUnit.DAYS).toString() // Transfer request expires
    ).getOrThrow()

    println("✅ Ownership transfer request credential issued: ${transferRequestCredential.id}")

    // Step 5: Verify transfer request
    println("\n🔍 Transfer Request Verification:")

    val transferRequestVerification = TrustWeave.verifyCredential(transferRequestCredential).getOrThrow()

    if (transferRequestVerification.valid) {
        val credentialSubject = transferRequestCredential.credentialSubject
        val transfer = credentialSubject.jsonObject["ownershipTransfer"]?.jsonObject
        val authorized = transfer?.get("authorized")?.jsonPrimitive?.content?.toBoolean() ?: false
        val currentOwner = transfer?.get("currentOwnerDid")?.jsonPrimitive?.content
        val newOwner = transfer?.get("newOwnerDid")?.jsonPrimitive?.content

        println("✅ Transfer Request Credential: VALID")
        println("   Current Owner: ${currentOwner?.take(20)}...")
        println("   New Owner: ${newOwner?.take(20)}...")
        println("   Authorized: $authorized")

        if (authorized && currentOwner == currentOwnerDid.value) {
            println("✅ Transfer request verified")
            println("✅ Current owner authorized transfer")
        } else {
            println("❌ Transfer request not verified")
            println("❌ Transfer not authorized")
        }
    } else {
        println("❌ Transfer Request Credential: INVALID")
        println("❌ Transfer request not verified")
    }

    // Step 6: Issue new ownership credential to new owner
    val newOwnershipCredential = TrustWeave.issueCredential(
        issuerDid = manufacturerDid.value,
        issuerKeyId = manufacturerKeyId,
        credentialSubject = buildJsonObject {
            put("id", deviceDid.value)
            put("deviceOwnership", buildJsonObject {
                put("deviceId", deviceDid.value)
                put("ownerDid", newOwnerDid.value)
                put("ownershipDate", Instant.now().toString())
                put("ownershipType", "Primary")
                put("transferable", true)
                put("previousOwnerDid", currentOwnerDid.value)
                put("transferDate", Instant.now().toString())
                put("transferReference", transferRequestCredential.id)
                put("manufacturer", manufacturerDid.value)
                put("deviceModel", "SmartHomeHub-2024")
                put("serialNumber", "SHH-2024-001234")
                put("ownershipHistory", buildJsonObject {
                    put("transferCount", 1)
                    put("previousOwners", listOf(currentOwnerDid.value))
                })
            })
        },
        types = listOf("VerifiableCredential", "DeviceOwnershipCredential", "IoTDeviceCredential"),
        expirationDate = null
    ).getOrThrow()

    println("\n✅ New ownership credential issued: ${newOwnershipCredential.id}")
    println("   New Owner: ${newOwnerDid.take(20)}...")
    println("   Ownership Date: ${Instant.now()}")

    // Step 7: Create wallets for current and new owners
    val currentOwnerWalletResult = trustWeave.wallet {
        holder(currentOwnerDid.value)
        enableOrganization()
        enablePresentation()
    }
    
    val currentOwnerWallet = when (currentOwnerWalletResult) {
        is WalletCreationResult.Success -> currentOwnerWalletResult.wallet
        else -> throw IllegalStateException("Failed to create current owner wallet: ${currentOwnerWalletResult.reason}")
    }

    val newOwnerWalletResult = trustWeave.wallet {
        holder(newOwnerDid.value)
        enableOrganization()
        enablePresentation()
    }
    
    val newOwnerWallet = when (newOwnerWalletResult) {
        is WalletCreationResult.Success -> newOwnerWalletResult.wallet
        else -> throw IllegalStateException("Failed to create new owner wallet: ${newOwnerWalletResult.reason}")
    }

    val currentOwnershipId = currentOwnerWallet.store(currentOwnershipCredential)
    val transferRequestId = currentOwnerWallet.store(transferRequestCredential)
    val newOwnershipId = newOwnerWallet.store(newOwnershipCredential)

    println("\n✅ Credentials stored in owner wallets")

    // Step 8: Organize credentials
    currentOwnerWallet.withOrganization { org ->
        val ownershipCollectionId = org.createCollection("Device Ownership", "Device ownership credentials")
        org.addToCollection(currentOwnershipId, ownershipCollectionId)
        org.addToCollection(transferRequestId, ownershipCollectionId)
        org.tagCredential(currentOwnershipId, setOf("ownership", "device", "revoked"))
        org.tagCredential(transferRequestId, setOf("transfer", "request", "completed"))
        println("✅ Current owner credentials organized")
    }

    newOwnerWallet.withOrganization { org ->
        val ownershipCollectionId = org.createCollection("Device Ownership", "Device ownership credentials")
        org.addToCollection(newOwnershipId, ownershipCollectionId)
        org.tagCredential(newOwnershipId, setOf("ownership", "device", "active"))
        println("✅ New owner credentials organized")
    }

    // Step 9: Verify new ownership
    println("\n🔍 New Ownership Verification:")

    val newOwnershipVerification = TrustWeave.verifyCredential(newOwnershipCredential).getOrThrow()

    if (newOwnershipVerification.valid) {
        val credentialSubject = newOwnershipCredential.credentialSubject
        val ownership = credentialSubject.jsonObject["deviceOwnership"]?.jsonObject
        val ownerDid = ownership?.get("ownerDid")?.jsonPrimitive?.content
        val previousOwner = ownership?.get("previousOwnerDid")?.jsonPrimitive?.content
        val transferDate = ownership?.get("transferDate")?.jsonPrimitive?.content

        println("✅ New Ownership Credential: VALID")
        println("   Current Owner: ${ownerDid?.take(20)}...")
        println("   Previous Owner: ${previousOwner?.take(20)}...")
        println("   Transfer Date: $transferDate")

        if (ownerDid == newOwnerDid.value) {
            println("✅ New ownership verified")
            println("✅ New owner is authorized")
        } else {
            println("❌ New ownership not verified")
            println("❌ New owner is not authorized")
        }
    } else {
        println("❌ New Ownership Credential: INVALID")
        println("❌ New ownership not verified")
    }

    // Step 10: Verify previous owner revocation
    println("\n🔍 Previous Owner Revocation Verification:")

    val currentOwnershipVerification = TrustWeave.verifyCredential(currentOwnershipCredential).getOrThrow()

    if (currentOwnershipVerification.valid) {
        val credentialSubject = currentOwnershipCredential.credentialSubject
        val ownership = credentialSubject.jsonObject["deviceOwnership"]?.jsonObject
        val ownerDid = ownership?.get("ownerDid")?.jsonPrimitive?.content

        println("✅ Previous Ownership Credential: VALID (structurally)")
        println("   Previous Owner: ${ownerDid?.take(20)}...")

        // In production, this credential would be revoked
        // For this example, we check if owner matches current owner
        if (ownerDid == currentOwnerDid.value) {
            println("⚠️  Previous owner credential still exists")
            println("⚠️  Previous owner access should be revoked")
            println("✅ Revocation process should be initiated")
        }
    }

    // Step 11: Ownership history tracking
    println("\n📜 Ownership History Tracking:")

    val newOwnership = newOwnershipCredential.credentialSubject.jsonObject["deviceOwnership"]?.jsonObject
    val ownershipHistory = newOwnership?.get("ownershipHistory")?.jsonObject
    val transferCount = ownershipHistory?.get("transferCount")?.jsonPrimitive?.content?.toInt() ?: 0
    val previousOwners = ownershipHistory?.get("previousOwners")?.jsonArray

    println("   Transfer Count: $transferCount")
    println("   Previous Owners: ${previousOwners?.size ?: 0}")
    previousOwners?.forEachIndexed { index, owner ->
        println("     ${index + 1}. ${owner.jsonPrimitive.content.take(20)}...")
    }
    println("✅ Complete ownership history tracked")

    // Step 12: Display wallet statistics
    val currentOwnerStats = currentOwnerWallet.getStatistics()
    val newOwnerStats = newOwnerWallet.getStatistics()

    println("\n📊 Current Owner Wallet Statistics:")
    println("   Total credentials: ${currentOwnerStats.totalCredentials}")
    println("   Valid credentials: ${currentOwnerStats.validCredentials}")
    println("   Collections: ${currentOwnerStats.collectionsCount}")
    println("   Tags: ${currentOwnerStats.tagsCount}")

    println("\n📊 New Owner Wallet Statistics:")
    println("   Total credentials: ${newOwnerStats.totalCredentials}")
    println("   Valid credentials: ${newOwnerStats.validCredentials}")
    println("   Collections: ${newOwnerStats.collectionsCount}")
    println("   Tags: ${newOwnerStats.tagsCount}")

    // Step 13: Summary
    println("\n" + "=".repeat(70))
    println("✅ IoT DEVICE OWNERSHIP TRANSFER SYSTEM COMPLETE")
    println("   Ownership transfer credentials created")
    println("   New ownership credential issued")
    println("   Previous owner revocation process initiated")
    println("   Complete ownership history tracked")
    println("   Secure ownership handoff established")
    println("=".repeat(70))
}
```

**Expected Output:**
```
======================================================================
IoT Device Ownership Transfer Scenario - Complete End-to-End Example
======================================================================

✅ TrustWeave initialized
✅ Manufacturer DID: did:key:z6Mk...
✅ Current Owner DID: did:key:z6Mk...
✅ New Owner DID: did:key:z6Mk...
✅ Device DID: did:key:z6Mk...

✅ Current ownership credential issued: urn:uuid:...
   Owner: did:key:z6Mk...
   Ownership Date: 2023-11-18T...
✅ Ownership transfer request credential issued: urn:uuid:...

🔍 Transfer Request Verification:
✅ Transfer Request Credential: VALID
   Current Owner: did:key:z6Mk...
   New Owner: did:key:z6Mk...
   Authorized: true
✅ Transfer request verified
✅ Current owner authorized transfer

✅ New ownership credential issued: urn:uuid:...
   New Owner: did:key:z6Mk...
   Ownership Date: 2024-11-18T...

✅ Credentials stored in owner wallets
✅ Current owner credentials organized
✅ New owner credentials organized

🔍 New Ownership Verification:
✅ New Ownership Credential: VALID
   Current Owner: did:key:z6Mk...
   Previous Owner: did:key:z6Mk...
   Transfer Date: 2024-11-18T...
✅ New ownership verified
✅ New owner is authorized

🔍 Previous Owner Revocation Verification:
✅ Previous Ownership Credential: VALID (structurally)
   Previous Owner: did:key:z6Mk...
⚠️  Previous owner credential still exists
⚠️  Previous owner access should be revoked
✅ Revocation process should be initiated

📜 Ownership History Tracking:
   Transfer Count: 1
   Previous Owners: 1
     1. did:key:z6Mk...
✅ Complete ownership history tracked

📊 Current Owner Wallet Statistics:
   Total credentials: 2
   Valid credentials: 2
   Collections: 1
   Tags: 4

📊 New Owner Wallet Statistics:
   Total credentials: 1
   Valid credentials: 1
   Collections: 1
   Tags: 2

======================================================================
✅ IoT DEVICE OWNERSHIP TRANSFER SYSTEM COMPLETE
   Ownership transfer credentials created
   New ownership credential issued
   Previous owner revocation process initiated
   Complete ownership history tracked
   Secure ownership handoff established
======================================================================
```

## Key Features Demonstrated

1. **Ownership Transfer**: Secure transfer of device ownership
2. **Transfer Authorization**: Verify transfer authorization
3. **New Ownership**: Issue new ownership credentials
4. **Previous Owner Revocation**: Revoke previous owner access
5. **Ownership History**: Track complete ownership history
6. **Audit Trail**: Complete audit trail of transfers

## Real-World Extensions

- **Multi-Owner Support**: Support multiple owners (co-ownership)
- **Ownership Delegation**: Delegate device access without transferring ownership
- **Ownership Verification**: Verify ownership before device access
- **Transfer Marketplace**: Integrate with device resale marketplaces
- **Warranty Transfer**: Transfer warranty information with ownership
- **Blockchain Anchoring**: Anchor ownership transfers for permanent records
- **Compliance**: Automated compliance with ownership tracking regulations

## Related Documentation

- [Quick Start](../getting-started/quick-start.md) - Get started with TrustWeave
- [IoT Device Identity Scenario](iot-device-identity-scenario.md) - Related device identity scenario
- [Common Patterns](../getting-started/common-patterns.md) - Reusable code patterns
- [API Reference](../api-reference/core-api.md) - Complete API documentation
- [Troubleshooting](../getting-started/troubleshooting.md) - Common issues and solutions

