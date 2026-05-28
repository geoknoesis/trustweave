package org.trustweave.credential.oidc4vci.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.trustweave.credential.oidc4vci.models.Oidc4VciNotification
import org.trustweave.credential.oidc4vci.models.TxCode

fun Routing.configureOidc4VciServerRoutes(service: Oidc4VciIssuerService) {

    get("/.well-known/openid-credential-issuer") {
        call.respond(service.getMetadata())
    }

    post("/api/offer") {
        val req = call.receive<CreateOfferRequest>()
        val resp = service.createOffer(req.credentialTypes, req.txCode, req.txCodeValue)
        call.respond(HttpStatusCode.Created, buildJsonObject {
            put("offer_uri", resp.offerUri)
            put("pre_authorized_code", resp.preAuthCode)
        })
    }

    post("/token") {
        val params = call.receiveParameters()
        val grantType = params["grant_type"]
        if (grantType != "urn:ietf:params:oauth:grant-type:pre-authorized_code") {
            call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "unsupported_grant_type") })
            return@post
        }
        val preAuthCode = params["pre-authorized_code"]
        if (preAuthCode == null) {
            call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_request") })
            return@post
        }
        val txCodeValue = params["tx_code"]
        runCatching { service.exchangePreAuthCode(preAuthCode, txCodeValue) }
            .onSuccess { tr ->
                call.respond(buildJsonObject {
                    put("access_token", tr.accessToken)
                    put("token_type", tr.tokenType)
                    put("expires_in", tr.expiresIn)
                })
            }
            .onFailure { call.respond(HttpStatusCode.BadRequest, buildJsonObject {
                put("error", "invalid_grant")
                it.message?.let { m -> put("error_description", m) }
            }) }
    }

    post("/credential") {
        val accessToken = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
        if (accessToken == null) {
            call.respond(HttpStatusCode.Unauthorized, buildJsonObject { put("error", "invalid_token") })
            return@post
        }
        val body = call.receive<JsonObject>()
        val format = body["format"]?.jsonPrimitive?.contentOrNull ?: "jwt_vc_json"
        val types = body["credential_definition"]?.jsonObject?.get("type")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        runCatching { service.issueCredential(accessToken, format, types) }
            .onSuccess { resp ->
                if (resp.credential != null)
                    call.respond(buildJsonObject {
                        put("credential", resp.credential)
                        put("format", resp.format ?: format)
                    })
                else
                    call.respond(buildJsonObject { resp.transactionId?.let { put("transaction_id", it) } })
            }
            .onFailure { call.respond(HttpStatusCode.Unauthorized, buildJsonObject { put("error", "invalid_token") }) }
    }

    post("/deferred_credential") {
        val accessToken = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
        if (accessToken == null) {
            call.respond(HttpStatusCode.Unauthorized, buildJsonObject { put("error", "invalid_token") })
            return@post
        }
        val body = call.receive<JsonObject>()
        val transactionId = body["transaction_id"]?.jsonPrimitive?.contentOrNull
        if (transactionId == null) {
            call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_transaction_id") })
            return@post
        }
        val resp = service.getDeferredCredential(transactionId, accessToken)
        if (resp == null) call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_transaction_id") })
        else call.respond(buildJsonObject { resp.credential?.let { put("credential", it) } })
    }

    post("/notification") {
        val notification = call.receive<Oidc4VciNotification>()
        service.recordNotification(notification)
        call.respond(HttpStatusCode.NoContent)
    }
}

@Serializable
data class CreateOfferRequest(
    val credentialTypes: List<String>,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
)
