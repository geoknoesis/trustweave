package org.trustweave.kms.pkcs11

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.util.EcdsaSignatureCodec
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.Provider
import java.security.Security
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * PKCS#11-backed [KeyManagementService].
 *
 * Implements the same SPI used by `InMemoryKeyManagementService`, `AwsKeyManagementService`, etc.,
 * but routes key generation, signing and (optionally) deletion to a PKCS#11 device via the JDK's
 * `SunPKCS11` provider. Keys are addressed by PKCS#11 *label* — the [KeyId.value] passed to
 * [sign] / [getPublicKey] / [deleteKey] must equal the label that was used at generation time.
 *
 * **JDK 21 compatibility.** The constructor uses the JDK 17+ `Provider.configure(String)` API
 * to load `SunPKCS11`. Earlier JDK 8/11 patterns using a constructor-with-config-file argument
 * are not used.
 *
 * **MVP caveats.**
 * - `Algorithm.Secp256k1` and `Algorithm.Custom` are rejected — most HSMs support secp256k1 but
 *   the standard JCA name differs by vendor, so we leave that mapping out of the MVP.
 * - The advertised algorithm set is the *upper bound* TrustWeave attempts; the actual support is
 *   discovered at key-generation time. A token that doesn't support `Ed25519` (for example) will
 *   fail at [generateKey] with [GenerateKeyResult.Failure.Error].
 * - Deletion semantics depend on the device. Many production HSMs forbid programmatic key
 *   destruction; see [Pkcs11Config.enableSoftDelete].
 *
 * See `docs/architecture/eidas-qes-design.md` §4.4 / §5.4.
 *
 * @param config PKCS#11 configuration. The SunPKCS11 provider is registered eagerly at
 *               construction so configuration errors surface immediately rather than at first use.
 */
