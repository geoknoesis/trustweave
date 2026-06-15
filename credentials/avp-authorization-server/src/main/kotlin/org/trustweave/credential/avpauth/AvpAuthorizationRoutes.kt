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

fun Application.configureAuthorization(engine: AuthorizationEngine) {
    install(ContentNegotiation) { json(Json { prettyPrint = false }) }
    routing { authorizationRoutes(engine) }
}

fun Routing.authorizationRoutes(engine: AuthorizationEngine) {
    post("/v1/authorizations/verify") {
        val parsed = try {
            lenientJson.parseToJsonElement(call.receiveText()).jsonObject
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
