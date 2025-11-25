---
title: Common Workflows
nav_order: 9
parent: Getting Started
---

# Common Workflows

This guide documents common workflows for using TrustWeave in real-world applications.

## Credential Issuance Workflow

Complete workflow for issuing a verifiable credential:

```kotlin
import com.trustweave.trust.TrustLayer
import com.trustweave.core.TrustWeaveError
import kotlinx.coroutines.runBlocking

suspend fun issueCredentialWorkflow(
    trustLayer: TrustLayer,
    issuerDid: String,
    holderDid: String,
    claims: Map<String, Any>
): Result<VerifiableCredential> {
    return try {
        // 1. Verify issuer DID is resolvable
        val context = trustLayer.getDslContext()
        val resolver = context.getDidResolver()
        val issuerResolution = resolver?.resolve(issuerDid)
        
        if (issuerResolution?.document == null) {
            return Result.failure(
                TrustWeaveError.DidNotFound(
                    did = issuerDid,
                    availableMethods = emptyList()
                )
            )
        }
        
        // 2. Get issuer key ID
        val issuerKeyId = "$issuerDid#key-1"
        
        // 3. Issue credential
        val credential = trustLayer.issue {
            credential {
                type("VerifiableCredential", "PersonCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    claims.forEach { (key, value) ->
                        claim(key, value)
                    }
                }
            }
            by(issuerDid = issuerDid, keyId = issuerKeyId)
        }
        
        Result.success(credential)
    } catch (error: TrustWeaveError) {
        Result.failure(error)
    }
}
```

## Credential Verification Workflow

Complete workflow for verifying a verifiable credential:

```kotlin
suspend fun verifyCredentialWorkflow(
    trustLayer: TrustLayer,
    credential: VerifiableCredential,
    checkTrust: Boolean = false
): CredentialVerificationResult {
    return try {
        val verification = trustLayer.verify {
            credential(credential)
            checkExpiration(true)
            checkRevocation(true)
            checkTrust(checkTrust)
        }
        
        // Log verification result
        if (verification.valid) {
            logger.info("Credential verified successfully", mapOf(
                "credentialId" to credential.id,
                "issuer" to credential.issuer
            ))
        } else {
            logger.warn("Credential verification failed", mapOf(
                "credentialId" to credential.id,
                "errors" to verification.errors
            ))
        }
        
        verification
    } catch (error: TrustWeaveError) {
        logger.error("Verification error", error)
        CredentialVerificationResult(
            valid = false,
            proofValid = false,
            issuerValid = false,
            notExpired = false,
            notRevoked = false,
            errors = listOf(error.message ?: "Unknown error"),
            warnings = emptyList()
        )
    }
}
```

## Credential Revocation Workflow

Complete workflow for revoking a credential:

```kotlin
import com.trustweave.credential.revocation.*

suspend fun revokeCredentialWorkflow(
    statusListManager: StatusListManager,
    credentialId: String,
    statusListId: String,
    reason: String? = null
): Result<Boolean> {
    return try {
        // 1. Check if credential is already revoked
        val status = statusListManager.checkStatus(credentialId, statusListId)
        if (status.revoked) {
            return Result.success(false) // Already revoked
        }
        
        // 2. Revoke credential
        val revoked = statusListManager.revoke(
            credentialId = credentialId,
            statusListId = statusListId,
            reason = reason
        )
        
        if (revoked) {
            logger.info("Credential revoked", mapOf(
                "credentialId" to credentialId,
                "statusListId" to statusListId,
                "reason" to reason
            ))
        }
        
        Result.success(revoked)
    } catch (error: Exception) {
        logger.error("Revocation failed", error)
        Result.failure(error)
    }
}
```

## Key Rotation Workflow

Complete workflow for rotating keys in a DID document:

```kotlin
suspend fun rotateKeyWorkflow(
    trustLayer: TrustLayer,
    did: String,
    oldKeyId: String,
    newKeyId: String
): Result<DidDocument> {
    return try {
        // 1. Verify current DID document
        val context = trustLayer.getDslContext()
        val resolver = context.getDidResolver()
        val currentResolution = resolver?.resolve(did)
        
        if (currentResolution?.document == null) {
            return Result.failure(
                TrustWeaveError.DidNotFound(
                    did = did,
                    availableMethods = emptyList()
                )
            )
        }
        
        // 2. Verify old key exists
        val oldKey = currentResolution.document.verificationMethod
            .find { it.id == oldKeyId }
        
        if (oldKey == null) {
            return Result.failure(
                TrustWeaveError.ValidationFailed(
                    field = "oldKeyId",
                    reason = "Old key not found in DID document",
                    value = oldKeyId
                )
            )
        }
        
        // 3. Rotate key
        val updated = trustLayer.rotateKey {
            did(did)
            oldKeyId(oldKeyId)
            newKeyId(newKeyId)
        }
        
        logger.info("Key rotated successfully", mapOf(
            "did" to did,
            "oldKeyId" to oldKeyId,
            "newKeyId" to newKeyId
        ))
        
        Result.success(updated as DidDocument)
    } catch (error: TrustWeaveError) {
        logger.error("Key rotation failed", error)
        Result.failure(error)
    }
}
```

