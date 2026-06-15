package org.trustweave.credential.avpauth

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.trustweave.credential.avpauth.dto.ErrorResponse
import org.trustweave.credential.avpauth.dto.VerifyResponse
import org.trustweave.credential.avpauth.engine.AuthorizationEngine
import org.trustweave.credential.avpauth.engine.AuthorizationVerdict

// Incoming PaymentAuthorization objects carry extension fields, so parse leniently
// (ignoreUnknownKeys). The ContentNegotiation Json below is only for serializing our own DTOs.
private val lenientJson = Json { ignoreUnknownKeys = true }

fun Application.configureAuthorization(engine: AuthorizationEngine) {
    install(ContentNegotiation) { json(Json { prettyPrint = false }) }
    routing { authorizationRoutes(engine) }
}

fun Routing.authorizationRoutes(engine: AuthorizationEngine) {
    post("/v1/authorizations/verify") {
        val document = try {
            lenientJson.parseToJsonElement(call.receiveText()).jsonObject
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_REQUEST", e.message ?: "malformed JSON"))
            return@post
        }
        val response = when (val v = engine.decide(document)) {
            is AuthorizationVerdict.Allow ->
                VerifyResponse("allow", payer = v.payer, payee = v.payee, amount = v.amount)
            is AuthorizationVerdict.Reject ->
                VerifyResponse("reject", reason = v.reason, detail = v.detail)
        }
        call.respond(HttpStatusCode.OK, response)
    }
}
