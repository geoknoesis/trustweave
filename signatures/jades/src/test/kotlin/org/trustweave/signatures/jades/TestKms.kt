package org.trustweave.signatures.jades

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny in-process [KeyManagementService] over Bouncy Castle key pairs.
 *
 * Tests need access to BOTH halves of each key: the private half for KMS signing and the public
 * half to build an X.509 cert. The shipping `kms:plugins:inmemory` does not expose its private
 * keys, so we use this adapter instead.
 */
internal class TestKms : KeyManagementService {

    private data class Entry(val keyPair: KeyPair, val algorithm: Algorithm, val handle: KeyHandle)

    private val store = ConcurrentHashMap<String, Entry>()

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = setOf(
        Algorithm.Ed25519, Algorithm.P256, Algorithm.P384, Algorithm.P521,
    )

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>,
    ): GenerateKeyResult {
        if (algorithm !in getSupportedAlgorithms()) {
            return GenerateKeyResult.Failure.UnsupportedAlgorithm(
                algorithm = algorithm,
                supportedAlgorithms = getSupportedAlgorithms(),
            )
        }
        return try {
            val keyPair = generateBcKeyPair(algorithm)
            val keyId = (options["keyId"] as? String) ?: ("test-${java.util.UUID.randomUUID()}")
            val handle = KeyHandle(id = KeyId(keyId), algorithm = algorithm.name)
            store[keyId] = Entry(keyPair, algorithm, handle)
            GenerateKeyResult.Success(handle)
        } catch (t: Throwable) {
            GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = t.message ?: "key generation failed",
                cause = t,
            )
        }
    }

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult {
        val entry = store[keyId.value]
            ?: return GetPublicKeyResult.Failure.KeyNotFound(keyId)
        return GetPublicKeyResult.Success(entry.handle)
    }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?,
    ): SignResult {
        val entry = store[keyId.value]
            ?: return SignResult.Failure.KeyNotFound(keyId)
        return try {
            val scheme = jcaScheme(entry.algorithm)
            val sig = Signature.getInstance(scheme, "BC")
            sig.initSign(entry.keyPair.private)
            sig.update(data)
            SignResult.Success(sig.sign())
        } catch (t: Throwable) {
            SignResult.Failure.Error(
                keyId = keyId,
                reason = "signing failed: ${t.message}",
                cause = t,
            )
        }
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult =
        if (store.remove(keyId.value) != null) DeleteKeyResult.Deleted else DeleteKeyResult.NotFound

    fun privateKey(keyId: KeyId): PrivateKey =
        store[keyId.value]?.keyPair?.private ?: error("no key for $keyId")

    fun publicKey(keyId: KeyId): PublicKey =
        store[keyId.value]?.keyPair?.public ?: error("no key for $keyId")

    private fun generateBcKeyPair(algorithm: Algorithm): KeyPair = when (algorithm) {
        Algorithm.Ed25519 -> KeyPairGenerator.getInstance("Ed25519", "BC").run {
            initialize(255)
            generateKeyPair()
        }
        Algorithm.P256 -> KeyPairGenerator.getInstance("EC", "BC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        Algorithm.P384 -> KeyPairGenerator.getInstance("EC", "BC").run {
            initialize(ECGenParameterSpec("secp384r1"))
            generateKeyPair()
        }
        Algorithm.P521 -> KeyPairGenerator.getInstance("EC", "BC").run {
            initialize(ECGenParameterSpec("secp521r1"))
            generateKeyPair()
        }
        else -> error("unsupported in TestKms: $algorithm")
    }

    private fun jcaScheme(algorithm: Algorithm): String = when (algorithm) {
        Algorithm.Ed25519 -> "Ed25519"
        Algorithm.P256 -> "SHA256withECDSA"
        Algorithm.P384 -> "SHA384withECDSA"
        Algorithm.P521 -> "SHA512withECDSA"
        else -> error("unsupported in TestKms: $algorithm")
    }
}
