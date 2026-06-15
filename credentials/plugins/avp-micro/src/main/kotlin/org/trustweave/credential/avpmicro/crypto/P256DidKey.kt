package org.trustweave.credential.avpmicro.crypto

import org.trustweave.core.util.decodeBase58
import org.trustweave.did.base.DidMethodUtils
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

/** Resolves a P-256 `did:key` (or its `#fragment` verificationMethod) to a JCA ECPublicKey. */
object P256DidKey {
    fun publicKeyFrom(didKeyOrVm: String): ECPublicKey {
        val mb = didKeyOrVm.substringBefore('#').removePrefix("did:key:")
        require(mb.startsWith("z")) { "expected base58btc multibase did:key" }
        val decoded = mb.substring(1).decodeBase58()
        val (alg, keyBytes) = DidMethodUtils.parseMulticodecKey(decoded)
            ?: throw IllegalArgumentException("unrecognized multicodec prefix")
        require(alg == "P-256") { "not a P-256 did:key (was $alg)" }
        val uncompressed = DidMethodUtils.decompressEcPublicKey("P-256", keyBytes) // 0x04 || x(32) || y(32)
        val x = BigInteger(1, uncompressed.copyOfRange(1, 33))
        val y = BigInteger(1, uncompressed.copyOfRange(33, 65))
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
        val spec = ECPublicKeySpec(ECPoint(x, y), params)
        return KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey
    }
}
