package org.trustweave.credential.avpmicro.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content
private fun JsonObject.dec(key: String): BigDecimal? = str(key)?.let(::BigDecimal)

/** Read-only view over a SpendingAuthorizationCredential's constraints. */
class SpendingAuthority(val raw: JsonObject) {
    private val subject = raw.getValue("credentialSubject").jsonObject
    val subjectId: String = subject.getValue("id").jsonPrimitive.content
    val currency: String = subject.getValue("currency").jsonPrimitive.content
    val maxPerTransaction: BigDecimal = BigDecimal(subject.getValue("maxPerTransaction").jsonPrimitive.content)
    val dailyLimit: BigDecimal? = subject.dec("dailyLimit")
    val allowedPayees: List<String>? =
        subject["allowedPayees"]?.jsonArray?.map { it.jsonPrimitive.content }
    val validFrom: Instant? = raw.str("validFrom")?.let(Instant::parse)
    val validUntil: Instant? = raw.str("validUntil")?.let(Instant::parse)
    val credentialId: String = raw.str("id") ?: subjectId
    val credentialStatus: JsonObject? = raw["credentialStatus"]?.jsonObject
}

/** Read-only view over a self-contained PaymentAuthorization (credential embedded in `vp`). */
class AuthorizationView(val raw: JsonObject) {
    val payer: String = raw.getValue("payer").jsonPrimitive.content
    val payee: String = raw.getValue("payee").jsonPrimitive.content
    val amount: BigDecimal = BigDecimal(raw.getValue("amount").jsonPrimitive.content)
    val currency: String = raw.getValue("currency").jsonPrimitive.content
    val expires: Instant? = raw.str("expires")?.let(Instant::parse)
    val nonce: String = raw.getValue("nonce").jsonPrimitive.content
    val id: String = raw.str("id") ?: nonce
    val quoteDigest: String? = raw.str("quoteDigest")

    val embeddedCredential: JsonObject =
        raw.getValue("vp").jsonObject.getValue("verifiableCredential").jsonArray.first().jsonObject
    val spendingAuthority: SpendingAuthority = SpendingAuthority(embeddedCredential)

    companion object {
        fun from(document: JsonObject) = AuthorizationView(document)
    }
}
