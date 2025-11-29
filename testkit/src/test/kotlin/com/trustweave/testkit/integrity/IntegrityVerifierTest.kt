package com.trustweave.testkit.integrity

import com.trustweave.anchor.AnchorRef
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.testkit.integrity.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for IntegrityVerifier.
 */
class IntegrityVerifierTest {

    @Test
    fun `test verifyVcIntegrity`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("algorand:testnet")

        val vc = buildJsonObject {
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
            put("credentialSubject", buildJsonObject { put("id", "did:key:subject") })
            put("issued", "2024-01-01T00:00:00Z")
        }
        val vcDigest = com.trustweave.core.util.DigestUtils.sha256DigestMultibase(vc)

        val anchorResult = client.writePayload(
            payload = buildJsonObject { put("vcDigest", vcDigest) }
        )
        val anchorRef = anchorResult.ref
        val registry = BlockchainAnchorRegistry().also { it.register("algorand:testnet", client) }

        val vcWithDigest = buildJsonObject {
            vc.entries.forEach { put(it.key, it.value) }
            put("digestMultibase", vcDigest)
        }

        val isValid = IntegrityVerifier.verifyVcIntegrity(vcWithDigest, anchorRef, registry)

        assertTrue(isValid)
    }

    @Test
    fun `test verifyLinksetIntegrity`() {
        val linkset = buildJsonObject {
            put("@context", "https://www.w3.org/ns/json-ld#")
            put("links", buildJsonArray {
                add(buildJsonObject {
                    put("href", "https://example.com/artifact")
                    put("digestMultibase", "digest-123")
                })
            })
        }
        val linksetDigest = com.trustweave.core.util.DigestUtils.sha256DigestMultibase(
            buildJsonObject {
                linkset.entries.forEach { (key, value) ->
                    if (key != "digestMultibase") {
                        put(key, value)
                    }
                }
            }
        )

        val linksetWithDigest = buildJsonObject {
            linkset.entries.forEach { put(it.key, it.value) }
            put("digestMultibase", linksetDigest)
        }

        val isValid = IntegrityVerifier.verifyLinksetIntegrity(linksetWithDigest, linksetDigest)

        assertTrue(isValid)
    }

    @Test
    fun `test verifyArtifactIntegrity`() {
        val content = buildJsonObject { put("title", "Test Dataset") }
        val contentDigest = com.trustweave.core.util.DigestUtils.sha256DigestMultibase(content)

        val artifact = buildJsonObject {
            put("id", "artifact-1")
            put("type", "Metadata")
            put("content", content)
            put("digestMultibase", contentDigest)
        }

        val isValid = IntegrityVerifier.verifyArtifactIntegrity(artifact, contentDigest)

        assertTrue(isValid)
    }

    @Test
    fun `test verifyIntegrityChain`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("algorand:testnet")
        val registry = BlockchainAnchorRegistry().also { it.register("algorand:testnet", client) }

        val artifactContent = buildJsonObject { put("title", "Test Dataset") }
        val artifactDigest = com.trustweave.core.util.DigestUtils.sha256DigestMultibase(artifactContent)
        val artifact = buildJsonObject {
            put("id", "artifact-1")
            put("content", artifactContent)
            put("digestMultibase", artifactDigest)
        }

        val linkset = buildJsonObject {
            put("@context", "https://www.w3.org/ns/json-ld#")
            put("links", buildJsonArray {
                add(buildJsonObject {
                    put("href", "artifact-1")
                    put("digestMultibase", artifactDigest)
                })
            })
        }
        val linksetDigest = com.trustweave.core.util.DigestUtils.sha256DigestMultibase(
            buildJsonObject {
                linkset.entries.forEach { (key, value) ->
                    if (key != "digestMultibase") {
                        put(key, value)
                    }
                }
            }
        )

        val vc = buildJsonObject {
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
            put("credentialSubject", buildJsonObject { put("id", "did:key:subject") })
            put("issued", "2024-01-01T00:00:00Z")
            put("linksetDigest", linksetDigest)
        }
        val vcDigest = com.trustweave.core.util.DigestUtils.sha256DigestMultibase(
            buildJsonObject {
                vc.entries.forEach { (key, value) ->
                    if (key != "digestMultibase" && key != "evidence" && key != "credentialStatus") {
                        put(key, value)
                    }
                }
            }
        )

        val anchorResult = client.writePayload(
            payload = buildJsonObject { put("vcDigest", vcDigest) }
        )
        val anchorRef = anchorResult.ref

        val linksetWithDigest = buildJsonObject {
            linkset.entries.forEach { put(it.key, it.value) }
            put("digestMultibase", linksetDigest)
        }

        val result = IntegrityVerifier.verifyIntegrityChain(
            vc = buildJsonObject {
                vc.entries.forEach { put(it.key, it.value) }
                put("digestMultibase", vcDigest)
            },
            linkset = linksetWithDigest,
            artifacts = mapOf("artifact-1" to artifact),
            anchorRef = anchorRef,
            registry = registry
        )

        assertTrue(result.valid)
        assertTrue(result.steps.isNotEmpty())
    }

    @Test
    fun `test discoverAnchorFromEvidence`() {
        val vc = buildJsonObject {
            put("evidence", buildJsonArray {
                add(buildJsonObject {
                    put("type", "BlockchainAnchorEvidence")
                    put("chainId", "algorand:testnet")
                    put("txHash", "tx-123")
                    put("digestMultibase", "digest-123")
                    put("contract", "app-456")
                })
            })
        }

        val anchorRef = IntegrityVerifier.discoverAnchorFromEvidence(vc)

        assertNotNull(anchorRef)
        assertEquals("algorand:testnet", anchorRef?.chainId)
        assertEquals("tx-123", anchorRef?.txHash)
        assertEquals("app-456", anchorRef?.contract)
    }

    @Test
    fun `test discoverAnchorFromEvidence returns null when no evidence`() {
        val vc = buildJsonObject {
            put("type", buildJsonArray { add("VerifiableCredential") })
        }

        val anchorRef = IntegrityVerifier.discoverAnchorFromEvidence(vc)

        assertNull(anchorRef)
    }

    @Test
    fun `test discoverAnchorFromDidDocument`() {
        val didDoc = buildJsonObject {
            put("service", buildJsonArray {
                add(buildJsonObject {
                    put("type", "AnchorService")
                    put("serviceEndpoint", buildJsonObject {
                        put("chainId", "algorand:testnet")
                        put("anchorLookup", "https://algoexplorer.io/tx/{txHash}")
                    })
                })
            })
        }

        val anchorRef = IntegrityVerifier.discoverAnchorFromDidDocument("did:key:issuer", didDoc)

        // Returns null because anchorLookup pattern needs to be resolved
        assertNull(anchorRef)
    }

    @Test
    fun `test discoverAnchorFromStatusService`() {
        val anchorInfo = TestDataBuilders.buildAnchorInfo(
            chainId = "algorand:testnet",
            txHash = "tx-123",
            digestMultibase = "digest-123"
        )
        val statusResponse = TestDataBuilders.buildStatusResponse("active", anchorInfo)

        val anchorRef = IntegrityVerifier.discoverAnchorFromStatusService(statusResponse)

        assertNotNull(anchorRef)
        assertEquals("algorand:testnet", anchorRef?.chainId)
        assertEquals("tx-123", anchorRef?.txHash)
    }

    @Test
    fun `test discoverAnchorFromRegistry`() {
        val registryEntry = AnchorRegistryEntry(
            digestMultibase = "digest-123",
            chainId = "algorand:testnet",
            txHash = "tx-123",
            timestamp = 1234567890L,
            contract = "app-456",
            issuer = "did:key:issuer"
        )

        val anchorRef = IntegrityVerifier.discoverAnchorFromRegistry(registryEntry)

        assertEquals("algorand:testnet", anchorRef.chainId)
        assertEquals("tx-123", anchorRef.txHash)
        assertEquals("app-456", anchorRef.contract)
    }

    @Test
    fun `test discoverAnchorFromManifest`() {
        val manifest = TestDataBuilders.buildAnchorManifest(
            manifestId = "manifest-1",
            chainId = "algorand:testnet",
            anchoredDigests = listOf("digest-1", "digest-2"),
            contract = "app-456"
        )

        val anchorRef = IntegrityVerifier.discoverAnchorFromManifest(manifest)

        assertNotNull(anchorRef)
        assertEquals("algorand:testnet", anchorRef?.chainId)
        assertEquals("app-456", anchorRef?.contract)
        assertEquals("", anchorRef?.txHash) // Manifest doesn't have single txHash
    }
}

