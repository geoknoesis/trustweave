package org.trustweave.kms.pkcs11

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult

/**
 * Error-path tests for [Pkcs11KeyManagementService].
 *
 * **Note:** End-to-end testing of the PKCS#11 KMS requires SoftHSM2 in CI
 * (see `docs/architecture/eidas-qes-design.md` §9). These tests only verify that the
 * service fails *cleanly* when the underlying PKCS#11 library cannot be loaded — either
 * by throwing a documented [Pkcs11Exception] at construction (preferred, fail-fast) or by
 * surfacing the failure through the sealed Result types on first call. In neither case may
 * an unrelated raw exception escape the SPI boundary.
 *
 * The contract verified here:
 *  1. Construction with a non-existent library either throws a [Pkcs11Exception] or succeeds
 *     (depending on platform — some JDKs lazily resolve the library on first keystore op).
 *  2. If construction succeeds, every SPI call returns the appropriate `Failure.Error` variant
 *     of its sealed Result class. No raw [Exception] escapes.
 *
 * SoftHSM2-backed end-to-end coverage (key generation, signing, verify, delete) is intentionally
 * out of scope for the MVP unit tests and will live in an integration test suite gated on the
 * `softhsm2` CI label.
 */
class Pkcs11KeyManagementServiceErrorTest {

    /** A library path guaranteed not to exist on any reasonable test machine. */
    private val bogusLibrary: String =
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            "C:\\does-not-exist\\trustweave-test-no-such-pkcs11.dll"
        } else {
            "/nonexistent/trustweave-test-no-such-pkcs11.so"
        }

    private fun configWithBogusLibrary(): Pkcs11Config = Pkcs11Config(
        libraryPath = bogusLibrary,
        slot = 0,
        providerName = "TrustWeave-Test-${System.nanoTime()}",
    )

    /**
     * Tries to construct the service. Returns the service if construction succeeded, or `null`
     * if construction failed *cleanly* with a [Pkcs11Exception]. Any other exception is a
     * contract violation and the test fails.
     */
    private fun tryConstruct(): Pkcs11KeyManagementService? {
        return try {
            Pkcs11KeyManagementService(configWithBogusLibrary())
        } catch (e: Pkcs11Exception) {
            // Fail-fast path — the documented exception type.
            assertNotNull(e.message, "Pkcs11Exception must carry a message")
            null
        } catch (e: Throwable) {
            fail<Nothing>(
                "Construction with a non-existent PKCS#11 library must either succeed or throw " +
                    "Pkcs11Exception, but threw ${e.javaClass.name}: ${e.message}",
            )
        }
    }

    @Test
    fun `construction with missing library either fails fast or defers cleanly`() {
        // Either branch is acceptable; tryConstruct() asserts the failure type if construction
        // does fail. If construction succeeds, the other tests in this class verify call-time
        // behavior.
        tryConstruct()
    }

    @Test
    fun `generateKey surfaces failure via Result types when provider cannot be loaded`() = runTest {
        val kms = tryConstruct() ?: return@runTest // construction already failed cleanly
        val result = kms.generateKey(Algorithm.P256)
        assertTrue(
            result is GenerateKeyResult.Failure,
            "expected GenerateKeyResult.Failure when PKCS#11 library is missing, got $result",
        )
    }

    @Test
    fun `getPublicKey surfaces failure via Result types when provider cannot be loaded`() = runTest {
        val kms = tryConstruct() ?: return@runTest
        val result = kms.getPublicKey(KeyId("any-label"))
        assertTrue(
            result is GetPublicKeyResult.Failure,
            "expected GetPublicKeyResult.Failure when PKCS#11 library is missing, got $result",
        )
    }

    @Test
    fun `sign surfaces failure via Result types when provider cannot be loaded`() = runTest {
        val kms = tryConstruct() ?: return@runTest
        val result = kms.sign(KeyId("any-label"), byteArrayOf(1, 2, 3))
        assertTrue(
            result is SignResult.Failure,
            "expected SignResult.Failure when PKCS#11 library is missing, got $result",
        )
    }

    @Test
    fun `deleteKey surfaces failure via Result types when provider cannot be loaded`() = runTest {
        val kms = tryConstruct() ?: return@runTest
        val result = kms.deleteKey(KeyId("any-label"))
        // Either NotFound (if the keystore happens to be empty but loadable) or Failure.Error
        // are acceptable; the contract is "no raw exception escapes".
        assertTrue(
            result is DeleteKeyResult.NotFound || result is DeleteKeyResult.Failure,
            "expected DeleteKeyResult.NotFound or Failure when PKCS#11 library is missing, got $result",
        )
    }

    @Test
    fun `provider create rejects missing libraryPath option`() {
        val provider = Pkcs11KeyManagementServiceProvider()
        val ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            provider.create(emptyMap())
        }
        assertTrue(
            ex.message!!.contains("libraryPath"),
            "error message must reference 'libraryPath'; was: ${ex.message}",
        )
    }
}
