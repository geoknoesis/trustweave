package com.geoknoesis.vericore.testkit.integrity

import com.geoknoesis.vericore.anchor.AnchorRef
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.json.DigestUtils
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for IntegrityVerifier.
 * Tests all conditional branches in integrity verification.
 */
class IntegrityVerifierBranchCoverageTest {

    private lateinit var client: InMemoryBlockchainAnchorClient
    private val chainId = "algorand:testnet"
    private lateinit var registry: BlockchainAnchorRegistry

    @BeforeEach
    fun setup() {
        registry = BlockchainAnchorRegistry()
        client = InMemoryBlockchainAnchorClient(chainId)
        registry.register(chainId, client)
    }

    // ========== verifyVcIntegrity() Branch Coverage ==========

    @Test
    fun `test branch verifyVcIntegrity excludes digestMultibase from computation`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
            put("digestMultibase", "should-be-excluded")
        }
        val digest = DigestUtils.sha256DigestMultibase(buildJsonObject {
            put("id", "cred-1")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
        })
        val payload = buildJsonObject {
            put("digestMultibase", digest)
        }
        val anchorResult = client.writePayload(payload, "application/json")
        
        val result = IntegrityVerifier.verifyVcIntegrity(vc, anchorResult.ref, registry)
        
        assertTrue(result)
    }

    @Test
    fun `test branch verifyVcIntegrity excludes evidence from computation`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
            put("evidence", buildJsonArray {
                add(buildJsonObject {
                    put("type", "BlockchainAnchorEvidence")
                })
            })
        }
        val digest = DigestUtils.sha256DigestMultibase(buildJsonObject {
            put("id", "cred-1")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
        })
        val payload = buildJsonObject {
            put("digestMultibase", digest)
        }
        val anchorResult = client.writePayload(payload, "application/json")
        
        val result = IntegrityVerifier.verifyVcIntegrity(vc, anchorResult.ref, registry)
        
        assertTrue(result)
    }

    @Test
    fun `test branch verifyVcIntegrity uses vcDigest from payload`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
        }
        val digest = DigestUtils.sha256DigestMultibase(vc)
        val payload = buildJsonObject {
            put("vcDigest", digest)
        }
        val anchorResult = client.writePayload(payload, "application/json")
        
        val result = IntegrityVerifier.verifyVcIntegrity(vc, anchorResult.ref, registry)
        
        assertTrue(result)
    }

    @Test
    fun `test branch verifyVcIntegrity throws when no client registered`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
        }
        val anchorRef = AnchorRef(chainId = "nonexistent:chain", txHash = "tx-123")
        
        assertFailsWith<com.geoknoesis.vericore.core.VeriCoreException> {
            IntegrityVerifier.verifyVcIntegrity(vc, anchorRef, BlockchainAnchorRegistry())
        }
    }

    @Test
    fun `test branch verifyVcIntegrity throws when payload has no digest`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
        }
        val payload = buildJsonObject {
            put("otherField", "value")
        }
        val anchorResult = client.writePayload(payload, "application/json")
        
        assertFailsWith<com.geoknoesis.vericore.core.VeriCoreException> {
            IntegrityVerifier.verifyVcIntegrity(vc, anchorResult.ref, registry)
        }
    }

    // ========== verifyLinksetIntegrity() Branch Coverage ==========

    @Test
    fun `test branch verifyLinksetIntegrity excludes digestMultibase`() = runBlocking {
        val linkset = buildJsonObject {
            put("id", "linkset-1")
            put("digestMultibase", "should-be-excluded")
        }
        val digest = DigestUtils.sha256DigestMultibase(buildJsonObject {
            put("id", "linkset-1")
        })
        
        val result = IntegrityVerifier.verifyLinksetIntegrity(linkset, digest)
        
        assertTrue(result)
    }

    @Test
    fun `test branch verifyLinksetIntegrity returns false when digests don't match`() = runBlocking {
        val linkset = buildJsonObject {
            put("id", "linkset-1")
        }
        val wrongDigest = "wrong-digest"
        
        val result = IntegrityVerifier.verifyLinksetIntegrity(linkset, wrongDigest)
        
        assertFalse(result)
    }

    // ========== verifyArtifactIntegrity() Branch Coverage ==========

    @Test
    fun `test branch verifyArtifactIntegrity uses content field`() = runBlocking {
        val artifact = buildJsonObject {
            put("id", "artifact-1")
            put("content", buildJsonObject {
                put("data", "test-data")
            })
        }
        val contentDigest = DigestUtils.sha256DigestMultibase(buildJsonObject {
            put("data", "test-data")
        })
        
        val result = IntegrityVerifier.verifyArtifactIntegrity(artifact, contentDigest)
        
        assertTrue(result)
    }

    @Test
    fun `test branch verifyArtifactIntegrity uses entire artifact when no content field`() = runBlocking {
        val artifact = buildJsonObject {
            put("id", "artifact-1")
            put("data", "test-data")
        }
        val artifactDigest = DigestUtils.sha256DigestMultibase(artifact)
        
        val result = IntegrityVerifier.verifyArtifactIntegrity(artifact, artifactDigest)
        
        assertTrue(result)
    }

    @Test
    fun `test branch verifyArtifactIntegrity returns false when digests don't match`() = runBlocking {
        val artifact = buildJsonObject {
            put("id", "artifact-1")
            put("content", buildJsonObject {
                put("data", "test-data")
            })
        }
        val wrongDigest = "wrong-digest"
        
        val result = IntegrityVerifier.verifyArtifactIntegrity(artifact, wrongDigest)
        
        assertFalse(result)
    }

    // ========== verifyIntegrityChain() Branch Coverage ==========

    @Test
    fun `test branch verifyIntegrityChain handles VC verification exception`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
        }
        val linkset = buildJsonObject {
            put("id", "linkset-1")
        }
        val artifacts = emptyMap<String, JsonObject>()
        val emptyRegistry = BlockchainAnchorRegistry()
        val anchorRef = AnchorRef(chainId = chainId, txHash = "tx-123")
        
        val result = IntegrityVerifier.verifyIntegrityChain(vc, linkset, artifacts, anchorRef, emptyRegistry)
        
        assertFalse(result.valid)
        assertTrue(result.steps.any { !it.valid })
    }

    @Test
    fun `test branch verifyIntegrityChain with linksetRef from links digestMultibase`() = runBlocking {
        val linkset = buildJsonObject {
            put("id", "linkset-1")
        }
        val linksetDigest = DigestUtils.sha256DigestMultibase(linkset)
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("links", buildJsonObject {
                put("digestMultibase", linksetDigest)
            })
        }
        val digest = DigestUtils.sha256DigestMultibase(buildJsonObject {
            put("id", "cred-1")
            put("links", buildJsonObject {
                put("digestMultibase", linksetDigest)
            })
        })
        val payload = buildJsonObject {
            put("digestMultibase", digest)
        }
        val anchorResult = client.writePayload(payload, "application/json")
        val artifacts = emptyMap<String, JsonObject>()
        
        val result = IntegrityVerifier.verifyIntegrityChain(vc, linkset, artifacts, anchorResult.ref, registry)
        
        assertNotNull(result)
    }

    @Test
    fun `test branch verifyIntegrityChain with linksetRef from linksetDigest`() = runBlocking {
        val linkset = buildJsonObject {
            put("id", "linkset-1")
        }
        val linksetDigest = DigestUtils.sha256DigestMultibase(linkset)
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("linksetDigest", linksetDigest)
        }
        val digest = DigestUtils.sha256DigestMultibase(buildJsonObject {
            put("id", "cred-1")
            put("linksetDigest", linksetDigest)
        })
        val payload = buildJsonObject {
            put("digestMultibase", digest)
        }
        val anchorResult = client.writePayload(payload, "application/json")
        val artifacts = emptyMap<String, JsonObject>()
        
        val result = IntegrityVerifier.verifyIntegrityChain(vc, linkset, artifacts, anchorResult.ref, registry)
        
        assertNotNull(result)
    }

    @Test
    fun `test branch verifyIntegrityChain with linksetRef from linkset object`() = runBlocking {
        val linkset = buildJsonObject {
            put("id", "linkset-1")
        }
        val linksetDigest = DigestUtils.sha256DigestMultibase(linkset)
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("linkset", buildJsonObject {
                put("digestMultibase", linksetDigest)
            })
        }
        val digest = DigestUtils.sha256DigestMultibase(buildJsonObject {
            put("id", "cred-1")
            put("linkset", buildJsonObject {
                put("digestMultibase", linksetDigest)
            })
        })
        val payload = buildJsonObject {
            put("digestMultibase", digest)
        }
        val anchorResult = client.writePayload(payload, "application/json")
        val artifacts = emptyMap<String, JsonObject>()
        
        val result = IntegrityVerifier.verifyIntegrityChain(vc, linkset, artifacts, anchorResult.ref, registry)
        
        assertNotNull(result)
    }

    @Test
    fun `test branch verifyIntegrityChain with no linksetRef validates digest format`() = runBlocking {
        val linkset = buildJsonObject {
            put("id", "linkset-1")
        }
        val vc = buildJsonObject {
            put("id", "cred-1")
        }
        val digest = DigestUtils.sha256DigestMultibase(vc)
        val payload = buildJsonObject {
            put("digestMultibase", digest)
        }
        val anchorResult = client.writePayload(payload, "application/json")
        val artifacts = emptyMap<String, JsonObject>()
        
        val result = IntegrityVerifier.verifyIntegrityChain(vc, linkset, artifacts, anchorResult.ref, registry)
        
        assertNotNull(result)
    }

    @Test
    fun `test branch verifyIntegrityChain with artifact found`() = runBlocking {
        val linkset = buildJsonObject {
            put("id", "linkset-1")
            put("links", buildJsonArray {
                add(buildJsonObject {
                    put("href", "artifact-1")
                    put("digestMultibase", "u-test-digest")
                })
            })
        }
        val artifact = buildJsonObject {
            put("id", "artifact-1")
            put("content", buildJsonObject {
                put("data", "test")
            })
        }
        val artifactDigest = DigestUtils.sha256DigestMultibase(buildJsonObject {
            put("data", "test")
        })
        val linksetWithDigest = buildJsonObject {
            put("id", "linkset-1")
            put("links", buildJsonArray {
                add(buildJsonObject {
                    put("href", "artifact-1")
                    put("digestMultibase", artifactDigest)
                })
            })
        }
        val linksetDigest = DigestUtils.sha256DigestMultibase(linksetWithDigest)
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("linksetDigest", linksetDigest)
        }
        val digest = DigestUtils.sha256DigestMultibase(vc)
        val payload = buildJsonObject {
            put("digestMultibase", digest)
        }
        val anchorResult = client.writePayload(payload, "application/json")
        val artifacts = mapOf("artifact-1" to artifact)
        
        val result = IntegrityVerifier.verifyIntegrityChain(vc, linksetWithDigest, artifacts, anchorResult.ref, registry)
        
        assertNotNull(result)
    }

    @Test
    fun `test branch verifyIntegrityChain with artifact not found`() = runBlocking {
        val linkset = buildJsonObject {
            put("id", "linkset-1")
            put("links", buildJsonArray {
                add(buildJsonObject {
                    put("href", "nonexistent-artifact")
                    put("digestMultibase", "u-test-digest")
                })
            })
        }
        val linksetDigest = DigestUtils.sha256DigestMultibase(linkset)
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("linksetDigest", linksetDigest)
        }
        val digest = DigestUtils.sha256DigestMultibase(vc)
        val payload = buildJsonObject {
            put("digestMultibase", digest)
        }
        val anchorResult = client.writePayload(payload, "application/json")
        val artifacts = emptyMap<String, JsonObject>()
        
        val result = IntegrityVerifier.verifyIntegrityChain(vc, linkset, artifacts, anchorResult.ref, registry)
        
        assertFalse(result.valid)
        assertTrue(result.steps.any { !it.valid && it.name.contains("Artifact") })
    }

    // ========== discoverAnchorFromEvidence() Branch Coverage ==========

    @Test
    fun `test branch discoverAnchorFromEvidence returns null when no evidence`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
        }
        
        val result = IntegrityVerifier.discoverAnchorFromEvidence(vc)
        
        assertNull(result)
    }

    @Test
    fun `test branch discoverAnchorFromEvidence finds BlockchainAnchorEvidence`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("evidence", buildJsonArray {
                add(buildJsonObject {
                    put("type", "BlockchainAnchorEvidence")
                    put("chainId", chainId)
                    put("txHash", "tx-123")
                })
            })
        }
        
        val result = IntegrityVerifier.discoverAnchorFromEvidence(vc)
        
        assertNotNull(result)
        assertEquals(chainId, result?.chainId)
        assertEquals("tx-123", result?.txHash)
    }

    @Test
    fun `test branch discoverAnchorFromEvidence finds evidence without type`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("evidence", buildJsonArray {
                add(buildJsonObject {
                    put("chainId", chainId)
                    put("txHash", "tx-123")
                })
            })
        }
        
        val result = IntegrityVerifier.discoverAnchorFromEvidence(vc)
        
        assertNotNull(result)
        assertEquals(chainId, result?.chainId)
    }

    @Test
    fun `test branch discoverAnchorFromEvidence includes contract if present`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("evidence", buildJsonArray {
                add(buildJsonObject {
                    put("type", "BlockchainAnchorEvidence")
                    put("chainId", chainId)
                    put("txHash", "tx-123")
                    put("contract", "contract-address")
                })
            })
        }
        
        val result = IntegrityVerifier.discoverAnchorFromEvidence(vc)
        
        assertNotNull(result)
        assertEquals("contract-address", result?.contract)
    }

    @Test
    fun `test branch discoverAnchorFromEvidence returns null when missing chainId`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("evidence", buildJsonArray {
                add(buildJsonObject {
                    put("type", "BlockchainAnchorEvidence")
                    put("txHash", "tx-123")
                })
            })
        }
        
        val result = IntegrityVerifier.discoverAnchorFromEvidence(vc)
        
        assertNull(result)
    }

    @Test
    fun `test branch discoverAnchorFromEvidence returns null when missing txHash`() = runBlocking {
        val vc = buildJsonObject {
            put("id", "cred-1")
            put("evidence", buildJsonArray {
                add(buildJsonObject {
                    put("type", "BlockchainAnchorEvidence")
                    put("chainId", chainId)
                })
            })
        }
        
        val result = IntegrityVerifier.discoverAnchorFromEvidence(vc)
        
        assertNull(result)
    }
}

