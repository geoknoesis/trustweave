package org.trustweave.credential.oidc4vci.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.trustweave.credential.oidc4vci.models.Oidc4VciNotification
import org.trustweave.credential.oidc4vci.models.TxCode

fun Routing.configureOidc4VciServerRoutes(service: Oidc4VciIssuerService) {
    get("/.well-known/openid-credential-issuer") {
        call.respond(service.getMetadata())
    }

    post("/api/offer") {
        val req =
            try {
                call.receive<CreateOfferRequest>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respondInvalidRequest("The offer request body is not valid")
                return@post
            }
        val resp =
            try {
                service.createOffer(req.credentialTypes, req.txCode, req.txCodeValue, req.claims)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IssuerCapacityExceededException) {
                call.application.log.warn("OID4VCI offer endpoint at capacity: ${e.message}")
                call.respond(HttpStatusCode.ServiceUnavailable, buildJsonObject { put("error", "temporarily_unavailable") })
                return@post
            } catch (e: IllegalArgumentException) {
                // Offers are minted by the authenticated operator, so the reason is theirs to see.
                call.respondInvalidRequest(e.message)
                return@post
            }
        call.respond(
            HttpStatusCode.Created,
            buildJsonObject {
                put("offer_uri", resp.offerUri)
                put("pre_authorized_code", resp.preAuthCode)
            },
        )
    }

    post("/token") {
        val params =
            try {
                call.receiveParameters()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respondInvalidRequest(null)
                return@post
            }
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
        val tr =
            try {
                service.exchangePreAuthCode(preAuthCode, txCodeValue)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IssuerCapacityExceededException) {
                call.application.log.warn("OID4VCI token endpoint at capacity: ${e.message}")
                call.respond(HttpStatusCode.ServiceUnavailable, buildJsonObject { put("error", "temporarily_unavailable") })
                return@post
            } catch (e: IllegalArgumentException) {
                // One answer for an unknown, expired or already-used code and for a wrong tx_code: the
                // caller must not learn which of them it got right.
                call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_grant") })
                return@post
            } catch (e: Exception) {
                call.application.log.error("OID4VCI token exchange failed unexpectedly", e)
                call.respond(HttpStatusCode.InternalServerError, buildJsonObject { put("error", "server_error") })
                return@post
            }
        call.respond(
            buildJsonObject {
                put("access_token", tr.accessToken)
                put("token_type", tr.tokenType)
                put("expires_in", tr.expiresIn)
                put("c_nonce", tr.cNonce)
                put("c_nonce_expires_in", tr.cNonceExpiresIn)
            },
        )
    }

    post("/credential") {
        val accessToken = call.bearerToken()
        if (accessToken == null) {
            call.respondInvalidToken("Missing Bearer access token")
            return@post
        }
        val format: String
        val types: List<String>
        val proofJwt: String?
        try {
            val body = call.receive<JsonObject>()
            format = body.optionalString("format") ?: "jwt_vc_json"
            types =
                body["credential_definition"]
                    ?.takeUnless { it is JsonNull }
                    ?.jsonObject
                    ?.get("type")
                    ?.takeUnless { it is JsonNull }
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content } ?: emptyList()
            // OID4VCI v1.0 §7.2: proof of possession — { "proof": { "proof_type": "jwt", "jwt": "..." } }
            proofJwt =
                body["proof"]
                    ?.takeUnless { it is JsonNull }
                    ?.jsonObject
                    ?.optionalString("jwt")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.respondInvalidRequest("The credential request is malformed")
            return@post
        }
        val resp =
            try {
                service.issueCredential(accessToken, format, types, proofJwt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidProofException) {
                // §7.3.1: invalid_proof MUST carry a fresh c_nonce so the wallet can retry
                call.respond(
                    HttpStatusCode.BadRequest,
                    buildJsonObject {
                        put("error", "invalid_proof")
                        e.message?.let { m -> put("error_description", m) }
                        put("c_nonce", e.freshCNonce)
                        put("c_nonce_expires_in", e.cNonceExpiresIn)
                    },
                )
                return@post
            } catch (e: InvalidTokenException) {
                call.respondInvalidToken(e.message)
                return@post
            } catch (e: CredentialLimitExceededException) {
                call.respondInvalidRequest(e.message)
                return@post
            } catch (e: UnsupportedCredentialFormatException) {
                // OID4VCI §8.3.1.2: the requested format cannot be issued. Never echoed as if it were.
                call.respond(
                    HttpStatusCode.BadRequest,
                    buildJsonObject {
                        put("error", "unsupported_credential_format")
                        e.message?.let { m -> put("error_description", m) }
                    },
                )
                return@post
            } catch (e: Exception) {
                call.application.log.error("OID4VCI credential issuance failed unexpectedly", e)
                call.respond(HttpStatusCode.InternalServerError, buildJsonObject { put("error", "server_error") })
                return@post
            }
        if (resp.credential != null) {
            call.respond(
                buildJsonObject {
                    put("credential", resp.credential)
                    put("format", resp.format ?: format)
                    resp.cNonce?.let { put("c_nonce", it) }
                    resp.cNonceExpiresIn?.let { put("c_nonce_expires_in", it) }
                },
            )
        } else {
            call.respond(buildJsonObject { resp.transactionId?.let { put("transaction_id", it) } })
        }
    }

    post("/deferred_credential") {
        val accessToken = call.bearerToken()
        if (accessToken == null) {
            call.respondInvalidToken("Missing Bearer access token")
            return@post
        }
        try {
            service.requireAccessToken(accessToken)
        } catch (e: InvalidTokenException) {
            call.respondInvalidToken(e.message)
            return@post
        }
        val transactionId =
            try {
                call.receive<JsonObject>().optionalString("transaction_id")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respondInvalidRequest("The deferred credential request is malformed")
                return@post
            }
        if (transactionId == null) {
            call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_transaction_id") })
            return@post
        }
        val resp =
            try {
                service.getDeferredCredential(transactionId, accessToken)
            } catch (e: InvalidTokenException) {
                call.respondInvalidToken(e.message)
                return@post
            }
        if (resp == null) {
            call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_transaction_id") })
        } else {
            call.respond(buildJsonObject { resp.credential?.let { put("credential", it) } })
        }
    }

    // OID4VCI §11: the notification endpoint is authenticated with the access token.
    post("/notification") {
        val accessToken = call.bearerToken()
        if (accessToken == null) {
            call.respondInvalidToken("Missing Bearer access token")
            return@post
        }
        try {
            service.requireAccessToken(accessToken)
        } catch (e: InvalidTokenException) {
            call.respondInvalidToken(e.message)
            return@post
        }
        val notification =
            try {
                call.receive<Oidc4VciNotification>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respondInvalidRequest("The notification request is malformed")
                return@post
            }
        service.recordNotification(notification)
        call.respond(HttpStatusCode.NoContent)
    }
}

