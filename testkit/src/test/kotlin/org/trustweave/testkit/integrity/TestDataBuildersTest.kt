package org.trustweave.testkit.integrity

import org.trustweave.did.identifiers.Did
import org.trustweave.testkit.integrity.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for TestDataBuilders.
 */
class TestDataBuildersTest {

    @Test
    fun `test buildVc with all fields`() {
        val subject = buildJsonObject { put("id", "did:key:subject") }
        val evidence = listOf(
            TestDataBuilders.buildAnchorEvidence("algorand:testnet", "tx-123", "digest-123")
        )
        val credentialStatus = TestDataBuilders.buildCredentialStatus("https://example.com/status/1")

        val vc = TestDataBuilders.buildVc(
            issuerDid = Did("did:key:issuer"),
            subject = subject,
            digestMultibase = "digest-123",
            evidence = evidence,
            credentialStatus = credentialStatus,
            vcId = "https://example.com/credentials/1",
            issued = "2024-01-01T00:00:00Z"
        )

        assertEquals("did:key:issuer", vc["issuer"]?.jsonPrimitive?.content)
        assertTrue(vc.containsKey("evidence"))
        assertTrue(vc.containsKey("credentialStatus"))
        assertEquals("https://example.com/credentials/1", vc["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test buildVc with defaults`() {
        val subject = buildJsonObject { put("id", "did:key:subject") }

        val vc = TestDataBuilders.buildVc(
            issuerDid = Did("did:key:issuer"),
            subject = subject,
            digestMultibase = "digest-123"
        )

        assertTrue(vc.containsKey("type"))
        assertTrue(vc.containsKey("issuer"))
        assertTrue(vc.containsKey("credentialSubject"))
        assertTrue(vc.containsKey("issued"))
    }

    @Test
    fun `test buildVc with empty digestMultibase`() {
        val subject = buildJsonObject { put("id", "did:key:subject") }

        val vc = TestDataBuilders.buildVc(
            issuerDid = Did("did:key:issuer"),
            subject = subject,
            digestMultibase = ""
        )

        assertFalse(vc.containsKey("digestMultibase"))
    }

    @Test
    fun `test buildLinkset`() {
        val links = listOf(
            TestDataBuilders.buildLink("https://example.com/artifact1", "digest-1"),
            TestDataBuilders.buildLink("https://example.com/artifact2", "digest-2", "Dataset", "item")
        )

        val linkset = TestDataBuilders.buildLinkset("linkset-digest", links, "https://example.com/linkset/1")

        assertEquals("https://example.com/linkset/1", linkset["id"]?.jsonPrimitive?.content)
        assertEquals("linkset-digest", linkset["digestMultibase"]?.jsonPrimitive?.content)
        assertTrue(linkset.containsKey("links"))
    }

    @Test
    fun `test buildLink`() {
        val link = TestDataBuilders.buildLink(
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
    fun `test buildLink with defaults`() {
        val link = TestDataBuilders.buildLink("https://example.com/artifact", "digest-123")

        assertNull(link.type)
        assertNull(link.rel)
    }

    @Test
    fun `test buildArtifact`() {
        val content = buildJsonObject { put("title", "Test Dataset") }

        val artifact = TestDataBuilders.buildArtifact(
            id = "artifact-1",
            type = "Metadata",
            content = content,
            digestMultibase = "digest-123",
            mediaType = "application/json"
        )

        assertEquals("artifact-1", artifact["id"]?.jsonPrimitive?.content)
        assertEquals("Metadata", artifact["type"]?.jsonPrimitive?.content)
        assertEquals("digest-123", artifact["digestMultibase"]?.jsonPrimitive?.content)
        assertEquals("application/json", artifact["mediaType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test buildAnchorEvidence`() {
        val evidence = TestDataBuilders.buildAnchorEvidence(
            chainId = "algorand:testnet",
            txHash = "tx-123",
            digestMultibase = "digest-123",
            network = "testnet",
            contract = "app-456"
        )

        assertEquals("algorand:testnet", evidence.chainId)
        assertEquals("tx-123", evidence.txHash)
        assertEquals("digest-123", evidence.digestMultibase)
        assertEquals("testnet", evidence.network)
        assertEquals("app-456", evidence.contract)
    }

    @Test
    fun `test buildCredentialStatus`() {
        val status = TestDataBuilders.buildCredentialStatus(
            statusServiceUrl = "https://example.com/status/1",
            statusListIndex = "0"
        )

        assertEquals("https://example.com/status/1", status.id)
        assertEquals("StatusList2021Entry", status.type)
        assertEquals("revocation", status.statusPurpose)
        assertEquals("0", status.statusListIndex)
    }

    @Test
    fun `test buildAnchorService`() {
        val service = TestDataBuilders.buildAnchorService(
            chainId = "algorand:testnet",
            anchorLookupPattern = "https://algoexplorer.io/tx/{txHash}",
            baseUrl = "https://anchor.example.com"
        )

        assertEquals("#anchor-service", service.id)
        assertEquals("AnchorService", service.type)
        assertEquals("algorand:testnet", service.serviceEndpoint.chainId)
        assertEquals("https://algoexplorer.io/tx/{txHash}", service.serviceEndpoint.anchorLookup)
    }

    @Test
    fun `test buildStatusResponse`() {
        val anchorInfo = TestDataBuilders.buildAnchorInfo(
            chainId = "algorand:testnet",
            txHash = "tx-123",
            digestMultibase = "digest-123"
        )

        val response = TestDataBuilders.buildStatusResponse("active", anchorInfo)

        assertEquals("active", response.revocationStatus)
        assertNotNull(response.anchor)
        assertEquals("algorand:testnet", response.anchor?.chainId)
    }

    @Test
    fun `test buildAnchorInfo`() {
        val anchorInfo = TestDataBuilders.buildAnchorInfo(
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
    fun `test buildAnchorManifest`() {
        val manifest = TestDataBuilders.buildAnchorManifest(
            manifestId = "manifest-1",
            chainId = "algorand:testnet",
            anchoredDigests = listOf("digest-1", "digest-2", "digest-3"),
            contract = "app-456",
            issuer = "did:key:issuer"
        )

        assertEquals("manifest-1", manifest.id)
        assertEquals("algorand:testnet", manifest.anchorContext.chainId)
        assertEquals(3, manifest.anchoredDigests.size)
        assertEquals("did:key:issuer", manifest.issuer)
        assertNotNull(manifest.timestamp)
    }

    @Test
    fun `test createMetadataArtifact`() {
        val (artifact, digest) = TestDataBuilders.createMetadataArtifact(
            id = "metadata-1",
            title = "Test Dataset",
            description = "A test dataset"
        )

        assertEquals("metadata-1", artifact["id"]?.jsonPrimitive?.content)
        assertEquals("Metadata", artifact["type"]?.jsonPrimitive?.content)
        assertNotNull(digest)
        assertTrue(digest.startsWith("u"))
    }

    @Test
    fun `test createProvenanceArtifact`() {
        val (artifact, digest) = TestDataBuilders.createProvenanceArtifact(
            id = "provenance-1",
            activity = "Data Collection",
            agent = Did("did:key:agent")
        )

        assertEquals("provenance-1", artifact["id"]?.jsonPrimitive?.content)
        assertEquals("Provenance", artifact["type"]?.jsonPrimitive?.content)
        assertNotNull(digest)
    }

    @Test
    fun `test createQualityReportArtifact`() {
        val metrics = mapOf("accuracy" to 0.95, "completeness" to 0.98)
        val (artifact, digest) = TestDataBuilders.createQualityReportArtifact(
            id = "quality-1",
            qualityScore = 0.96,
            metrics = metrics
        )

        assertEquals("quality-1", artifact["id"]?.jsonPrimitive?.content)
        assertEquals("QualityReport", artifact["type"]?.jsonPrimitive?.content)
        assertNotNull(digest)
    }
}



