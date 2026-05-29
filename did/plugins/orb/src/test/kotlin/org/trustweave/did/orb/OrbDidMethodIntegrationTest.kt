package org.trustweave.did.orb

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Integration tests that exercise the real Orb Sidetree wire protocol.
 *
 * These tests are gated on the `ORB_BASE_URL` environment variable. Spin up an
 * Orb sandbox (see `README.md`) before running:
 *
 * ```bash
 * export ORB_BASE_URL=https://localhost:48326
 * ./gradlew :did:plugins:orb:test --tests "*Integration*"
 * ```
 *
 * The tests round-trip a create + resolve + update + deactivate.
 */
@EnabledIfEnvironmentVariable(named = "ORB_BASE_URL", matches = ".+")
class OrbDidMethodIntegrationTest {

    private val baseUrl = System.getenv("ORB_BASE_URL")

    @Test
    fun `create resolve update deactivate against a real Orb node`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val cfg = OrbDidConfig(
            baseUrl = baseUrl,
            authHeader = System.getenv("ORB_AUTH_HEADER")?.let { headerLine ->
                val (name, value) = headerLine.split(":", limit = 2).map { it.trim() }
                name to value
            },
            timeoutSeconds = 30L,
        )
        val method = OrbDidMethod(kms, cfg)

        // ── create
        val document = method.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 })
        assertTrue(document.id.value.startsWith("did:orb:"))

        // ── resolve (long-form should be deterministically resolvable)
        val resolved = method.resolveDid(document.id)
        assertIs<DidResolutionResult.Success>(resolved)
        assertEquals(document.id.value, resolved.document.id.value)

        // ── update (no-op updater just to exercise the wire path)
        val updated = method.updateDid(document.id) { it }
        assertEquals(document.id.value, updated.id.value)

        // ── deactivate
        val deactivated = method.deactivateDid(document.id)
        assertTrue(deactivated, "Orb deactivate operation should be accepted")
    }
}