/** A string member, `null` when absent or JSON null; a member of any other JSON type is a malformed request. */
private fun JsonObject.optionalString(name: String): String? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    val primitive = value as? JsonPrimitive
    require(primitive != null && primitive.isString) { "'$name' must be a string" }
    return primitive.content
}

/** OAuth-style 400 `invalid_request`. */
private suspend fun ApplicationCall.respondInvalidRequest(description: String?) {
    respond(
        HttpStatusCode.BadRequest,
        buildJsonObject {
            put("error", "invalid_request")
            description?.let { put("error_description", it) }
        },
    )
}

private fun ApplicationCall.bearerToken(): String? {
    val header = request.headers["Authorization"] ?: return null
    if (!header.startsWith("Bearer ", ignoreCase = true)) return null
    return header.substring(7).trim().ifEmpty { null }
}

/** RFC 6750 / OID4VCI: 401 with `WWW-Authenticate: Bearer error="invalid_token"` and a JSON error body. */
private suspend fun ApplicationCall.respondInvalidToken(description: String?) {
    response.header(HttpHeaders.WWWAuthenticate, "Bearer error=\"invalid_token\"")
    respond(
        HttpStatusCode.Unauthorized,
        buildJsonObject {
            put("error", "invalid_token")
            description?.let { put("error_description", it) }
        },
    )
}

@Serializable
data class CreateOfferRequest(
    val credentialTypes: List<String>,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    /** Claims for the credential's `credentialSubject`, carried into the issued credential. */
    val claims: JsonObject = JsonObject(emptyMap()),
)
