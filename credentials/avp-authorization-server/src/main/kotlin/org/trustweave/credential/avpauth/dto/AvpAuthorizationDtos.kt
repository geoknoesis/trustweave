package org.trustweave.credential.avpauth.dto

import kotlinx.serialization.Serializable

@Serializable
data class VerifyResponse(
    val decision: String,            // "allow" | "reject"
    val reason: String? = null,
    val detail: String? = null,
    val payer: String? = null,
    val payee: String? = null,
    val amount: String? = null,
)

@Serializable
data class ErrorResponse(val error: String, val message: String)
