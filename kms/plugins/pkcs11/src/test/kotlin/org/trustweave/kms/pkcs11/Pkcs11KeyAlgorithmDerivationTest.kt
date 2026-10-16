package org.trustweave.kms.pkcs11

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.kms.Algorithm
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Key-algorithm derivation, sign planning, provider reuse and PIN hygiene; none of it needs an HSM. */
class Pkcs11KeyAlgorithmDerivationTest {
    private fun ec(curve: String) = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(curve)) }.generateKeyPair()

    @Test
    fun `EC curve is derived from the key parameters`() {
        assertEquals(Algorithm.P256, Pkcs11KeyManagementService.deriveKeyAlgorithm(ec("secp256r1").private))
        assertEquals(Algorithm.P384, Pkcs11KeyManagementService.deriveKeyAlgorithm(ec("secp384r1").public))
        assertEquals(Algorithm.P521, Pkcs11KeyManagementService.deriveKeyAlgorithm(ec("secp521r1").private))
        val k1 =
            KeyPairGenerator
                .getInstance(
                    "EC",
                    BouncyCastleProvider(),
                ).apply { initialize(ECGenParameterSpec("secp256k1")) }
                .generateKeyPair()
        assertEquals(Algorithm.Secp256k1, Pkcs11KeyManagementService.deriveKeyAlgorithm(k1.public))
    }

    @Test
    fun `RSA size is derived from the modulus and Ed25519 from the key type`() {
        for (bits in listOf(2048, 3072, 4096)) {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
            assertEquals(Algorithm.RSA(bits), Pkcs11KeyManagementService.deriveKeyAlgorithm(pair.private))
        }
        assertEquals(
            Algorithm.Ed25519,
            Pkcs11KeyManagementService.deriveKeyAlgorithm(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public),
        )
        assertNull(Pkcs11KeyManagementService.deriveKeyAlgorithm(null))
    }

    @Test
    fun `an incompatible requested algorithm is refused`() {
        val resolution = Pkcs11KeyManagementService.resolveSigning(Algorithm.P256, "EC", Algorithm.P384)
        assertEquals(SigningResolution.Incompatible(Algorithm.P384), resolution)
        assertIs<SigningResolution.Incompatible>(
            Pkcs11KeyManagementService.resolveSigning(Algorithm.RSA.RSA_2048, "RSA", Algorithm.RSA.RSA_4096),
        )
        assertIs<SigningResolution.Incompatible>(Pkcs11KeyManagementService.resolveSigning(Algorithm.Ed25519, "EC", Algorithm.P256))
    }

    @Test
    fun `with no requested algorithm the hash follows the key`() {
        fun scheme(
            key: Algorithm,
            type: String,
        ) = (Pkcs11KeyManagementService.resolveSigning(null, type, key) as SigningResolution.Ready).scheme
        assertEquals("SHA256withECDSA", scheme(Algorithm.P256, "EC"))
        assertEquals("SHA384withECDSA", scheme(Algorithm.P384, "EC"))
        assertEquals("SHA512withECDSA", scheme(Algorithm.P521, "EC"))
        assertEquals("SHA256withRSA", scheme(Algorithm.RSA.RSA_2048, "RSA"))
        assertEquals("SHA384withRSA", scheme(Algorithm.RSA.RSA_3072, "RSA"))
        assertEquals("SHA512withRSA", scheme(Algorithm.RSA.RSA_4096, "RSA"))
    }

    @Test
    fun `an underivable key falls back to the requested algorithm or key type`() {
        assertEquals(
            "SHA384withECDSA",
            (Pkcs11KeyManagementService.resolveSigning(Algorithm.P384, "EC", null) as SigningResolution.Ready).scheme,
        )
        assertEquals("SHA256withECDSA", (Pkcs11KeyManagementService.resolveSigning(null, "EC", null) as SigningResolution.Ready).scheme)
        assertIs<SigningResolution.Unsupported>(Pkcs11KeyManagementService.resolveSigning(null, "DSA", null))
    }

    // --- provider reuse -------------------------------------------------------------------

    @Test
    fun `a provider registered for another library or slot is not reused`() {
        val name = "TrustWeave-Reuse-${System.nanoTime()}"
        val effective = "SunPKCS11-$name"
        val provider = object : Provider(effective, "1", "test") {}
        Security.addProvider(provider)
        try {
            val cfg = Pkcs11Config(libraryPath = "/lib/a.so", slot = 1, providerName = name)
            // Registered outside this plugin: cannot be verified, so it is refused.
            assertThrows<Pkcs11Exception> { Pkcs11KeyManagementService.reuseOrReject(effective, cfg) }

            Pkcs11KeyManagementService.registeredProviders[effective] = "/lib/a.so" to 1
            assertSame(provider, Pkcs11KeyManagementService.reuseOrReject(effective, cfg))
            assertThrows<Pkcs11Exception> { Pkcs11KeyManagementService.reuseOrReject(effective, cfg.copy(slot = 2)) }
            assertThrows<Pkcs11Exception> { Pkcs11KeyManagementService.reuseOrReject(effective, cfg.copy(libraryPath = "/lib/b.so")) }
        } finally {
            Pkcs11KeyManagementService.registeredProviders.remove(effective)
            Security.removeProvider(effective)
        }
        assertNull(Pkcs11KeyManagementService.reuseOrReject(effective, Pkcs11Config(libraryPath = "/lib/a.so", providerName = name)))
    }

    // --- PIN hygiene ----------------------------------------------------------------------

    class RecordingKeyStoreSpi : KeyStoreSpi() {
        override fun engineLoad(
            stream: java.io.InputStream?,
            password: CharArray?,
        ) {
            seenPin = password?.concatToString()
        }

        override fun engineGetKey(
            alias: String?,
            password: CharArray?,
        ) = null

        override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null

        override fun engineGetCertificate(alias: String?): Certificate? = null

        override fun engineGetCreationDate(alias: String?): Date? = null

        override fun engineSetKeyEntry(
            alias: String?,
            key: java.security.Key?,
            password: CharArray?,
            chain: Array<out Certificate>?,
        ) = Unit

        override fun engineSetKeyEntry(
            alias: String?,
            key: ByteArray?,
            chain: Array<out Certificate>?,
        ) = Unit

        override fun engineSetCertificateEntry(
            alias: String?,
            cert: Certificate?,
        ) = Unit

        override fun engineDeleteEntry(alias: String?) = Unit

        override fun engineAliases(): Enumeration<String> = Collections.emptyEnumeration()

        override fun engineContainsAlias(alias: String?) = false

        override fun engineSize() = 0

        override fun engineIsKeyEntry(alias: String?) = false

        override fun engineIsCertificateEntry(alias: String?) = false

        override fun engineGetCertificateAlias(cert: Certificate?): String? = null

        override fun engineStore(
            stream: java.io.OutputStream?,
            password: CharArray?,
        ) = Unit

        companion object {
            @Volatile
            var seenPin: String? = null
        }
    }

    @Test
    fun `the PIN reaches the keystore and is zeroed afterwards`() {
        val provider =
            object : Provider("pin-test", "1", "test") {
                init {
                    putService(Service(this, "KeyStore", "PKCS11", RecordingKeyStoreSpi::class.java.name, null, null))
                }
            }
        val pin = "1234:secret".toCharArray()
        val ks = Pkcs11KeyManagementService.loadKeyStore(provider, pin)
        assertEquals("1234:secret", RecordingKeyStoreSpi.seenPin)
        assertTrue(pin.all { it == '\u0000' }, "PIN array must be zeroed after use")
        assertEquals(0, ks.size())
        KeyStore.getInstance("PKCS11", provider) // the provider remains usable
    }
}
