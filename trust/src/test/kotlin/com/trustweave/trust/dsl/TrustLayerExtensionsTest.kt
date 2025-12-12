package com.trustweave.trust.dsl

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.testkit.credential.InMemoryWallet
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.trust.dsl.TrustWeaveConfig
import com.trustweave.trust.dsl.trustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.dsl.wallet.organize
import com.trustweave.testkit.getOrFail
import com.trustweave.credential.results.IssuanceResult
import com.trustweave.kms.results.SignResult
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
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

        // Create temporary TrustWeaveConfig to get DID registry for resolver
        val tempTrustWeaveConfig = trustWeave {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId ->
                    when (val result = kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
        }
        
        val didResolver = com.trustweave.did.resolver.DidResolver { did ->
            tempTrustWeaveConfig.getDslContext().getConfig().registries.didRegistry.resolve(did.value)
        }
        
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            when (val result = kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
                is SignResult.Success -> result.signature
                else -> throw IllegalStateException("Signing failed: $result")
            }
        }
        
        // Create CredentialService with resolver that will use the final TrustWeaveConfig's registry
        // We'll use a mutable reference that gets updated after trustWeave is built
        var trustWeaveConfigRef: TrustWeaveConfig? = null
        val finalDidResolver = com.trustweave.did.resolver.DidResolver { did ->
            (trustWeaveConfigRef ?: tempTrustWeaveConfig).getDslContext().getConfig().registries.didRegistry.resolve(did.value)
        }
        
        val finalCredentialService = com.trustweave.credential.credentialService(
            didResolver = finalDidResolver,
            signer = signer
        )

        trustWeave = trustWeave {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId ->
                    when (val result = kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
            issuer(finalCredentialService)
        }
        
        // Update the DID resolver to use the final TrustWeaveConfig's registry
        trustWeaveConfigRef = trustWeave
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
            val keyId = didDoc.verificationMethod.firstOrNull()?.id?.value?.substringAfter("#")
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
        assertTrue(credential.issuer.id.value.startsWith("did:key:"))
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
                val keyId = didDoc.verificationMethod.firstOrNull()?.id?.value?.substringAfter("#")
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
        ).getOrElse { throw IllegalStateException("Failed to create DID, issue credential, and store: $it") }

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
                val keyId = didDoc.verificationMethod.firstOrNull()?.id?.value?.substringAfter("#")
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
                val credentialId: String = stored.id?.value ?: wallet.store(stored)
                // Use wallet.organize extension function
                wallet.organize {
                    tag(credentialId, "test")
                }
            }
        ).getOrElse { throw IllegalStateException("Failed to complete workflow: $it") }

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
                val keyId = didDoc.verificationMethod.firstOrNull()?.id?.value?.substringAfter("#")
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
        ).getOrElse { throw IllegalStateException("Failed: $it") }

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
            val keyId = didDoc.verificationMethod.firstOrNull()?.id?.value?.substringAfter("#")
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
        assertTrue(credential.issuer.id.value.startsWith("did:key:"))
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
                val keyId = didDoc.verificationMethod.firstOrNull()?.id?.value?.substringAfter("#")
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
        ).getOrElse { throw IllegalStateException("Failed to create DID, issue credential, and store: $it") }

        assertNotNull(stored)
        // StoredCredential is just VerifiableCredential, wallet storage is separate
        assertTrue(stored is VerifiableCredential)
    }
}


