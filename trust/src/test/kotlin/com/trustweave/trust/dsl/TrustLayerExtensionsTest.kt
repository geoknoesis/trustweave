package com.trustweave.trust.dsl

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.testkit.credential.InMemoryWallet
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.trust.dsl.TrustWeaveConfig
import com.trustweave.trust.dsl.trustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.dsl.wallet.organize
import com.trustweave.testkit.getOrFail
import com.trustweave.trust.types.IssuanceResult
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for TrustLayerExtensions.kt
 */
class TrustLayerExtensionsTest {

    private lateinit var trustWeave: TrustWeaveConfig
    private lateinit var wallet: InMemoryWallet

    @BeforeEach
    fun setup() = runBlocking {
        val kms = InMemoryKeyManagementService()
        wallet = InMemoryWallet(
            walletId = "test-wallet",
            holderDid = "did:key:holder"
        )

        trustWeave = trustWeave {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(com.trustweave.core.types.KeyId(keyId), data) }
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
        }
    }

    @Test
    fun `test createDidAndIssue`() = runBlocking {
        val credential = trustWeave.createDidAndIssue(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            }
        ) { did ->
            // Extract key ID from DID document
            val didResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(did)
                ?: throw IllegalStateException("Failed to resolve DID")
            val didDoc = when (didResolution) {
                is com.trustweave.did.resolver.DidResolutionResult.Success -> didResolution.document
                else -> throw IllegalStateException("Failed to resolve DID")
            }
            val keyId = didDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
                ?: throw IllegalStateException("No verification method in DID")
            
            trustWeave.issue {
                credential {
                    type("VerifiableCredential")
                    issuer(did)
                    subject {
                        id("did:key:subject")
                    }
                    issued(Instant.parse("2024-01-01T00:00:00Z"))
                }
                signedBy(issuerDid = did, keyId = keyId)
            }
        }.getOrFail()

        assertNotNull(credential)
        assertTrue(credential.issuer.startsWith("did:key:"))
    }

    @Test
    fun `test createDidIssueAndStore`() = runBlocking {
        val stored = trustWeave.createDidIssueAndStore(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            },
            credentialBlock = { did ->
                // Extract key ID from DID document
                val didResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(did)
                    ?: throw IllegalStateException("Failed to resolve DID")
                val didDoc = when (didResolution) {
                    is com.trustweave.did.resolver.DidResolutionResult.Success -> didResolution.document
                    else -> throw IllegalStateException("Failed to resolve DID")
                }
                val keyId = didDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
                    ?: throw IllegalStateException("No verification method in DID")
                
                trustWeave.issue {
                    credential {
                        type("VerifiableCredential")
                        issuer(did)
                        subject {
                            id("did:key:subject")
                        }
                        issued(Instant.parse("2024-01-01T00:00:00Z"))
                    }
                    signedBy(issuerDid = did, keyId = keyId)
                }
            },
            wallet = wallet
        ).getOrThrow()

        assertNotNull(stored)
        // StoredCredential is just VerifiableCredential, wallet storage is separate
        assertTrue(stored is VerifiableCredential)
        // Get credential ID from wallet store result
        val credentialId = wallet.store(stored)
        assertNotNull(credentialId)
    }

    @Test
    fun `test completeWorkflow`() = runBlocking {
        val result = trustWeave.completeWorkflow(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            },
            credentialBlock = { did ->
                // Extract key ID from DID document
                val didResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(did)
                    ?: throw IllegalStateException("Failed to resolve DID")
                val didDoc = when (didResolution) {
                    is com.trustweave.did.resolver.DidResolutionResult.Success -> didResolution.document
                    else -> throw IllegalStateException("Failed to resolve DID")
                }
                val keyId = didDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
                    ?: throw IllegalStateException("No verification method in DID")
                
                trustWeave.issue {
                    credential {
                        type("VerifiableCredential")
                        issuer(did)
                        subject {
                            id("did:key:subject")
                        }
                        issued(Instant.parse("2024-01-01T00:00:00Z"))
                    }
                    signedBy(issuerDid = did, keyId = keyId)
                }
            },
            wallet = wallet,
            organizeBlock = { stored ->
                // Get credential ID from stored credential
                val credentialId = stored.id ?: wallet.store(stored)
                // Use wallet.organize extension function
                wallet.organize {
                    tag(credentialId, "test")
                }
            }
        ).getOrThrow()

        assertNotNull(result)
        assertTrue(result.did.value.startsWith("did:key:"))
        assertNotNull(result.credential)
        assertNotNull(result.storedCredential)
        assertNotNull(result.verificationResult)
    }

    @Test
    fun `test completeWorkflow without organization`() = runBlocking {
        val result = trustWeave.completeWorkflow(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            },
            credentialBlock = { did ->
                // Extract key ID from DID document
                val didResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(did)
                    ?: throw IllegalStateException("Failed to resolve DID")
                val didDoc = when (didResolution) {
                    is com.trustweave.did.resolver.DidResolutionResult.Success -> didResolution.document
                    else -> throw IllegalStateException("Failed to resolve DID")
                }
                val keyId = didDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
                    ?: throw IllegalStateException("No verification method in DID")
                
                trustWeave.issue {
                    credential {
                        type("VerifiableCredential")
                        issuer(did)
                        subject {
                            id("did:key:subject")
                        }
                        issued(Instant.parse("2024-01-01T00:00:00Z"))
                    }
                    signedBy(issuerDid = did, keyId = keyId)
                }
            },
            wallet = wallet
        ).getOrThrow()

        assertNotNull(result)
        assertNull(result.organizationResult)
    }

    @Test
    fun `test createDidAndIssue via TrustWeaveContext`() = runBlocking {
        val context = trustWeave.getDslContext()
        val credential = context.createDidAndIssue(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            }
        ) { did ->
            // Extract key ID from DID document
            val didResolution = context.getConfig().registries.didRegistry.resolve(did)
                ?: throw IllegalStateException("Failed to resolve DID")
            val didDoc = when (didResolution) {
                is com.trustweave.did.resolver.DidResolutionResult.Success -> didResolution.document
                else -> throw IllegalStateException("Failed to resolve DID")
            }
            val keyId = didDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
                ?: throw IllegalStateException("No verification method in DID")
            
            context.issue {
                credential {
                    type("VerifiableCredential")
                    issuer(did)
                    subject {
                        id("did:key:subject")
                    }
                    issued(Instant.parse("2024-01-01T00:00:00Z"))
                }
                signedBy(issuerDid = did, keyId = keyId)
            }
        }.getOrFail()

        assertNotNull(credential)
        assertTrue(credential.issuer.startsWith("did:key:"))
    }

    @Test
    fun `test createDidIssueAndStore via TrustWeaveContext`() = runBlocking {
        val context = trustWeave.getDslContext()
        val stored = context.createDidIssueAndStore(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            },
            credentialBlock = { did ->
                // Extract key ID from DID document
                val didResolution = context.getConfig().registries.didRegistry.resolve(did)
                    ?: throw IllegalStateException("Failed to resolve DID")
                val didDoc = when (didResolution) {
                    is com.trustweave.did.resolver.DidResolutionResult.Success -> didResolution.document
                    else -> throw IllegalStateException("Failed to resolve DID")
                }
                val keyId = didDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
                    ?: throw IllegalStateException("No verification method in DID")
                
                context.issue {
                    credential {
                        type("VerifiableCredential")
                        issuer(did)
                        subject {
                            id("did:key:subject")
                        }
                        issued(Instant.parse("2024-01-01T00:00:00Z"))
                    }
                    signedBy(issuerDid = did, keyId = keyId)
                }
            },
            wallet = wallet
        ).getOrThrow()

        assertNotNull(stored)
        // StoredCredential is just VerifiableCredential, wallet storage is separate
        assertTrue(stored is VerifiableCredential)
    }
}


