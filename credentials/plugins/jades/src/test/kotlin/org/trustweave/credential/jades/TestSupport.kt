package org.trustweave.credential.jades

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Test KMS for the JAdES engine tests. Stores BC-generated key pairs and exposes the public key
 * so the test can issue a matching X.509 cert chain.
 */
internal class TestKms : KeyManagementService {
    private data class Entry(val keyPair: KeyPair, val algorithm: Algorithm, val handle: KeyHandle)

    private val store = ConcurrentHashMap<String, Entry>()

    init {
        if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
    }

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> =
        setOf(Algorithm.Ed25519, Algorithm.P256)

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>,
    ): GenerateKeyResult {
        if (algorithm !in getSupportedAlgorithms()) {
            return GenerateKeyResult.Failure.UnsupportedAlgorithm(algorithm, getSupportedAlgorithms())
        }
        val kp = when (algorithm) {
            Algorithm.Ed25519 ->
                KeyPairGenerator.getInstance("Ed25519", "BC").run { initialize(255); generateKeyPair() }
            Algorithm.P256 ->
                KeyPairGenerator.getInstance("EC", "BC")
                    .run { initialize(java.security.spec.ECGenParameterSpec("secp256r1")); generateKeyPair() }
            else -> error("unsupported in TestKms: $algorithm")
        }
        val keyId = (options["keyId"] as? String) ?: "test-${java.util.UUID.randomUUID()}"
        val handle = KeyHandle(id = KeyId(keyId), algorithm = algorithm.name)
        store[keyId] = Entry(kp, algorithm, handle)
        return GenerateKeyResult.Success(handle)
    }

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult =
        store[keyId.value]?.let { GetPublicKeyResult.Success(it.handle) }
            ?: GetPublicKeyResult.Failure.KeyNotFound(keyId)

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?,
    ): SignResult {
        val entry = store[keyId.value] ?: return SignResult.Failure.KeyNotFound(keyId)
        val scheme = when (entry.algorithm) {
            Algorithm.Ed25519 -> "Ed25519"
            Algorithm.P256 -> "SHA256withECDSA"
            else -> return SignResult.Failure.Error(keyId, "unsupported algorithm ${entry.algorithm}")
        }
        return try {
            val sig = Signature.getInstance(scheme, "BC")
            sig.initSign(entry.keyPair.private)
            sig.update(data)
            SignResult.Success(sig.sign())
        } catch (t: Throwable) {
            SignResult.Failure.Error(keyId, "signing failed: ${t.message}", t)
        }
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult =
        if (store.remove(keyId.value) != null) DeleteKeyResult.Deleted else DeleteKeyResult.NotFound

    fun publicKey(keyId: KeyId): PublicKey =
        store[keyId.value]?.keyPair?.public ?: error("no key for $keyId")
}

/**
 * Tiny CA that issues a single end-entity cert against a supplied public key. Returns the
 * resulting cert chain (signer first) as DER bytes — exactly what the JAdES engine wants.
 */
internal class TestCa(private val caSubject: String = "CN=JAdES Engine Test CA, O=TrustWeave, C=EU") {
    private val caKey: KeyPair = KeyPairGenerator.getInstance("RSA").also { it.initialize(2048) }.generateKeyPair()
    val caCert: X509Certificate

    init {
        val now = Date()
        val notAfter = Date(now.time + 365L * 24 * 3600 * 1000)
        val dn = X500Name(caSubject)
        val builder = JcaX509v3CertificateBuilder(
            dn, BigInteger.valueOf(System.nanoTime()), now, notAfter, dn, caKey.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(caKey.private)
        caCert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    fun issueChainBytes(subjectPublicKey: PublicKey, subject: String): List<ByteArray> {
        val now = Date(System.currentTimeMillis() - 60_000)
        val notAfter = Date(now.time + 365L * 24 * 3600 * 1000)
        val builder = JcaX509v3CertificateBuilder(
            X500Name(caSubject), BigInteger.valueOf(System.nanoTime()),
            now, notAfter, X500Name(subject), subjectPublicKey,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(caKey.private)
        val endEntity: X509CertificateHolder = builder.build(signer)
        return listOf(
            JcaX509CertificateConverter().getCertificate(endEntity).encoded,
            caCert.encoded,
        )
    }
}
