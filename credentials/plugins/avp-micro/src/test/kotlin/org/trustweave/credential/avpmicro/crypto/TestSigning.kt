package org.trustweave.credential.avpmicro.crypto

import org.bouncycastle.jce.ECNamedCurveTable
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.base.DidMethodUtils
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec

/** Test-only signing helpers reproducing the Python harness's deterministic seed keys. */
object TestSigning {
    private val curve = ECNamedCurveTable.getParameterSpec("secp256r1")

    private fun scalar(label: String): BigInteger {
        val h = MessageDigest.getInstance("SHA-256").digest("avp-micro-test:$label".toByteArray())
        return BigInteger(1, h).mod(curve.n.subtract(BigInteger.ONE)).add(BigInteger.ONE)
    }

    fun seedKey(label: String): ECPrivateKey {
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
        return KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(scalar(label), params)) as ECPrivateKey
    }

    /** Compressed SEC1 public point (33 bytes) for the label's key. */
    private fun compressedPoint(label: String): ByteArray =
        curve.g.multiply(scalar(label)).normalize().getEncoded(true)

    fun didKey(label: String): String {
        val multikey = DidMethodUtils.getMulticodecPrefix("P-256") + compressedPoint(label)
        return "did:key:z" + multikey.encodeBase58()
    }

    fun verificationMethod(label: String): String {
        val did = didKey(label)
        return "$did#${did.removePrefix("did:key:")}"
    }
}
