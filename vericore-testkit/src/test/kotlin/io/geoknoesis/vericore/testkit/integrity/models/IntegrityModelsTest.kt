package io.geoknoesis.vericore.testkit.integrity.models

import io.geoknoesis.vericore.testkit.integrity.VerificationStep
import io.geoknoesis.vericore.testkit.integrity.IntegrityVerificationResult
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for IntegrityModels.
 */
class IntegrityModelsTest {

    @Test
    fun `test Link with all fields`() {
        val link = Link(
            href = "https://example.com/artifact",
            digestMultibase = "digest-123",
            type = "Dataset",
            rel = "item"
        )
        
        assertEquals("https://example.com/artifact", link.href)
        assertEquals("digest-123", link.digestMultibase)
        assertEquals("Dataset", link.type)
        assertEquals("item", link.rel)
    }

    @Test
    fun `test Link with defaults`() {
        val link = Link(
            href = "https://example.com/artifact",
            digestMultibase = "digest-123"
        )
        
        assertNull(link.type)
        assertNull(link.rel)
    }

    @Test
    fun `test Linkset with all fields`() {
        val links = listOf(
            Link("https://example.com/artifact1", "digest-1"),
            Link("https://example.com/artifact2", "digest-2")
        )
        
        val linkset = Linkset(
            id = "https://example.com/linkset/1",
            digestMultibase = "linkset-digest",
            links = links,
            context = "https://www.w3.org/ns/json-ld#"
        )
        
        assertEquals("https://example.com/linkset/1", linkset.id)
        assertEquals("linkset-digest", linkset.digestMultibase)
        assertEquals(2, linkset.links.size)
        assertEquals("https://www.w3.org/ns/json-ld#", linkset.context)
    }

    @Test
    fun `test Linkset with defaults`() {
        val linkset = Linkset(
            digestMultibase = "digest-123",
            links = emptyList()
        )
        
        assertNull(linkset.id)
        assertEquals("https://www.w3.org/ns/json-ld#", linkset.context)
    }

    @Test
    fun `test BlockchainAnchorEvidence with all fields`() {
        val evidence = BlockchainAnchorEvidence(
            type = "BlockchainAnchorEvidence",
            chainId = "algorand:testnet",
            network = "testnet",
            txHash = "tx-123",
            digestMultibase = "digest-123",
            contract = "app-456"
        )
        
        assertEquals("BlockchainAnchorEvidence", evidence.type)
        assertEquals("algorand:testnet", evidence.chainId)
        assertEquals("testnet", evidence.network)
        assertEquals("tx-123", evidence.txHash)
        assertEquals("digest-123", evidence.digestMultibase)
        assertEquals("app-456", evidence.contract)
    }

    @Test
    fun `test BlockchainAnchorEvidence with defaults`() {
        val evidence = BlockchainAnchorEvidence(
            chainId = "algorand:testnet",
            txHash = "tx-123",
            digestMultibase = "digest-123"
        )
        
        assertEquals("BlockchainAnchorEvidence", evidence.type)
        assertNull(evidence.network)
        assertNull(evidence.contract)
    }

    @Test
    fun `test CredentialStatus with all fields`() {
        val status = CredentialStatus(
            id = "https://example.com/status/1",
            type = "StatusList2021Entry",
            statusPurpose = "revocation",
            statusListIndex = "0"
        )
        
        assertEquals("https://example.com/status/1", status.id)
        assertEquals("StatusList2021Entry", status.type)
        assertEquals("revocation", status.statusPurpose)
        assertEquals("0", status.statusListIndex)
    }

    @Test
    fun `test CredentialStatus with defaults`() {
        val status = CredentialStatus(id = "https://example.com/status/1")
        
        assertEquals("StatusList2021Entry", status.type)
        assertEquals("revocation", status.statusPurpose)
        assertNull(status.statusListIndex)
    }

    @Test
    fun `test AnchorServiceEndpoint`() {
        val endpoint = AnchorServiceEndpoint(
            chainId = "algorand:testnet",
            anchorLookup = "https://algoexplorer.io/tx/{txHash}",
            baseUrl = "https://anchor.example.com"
        )
        
        assertEquals("algorand:testnet", endpoint.chainId)
        assertEquals("https://algoexplorer.io/tx/{txHash}", endpoint.anchorLookup)
        assertEquals("https://anchor.example.com", endpoint.baseUrl)
    }

    @Test
    fun `test AnchorService`() {
        val endpoint = AnchorServiceEndpoint(
            chainId = "algorand:testnet",
            anchorLookup = "https://algoexplorer.io/tx/{txHash}"
        )
        val service = AnchorService(
            id = "#custom-anchor-service",
            type = "AnchorService",
            serviceEndpoint = endpoint
        )
        
        assertEquals("#custom-anchor-service", service.id)
        assertEquals("AnchorService", service.type)
        assertEquals("algorand:testnet", service.serviceEndpoint.chainId)
    }

