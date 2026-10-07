package org.trustweave.kms.cyberark

import okhttp3.OkHttpClient
import okhttp3.Request

/** A vendor HTTP response that has been fully read and closed. */
internal class ClosedHttpResponse(
    val code: Int,
    val message: String,
    val body: String?,
) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * Executes [request], reads the body and always closes the response, whatever the status.
 */
internal fun OkHttpClient.callAndClose(request: Request): ClosedHttpResponse =
    newCall(request).execute().use { ClosedHttpResponse(it.code, it.message, it.body?.string()) }

/**
 * The response body, or an error naming [what] when the vendor returned no body: a missing body is
 * never treated as an empty JSON object.
 */
internal fun String?.requireBody(what: String): String {
    if (isNullOrBlank()) throw IllegalStateException("$what: the vendor API returned an empty response body")
    return this
}
