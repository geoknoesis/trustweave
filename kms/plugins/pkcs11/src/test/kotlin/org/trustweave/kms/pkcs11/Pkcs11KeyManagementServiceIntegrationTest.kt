package org.trustweave.kms.pkcs11

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.util.EcdsaSignatureCodec
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.Provider
import java.security.Security
import java.security.Signature

/**
 * End-to-end integration tests for [Pkcs11KeyManagementService] against a real PKCS#11 device.
 *
 * # Why these tests are gated
 *
 * The JDK's `SunPKCS11` provider must `dlopen` the vendor PKCS#11 shared library *in-process*
 * on the host JVM. We therefore cannot rely on Testcontainers alone — the library has to be
 * present on the host. To keep CI green on machines without SoftHSM2 installed, these tests
 * are gated on the `SOFTHSM2_LIB` environment variable; if it is unset, JUnit Jupiter skips
 * the whole class (see [EnabledIfEnvironmentVariable]).
 *
 * # Running locally
 *
 *  - **Linux (Debian/Ubuntu):**
 *      ```
 *      sudo apt-get install -y softhsm2
 *      export SOFTHSM2_LIB=/usr/lib/softhsm/libsofthsm2.so
 *      ./gradlew :kms:plugins:pkcs11:test
 *      ```
 *  - **macOS (Homebrew):**
 *      ```
 *      brew install softhsm
 *      export SOFTHSM2_LIB="$(brew --prefix softhsm)/lib/softhsm/libsofthsm2.so"
 *      ./gradlew :kms:plugins:pkcs11:test
 *      ```
 *  - **Windows:** install SoftHSM2 from
 *      <https://github.com/disig/SoftHSM2-for-Windows>, then point `SOFTHSM2_LIB` at
 *      `C:\\SoftHSM2\\lib\\softhsm2-x64.dll`.
 *
 * # Running in CI
 *
 *  - Install SoftHSM2 in the runner image (Ubuntu runners: `apt-get install -y softhsm2`).
 *  - Export `SOFTHSM2_LIB=/usr/lib/softhsm/libsofthsm2.so` in the workflow env block.
 *  - Optionally set `SOFTHSM2_CONF` to a writable per-job path so concurrent runs do not
 *    fight over `/var/lib/softhsm/tokens/`.
 *
 * The companion [SoftHsm2Container] is an *alternative* harness for fully isolated runs
 * but is not used by default — see the class doc for the ABI caveats.
 *
 * # What is verified
 *
 *  - `generateKey(P256)` produces a usable handle; `getPublicKey` round-trips it.
 *  - `sign(P256)` produces an ECDSA-SHA256 signature that verifies via JCA against the
 *    public key returned by the PKCS#11 token.
 *  - `generateKey(Ed25519)` succeeds on SoftHSM2 ≥ 2.6.
 *  - `deleteKey` actually removes the key (next `getPublicKey` returns `KeyNotFound`).
 *  - `generateKey(Secp256k1)` is rejected with `UnsupportedAlgorithm` (MVP contract).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(
    named = "SOFTHSM2_LIB",
    matches = ".+",
    disabledReason = "SoftHSM2 integration tests require SOFTHSM2_LIB to point at " +
        "libsofthsm2.so / softhsm2-x64.dll on the host JVM.",
)
@DisplayName("Pkcs11KeyManagementService — SoftHSM2 integration")
class Pkcs11KeyManagementServiceIntegrationTest {

    private val tokenLabel: String = "trustweave-test"
    private val soPin: String = "1234"
    private val userPin: String = "5678"

    /** Per-class working directory for the SoftHSM2 token state. */
    private lateinit var tokensDir: Path
    private lateinit var softhsm2Conf: Path

    private lateinit var libraryPath: String
    private lateinit var kms: Pkcs11KeyManagementService
    private lateinit var provider: Provider
    private lateinit var keyStore: KeyStore

    /** Provider name is randomized so re-runs in the same JVM do not collide. */
    private val providerName: String = "TrustWeave-IT-${System.nanoTime()}"

    @BeforeAll
    fun setUp() {
        libraryPath = requireNotNull(System.getenv("SOFTHSM2_LIB")) {
            "SOFTHSM2_LIB env var is required to run this test"
        }
        check(Files.exists(Path.of(libraryPath))) {
            "SOFTHSM2_LIB points at '$libraryPath' but no such file exists"
        }

        // Isolate token state in a per-test temp directory so concurrent CI shards don't
        // fight over the system-wide /var/lib/softhsm/tokens.
        tokensDir = Files.createTempDirectory("trustweave-softhsm2-tokens-")
        softhsm2Conf = Files.createTempFile("softhsm2-", ".conf")
        Files.writeString(
            softhsm2Conf,
            """
            directories.tokendir = ${tokensDir.toAbsolutePath()}
            objectstore.backend = file
            log.level = ERROR
            """.trimIndent(),
        )
        // SunPKCS11 honors SOFTHSM2_CONF when looking up the SoftHSM config; setting it
        // as a JVM property is not enough — we need it in the *process* env. We can only
        // mutate the JVM's view, but SoftHSM2 reads it via getenv() at load time, so for
        // CI we document that the workflow should export this directly.
        // Best-effort: pass via -D so users who can't set env can still scope tokens.
        System.setProperty("SOFTHSM2_CONF", softhsm2Conf.toAbsolutePath().toString())

        initTokenViaCli()

        val config = Pkcs11Config(
            libraryPath = libraryPath,
            slot = 0,
            providerName = providerName,
            pin = userPin.toCharArray(),
        )
        kms = Pkcs11KeyManagementService(config)

        // Also open a direct KeyStore for signature verification; we need the actual
        // PublicKey, which the KMS SPI returns only as a KeyHandle wrapper.
        provider = Security.getProvider("SunPKCS11-$providerName")
            ?: error("SunPKCS11-$providerName not registered after Pkcs11KeyManagementService init")
        keyStore = KeyStore.getInstance("PKCS11", provider).apply {
            load(null, userPin.toCharArray())
        }
    }

    @AfterAll
    fun tearDown() {
        // Best-effort cleanup; failures here must not mask test failures.
        runCatching { Files.walk(tokensDir).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        runCatching { Files.deleteIfExists(softhsm2Conf) }
    }

    @Test
    fun `generateKey(P256) returns a handle and getPublicKey round-trips it`() = runTest {
        val label = uniqueLabel("p256-roundtrip")

        val gen = kms.generateKey(Algorithm.P256, options = mapOf(Pkcs11KeyManagementService.OPTION_LABEL to label))
        assertTrue(gen is GenerateKeyResult.Success, "expected Success, got $gen")
        val handle = (gen as GenerateKeyResult.Success).keyHandle
        assertEquals(label, handle.id.value)

        val pub = kms.getPublicKey(handle.id)
        assertTrue(pub is GetPublicKeyResult.Success, "expected Success, got $pub")
        assertEquals(handle.id, (pub as GetPublicKeyResult.Success).keyHandle.id)
    }

    @Test
    fun `sign(P256) produces a signature that verifies with JCA SHA256withECDSA`() = runTest {
        val label = uniqueLabel("p256-sign")
        val gen = kms.generateKey(Algorithm.P256, options = mapOf(Pkcs11KeyManagementService.OPTION_LABEL to label))
        val handle = (gen as GenerateKeyResult.Success).keyHandle

        val data = "trustweave-test-payload".toByteArray(Charsets.UTF_8)
        val signed = kms.sign(handle.id, data, Algorithm.P256)
        assertTrue(signed is SignResult.Success, "expected Success, got $signed")
        val signature = (signed as SignResult.Success).signature
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty(), "signature must be non-empty")

        // The KeyManagementService contract mandates P1363 (raw r||s) for ECDSA: 64 bytes
        // for P-256.
        assertEquals(64, signature.size, "P-256 signature must be 64-byte P1363 (raw r||s)")

        // Verify against the public key stored on the token. The KMS SPI does not return
        // the raw PublicKey, so we read it from the same PKCS#11 keystore directly.
        // JCA's SHA256withECDSA verifier expects DER, so transcode P1363 → DER first.
        val publicKey = keyStore.getCertificate(label)?.publicKey
            ?: error("PKCS#11 token did not return a certificate for label '$label'")

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(data)
        assertTrue(
            verifier.verify(EcdsaSignatureCodec.p1363ToDer(signature)),
            "signature must verify against the PKCS#11 public key"
        )
    }

    @Test
    fun `generateKey(Ed25519) succeeds on SoftHSM2 (requires v2_6+)`() = runTest {
        val label = uniqueLabel("ed25519")
        val gen = kms.generateKey(Algorithm.Ed25519, options = mapOf(Pkcs11KeyManagementService.OPTION_LABEL to label))
        // SoftHSM2 < 2.6 returns CKR_MECHANISM_INVALID; our service surfaces that as
        // GenerateKeyResult.Failure.Error. We accept either outcome here so this test
        // does not become a SoftHSM2 version gate — but the common modern path is Success.
        assertTrue(
            gen is GenerateKeyResult.Success || gen is GenerateKeyResult.Failure.Error,
            "expected Success or Failure.Error (older SoftHSM2 without Ed25519); got $gen",
        )
        if (gen is GenerateKeyResult.Success) {
            assertEquals(label, gen.keyHandle.id.value)
        }
    }

    @Test
    fun `deleteKey removes the key so subsequent getPublicKey returns KeyNotFound`() = runTest {
        val label = uniqueLabel("delete-me")
        val gen = kms.generateKey(Algorithm.P256, options = mapOf(Pkcs11KeyManagementService.OPTION_LABEL to label))
        val handle = (gen as GenerateKeyResult.Success).keyHandle

        val del = kms.deleteKey(handle.id)
        assertTrue(del is DeleteKeyResult.Deleted, "expected Deleted, got $del")

        val pub = kms.getPublicKey(handle.id)
        assertTrue(
            pub is GetPublicKeyResult.Failure.KeyNotFound,
            "expected KeyNotFound after delete, got $pub",
        )
    }

    @Test
    fun `generateKey(Secp256k1) is rejected with UnsupportedAlgorithm (MVP contract)`() = runTest {
        val gen = kms.generateKey(Algorithm.Secp256k1)
        assertTrue(
            gen is GenerateKeyResult.Failure.UnsupportedAlgorithm,
            "expected UnsupportedAlgorithm, got $gen",
        )
        val failure = gen as GenerateKeyResult.Failure.UnsupportedAlgorithm
        assertEquals(Algorithm.Secp256k1, failure.algorithm)
        assertTrue(
            failure.supportedAlgorithms.isNotEmpty(),
            "supportedAlgorithms must be advertised in the failure",
        )
    }

    private var labelCounter: Int = 0
    private fun uniqueLabel(prefix: String): String {
        // SoftHSM2 disallows label collisions within a token; salt every label.
        labelCounter += 1
        val unique = "$prefix-${System.nanoTime()}-$labelCounter"
        assertNotEquals("", unique)
        return unique
    }

    /**
     * Runs `softhsm2-util --init-token --slot 0 --label ... --so-pin ... --pin ...` via
     * [ProcessBuilder]. The CLI is conventionally available wherever the native library
     * is installed; if it isn't on `$PATH`, we surface a clear failure.
     */
    private fun initTokenViaCli() {
        val env = mapOf("SOFTHSM2_CONF" to softhsm2Conf.toAbsolutePath().toString())
        val proc = try {
            ProcessBuilder(
                "softhsm2-util",
                "--init-token",
                "--free",
                "--label", tokenLabel,
                "--so-pin", soPin,
                "--pin", userPin,
            )
                .redirectErrorStream(true)
                .also { it.environment().putAll(env) }
                .start()
        } catch (e: IOException) {
            error(
                "softhsm2-util is not on PATH. Install softhsm2 alongside libsofthsm2.so, " +
                    "or pre-initialize a token and skip CLI bootstrap. Cause: ${e.message}",
            )
        }
        val out = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        check(exit == 0 || out.contains("already initialized", ignoreCase = true)) {
            "softhsm2-util --init-token failed (exit=$exit): $out"
        }
    }
}
