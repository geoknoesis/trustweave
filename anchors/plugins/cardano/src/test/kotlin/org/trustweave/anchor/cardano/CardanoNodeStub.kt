package org.trustweave.anchor.cardano

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

internal const val CIP20_METADATA_BODY = """[{"label":"674","json_metadata":{"msg":["{\"hello\":\"world\",\"n\":","7}"]}}]"""

/**
 * Routes the Blockfrost endpoints a Cardano anchor read touches: `/txs/{hash}/metadata`, `/txs/{hash}` and
 * `/blocks/latest`. A null body answers 404.
 */
internal fun cardanoDispatcher(
    metadataBody: String? = CIP20_METADATA_BODY,
    txBody: String? = null,
    latestBlockBody: String? = null,
): Dispatcher =
    object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty().substringBefore('?')
            val body =
                when {
                    path.endsWith("/metadata") -> metadataBody
                    path.endsWith("/blocks/latest") -> latestBlockBody
                    path.contains("/txs/") -> txBody
                    else -> null
                }
            return if (body ==
                null
            ) {
                MockResponse().setResponseCode(404).setBody("""{"status_code":404,"error":"Not Found","message":"x"}""")
            } else {
                MockResponse().setResponseCode(200).setBody(body)
            }
        }
    }

internal fun txContentBody(
    hash: String,
    block: String? = "b".repeat(64),
    height: Int? = 100,
    time: Long? = 1_700_000_000L,
): String =
    buildString {
        append("{\"hash\":\"$hash\"")
        if (block != null) append(",\"block\":\"$block\"")
        if (height != null) append(",\"block_height\":$height")
        if (time != null) append(",\"block_time\":$time")
        append("}")
    }

internal fun latestBlockBody(height: Int): String = "{\"hash\":\"${"c".repeat(64)}\",\"height\":$height,\"time\":1700000100}"
