package org.trustweave.peerdid

import org.trustweave.core.util.decodeBase58
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.ServiceEndpoint
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec-compliance tests for [PeerDidMethod]:
 * - did:peer:2 service segments must be unpadded base64url of abbreviated JSON (not multibase)
 * - abbreviation expansion (t/s/r/a, dm → DIDCommMessaging)
 * - did:peer:2 `.E` segments (X25519 key agreement) must be parsed, not dropped
 * - did:peer:1 numeric basis must be a multihash (0x12 0x20 + SHA-256)
 */
class PeerDidMethodTest {

    private fun newMethod(config: PeerDidConfig = PeerDidConfig.numalgo2()): PeerDidMethod =
        PeerDidMethod(InMemoryKeyManagementService(), config)

    // ─── did:peer:2 service segment encoding ───

    @Test
    fun `numalgo2 service segment is base64url not multibase`() = runBlocking {
        val document = newMethod().createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
                property("serviceEndpoint", "https://example.com/didcomm")
            }
        )

        val did = document.id.value
        val serviceSegment = did.split(".").first { it.startsWith("S") }.substring(1)

        // Spec: .S<base64url(json)> — must NOT be 'z'-multibase
        assertFalse(serviceSegment.startsWith("z"), "service segment must not be multibase: $serviceSegment")
        val json = Base64.getUrlDecoder().decode(serviceSegment).toString(Charsets.UTF_8)
        assertEquals("""{"t":"dm","s":"https://example.com/didcomm"}""", json)
    }

    @Test
    fun `numalgo2 service round-trips through a fresh resolver instance`() = runBlocking {
        val document = newMethod().createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
                property("serviceEndpoint", "https://example.com/didcomm")
            }
        )

        // Fresh instance: no local cache, document must be derived from the DID string
        val result = newMethod().resolveDid(document.id)
        assertTrue(result is DidResolutionResult.Success)
        val resolved = (result as DidResolutionResult.Success).document

        assertEquals(1, resolved.service.size)
        val service = resolved.service.first()
        assertEquals(listOf("DIDCommMessaging"), service.type)
        assertEquals(ServiceEndpoint.Url("https://example.com/didcomm"), service.serviceEndpoint)
        assertTrue(resolved.authentication.isNotEmpty())
    }

    @Test
    fun `numalgo2 cross-vendor fixture from did-peer spec is parsed`() = runBlocking {
        // Example DID from the did:peer specification (numalgo 2):
        // one X25519 key agreement key, two Ed25519 signing keys, one DIDCommMessaging
        // service with routingKeys and accept.
        val did = Did(
            "did:peer:2" +
                ".Ez6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc" +
                ".Vz6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V" +
                ".Vz6MkgoLTnTypo3tDRwCkZXSccTPHRLhF4ZnjhueYAFpEX6vg" +
                ".SeyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1l" +
                "ZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0"
        )

        val result = newMethod().resolveDid(did)
        assertTrue(result is DidResolutionResult.Success, "spec fixture must resolve: $result")
        val document = (result as DidResolutionResult.Success).document

        // .E segment (X25519) must be a key agreement verification method — not dropped
        assertEquals(1, document.keyAgreement.size, "X25519 .E key must populate keyAgreement")
        assertEquals(2, document.authentication.size)
        assertEquals(3, document.verificationMethod.size)
        val kaVm = document.verificationMethod.first { it.id == document.keyAgreement.first() }
        assertEquals("X25519KeyAgreementKey2020", kaVm.type)

        // Service with expanded abbreviations
        assertEquals(1, document.service.size)
        val service = document.service.first()
        assertEquals(listOf("DIDCommMessaging"), service.type)
        val endpoint = service.serviceEndpoint
        assertTrue(endpoint is ServiceEndpoint.ObjectEndpoint, "routingKeys/accept must survive expansion")
        val data = (endpoint as ServiceEndpoint.ObjectEndpoint).data
        assertEquals("https://example.com/endpoint", data["uri"])
        assertEquals(listOf("did:example:somemediator#somekey"), data["routingKeys"])
        assertEquals(listOf("didcomm/v2", "didcomm/aip2;env=rfc587"), data["accept"])
    }

    @Test
    fun `legacy multibase service segment is still accepted on parse`() = runBlocking {
        // Segment produced by earlier versions of this plugin: 'z' + base58btc(JSON)
        val legacyJson = """{"t":"dm","s":"https://legacy.example/endpoint"}"""
        val legacySegment = "z" + legacyJson.toByteArray(Charsets.UTF_8).encodeBase58()
        val keyMb = "z" + (byteArrayOf(0xed.toByte(), 0x01) + ByteArray(32) { (it + 1).toByte() }).encodeBase58()
        val did = Did("did:peer:2.V$keyMb.S$legacySegment")

        val result = newMethod().resolveDid(did)
        assertTrue(result is DidResolutionResult.Success)
        val document = (result as DidResolutionResult.Success).document

        assertEquals(1, document.service.size)
        assertEquals(
            ServiceEndpoint.Url("https://legacy.example/endpoint"),
            document.service.first().serviceEndpoint
        )
    }

    // ─── did:peer:1 multihash ───

    @Test
    fun `numalgo1 numeric basis is a multihash of the genesis document`() = runBlocking {
        val document = newMethod(PeerDidConfig.numalgo1()).createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        val did = document.id.value
        assertTrue(did.startsWith("did:peer:1z"), "unexpected DID: $did")

        val decoded = did.removePrefix("did:peer:1z").decodeBase58()
        // Multihash header: 0x12 = sha2-256, 0x20 = 32-byte digest
        assertEquals(34, decoded.size, "multihash must be 2-byte header + 32-byte digest")
        assertEquals(0x12.toByte(), decoded[0])
        assertEquals(0x20.toByte(), decoded[1])
    }

    // ─── did:peer:0 (regression) ───

    @Test
    fun `numalgo0 did round-trips through a fresh resolver instance`() = runBlocking {
        val document = newMethod(PeerDidConfig.numalgo0()).createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        assertTrue(document.id.value.startsWith("did:peer:0z"))

        val result = newMethod().resolveDid(document.id)
        assertTrue(result is DidResolutionResult.Success)
        val resolved = (result as DidResolutionResult.Success).document
        assertTrue(resolved.verificationMethod.firstOrNull()?.publicKeyMultibase != null)
    }
}