class Pkcs11KeyManagementService(
    private val config: Pkcs11Config,
) : KeyManagementService {

    private val logger = LoggerFactory.getLogger(Pkcs11KeyManagementService::class.java)

    /**
     * The SunPKCS11 provider configured for this service.
     *
     * Eagerly initialized so that misconfiguration (missing native library, bad slot id, etc.)
     * fails fast. Any underlying exception is wrapped in [Pkcs11Exception] so callers see a
     * single, well-documented type.
     */
    private val provider: Provider = installProvider(config)

    /**
     * The PKCS#11 keystore. Eagerly loaded so that PIN errors and token-not-present errors are
     * caught at construction.
     */
    private val keyStore: KeyStore = loadKeyStore(provider, config.pin)

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>,
    ): GenerateKeyResult = withContext(Dispatchers.IO) {
        if (algorithm is Algorithm.Custom || algorithm is Algorithm.Secp256k1) {
            return@withContext GenerateKeyResult.Failure.UnsupportedAlgorithm(
                algorithm = algorithm,
                supportedAlgorithms = SUPPORTED_ALGORITHMS,
            )
        }
        if (!supportsAlgorithm(algorithm)) {
            return@withContext GenerateKeyResult.Failure.UnsupportedAlgorithm(
                algorithm = algorithm,
                supportedAlgorithms = SUPPORTED_ALGORITHMS,
            )
        }

        val label = (options[OPTION_LABEL] as? String)?.takeIf { it.isNotBlank() }
            ?: java.util.UUID.randomUUID().toString()

        try {
            val (jcaName, paramSpec, keySize) = mapAlgorithmForKeyGen(algorithm)
                ?: return@withContext GenerateKeyResult.Failure.UnsupportedAlgorithm(
                    algorithm = algorithm,
                    supportedAlgorithms = SUPPORTED_ALGORITHMS,
                )

            val kpg = KeyPairGenerator.getInstance(jcaName, provider)
            when {
                paramSpec != null -> kpg.initialize(paramSpec)
                keySize != null -> kpg.initialize(keySize)
                else -> error("internal: neither keysize nor paramSpec provided for $algorithm")
            }
            val keyPair = kpg.generateKeyPair()

            // HSMs require a certificate to store a key entry. Generate a minimal self-signed
            // placeholder so the token accepts the setKeyEntry call. The certificate is *not*
            // a trust anchor — it exists only to satisfy the JCA KeyStore contract.
            val cert = buildPlaceholderCertificate(keyPair, jcaName, algorithm)

            keyStore.setKeyEntry(label, keyPair.private, null, arrayOf<Certificate>(cert))
            logger.debug("Generated PKCS#11 key: label={}, algorithm={}", label, algorithm.name)

            GenerateKeyResult.Success(
                KeyHandle(
                    id = KeyId(label),
                    algorithm = algorithm.name,
                    publicKeyJwk = null,
                    publicKeyMultibase = null,
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to generate PKCS#11 key: label=$label, algorithm=${algorithm.name}", e)
            GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = "Failed to generate key on PKCS#11 device: ${e.message ?: e.javaClass.simpleName}",
                cause = e,
            )
        }
    }

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult = withContext(Dispatchers.IO) {
        try {
            if (!keyStore.containsAlias(keyId.value)) {
                return@withContext GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
            }
            val cert = keyStore.getCertificate(keyId.value)
                ?: return@withContext GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
            val publicKey = cert.publicKey
            // We can't reliably round-trip the original Algorithm choice from the PKCS#11 public
            // key alone (RSA key size is known, EC curves require ASN.1 inspection). Report the
            // JCA algorithm name; callers that need fine-grained Algorithm matching should track
            // the algorithm out-of-band when issuing.
            val algorithmName = publicKey.algorithm ?: "unknown"

            GetPublicKeyResult.Success(
                KeyHandle(
                    id = keyId,
                    algorithm = algorithmName,
                    publicKeyJwk = null,
                    publicKeyMultibase = null,
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to read PKCS#11 public key: keyId=${keyId.value}", e)
            GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to read public key from PKCS#11 device: ${e.message ?: e.javaClass.simpleName}",
                cause = e,
            )
        }
    }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?,
    ): SignResult = withContext(Dispatchers.IO) {
        try {
            if (!keyStore.containsAlias(keyId.value)) {
                return@withContext SignResult.Failure.KeyNotFound(keyId = keyId)
            }

            val privateKey = try {
                keyStore.getKey(keyId.value, null) as? java.security.PrivateKey
                    ?: return@withContext SignResult.Failure.Error(
                        keyId = keyId,
                        reason = "PKCS#11 alias '${keyId.value}' does not resolve to a private key",
                    )
            } catch (e: KeyStoreException) {
                // "no such alias" is reported here on some providers
                return@withContext SignResult.Failure.KeyNotFound(
                    keyId = keyId,
                    reason = e.message,
                )
            }

            // Map algorithm → JCA signature scheme. If `algorithm` is null, fall back to the
            // JCA key type. Note: PKCS#11 has no concept of "key default signing algorithm"
            // beyond key type, and the algorithm is *not* persisted on the token in a way the
            // JCA layer exposes — so we conservatively map the JCA key type to a deterministic
            // SHA-256 scheme. Callers that need curve- or key-size-matched hashes (SHA-384 for
            // P-384/RSA-3072, SHA-512 for P-521/RSA-4096) must pass the algorithm explicitly.
            val signatureScheme = signatureSchemeFor(algorithm, privateKey.algorithm)
                ?: return@withContext SignResult.Failure.UnsupportedAlgorithm(
                    keyId = keyId,
                    requestedAlgorithm = algorithm,
                    keyAlgorithm = Algorithm.parse(privateKey.algorithm) ?: Algorithm.Custom(privateKey.algorithm),
                    reason = "Cannot derive a PKCS#11 signature scheme for algorithm=${algorithm?.name} " +
                        "keyType=${privateKey.algorithm}",
                )

            val signer = Signature.getInstance(signatureScheme, provider)
            signer.initSign(privateKey)
            signer.update(data)
            val signature = signer.sign()
            // JCA/PKCS#11 ECDSA emits ASN.1 DER; the KeyManagementService contract requires
            // P1363 (raw r||s). Derive the field size from the requested algorithm or, when the
            // caller passed no algorithm, from the EC key parameters reported by the token.
            val normalized = if (signatureScheme.contains("ECDSA")) {
                val fieldSizeBytes = algorithm?.let { EcdsaSignatureCodec.fieldSizeBytes(it) }
                    ?: (privateKey as? java.security.interfaces.ECKey)
                        ?.params?.curve?.field?.fieldSize?.let { (it + 7) / 8 }
                if (fieldSizeBytes != null && EcdsaSignatureCodec.isDer(signature)) {
                    EcdsaSignatureCodec.derToP1363(signature, fieldSizeBytes)
                } else {
                    signature
                }
            } else {
                signature
            }
            SignResult.Success(normalized)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to sign with PKCS#11 key: keyId=${keyId.value}", e)
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to sign with PKCS#11 device: ${e.message ?: e.javaClass.simpleName}",
                cause = e,
            )
        }
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult = withContext(Dispatchers.IO) {
        // containsAlias may itself fail on a broken provider; treat any pre-delete failure as
        // a hard error so callers see what went wrong.
        val exists = try {
            keyStore.containsAlias(keyId.value)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to query PKCS#11 alias: keyId=${keyId.value}", e)
            return@withContext DeleteKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to query PKCS#11 alias: ${e.message ?: e.javaClass.simpleName}",
                cause = e,
            )
        }
        if (!exists) {
            return@withContext DeleteKeyResult.NotFound
        }

        try {
            keyStore.deleteEntry(keyId.value)
            DeleteKeyResult.Deleted
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (config.enableSoftDelete) {
                logger.warn(
                    "PKCS#11 device refused deleteEntry for '{}'; soft-delete enabled, returning Deleted",
                    keyId.value,
                    e,
                )
                DeleteKeyResult.Deleted
            } else {
                DeleteKeyResult.Failure.Error(
                    keyId = keyId,
                    reason = "PKCS#11 device refused key deletion (set Pkcs11Config.enableSoftDelete=true " +
                        "to ignore): ${e.message ?: e.javaClass.simpleName}",
                    cause = e,
                )
            }
        }
    }

    /**
     * Maps a TrustWeave [Algorithm] to:
     *  - JCA `KeyPairGenerator` algorithm name,
     *  - optional `AlgorithmParameterSpec` (for EC curves),
     *  - optional RSA key size.
     *
     * Returns `null` for algorithms we don't attempt on PKCS#11.
     */
    private fun mapAlgorithmForKeyGen(
        algorithm: Algorithm,
    ): Triple<String, java.security.spec.AlgorithmParameterSpec?, Int?>? = when (algorithm) {
        Algorithm.Ed25519 -> Triple("Ed25519", null, null)
        Algorithm.P256 -> Triple("EC", ECGenParameterSpec("secp256r1"), null)
        Algorithm.P384 -> Triple("EC", ECGenParameterSpec("secp384r1"), null)
        Algorithm.P521 -> Triple("EC", ECGenParameterSpec("secp521r1"), null)
        is Algorithm.RSA -> Triple("RSA", null, algorithm.rsaKeySize)
        Algorithm.Secp256k1 -> null
        Algorithm.BLS12_381 -> null
        is Algorithm.Custom -> null
    }

    /**
     * Builds a minimal self-signed certificate sufficient to satisfy [KeyStore.setKeyEntry] on a
     * PKCS#11 token. Some token implementations require a real X.509 cert; we use BouncyCastle's
     * [org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder] to construct one.
     *
     * The cert is **not** intended as a trust anchor — production callers should replace it with
     * a CA-issued certificate via a separate enrollment flow.
     */
    private fun buildPlaceholderCertificate(
        keyPair: java.security.KeyPair,
        jcaKeyType: String,
        algorithm: Algorithm,
    ): X509Certificate {
        val subject = X500Principal("CN=trustweave-pkcs11-placeholder")
        val notBefore = Date(System.currentTimeMillis() - 60_000L)
        // 100 years; placeholder lifetime is irrelevant — the cert is overwritten or never trusted.
        val notAfter = Date(notBefore.time + 100L * 365 * 24 * 3600 * 1000)
        val serial = BigInteger(64, java.security.SecureRandom())

        val sigAlg = when {
            jcaKeyType.equals("Ed25519", ignoreCase = true) -> "Ed25519"
            jcaKeyType.equals("EC", ignoreCase = true) -> "SHA256withECDSA"
            jcaKeyType.equals("RSA", ignoreCase = true) -> "SHA256withRSA"
            else -> throw Pkcs11Exception(
                "internal: cannot build placeholder certificate for keyType=$jcaKeyType algorithm=$algorithm",
            )
        }

        // Use BouncyCastle's certificate builder via reflection-free direct call. This keeps the
        // code path standard and avoids pulling in additional optional dependencies.
        val certBuilder = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public,
        )
        // The placeholder is signed by the same private key. The private key lives on the token,
        // so we sign through the provider — but at this point [setKeyEntry] has not yet been
        // called, so the in-memory `keyPair.private` is still usable for signing.
        val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder(sigAlg)
            .build(keyPair.private)
        val holder = certBuilder.build(signer)
        return org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .getCertificate(holder)
    }

    companion object {
        /**
         * Upper-bound algorithm set TrustWeave attempts on PKCS#11. Actual support is discovered
         * at key-generation time — a given token may support a strict subset.
         *
         * `Secp256k1` is intentionally omitted from the MVP because the JCA name differs by vendor
         * (`secp256k1` vs proprietary names) and would require per-vendor probing.
         */
        val SUPPORTED_ALGORITHMS: Set<Algorithm> = setOf(
            Algorithm.Ed25519,
            Algorithm.P256,
            Algorithm.P384,
            Algorithm.P521,
            Algorithm.RSA.RSA_2048,
            Algorithm.RSA.RSA_3072,
            Algorithm.RSA.RSA_4096,
        )

        /**
         * Option key used by [generateKey] to address a generated key by a caller-supplied label.
         * If absent, a random UUID is used. See [KeyId].
         */
        const val OPTION_LABEL: String = "label"

        /**
         * Resolves the JCA signature scheme name for the given (optional) requested algorithm and
         * the JCA key-type string reported by the token.
         *
         * Schemes follow the [KeyManagementService.sign] contract:
         * - ECDSA uses the hash that matches the curve (SHA-256/384/512 for P-256/384/521).
         * - RSA uses PKCS#1 v1.5 (`SHAxxxwithRSA`), with the hash sized to the key
         *   (2048→SHA-256, 3072→SHA-384, 4096→SHA-512) — consistent with the other KMS plugins.
         *
         * Internal for testability (mapping-level unit tests do not require an HSM).
         */
        internal fun signatureSchemeFor(requested: Algorithm?, keyType: String): String? {
            if (requested != null) {
                return when (requested) {
                    Algorithm.Ed25519 -> "Ed25519"
                    Algorithm.P256 -> "SHA256withECDSA"
                    Algorithm.P384 -> "SHA384withECDSA"
                    Algorithm.P521 -> "SHA512withECDSA"
                    is Algorithm.RSA -> when (requested.rsaKeySize) {
                        2048 -> "SHA256withRSA"
                        3072 -> "SHA384withRSA"
                        4096 -> "SHA512withRSA"
                        else -> null
                    }
                    Algorithm.Secp256k1 -> null
                    Algorithm.BLS12_381 -> null
                    is Algorithm.Custom -> null
                }
            }
            // Fall back to JCA key type
            return when (keyType.uppercase()) {
                "ED25519", "EDDSA" -> "Ed25519"
                "EC" -> "SHA256withECDSA"
                "RSA" -> "SHA256withRSA"
                else -> null
            }
        }

        /**
         * Installs (or reuses) a SunPKCS11 provider for the given configuration.
         *
         * Uses the JDK 17+ approach: `Provider.configure(String)` on the prototype `SunPKCS11`
         * instance, returning a *new* provider. The provider name is reused across calls so that
         * the JVM keeps a single registered instance per `Pkcs11Config.providerName`.
         */
        private fun installProvider(config: Pkcs11Config): Provider {
            // Use SunPKCS11's standard inline configuration format (each directive on its own line).
            // The leading "--" tells SunPKCS11 to treat the argument as inline config rather than a
            // path to a config file.
            val configString = buildString {
                append("--")
                append('\n')
                append("name=").append(config.providerName).append('\n')
                append("library=").append(config.libraryPath).append('\n')
                append("slot=").append(config.slot).append('\n')
            }

            // SunPKCS11's effective registered name is the prefix "SunPKCS11-" + configured name.
            val effectiveName = "SunPKCS11-${config.providerName}"
            Security.getProvider(effectiveName)?.let { return it }

            // JDK 17+ approach: obtain a prototype SunPKCS11 provider, then call
            // Provider.configure(String) which returns a *new* configured Provider. The prototype
            // is shipped in every mainstream JDK 17+ distribution but is not necessarily
            // auto-registered in java.security — so we fall back to reflective instantiation if
            // Security.getProvider("SunPKCS11") returns null.
            val prototype: Provider = Security.getProvider("SunPKCS11")
                ?: try {
                    @Suppress("UNCHECKED_CAST")
                    val cls = Class.forName("sun.security.pkcs11.SunPKCS11") as Class<out Provider>
                    cls.getDeclaredConstructor().newInstance()
                } catch (e: Exception) {
                    throw Pkcs11Exception(
                        "SunPKCS11 provider is not available in this JDK; PKCS#11 support requires " +
                            "OpenJDK 17+ with the sun.security.pkcs11 module on the classpath.",
                        e,
                    )
                }

            val configured = try {
                prototype.configure(configString)
            } catch (e: Exception) {
                throw Pkcs11Exception(
                    "Failed to configure SunPKCS11 provider (libraryPath='${config.libraryPath}', " +
                        "slot=${config.slot}): ${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }
            Security.addProvider(configured)
            return configured
        }

        private fun loadKeyStore(provider: Provider, pin: CharArray?): KeyStore {
            return try {
                val ks = KeyStore.getInstance("PKCS11", provider)
                ks.load(null, pin)
                ks
            } catch (e: Exception) {
                throw Pkcs11Exception(
                    "Failed to open PKCS#11 keystore via provider '${provider.name}': " +
                        "${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }
        }
    }
}
