package org.trustweave.hashicorpkms

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.HexFormat
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** An isolated real Transit service; this does not certify production custody or Vault HA. */
class VaultTransitLifecycleTest {
    private class VaultContainer(image: DockerImageName) : GenericContainer<VaultContainer>(image)

    @Test
    fun `create retrieve sign reject and reopen against real Transit`() = runBlocking {
        val container = VaultContainer(DockerImageName.parse(
            "hashicorp/vault@sha256:5520cc26271c024e6ffa45cdf95255bd26b70d71ba4b7e0bc18925bef4128adb",
        )).withExposedPorts(8200)
            .withEnv("VAULT_DEV_ROOT_TOKEN_ID", "isolated-transit-test")
            .withCommand("server", "-dev", "-dev-listen-address=0.0.0.0:8200")
            .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(200))
        container.start()
        try {
            val config = VaultKmsConfig("http://${container.host}:${container.getMappedPort(8200)}", token = "isolated-transit-test")
            VaultKmsClientFactory.createClient(config).logical().write("sys/mounts/transit", mapOf("type" to "transit"))
            val kms = VaultKeyManagementService(config)
            val key = assertIs<GenerateKeyResult.Success>(kms.generateKey(Algorithm.Ed25519, emptyMap())).keyHandle
            assertTrue(Regex("transit/keys/ed25519-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                .matches(key.id.value), "Generated names must retain the complete UUID")
            val publicKey = assertIs<GetPublicKeyResult.Success>(kms.getPublicKey(key.id)).keyHandle
            assertEquals(key.publicKeyJwk, publicKey.publicKeyJwk)
            val message = "independent-transit-signature-check".toByteArray()
            val signature = assertIs<SignResult.Success>(kms.sign(key.id, message, Algorithm.Ed25519)).signature
            val raw = Base64.getUrlDecoder().decode(requireNotNull(key.publicKeyJwk)["x"] as String)
            val javaKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(
                HexFormat.of().parseHex("302a300506032b6570032100") + raw,
            ))
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(javaKey); verifier.update(message)
            assertTrue(verifier.verify(signature))
            verifier.initVerify(javaKey); verifier.update("altered".toByteArray())
            assertFalse(verifier.verify(signature))
            assertIs<SignResult.Failure.UnsupportedAlgorithm>(kms.sign(key.id, message, Algorithm.P256))
            kms.close()
            val reopened = VaultKeyManagementService(config)
            assertEquals(key.publicKeyJwk, assertIs<GetPublicKeyResult.Success>(reopened.getPublicKey(key.id)).keyHandle.publicKeyJwk)
            assertIs<SignResult.Success>(reopened.sign(key.id, message, Algorithm.Ed25519))
            reopened.close()
            val denied = VaultKeyManagementService(config.copy(token = "invalid-test-token"))
            assertIs<SignResult.Failure>(denied.sign(key.id, message, Algorithm.Ed25519))
            denied.close()
        } finally { container.stop() }
    }
}
