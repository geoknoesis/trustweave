package org.trustweave.credential.avpmicro.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

private fun JsonObject.dec(key: String): BigDecimal? = str(key)?.let(::BigDecimal)

/** Thrown when an authorization amount is zero or negative. */
class NonPositiveAmountException(
    message: String,
) : IllegalArgumentException(message)

/** Read-only view over a SpendingAuthorizationCredential's constraints. */
class SpendingAuthority(
    val raw: JsonObject,
) {
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

    /** The `issuer` claim, whether written as a string or as an object with an `id`. */
    val issuer: String =
        raw.getValue("issuer").let { if (it is JsonObject) it.getValue("id").jsonPrimitive.content else it.jsonPrimitive.content }

    /** The credential `type` entries (a single string is read as a one-element list). */
    val types: List<String> =
        when (val t = raw["type"]) {
            null -> emptyList()
            is kotlinx.serialization.json.JsonArray -> t.map { it.jsonPrimitive.content }
            else -> listOf(t.jsonPrimitive.content)
        }
}

/** Read-only view over a self-contained PaymentAuthorization (credential embedded in `vp`). */
class AuthorizationView(
    val raw: JsonObject,
) {
    val payer: String = raw.getValue("payer").jsonPrimitive.content
    val payee: String = raw.getValue("payee").jsonPrimitive.content
    val amount: BigDecimal =
        BigDecimal(raw.getValue("amount").jsonPrimitive.content).also {
            if (it.signum() <= 0) throw NonPositiveAmountException("amount must be greater than zero")
        }
    val currency: String = raw.getValue("currency").jsonPrimitive.content
    val expires: Instant? = raw.str("expires")?.let(Instant::parse)
    val nonce: String = raw.getValue("nonce").jsonPrimitive.content
    val id: String = raw.str("id") ?: nonce
    val quoteDigest: String? = raw.str("quoteDigest")
    val requestHash: String? = raw.str("requestHash")
    val settlementMethod: String? = raw.str("settlementMethod")
    val settlementTarget: String? = raw.str("settlementTarget")

    val embeddedCredential: JsonObject =
        raw
            .getValue("vp")
            .jsonObject
            .getValue("verifiableCredential")
            .jsonArray
            .first()
            .jsonObject
    val spendingAuthority: SpendingAuthority = SpendingAuthority(embeddedCredential)

    companion object {
        fun from(document: JsonObject) = AuthorizationView(document)
    }
}

/** Read-only view over a payee-signed PaymentQuote. */
class QuoteView(
    val raw: JsonObject,
) {
    val payer: String? = raw.str("payer")
    val payee: String = raw.getValue("payee").jsonPrimitive.content
    val amount: BigDecimal = BigDecimal(raw.getValue("amount").jsonPrimitive.content)
    val currency: String = raw.getValue("currency").jsonPrimitive.content
    val requestHash: String? = raw.str("requestHash")
    val settlementMethod: String? = raw.str("settlementMethod")
    val settlementTarget: String? = raw.str("settlementTarget")
    val expires: Instant? = raw.str("expires")?.let(Instant::parse)
}
