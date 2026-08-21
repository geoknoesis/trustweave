package org.trustweave.credential.vi.crypto

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.json.JsonObject
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.util.EcdsaSignatureCodec

/** ES256 (ECDSA P-256 + SHA-256) — the single algorithm VI permits. */
internal object Es256 {
    /**
     * Verifies a compact ES256 JWT against an EC public key supplied as a JWK [JsonObject]
     * (e.g. an L1 `cnf.jwk` or an L2 mandate `cnf.jwk`). Returns false on any parse/verify failure.
     */
    fun verify(
        jwt: String,
        jwk: JsonObject,
    ): Boolean =
        runCatching {
            val ecKey = ECKey.parse(jwk.toString())
            // ES256 is the only algorithm VI permits, and the whole chain leans on that. Nimbus would
            // reject a header algorithm its verifier does not support for the key, but that pairing is
            // the library's internal business: a P-521 cnf.jwk with an ES512 token verifies happily
            // under it. Pin both ends here so the guarantee is this module's, not a dependency's.
            if (ecKey.curve != Curve.P_256) return@runCatching false
            val signedJwt = SignedJWT.parse(jwt)
            if (signedJwt.header.algorithm != JWSAlgorithm.ES256) return@runCatching false
            signedJwt.verify(ECDSAVerifier(ecKey.toECPublicKey()))
        }.getOrDefault(false)
}

/** Signs the JWS signing input, returning a P1363 (`r||s`) ES256 signature. */
public fun interface Es256Signer {
    public suspend fun sign(signingInput: ByteArray): ByteArray
}

/**
 * [Es256Signer] backed by a TrustWeave [KeyManagementService] P-256 key.
 *
 * The KMS `sign` contract already returns P1363 for EC keys; [EcdsaSignatureCodec.normalize] is a
 * defensive transcode for backends that emit DER. This adapter is the entire "ES256 via KMS" surface
 * the VI issuance path needs — the signing primitive already exists in `kms-core`.
 */
public class KmsEs256Signer(
    private val kms: KeyManagementService,
    private val keyId: KeyId,
) : Es256Signer {
    override suspend fun sign(signingInput: ByteArray): ByteArray =
        when (val result = kms.sign(keyId, signingInput, Algorithm.P256)) {
            is SignResult.Success -> EcdsaSignatureCodec.normalize(result.signature, Algorithm.P256)
            is SignResult.Failure.KeyNotFound ->
                throw IllegalStateException("ES256 sign failed: key not found: ${result.keyId.value}")
            is SignResult.Failure.UnsupportedAlgorithm ->
                throw IllegalStateException("ES256 sign failed: key is not P-256")
            is SignResult.Failure.Error ->
                throw IllegalStateException("ES256 sign failed: ${result.reason}", result.cause)
        }
}
