# Exception Fixing Guide

## Remaining Files to Fix

There are approximately 182 remaining instances of `throw TrustWeaveException(...)` that need to be fixed.

## Common Patterns

### Pattern 1: Generic Error with Exception Cause
**Before:**
```kotlin
throw TrustWeaveException("Failed to do something: ${e.message}", e)
```

**After:**
```kotlin
throw com.trustweave.core.exception.TrustWeaveException.Unknown(
    message = "Failed to do something: ${e.message ?: "Unknown error"}",
    context = mapOf("operation" to "something"),
    cause = e
)
```

### Pattern 2: Simple Error Message
**Before:**
```kotlin
throw TrustWeaveException("Something went wrong")
```

**After:**
```kotlin
throw com.trustweave.core.exception.TrustWeaveException.Unknown(
    message = "Something went wrong",
    context = mapOf()
)
```

### Pattern 3: DID-Related Errors
**Before:**
```kotlin
throw TrustWeaveException("DID not found: $did")
```

**After:**
```kotlin
throw com.trustweave.did.exception.DidException.DidNotFound(
    did = did,
    availableMethods = emptyList()
)
```

### Pattern 4: Validation Errors
**Before:**
```kotlin
throw TrustWeaveException("Invalid format: $value")
```

**After:**
```kotlin
throw com.trustweave.core.exception.TrustWeaveException.ValidationFailed(
    field = "fieldName",
    reason = "Invalid format: $value",
    value = value
)
```

### Pattern 5: KMS Key Not Found
**Before:**
```kotlin
throw TrustWeaveException("Key not found: $keyId")
```

**After:**
```kotlin
throw com.trustweave.kms.exception.KmsException.KeyNotFound(
    keyId = keyId,
    keyType = null
)
```

### Pattern 6: Blockchain Chain Not Registered
**Before:**
```kotlin
throw TrustWeaveException("Chain not found: $chainId")
```

**After:**
```kotlin
throw com.trustweave.anchor.exceptions.BlockchainException.ChainNotRegistered(
    chainId = chainId,
    availableChains = emptyList()
)
```

### Pattern 7: Invalid State/Operation
**Before:**
```kotlin
throw TrustWeaveException("Operation not supported")
```

**After:**
```kotlin
throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
    message = "Operation not supported",
    context = mapOf("operation" to "operationName")
)
```

## Files by Category

### DID Plugins (Most remaining)
- `did/plugins/web/WebDidMethod.kt`
- `did/plugins/ion/IonDidMethod.kt`
- `did/plugins/ethr/EthrDidMethod.kt`
- `did/plugins/ens/EnsDidMethod.kt`
- `did/plugins/cheqd/CheqdDidMethod.kt`
- `did/plugins/btcr/BtcrDidMethod.kt`
- `did/plugins/plc/PlcDidMethod.kt`
- `did/plugins/peer/PeerDidMethod.kt`
- `did/plugins/orb/OrbDidMethod.kt`
- `did/plugins/jwk/JwkDidMethod.kt`
- `did/plugins/threebox/ThreeBoxDidMethod.kt`
- `did/plugins/tezos/TezosDidMethod.kt`
- `did/plugins/sol/SolDidMethod.kt`
- `did/plugins/sol/SolanaClient.kt`
- `did/plugins/polygon/PolygonDidMethod.kt`
- `did/plugins/base/AbstractBlockchainDidMethod.kt`

### KMS Plugins
- `kms/plugins/aws/AwsKeyManagementService.kt`
- `kms/plugins/azure/AzureKeyManagementService.kt`
- `kms/plugins/cloudhsm/CloudHsmKeyManagementService.kt`
- `kms/plugins/cyberark/CyberArkKeyManagementService.kt`
- `kms/plugins/entrust/EntrustKeyManagementService.kt`
- `kms/plugins/fortanix/FortanixKeyManagementService.kt`
- `kms/plugins/google/GoogleCloudKeyManagementService.kt`
- `kms/plugins/hashicorp/VaultKeyManagementService.kt`
- `kms/plugins/ibm/IbmKeyManagementService.kt`
- `kms/plugins/thales/ThalesKeyManagementService.kt`
- `kms/plugins/thales-luna/ThalesLunaKeyManagementService.kt`
- `kms/plugins/utimaco/UtimacoKeyManagementService.kt`
- `kms/plugins/venafi/VenafiIntegration.kt`

### Anchor Plugins
- `anchors/plugins/bitcoin/BitcoinBlockchainAnchorClient.kt`
- `anchors/plugins/cardano/CardanoBlockchainAnchorClient.kt`
- `anchors/plugins/starknet/StarkNetBlockchainAnchorClient.kt`

### Credential Plugins
- `credentials/plugins/platforms/entra/EntraIntegration.kt`
- `credentials/plugins/platforms/salesforce/SalesforceIntegration.kt`
- `credentials/plugins/platforms/servicenow/ServiceNowIntegration.kt`

## Notes

- Most plugin files follow similar patterns and can be fixed using the patterns above
- Use domain-specific exceptions (DidException, KmsException, BlockchainException, CredentialException) when appropriate
- Use TrustWeaveException.Unknown for generic errors
- Always include context maps with relevant information
- Use `?: "Unknown error"` for nullable message strings

