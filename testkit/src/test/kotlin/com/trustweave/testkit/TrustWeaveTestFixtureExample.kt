package com.trustweave.testkit

import com.trustweave.anchor.*
import com.trustweave.json.DigestUtils
import com.trustweave.testkit.integrity.TestDataBuilders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Example test demonstrating the use of TrustWeaveTestFixture.
 * 
 * This test shows how to use the consolidated test utilities to create
 * comprehensive test scenarios with minimal boilerplate.
 */
class TrustWeaveTestFixtureExample {

    @Test
    fun `test using TrustWeaveTestFixture minimal setup`() = runBlocking {
        // Use minimal fixture with defaults
        TrustWeaveTestFixture.minimal().use { fixture ->
            // Create issuer DID
            val issuerDoc = fixture.createIssuerDid()
            assertNotNull(issuerDoc.id)
            
            // Get blockchain client
            val anchorClient = fixture.getBlockchainClient("algorand:testnet")
            assertNotNull(anchorClient)
            
            // Create and anchor payload
            val payload = buildJsonObject {
                put("test", "data")
            }
            val result = anchorClient.writePayload(payload)
            assertNotNull(result.ref.txHash)
        }
        // Automatic cleanup via use {}
    }

    @Test
    fun `test using TrustWeaveTestFixture builder pattern`() = runBlocking {
        // Use builder for custom configuration
        TrustWeaveTestFixture.builder()
            .withInMemoryBlockchainClient("algorand:testnet")
            .withInMemoryBlockchainClient("polygon:mumbai")
            .build()
            .use { fixture ->
                val issuerDoc = fixture.createIssuerDid()
                
                // Test multiple chains
                val algoClient = fixture.getBlockchainClient("algorand:testnet")
                val polygonClient = fixture.getBlockchainClient("polygon:mumbai")
                
                assertNotNull(algoClient)
                assertNotNull(polygonClient)
                
                // Anchor to both chains
                val payload = buildJsonObject { put("test", "multi-chain") }
                val algoResult = algoClient.writePayload(payload)
                val polygonResult = polygonClient.writePayload(payload)
                
                assertNotNull(algoResult.ref.txHash)
                assertNotNull(polygonResult.ref.txHash)
            }
    }

    @Test
    fun `test EO workflow with TrustWeaveTestFixture`() = runBlocking {
        TrustWeaveTestFixture.builder()
            .withInMemoryBlockchainClient("algorand:testnet")
            .build()
            .use { fixture ->
                val issuerDoc = fixture.createIssuerDid()
                val anchorClient = fixture.getBlockchainClient("algorand:testnet")!!
                
                // Create EO artifacts using TestDataBuilders
                val (metadataArtifact, metadataDigest) = TestDataBuilders.createMetadataArtifact(
                    id = "metadata-1",
                    title = "Test Dataset",
                    description = "A test dataset"
                )
                
                val (provenanceArtifact, provenanceDigest) = TestDataBuilders.createProvenanceArtifact(
                    id = "provenance-1",
                    activity = "data-collection",
                    agent = issuerDoc.id
                )
                
                // Create Linkset
                val links = listOf(
                    TestDataBuilders.buildLink(
                        href = "metadata-1",
                        digestMultibase = metadataDigest
                    ),
                    TestDataBuilders.buildLink(
                        href = "provenance-1",
                        digestMultibase = provenanceDigest
                    )
                )
                val linksetDigest = DigestUtils.sha256DigestMultibase(
                    TestDataBuilders.buildLinkset("", links)
                )
                val linkset = TestDataBuilders.buildLinkset(linksetDigest, links)
                
                // Create VC
                val vcSubject = buildJsonObject {
                    put("linkset", linkset)
                }
                val vc = TestDataBuilders.buildVc(
                    issuerDid = issuerDoc.id,
                    subject = vcSubject,
                    digestMultibase = DigestUtils.sha256DigestMultibase(vcSubject)
                )
                
                // Anchor VC digest
                val vcDigest = DigestUtils.sha256DigestMultibase(vc)
                val digestPayload = buildJsonObject {
                    put("vcDigest", vcDigest)
                }
                val anchorResult = anchorClient.writePayload(digestPayload)
                
                assertNotNull(anchorResult.ref.txHash)
                assertTrue(anchorResult.ref.txHash.isNotEmpty())
            }
    }
}

