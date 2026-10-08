package org.trustweave.credential.statuslist.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import java.security.MessageDigest
import java.util.Base64

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
 *
 * Both answer with `Cache-Control: public, max-age=<cacheMaxAge>` and an `ETag`, and honour
 * `If-None-Match` with 304, so verifiers and intermediaries do not hit the signing path on every
 * check. [cacheMaxAge] is also the longest a revocation can stay unseen by a caching client; set
 * it to [kotlin.time.Duration.ZERO] for `no-cache` (revalidate every time). The signed Bitstring
 * credential itself is cached by the manager until the list changes.
 */
@JvmOverloads
fun Routing.configureStatusListRoutes(
    bitstringManager: BitstringStatusListManager?,
    tokenManager: TokenStatusListManager?,
    cacheMaxAge: kotlin.time.Duration = DEFAULT_CACHE_MAX_AGE,
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
            call.respondCacheable(vcJson, ContentType.parse("application/vc+ld+json"), cacheMaxAge)
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
            call.respondCacheable(token.jwt, ContentType.parse("application/statuslist+jwt"), cacheMaxAge)
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

/** Default `max-age` of status-list responses. */
val DEFAULT_CACHE_MAX_AGE: kotlin.time.Duration = kotlin.time.Duration.parse("1m")

private suspend fun ApplicationCall.respondCacheable(
    body: String,
    contentType: ContentType,
    maxAge: kotlin.time.Duration,
) {
    val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))
    val etag = "\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest) + "\""
    response.headers.append(
        HttpHeaders.CacheControl,
        if (maxAge.isPositive()) "public, max-age=${maxAge.inWholeSeconds}" else "no-cache",
    )
    response.headers.append(HttpHeaders.ETag, etag)
    val presented = request.headers[HttpHeaders.IfNoneMatch]
    if (presented != null && presented.split(',').any { it.trim().removePrefix("W/") == etag }) {
        respond(HttpStatusCode.NotModified)
    } else {
        respondText(body, contentType, HttpStatusCode.OK)
    }
}

@Serializable
private data class ErrorResponse(
    val error: String,
    val message: String,
    val requestId: String,
)
