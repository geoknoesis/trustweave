package org.trustweave.trust.dsl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.getOrThrowDid
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for DidDsl.kt
 */
class DidDslTest {
    private lateinit var trustWeave: org.trustweave.trust.TrustWeave
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setup() =
        runBlocking {
            kms = InMemoryKeyManagementService()
            val kmsInstance = kms
            trustWeave =
                org.trustweave.trust.TrustWeave.build {
                    // DID methods auto-discovered via SPI
                    keys {
                        custom(kmsInstance)
                        signer { data, keyId ->
                            when (
                                val result =
                                    kmsInstance.sign(
                                        org.trustweave.core.identifiers
                                            .KeyId(keyId),
                                        data,
                                    )
                            ) {
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
        }

    @Test
    fun `test createDid with method and algorithm`() =
        runBlocking<Unit> {
            val did =
                trustWeave
                    .createDid {
                        method("key")
                        algorithm("Ed25519")
                    }.getOrThrowDid()

            assertTrue(did.value.startsWith("did:key:"), "DID should start with did:key:")
            assertNotNull(did)
        }

    @Test
    fun `test createDid without method falls back to the configured default`() =
        runBlocking<Unit> {
            // DidBuilder resolves "explicit method, or config's default, or first registered method".
            // This fixture configures did { method("key") }, so omitting method() must yield a did:key.
            val did =
                trustWeave
                    .createDid {
                        algorithm("Ed25519")
                    }.getOrThrowDid()

            assertTrue(
                did.value.startsWith("did:key:"),
                "Omitting method() must fall back to the configured default, got: ${'$'}{did.value}",
            )
        }

    @Test
    fun `test createDid with unconfigured method returns MethodNotRegistered`() =
        runBlocking<Unit> {
            val result =
                trustWeave.createDid {
                    method("web")
                    algorithm("Ed25519")
                }

            assertTrue(
                result is DidCreationResult.Failure.MethodNotRegistered,
                "An unconfigured method must yield MethodNotRegistered, got: $result",
            )
        }

    @Test
    fun `test createDid with custom options`() =
        runBlocking<Unit> {
            val did =
                trustWeave
                    .createDid {
                        method("key")
                        algorithm("Ed25519")
                        option("custom", "value")
                    }.getOrThrowDid()

            assertNotNull(did)
            assertTrue(did.value.startsWith("did:key:"))
        }

    @Test
    fun `test createDid via TrustWeaveContext`() =
        runBlocking<Unit> {
            val did =
                trustWeave
                    .createDid {
                        method("key")
                        algorithm("Ed25519")
                    }.getOrThrowDid()

            assertNotNull(did)
            assertTrue(did.value.startsWith("did:key:"))
        }

    @Test
    fun `test createDid with different algorithms`() =
        runBlocking<Unit> {
            val did1 =
                trustWeave
                    .createDid {
                        method("key")
                        algorithm("Ed25519")
                    }.getOrThrowDid()

            // Create second DID - even with same algorithm, should produce different DID
            // since it generates a new key
            val did2 =
                trustWeave
                    .createDid {
                        method("key")
                        algorithm("Ed25519")
                    }.getOrThrowDid()

            assertNotNull(did1)
            assertNotNull(did2)
            assertNotEquals(did1, did2, "Different key generations should produce different DIDs")
        }

    @Test
    fun `test createDid extracts DID from document`() =
        runBlocking<Unit> {
            val did =
                trustWeave
                    .createDid {
                        method("key")
                        algorithm("Ed25519")
                    }.getOrThrowDid()

            // Verify DID format
            assertTrue(did.value.startsWith("did:key:"))
            assertTrue(did.value.length > 10) // Should have some identifier
        }
}
