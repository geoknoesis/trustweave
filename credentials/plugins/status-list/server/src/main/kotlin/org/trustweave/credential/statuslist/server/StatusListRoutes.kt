package org.trustweave.credential.statuslist.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.observability.HostRequestId
import org.trustweave.revocation.bitstring.BitstringStatusListManager
import org.trustweave.revocation.token.TokenStatusListManager

private val logger = org.slf4j.LoggerFactory.getLogger("org.trustweave.credential.statuslist.server")

/** Fixed safe categories; provider exception classes/messages can themselves contain private data. */
internal fun statusListErrorCode(failure: Exception): String =
    when (failure) {
        is java.sql.SQLTimeoutException, is java.net.SocketTimeoutException -> "TIMEOUT"
        is java.sql.SQLException -> "STORAGE_FAILURE"
        is org.trustweave.core.exception.ConfigException -> "CONFIGURATION_FAILURE"
        else -> "INTERNAL_FAILURE"
    }

private fun ApplicationCall.statusListRequestId(): String {
    attributes.getOrNull(HostRequestId)?.let { return it }
    val requestId =
        java.util.UUID
            .randomUUID()
            .toString()
    response.headers.append("X-Request-ID", requestId)
    return requestId
}

private val json =
    Json {
        serializersModule = SerializationModule.default
        ignoreUnknownKeys = true
        prettyPrint = true
    }

/**
 * Configures status-list serving routes.
 *
 * `GET /status-lists/{id}` — Bitstring Status List credential (JSON-LD).
 * `GET /token-status-lists/{id}` — Token Status List JWT.
 */
fun Routing.configureStatusListRoutes(
    bitstringManager: BitstringStatusListManager?,
    tokenManager: TokenStatusListManager?,
) {
    /**
     * GET /status-lists/{id}
     *
     * Returns the signed W3C BitstringStatusListCredential for the given status list ID.
     * Content-Type: application/vc+ld+json
     */
    get("/status-lists/{id}") {
        val requestId = call.statusListRequestId()
        val id =
            call.parameters["id"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("MISSING_ID", "Missing status list ID", requestId),
                )

        if (bitstringManager == null) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("NOT_CONFIGURED", "Bitstring status list manager not configured", requestId),
            )
            return@get
        }

        try {
            val vc: VerifiableCredential = bitstringManager.buildStatusListVc(StatusListId(id))
            val vcJson = json.encodeToString(VerifiableCredential.serializer(), vc)
            call.respondText(vcJson, ContentType.parse("application/vc+ld+json"), HttpStatusCode.OK)
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("NOT_FOUND", "Status list not found", requestId),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("event=status_list_failure operation=read error_code={} request_id={}", statusListErrorCode(e), requestId)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "Unable to retrieve status list", requestId),
            )
        }
    }

    /**
     * GET /token-status-lists/{id}
     *
     * Returns the signed IETF Token Status List JWT for the given status list ID.
     * Content-Type: application/statuslist+jwt
     */
    get("/token-status-lists/{id}") {
        val requestId = call.statusListRequestId()
        val id =
            call.parameters["id"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("MISSING_ID", "Missing status list ID", requestId),
                )

        if (tokenManager == null) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("NOT_CONFIGURED", "Token status list manager not configured", requestId),
            )
            return@get
        }

        try {
            val token = tokenManager.buildStatusListToken(StatusListId(id))
            call.respondText(token.jwt, ContentType.parse("application/statuslist+jwt"), HttpStatusCode.OK)
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("NOT_FOUND", "Token status list not found", requestId),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("event=status_list_failure operation=read error_code={} request_id={}", statusListErrorCode(e), requestId)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "Unable to retrieve status list", requestId),
            )
        }
    }
}

@Serializable
private data class ErrorResponse(
    val error: String,
    val message: String,
    val requestId: String,
)
