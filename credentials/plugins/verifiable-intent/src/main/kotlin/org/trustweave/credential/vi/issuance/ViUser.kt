package org.trustweave.credential.vi.issuance

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.credential.vi.crypto.Disclosures
import org.trustweave.credential.vi.crypto.Es256Signer
import org.trustweave.credential.vi.crypto.Jws
import org.trustweave.credential.vi.crypto.contentOrNull
import org.trustweave.credential.vi.crypto.serializeSdJwt
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.model.Constraint
import org.trustweave.credential.vi.model.Vct

/**
 * Issues a Layer 2 (`kb-sd-jwt+kb`) autonomous user mandate, signed by the user's key.
 *
 * Each mandate is an array-element disclosure referenced from `delegate_payload`; the top-level
 * `sd_hash` binds to the full L1 serialization. When both mandates are present the payment mandate's
 * `mandate.payment.reference` constraint is wired with `conditional_transaction_id =
 * hash(checkout_disclosure)`, which is exactly what [org.trustweave.credential.vi.verification]
 * checks. Mandates are passed as pre-built JSON (`vct`, `cnf.jwk`, `constraints`, ...).
 */
public object ViUser {

    @Suppress("LongParameterList")
    public suspend fun createLayer2Autonomous(
        l1Compact: String,
        checkoutMandate: JsonObject?,
        paymentMandate: JsonObject?,
        nonce: String,
        aud: String,
        iat: Long,
        exp: Long?,
        iss: String?,
        signer: Es256Signer,
        kid: String,
    ): IssuedL2 = assemble(
        l1Compact, checkoutMandate, paymentMandate, Vct.Typ.L2_AUTONOMOUS, wireReference = true,
        nonce, aud, iat, exp, iss, signer, kid,
    )

    /**
     * Issues a Layer 2 (`kb-sd-jwt`) immediate user mandate: both mandates carry finalized values
     * (no `cnf`, no `constraints`); the payment mandate's `transaction_id` must equal the checkout
     * mandate's `checkout_hash`.
     */
    @Suppress("LongParameterList")
    public suspend fun createLayer2Immediate(
        l1Compact: String,
        checkoutMandate: JsonObject,
        paymentMandate: JsonObject,
        nonce: String,
        aud: String,
        iat: Long,
        exp: Long?,
        iss: String?,
        signer: Es256Signer,
        kid: String,
    ): IssuedL2 = assemble(
        l1Compact, checkoutMandate, paymentMandate, Vct.Typ.L2_IMMEDIATE, wireReference = false,
        nonce, aud, iat, exp, iss, signer, kid,
    )

    @Suppress("LongParameterList")
    private suspend fun assemble(
        l1Compact: String,
        checkoutMandate: JsonObject?,
        paymentMandate: JsonObject?,
        typ: String,
        wireReference: Boolean,
        nonce: String,
        aud: String,
        iat: Long,
        exp: Long?,
        iss: String?,
        signer: Es256Signer,
        kid: String,
    ): IssuedL2 {
        val disclosures = mutableListOf<String>()
        val sd = mutableListOf<String>()
        val delegate = mutableListOf<JsonObject>()
        var checkoutB64: String? = null
        var paymentB64: String? = null

        if (checkoutMandate != null) {
            val made = Disclosures.makeArrayElement(checkoutMandate)
            checkoutB64 = made.b64
            disclosures += made.b64
            sd += made.hash
            delegate += buildJsonObject { put("...", made.hash) }
        }
        if (paymentMandate != null) {
            val wired = if (wireReference && checkoutB64 != null) {
                injectReference(paymentMandate, Disclosures.hash(checkoutB64))
            } else {
                paymentMandate
            }
            val made = Disclosures.makeArrayElement(wired)
            paymentB64 = made.b64
            disclosures += made.b64
            sd += made.hash
            delegate += buildJsonObject { put("...", made.hash) }
        }

        val payload = buildJsonObject {
            put("nonce", nonce)
            put("aud", aud)
            put("iat", iat)
            put("sd_hash", sha256B64Url(l1Compact.toByteArray(Charsets.US_ASCII)))
            put("delegate_payload", JsonArray(delegate))
            put("_sd_alg", Vct.SD_ALG)
            put("_sd", JsonArray(sd.map { JsonPrimitive(it) }))
            iss?.let { put("iss", it) }
            exp?.let { put("exp", it) }
        }
        val header = buildJsonObject {
            put("alg", Vct.ALG)
            put("typ", typ)
            put("kid", kid)
        }
        val jwt = Jws.sign(header, payload, signer)
        return IssuedL2(serializeSdJwt(jwt, disclosures), jwt, checkoutB64, paymentB64)
    }

    /** Sets (or appends) the payment mandate's reference constraint to bind the checkout disclosure. */
    private fun injectReference(payment: JsonObject, conditionalTransactionId: String): JsonObject {
        val constraints = (payment["constraints"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        var found = false
        for (i in constraints.indices) {
            val c = constraints[i] as? JsonObject ?: continue
            if (c["type"]?.contentOrNull() == Constraint.Reference.TYPE) {
                constraints[i] = buildJsonObject {
                    c.forEach { (k, v) -> if (k != "conditional_transaction_id") put(k, v) }
                    put("conditional_transaction_id", conditionalTransactionId)
                }
                found = true
            }
        }
        if (!found) {
            constraints += buildJsonObject {
                put("type", Constraint.Reference.TYPE)
                put("conditional_transaction_id", conditionalTransactionId)
            }
        }
        return buildJsonObject {
            payment.forEach { (k, v) -> if (k != "constraints") put(k, v) }
            put("constraints", JsonArray(constraints))
        }
    }
}
