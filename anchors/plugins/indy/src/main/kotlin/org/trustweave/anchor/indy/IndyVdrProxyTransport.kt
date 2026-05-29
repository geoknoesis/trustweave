package org.trustweave.anchor.indy

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.trustweave.anchor.exceptions.BlockchainException

/**
 * HTTP transport that submits Indy ledger requests via an `indy-vdr-proxy` instance.
 *
 * `indy-vdr-proxy` is the canonical HTTP gateway to an Indy pool. It is shipped by
 * the Hyperledger Indy project as a Rocket-based service that wraps the native
 * `indy-vdr` Rust library and forwards JSON requests over ZeroMQ to the validator nodes.
 *
 * The two endpoints used here are:
 * - `POST /submit` — accepts a signed write request (e.g. ATTRIB) and returns the
 *   ledger's REPLY envelope as JSON.
 * - `POST /submit` (with read body) — accepts a read request (e.g. GET_ATTRIB) and
 *   returns the ledger's read REPLY envelope as JSON. Reads are unsigned.
 *
 * See: https://github.com/hyperledger/indy-vdr/tree/main/indy-vdr-proxy
 */
internal class IndyVdrProxyTransport(
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) {

    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "Indy vdr-proxy baseUrl must include scheme: $baseUrl"
        }
    }

    suspend fun submit(request: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val response: HttpResponse = httpClient.post(buildUrl("submit")) {
            contentType(ContentType.Application.Json)
            headers { append(HttpHeaders.Accept, ContentType.Application.Json.toString()) }
            setBody(IndyRequestCodec.json.encodeToString(JsonObject.serializer(), request))
        }
        decode(response, "submit")
    }

    /**
     * Health probe — pings the proxy's status endpoint. Returns `true` if the proxy is
     * reachable and reports `READY` (or any 2xx with a body).
     */
    suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp: HttpResponse = httpClient.get(buildUrl("status"))
            resp.status.isSuccess()
        } catch (t: Throwable) {
            false
        }
    }

    private fun buildUrl(path: String): String {
        val trimmed = baseUrl.trimEnd('/')
        val suffix = path.trimStart('/')
        return "$trimmed/$suffix"
    }

    private suspend fun decode(response: HttpResponse, op: String): JsonObject {
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw BlockchainException.ConnectionFailed(
                chainId = null,
                endpoint = baseUrl,
                reason = "indy-vdr-proxy $op returned HTTP ${response.status.value}: $body"
            )
        }
        val parsed = try {
            IndyRequestCodec.json.parseToJsonElement(body).jsonObject
        } catch (t: Throwable) {
            throw BlockchainException.TransactionFailed(
                chainId = null,
                operation = op,
                reason = "indy-vdr-proxy returned non-JSON body: $body"
            )
        }

        val opField = parsed["op"]
        if (opField != null && opField.toString().trim('"') == "REJECT") {
            throw BlockchainException.TransactionFailed(
                chainId = null,
                operation = op,
                reason = "indy ledger rejected request: $body"
            )
        }
        return parsed
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 30_000

        /** HTTP status code used by indy-vdr-proxy for ledger NACKs. */
        val LEDGER_REJECTED: HttpStatusCode = HttpStatusCode(409, "Ledger Rejected")
    }
}