    @Test
    fun `test AnchorService with defaults`() {
        val endpoint = AnchorServiceEndpoint(chainId = "algorand:testnet")
        val service = AnchorService(serviceEndpoint = endpoint)
        
        assertEquals("#anchor-service", service.id)
        assertEquals("AnchorService", service.type)
    }

    @Test
    fun `test StatusResponse`() {
        val anchorInfo = AnchorInfo(
            chainId = "algorand:testnet",
            txHash = "tx-123",
            digestMultibase = "digest-123"
        )
        val response = StatusResponse(
            revocationStatus = "active",
            anchor = anchorInfo
        )
        
        assertEquals("active", response.revocationStatus)
        assertNotNull(response.anchor)
    }

    @Test
    fun `test StatusResponse with defaults`() {
        val response = StatusResponse(revocationStatus = "active")
        
        assertNull(response.anchor)
    }

    @Test
    fun `test AnchorInfo with all fields`() {
        val anchorInfo = AnchorInfo(
            chainId = "algorand:testnet",
            txHash = "tx-123",
            digestMultibase = "digest-123",
            timestamp = 1234567890L,
            contract = "app-456"
        )
        
        assertEquals("algorand:testnet", anchorInfo.chainId)
        assertEquals("tx-123", anchorInfo.txHash)
        assertEquals("digest-123", anchorInfo.digestMultibase)
        assertEquals(1234567890L, anchorInfo.timestamp)
        assertEquals("app-456", anchorInfo.contract)
    }

    @Test
    fun `test AnchorInfo with defaults`() {
        val anchorInfo = AnchorInfo(
            chainId = "algorand:testnet",
            txHash = "tx-123",
            digestMultibase = "digest-123"
        )
        
        assertNull(anchorInfo.timestamp)
        assertNull(anchorInfo.contract)
    }

    @Test
    fun `test AnchorRegistryEntry`() {
        val entry = AnchorRegistryEntry(
            digestMultibase = "digest-123",
            chainId = "algorand:testnet",
            txHash = "tx-123",
            timestamp = 1234567890L,
            contract = "app-456",
            issuer = "did:key:issuer"
        )
        
        assertEquals("digest-123", entry.digestMultibase)
        assertEquals("algorand:testnet", entry.chainId)
        assertEquals("tx-123", entry.txHash)
        assertEquals(1234567890L, entry.timestamp)
        assertEquals("app-456", entry.contract)
        assertEquals("did:key:issuer", entry.issuer)
    }

    @Test
    fun `test AnchorManifest`() {
        val context = AnchorContext(
            chainId = "algorand:testnet",
            contract = "app-456",
            network = "testnet"
        )
        val manifest = AnchorManifest(
            id = "manifest-1",
            anchorContext = context,
            anchoredDigests = listOf("digest-1", "digest-2"),
            issuer = "did:key:issuer",
            timestamp = 1234567890L
        )
        
        assertEquals("manifest-1", manifest.id)
        assertEquals("algorand:testnet", manifest.anchorContext.chainId)
        assertEquals(2, manifest.anchoredDigests.size)
        assertEquals("did:key:issuer", manifest.issuer)
        assertEquals(1234567890L, manifest.timestamp)
    }

    @Test
    fun `test AnchorContext`() {
        val context = AnchorContext(
            chainId = "algorand:testnet",
            contract = "app-456",
            network = "testnet"
        )
        
        assertEquals("algorand:testnet", context.chainId)
        assertEquals("app-456", context.contract)
        assertEquals("testnet", context.network)
    }

    @Test
    fun `test VerificationStep`() {
        val step = VerificationStep(
            name = "VC Digest",
            valid = true,
            digest = "digest-123",
            error = null
        )
        
        assertEquals("VC Digest", step.name)
        assertTrue(step.valid)
        assertEquals("digest-123", step.digest)
        assertNull(step.error)
    }

    @Test
    fun `test VerificationStep with error`() {
        val step = VerificationStep(
            name = "Artifact",
            valid = false,
            digest = null,
            error = "Artifact not found"
        )
        
        assertFalse(step.valid)
        assertNull(step.digest)
        assertEquals("Artifact not found", step.error)
    }

    @Test
    fun `test IntegrityVerificationResult`() {
        val steps = listOf(
            VerificationStep("Step 1", true, "digest-1"),
            VerificationStep("Step 2", true, "digest-2")
        )
        
        val result = IntegrityVerificationResult(valid = true, steps = steps)
        
        assertTrue(result.valid)
        assertEquals(2, result.steps.size)
    }
}

