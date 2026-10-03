package org.trustweave.soldid

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.util.decodeBase58
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.kms.KeyHandle
import java.util.Base64

private const val ED25519_PUBLIC_KEY_SIZE = 32

/**
 * Derives the Solana address for an Ed25519 key: the base58 (Bitcoin alphabet) encoding of the
 * raw 32-byte public key.
 *
 * The public key is read from the handle's JWK (`kty=OKP`, `crv=Ed25519`, base64url `x`) or,
 * failing that, from its Multikey `publicKeyMultibase` (`z` + base58btc of `0xed01` + key).
 *
 * @throws IllegalArgumentException if the key is not Ed25519
 * @throws TrustWeaveException if the handle carries no usable Ed25519 public key
 */
internal fun solanaAddressFromKeyHandle(keyHandle: KeyHandle): String {
    require(keyHandle.algorithm.equals("Ed25519", ignoreCase = true)) {
        "did:sol requires an Ed25519 key, got ${keyHandle.algorithm}"
    }
    val publicKey =
        ed25519FromJwk(keyHandle.publicKeyJwk)
            ?: ed25519FromMultibase(keyHandle.publicKeyMultibase)
            ?: throw TrustWeaveException.Unknown(
                code = "MISSING_PUBLIC_KEY",
                message =
                    "Cannot derive a Solana address: key '${keyHandle.id.value}' exposes no Ed25519 public key " +
                        "(neither a JWK 'x' nor a publicKeyMultibase)",
            )
    if (publicKey.size != ED25519_PUBLIC_KEY_SIZE) {
        throw TrustWeaveException.Unknown(
            code = "INVALID_PUBLIC_KEY",
            message = "Ed25519 public key must be $ED25519_PUBLIC_KEY_SIZE bytes, got ${publicKey.size}",
        )
    }
    return publicKey.encodeBase58()
}

private fun ed25519FromJwk(jwk: Map<String, Any?>?): ByteArray? {
    if (jwk == null) return null
    val x = jwk["x"] as? String ?: return null
    val kty = jwk["kty"] as? String
    val crv = jwk["crv"] as? String
    if (kty != null && kty != "OKP") {
        throw TrustWeaveException.Unknown(code = "INVALID_PUBLIC_KEY", message = "Expected an OKP JWK, got kty=$kty")
    }
    if (crv != null && crv != "Ed25519") {
        throw TrustWeaveException.Unknown(code = "INVALID_PUBLIC_KEY", message = "Expected crv=Ed25519, got crv=$crv")
    }
    return try {
        Base64.getUrlDecoder().decode(x)
    } catch (e: IllegalArgumentException) {
        throw TrustWeaveException.Unknown(code = "INVALID_PUBLIC_KEY", message = "JWK 'x' is not base64url", cause = e)
    }
}

private fun ed25519FromMultibase(multibase: String?): ByteArray? {
    if (multibase == null || !multibase.startsWith("z")) return null
    val decoded =
        try {
            multibase.substring(1).decodeBase58()
        } catch (e: IllegalArgumentException) {
            return null
        }
    val (algorithm, key) = DidMethodUtils.parseMulticodecKey(decoded) ?: return null
    return if (algorithm == "ED25519") key else null
}
