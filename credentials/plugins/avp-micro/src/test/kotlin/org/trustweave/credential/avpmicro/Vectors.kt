package org.trustweave.credential.avpmicro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object Vectors {
    private fun load(name: String): JsonObject {
        val text = requireNotNull(this::class.java.getResourceAsStream("/vectors/$name")) {
            "missing test resource /vectors/$name"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return Json.parseToJsonElement(text).jsonObject
    }

    val spendingAuthorizationCredential get() = load("spending-authorization-credential.json")
    val paymentAuthorization get() = load("02-payment-authorization.json")
    val paymentQuote get() = load("01-payment-quote.json")
}
