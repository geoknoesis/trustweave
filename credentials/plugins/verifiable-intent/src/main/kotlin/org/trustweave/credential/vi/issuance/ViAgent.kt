package org.trustweave.credential.vi.issuance

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.credential.vi.crypto.Disclosures
import org.trustweave.credential.vi.crypto.Es256Signer
import org.trustweave.credential.vi.crypto.Jws
import org.trustweave.credential.vi.crypto.selectivePresentation
import org.trustweave.credential.vi.crypto.serializeSdJwt
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.model.Vct

/**
 * Issues the split Layer 3 (`kb-sd-jwt`) fulfilments, signed by the agent's delegated key.
 *
 * The final mandate is an array-element disclosure referenced from `delegate_payload`; the `sd_hash`
 * binds to the **routed L2 selective presentation** the recipient received (built from
 * [routedL2Disclosures], which must include the matching L2 mandate disclosure so the verifier's
 * pair-identity binding holds). Header `kid` must equal the L2 mandate's `cnf.jwk.kid`.
 */
public object ViAgent {

    @Suppress("LongParameterList")
    public suspend fun createLayer3Payment(
        finalPayment: JsonObject,
        l2BaseJwt: String,
        routedL2Disclosures: List<String>,
        nonce: String,
        aud: String,
        iat: Long,
        exp: Long?,
        iss: String?,
        signer: Es256Signer,
        agentKid: String,
    ): IssuedL3 = createLayer3(finalPayment, l2BaseJwt, routedL2Disclosures, nonce, aud, iat, exp, iss, signer, agentKid)

    @Suppress("LongParameterList")
    public suspend fun createLayer3Checkout(
        finalCheckout: JsonObject,
        l2BaseJwt: String,
        routedL2Disclosures: List<String>,
        nonce: String,
        aud: String,
        iat: Long,
        exp: Long?,
        iss: String?,
        signer: Es256Signer,
        agentKid: String,
    ): IssuedL3 = createLayer3(finalCheckout, l2BaseJwt, routedL2Disclosures, nonce, aud, iat, exp, iss, signer, agentKid)

    @Suppress("LongParameterList")
    private suspend fun createLayer3(
        finalMandate: JsonObject,
        l2BaseJwt: String,
        routedL2Disclosures: List<String>,
        nonce: String,
        aud: String,
        iat: Long,
        exp: Long?,
        iss: String?,
        signer: Es256Signer,
        agentKid: String,
    ): IssuedL3 {
        val made = Disclosures.makeArrayElement(finalMandate)
        val routedL2 = selectivePresentation(l2BaseJwt, routedL2Disclosures)
        val payload = buildJsonObject {
            put("nonce", nonce)
            put("aud", aud)
            put("iat", iat)
            put("sd_hash", sha256B64Url(routedL2.toByteArray(Charsets.US_ASCII)))
            put("delegate_payload", JsonArray(listOf(buildJsonObject { put("...", made.hash) })))
            put("_sd_alg", Vct.SD_ALG)
            put("_sd", JsonArray(listOf(JsonPrimitive(made.hash))))
            iss?.let { put("iss", it) }
            exp?.let { put("exp", it) }
        }
        val header = buildJsonObject {
            put("alg", Vct.ALG)
            put("typ", Vct.Typ.L3)
            put("kid", agentKid)
        }
        val jwt = Jws.sign(header, payload, signer)
        return IssuedL3(serializeSdJwt(jwt, listOf(made.b64)), routedL2)
    }
}
