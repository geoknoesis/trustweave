package org.trustweave.credential.vi.issuance

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.credential.vi.crypto.Disclosures
import org.trustweave.credential.vi.crypto.Es256Signer
import org.trustweave.credential.vi.crypto.Jws
import org.trustweave.credential.vi.crypto.serializeSdJwt
import org.trustweave.credential.vi.model.IssuerCredential
import org.trustweave.credential.vi.model.Vct

/**
 * Issues a Layer 1 (`sd+jwt`) issuer credential binding the user's public key via `cnf.jwk`, with
 * `email` selectively disclosable. Signs through [Es256Signer] (the issuer's P-256 key).
 */
public object ViIssuer {

    public suspend fun createLayer1(
        credential: IssuerCredential,
        signer: Es256Signer,
        issuerKid: String,
    ): String {
        val disclosures = mutableListOf<String>()
        val sd = mutableListOf<String>()
        credential.email?.let {
            val made = Disclosures.makeClaim("email", JsonPrimitive(it))
            disclosures += made.b64
            sd += made.hash
        }

        val payload = buildJsonObject {
            put("iss", credential.iss)
            put("sub", credential.sub)
            put("iat", credential.iat)
            put("exp", credential.exp)
            put("vct", credential.vct)
            put("cnf", buildJsonObject { put("jwk", credential.userCnfJwk) })
            credential.aud?.let { put("aud", it) }
            put("pan_last_four", credential.panLastFour)
            put("scheme", credential.scheme)
            credential.cardId?.let { put("card_id", it) }
            put("_sd", JsonArray(sd.map { JsonPrimitive(it) }))
            put("_sd_alg", Vct.SD_ALG)
        }
        val header = buildJsonObject {
            put("alg", Vct.ALG)
            put("typ", Vct.Typ.L1)
            put("kid", issuerKid)
        }
        return serializeSdJwt(Jws.sign(header, payload, signer), disclosures)
    }
}

/** The issued L2 mandate plus the handles an agent needs to build routed L3 presentations. */
public data class IssuedL2(
    val compact: String,
    val baseJwt: String,
    val checkoutDiscB64: String?,
    val paymentDiscB64: String?,
)

/** An issued L3 credential plus the exact routed L2 presentation its `sd_hash` binds to. */
public data class IssuedL3(
    val compact: String,
    val routedL2: String,
)
