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
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.IssuanceResult
import kotlinx.coroutines.runBlocking

suspend fun issueCredentialWorkflow(
    trustWeave: TrustWeave,
    issuerDid: org.trustweave.did.identifiers.Did,
    holderDid: org.trustweave.did.identifiers.Did,
    claims: Map<String, Any>
): Result<org.trustweave.credential.model.vc.VerifiableCredential> {
    return try {
        // 1. Verify issuer DID is resolvable (optional - issue() will resolve automatically)
        val issuerResolution = trustWeave.resolveDid(issuerDid)
        
        if (issuerResolution !is org.trustweave.did.resolver.DidResolutionResult.Success) {
            return Result.failure(
                IllegalStateException("Failed to resolve issuer DID: ${issuerResolution.errorMessage}")
            )
        }

        // 2. Issue credential (key ID will be auto-extracted from DID document)
        val issuanceResult = trustWeave.issue {
            credential {
                type("VerifiableCredential", "PersonCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid.value)
                    claims.forEach { (key, value) ->
                        key to value
                    }
                }
            }
            signedBy(issuerDid)
        }
        
        val credential = issuanceResult.getOrThrow()

        Result.success(credential)
    } catch (error: Exception) {
        Result.failure(error)
    }
}
```

## Credential Verification Workflow

Complete workflow for verifying a verifiable credential:

```kotlin
suspend fun verifyCredentialWorkflow(
    trustWeave: TrustWeave,
    credential: org.trustweave.credential.model.vc.VerifiableCredential,
    checkTrust: Boolean = false
): org.trustweave.trust.types.VerificationResult {
    return try {
        val verification = trustWeave.verify {
            credential(credential)
            checkExpiration()
            checkRevocation()
            checkTrust(checkTrust)
        }

        // Log verification result
        when (verification) {
            is org.trustweave.trust.types.VerificationResult.Valid -> {
                logger.info("Credential verified successfully", mapOf(
                    "credentialId" to verification.credential.id,
                    "issuer" to verification.credential.issuer
                ))
            }
            is org.trustweave.trust.types.VerificationResult.Invalid -> {
                logger.warn("Credential verification failed", mapOf(
                    "credentialId" to credential.id,
                    "reason" to when (verification) {
                        is org.trustweave.trust.types.VerificationResult.Invalid.Expired -> "Expired: ${verification.expiredAt}"
                        is org.trustweave.trust.types.VerificationResult.Invalid.Revoked -> "Revoked"
                        is org.trustweave.trust.types.VerificationResult.Invalid.InvalidProof -> "Invalid proof: ${verification.reason}"
                        is org.trustweave.trust.types.VerificationResult.Invalid.UntrustedIssuer -> "Untrusted issuer: ${verification.issuer}"
                        is org.trustweave.trust.types.VerificationResult.Invalid.SchemaValidationFailed -> "Schema validation failed: ${verification.errors.joinToString()}"
                        else -> "Unknown error"
                    }
                ))
            }
        }

        verification
    } catch (error: Exception) {
        logger.error("Verification error", error)
        throw error // Re-throw or handle as needed
    }
}
```

## Credential Revocation Workflow

Complete workflow for revoking a credential:

```kotlin
import org.trustweave.credential.revocation.*

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
    trustWeave: TrustWeave,
    did: String,
    oldKeyId: String,
    newKeyId: String
): Result<DidDocument> {
    return try {
        // 1. Verify current DID document
        val context = trustWeave.configuration
        val resolver = context.getDidResolver()
        val currentResolution = resolver?.resolve(did)

        if (currentResolution !is org.trustweave.did.resolver.DidResolutionResult.Success) {
            return Result.failure(
                IllegalStateException("Failed to resolve DID: ${currentResolution.errorMessage}")
            )
        }

        // 2. Verify old key exists
        val oldKey = currentResolution.document.verificationMethod
            .find { it.id.value.contains(oldKeyId) }

        if (oldKey == null) {
            return Result.failure(
                IllegalStateException("Old key not found in DID document: $oldKeyId")
            )
        }

        // 3. Rotate key (returns DidDocument directly)
        val updated = trustWeave.rotateKey {
            did(did.value)
            algorithm("Ed25519") // Algorithm required for key generation
        }

        logger.info("Key rotated successfully", mapOf(
            "did" to did.value,
            "oldKeyId" to oldKeyId,
            "newKeyId" to newKeyId
        ))

        Result.success(updated)
    } catch (error: Exception) {
        logger.error("Key rotation failed", error)
        Result.failure(error)
    }
}
```

## Trust Anchor Management Workflow

Complete workflow for managing trust anchors:

```kotlin
suspend fun manageTrustAnchorWorkflow(
    trustWeave: TrustWeave,
    anchorDid: org.trustweave.did.identifiers.Did,
    credentialTypes: List<String>,
    description: String
): Result<Boolean> {
    return try {
        // 1. Verify anchor DID is resolvable
        val resolution = trustWeave.resolveDid(anchorDid)

        if (resolution !is org.trustweave.did.resolver.DidResolutionResult.Success) {
            return Result.failure(
                IllegalStateException("Failed to resolve DID: ${resolution.errorMessage}")
            )
        }

        // 2. Add trust anchor using trust DSL
        trustWeave.trust {
            addAnchor(anchorDid) {
                credentialTypes(*credentialTypes.toTypedArray())
                description(description)
            }
        }
        val added = true

        logger.info("Trust anchor added", mapOf(
            "anchorDid" to anchorDid.value,
            "credentialTypes" to credentialTypes
        ))

        Result.success(added)
    } catch (error: Exception) {
        logger.error("Failed to add trust anchor", error)
        Result.failure(error)
    }
}
```

## Wallet Management Workflow

Complete workflow for managing credentials in a wallet:

```kotlin
suspend fun walletManagementWorkflow(
    trustWeave: TrustWeave,
    holderDid: String
): Result<Wallet> {
    return try {
        // 1. Create or get wallet
        import org.trustweave.trust.types.getOrThrow
        
        val walletResult = trustWeave.wallet {
            holder(org.trustweave.did.identifiers.Did(holderDid))
            enableOrganization()
            enablePresentation()
        }
        
        val wallet = when (walletResult) {
            is org.trustweave.trust.types.WalletCreationResult.Success -> walletResult.wallet
            else -> return Result.failure(IllegalStateException("Wallet creation failed"))
        }

        // 2. Store credential
        val credentialResult = trustWeave.issue {
            credential {
                type("VerifiableCredential", "PersonCredential")
                issuer("did:key:issuer")
                subject {
                    id(holderDid)
                    "name" to "Alice"
                }
            }
            signedBy(org.trustweave.did.identifiers.Did("did:key:issuer"))
        }
        
        val credential = when (credentialResult) {
            is org.trustweave.credential.results.IssuanceResult.Success -> credentialResult.credential
            else -> return Result.failure(IllegalStateException("Credential issuance failed"))
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
    } catch (error: Exception) {
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
    trustWeave: TrustWeave,
    requests: List<CredentialRequest>
): Result<List<VerifiableCredential>> {
    return try {
        // Issue credentials concurrently
        import org.trustweave.trust.types.IssuanceResult
        
        import org.trustweave.trust.types.getOrThrow
        
        val credentials = requests.map { request ->
            async {
                runCatching {
                    trustWeave.issue {
                        credential {
                            type("VerifiableCredential", request.type)
                            issuer(request.issuerDid)
                            subject {
                                id(request.holderDid)
                                request.claims.forEach { (key, value) ->
                                    key to value
                                }
                            }
                        }
                        signedBy(issuerDid = request.issuerDid, keyId = request.keyId)
                    }.getOrThrow()
                }
            }
        }.awaitAll().mapNotNull { result ->
            result.fold(
                onSuccess = { it },
                onFailure = { error ->
                    logger.warn("Failed to issue credential: ${error.message}")
                    null
                }
            )
        }

        logger.info("Batch issuance completed", mapOf(
            "count" to credentials.size
        ))

        Result.success(credentials)
    } catch (error: Exception) {
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