## Trust Anchor Management Workflow

Complete workflow for managing trust anchors:

```kotlin
suspend fun manageTrustAnchorWorkflow(
    trustLayer: TrustLayer,
    anchorDid: String,
    credentialTypes: List<String>,
    description: String
): Result<Boolean> {
    return try {
        // 1. Verify anchor DID is resolvable
        val context = trustLayer.getDslContext()
        val resolver = context.getDidResolver()
        val resolution = resolver?.resolve(anchorDid)
        
        if (resolution?.document == null) {
            return Result.failure(
                TrustWeaveError.DidNotFound(
                    did = anchorDid,
                    availableMethods = emptyList()
                )
            )
        }
        
        // 2. Add trust anchor
        val added = trustLayer.addTrustAnchor(anchorDid) {
            credentialTypes(*credentialTypes.toTypedArray())
            description(description)
        }
        
        if (added) {
            logger.info("Trust anchor added", mapOf(
                "anchorDid" to anchorDid,
                "credentialTypes" to credentialTypes
            ))
        } else {
            logger.info("Trust anchor already exists", mapOf(
                "anchorDid" to anchorDid
            ))
        }
        
        Result.success(added)
    } catch (error: TrustWeaveError) {
        logger.error("Failed to add trust anchor", error)
        Result.failure(error)
    }
}
```

## Wallet Management Workflow

Complete workflow for managing credentials in a wallet:

```kotlin
suspend fun walletManagementWorkflow(
    trustLayer: TrustLayer,
    holderDid: String
): Result<Wallet> {
    return try {
        // 1. Create or get wallet
        val wallet = trustLayer.wallet {
            holder(holderDid)
            enableOrganization()
            enablePresentation()
        }
        
        // 2. Store credential
        val credential = trustLayer.issue {
            credential {
                type("VerifiableCredential", "PersonCredential")
                issuer("did:key:issuer")
                subject {
                    id(holderDid)
                    claim("name", "Alice")
                }
            }
            by(issuerDid = "did:key:issuer", keyId = "did:key:issuer#key-1")
        }
        
        val credentialId = wallet.store(credential)
        logger.info("Credential stored", mapOf(
            "credentialId" to credentialId,
            "holderDid" to holderDid
        ))
        
        // 3. Retrieve credential
        val retrieved = wallet.get(credentialId)
        if (retrieved != null) {
            logger.info("Credential retrieved", mapOf(
                "credentialId" to credentialId
            ))
        }
        
        // 4. List all credentials
        val allCredentials = wallet.list()
        logger.info("Wallet contains ${allCredentials.size} credentials")
        
        Result.success(wallet)
    } catch (error: TrustWeaveError) {
        logger.error("Wallet management failed", error)
        Result.failure(error)
    }
}
```

## Batch Operations Workflow

Complete workflow for batch operations:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

suspend fun batchIssuanceWorkflow(
    trustLayer: TrustLayer,
    requests: List<CredentialRequest>
): Result<List<VerifiableCredential>> {
    return try {
        // Issue credentials concurrently
        val credentials = requests.map { request ->
            async {
                trustLayer.issue {
                    credential {
                        type("VerifiableCredential", request.type)
                        issuer(request.issuerDid)
                        subject {
                            id(request.holderDid)
                            request.claims.forEach { (key, value) ->
                                claim(key, value)
                            }
                        }
                    }
                    by(issuerDid = request.issuerDid, keyId = request.keyId)
                }
            }
        }.awaitAll()
        
        logger.info("Batch issuance completed", mapOf(
            "count" to credentials.size
        ))
        
        Result.success(credentials)
    } catch (error: TrustWeaveError) {
        logger.error("Batch issuance failed", error)
        Result.failure(error)
    }
}

data class CredentialRequest(
    val type: String,
    val issuerDid: String,
    val holderDid: String,
    val keyId: String,
    val claims: Map<String, Any>
)
```

## Related Documentation

- [API Patterns](api-patterns.md) - Correct API usage patterns
- [Production Deployment](production-deployment.md) - Production best practices
- [Error Handling](../advanced/error-handling.md) - Error handling patterns
- [API Reference](../api-reference/core-api.md) - Complete API documentation

