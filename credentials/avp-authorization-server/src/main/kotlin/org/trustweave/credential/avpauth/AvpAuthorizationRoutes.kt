package org.trustweave.credential.avpauth

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.trustweave.credential.avpauth.dto.ErrorResponse
import org.trustweave.credential.avpauth.dto.VerifyResponse
import org.trustweave.credential.avpauth.engine.AuthorizationEngine
import org.trustweave.credential.avpauth.engine.AuthorizationVerdict
import org.trustweave.credential.avpmicro.verification.VerificationFailure

// Incoming PaymentAuthorization objects carry extension fields, so parse leniently
// (ignoreUnknownKeys). The ContentNegotiation Json below is only for serializing our own DTOs.
private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * 256 KiB. A payment authorization plus its quote is a few kilobytes; this leaves generous room for
 * extension fields while keeping a single request from exhausting memory.
 */
public const val DEFAULT_MAX_REQUEST_BYTES: Long = 256L * 1024

fun Application.configureAuthorization(
    engine: AuthorizationEngine,
    maxRequestBytes: Long = DEFAULT_MAX_REQUEST_BYTES,
) {
    install(ContentNegotiation) { json(Json { prettyPrint = false }) }
    routing { authorizationRoutes(engine, maxRequestBytes) }
}

fun Routing.authorizationRoutes(
    engine: AuthorizationEngine,
    maxRequestBytes: Long = DEFAULT_MAX_REQUEST_BYTES,
) {
    post("/v1/authorizations/verify") {
        // This route is unauthenticated by design (it expects a proxy in front), so anyone who can
        // reach it can post to it. Reading the body whole would let one request drive allocation
        // until the process dies, so bound it before parsing.
        //
        // Content-Length is checked first as a cheap reject, but it is attacker-supplied and absent
        // under chunked encoding — so the read itself is bounded too, and that is what actually
        // enforces the limit.
        val declared = call.request.contentLength()
        if (declared != null && declared > maxRequestBytes) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponse("REQUEST_TOO_LARGE", "Request body exceeds $maxRequestBytes bytes"),
            )
            return@post
        }

        val raw =
            try {
                readBounded(call, maxRequestBytes)
            } catch (e: RequestTooLargeException) {
                call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    ErrorResponse("REQUEST_TOO_LARGE", e.message ?: "Request body too large"),
                )
                return@post
            }

        val parsed =
            try {
                lenientJson.parseToJsonElement(raw).jsonObject
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_REQUEST", e.message ?: "malformed JSON"))
                return@post
            }
        val authorization = parsed["authorization"] as? JsonObject
        val quote = parsed["quote"] as? JsonObject
        if (authorization == null || quote == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_REQUEST", "request must contain 'authorization' and 'quote' objects"),
            )
            return@post
        }
        when (val v = engine.decide(authorization, quote)) {
            is AuthorizationVerdict.Allow ->
                call.respond(HttpStatusCode.OK, VerifyResponse("allow", payer = v.payer, payee = v.payee, amount = v.amount))
            is AuthorizationVerdict.Reject ->
                if (v.reason == VerificationFailure.MALFORMED_REQUEST.name) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_REQUEST", v.detail))
                } else {
                    call.respond(HttpStatusCode.OK, VerifyResponse("reject", reason = v.reason, detail = v.detail))
                }
        }
    }
}

private class RequestTooLargeException(
    message: String,
) : Exception(message)

/**
 * Reads the request body, refusing anything past [maxBytes].
 *
 * Reads one byte more than the limit so "exactly at the limit" is accepted and one byte over is
 * caught, without ever materialising an oversized body.
 */
private suspend fun readBounded(
    call: io.ktor.server.application.ApplicationCall,
    maxBytes: Long,
): String {
    val channel = call.receiveChannel()
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    while (true) {
        val read = channel.readAvailable(chunk, 0, chunk.size)
        if (read <= 0) break
        if (buffer.size() + read > maxBytes) {
            throw RequestTooLargeException("Request body exceeds $maxBytes bytes")
        }
        buffer.write(chunk, 0, read)
    }
    return buffer.toString(Charsets.UTF_8.name())
}
