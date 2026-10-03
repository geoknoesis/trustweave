package org.trustweave.revocation.bitstring

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.BitSet
import java.util.zip.GZIPInputStream
import kotlin.time.Duration.Companion.minutes

/** Retrieves the serialized status list credential published at a URL. */
fun interface StatusListCredentialFetcher {
    /**
     * @return the response body (a JSON-serialized status list credential)
     * @throws Exception when the credential cannot be retrieved; the caller fails closed.
     */
    suspend fun fetch(url: URI): String
}

/**
 * Verifies a fetched status list credential's proof (and validity period). Typically
 * `{ credentialService.verify(it, VerificationOptions(checkRevocation = false)) }` — the status
 * list credential itself is not status-checked, which would recurse.
 */
fun interface StatusListCredentialVerifier {
    suspend fun verify(credential: VerifiableCredential): VerificationResult
}

/**
 * HTTPS fetcher with SSRF-safe defaults.
 *
 * - only `https:` URLs without user-info are fetched;
 * - every address the host resolves to must be public — loopback, private (RFC 1918, ULA),
 *   link-local (incl. cloud metadata `169.254.169.254`), CGNAT, multicast and unspecified
 *   addresses are refused;
 * - redirects are not followed, the response must be `200`, and the body is capped at
 *   [maxResponseBytes].
 *
 * Residual risk: the JDK client resolves the host again when connecting, so a DNS-rebinding
 * attacker controlling the status list host's DNS could still race the check. Deployments that
 * need a hard guarantee should also restrict egress at the network layer.
 */
class HttpsStatusListCredentialFetcher(
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    private val timeout: Duration = Duration.ofSeconds(10),
    private val resolveHost: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build(),
) : StatusListCredentialFetcher {
    override suspend fun fetch(url: URI): String =
        withContext(Dispatchers.IO) {
            validateTarget(url)
            val request =
                HttpRequest
                    .newBuilder(url)
                    .timeout(timeout)
                    .header("Accept", "application/vc+ld+json, application/vc, application/ld+json, application/json")
                    .GET()
                    .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { body ->
                if (response.statusCode() != 200) {
                    throw IllegalStateException("status list fetch returned HTTP ${response.statusCode()}")
                }
                String(readCapped(body, maxResponseBytes), Charsets.UTF_8)
            }
        }

    /** Throws [IllegalArgumentException] when [url] is not a permitted public HTTPS target. */
    fun validateTarget(url: URI) {
        require(url.scheme.equals("https", ignoreCase = true)) { "status list URL must use https: $url" }
        require(url.rawUserInfo == null) { "status list URL must not carry user info" }
        val host = url.host ?: throw IllegalArgumentException("status list URL has no host: $url")
        val addresses = resolveHost(host.removePrefix("[").removeSuffix("]"))
        require(addresses.isNotEmpty()) { "status list host $host did not resolve" }
        addresses.forEach { address ->
            require(isPublic(address)) { "status list host $host resolves to non-public address ${address.hostAddress}" }
        }
    }

    companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES: Int = 2 * 1024 * 1024

        internal fun isPublic(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
            ) {
                return false
            }
            val bytes = address.address
            return when (address) {
                is Inet4Address -> {
                    val b0 = bytes[0].toInt() and 0xff
                    val b1 = bytes[1].toInt() and 0xff
                    !(b0 == 0 || (b0 == 100 && b1 in 64..127) || (b0 == 192 && b1 == 0 && (bytes[2].toInt() and 0xff) == 0) || b0 >= 240)
                }
                is Inet6Address -> {
                    val b0 = bytes[0].toInt() and 0xff
                    val ula = (b0 and 0xfe) == 0xfc
                    val mapped = bytes.copyOfRange(0, 10).all { it.toInt() == 0 } && bytes[10].toInt() == -1 && bytes[11].toInt() == -1
                    !ula && !(mapped && !isPublic(InetAddress.getByAddress(bytes.copyOfRange(12, 16))))
                }
                else -> false
            }
        }

        internal fun readCapped(
            input: InputStream,
            maxBytes: Int,
        ): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw IllegalStateException("response exceeds $maxBytes bytes")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}

/** A status list fetched from its publisher and verified. */
data class RemoteStatusList(
    val url: String,
    val issuer: String,
    val purpose: StatusPurpose,
    val sizeBits: Int,
    internal val bits: BitSet,
) {
    fun isSet(index: Int): Boolean = bits.get(index)
}

/**
 * Resolves remote W3C Bitstring Status List credentials for [BitstringStatusListManager].
 *
 * Every failure — fetch error, oversize or malformed list, failed proof verification, issuer or
 * type mismatch, a list shorter than the spec minimum — throws [TrustWeaveException.InvalidState],
 * so the status check fails closed. Successfully verified lists are cached for [cacheTtl] (or the
 * credential's own `ttl`, if shorter) in a bounded in-memory cache.
 *
 * @param maxDecodedBytes Cap on the decompressed bitstring (GZIP-bomb guard).
 */
class RemoteStatusListResolver(
    private val verifier: StatusListCredentialVerifier,
    private val fetcher: StatusListCredentialFetcher = HttpsStatusListCredentialFetcher(),
    private val maxDecodedBytes: Int = DEFAULT_MAX_DECODED_BYTES,
    private val cacheTtl: kotlin.time.Duration = 5.minutes,
    private val maxCacheEntries: Int = 256,
    private val clock: Clock = Clock.System,
) {
    private data class CacheEntry(
        val list: RemoteStatusList,
        val expiresAt: Instant,
    )

    private val cache =
        object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > maxCacheEntries
        }

    /**
     * Fetches, verifies and decodes the status list credential at [url].
     *
     * @param expectedIssuer When set, the status list credential must be issued by this DID/IRI.
     */
    suspend fun resolve(
        url: String,
        expectedIssuer: String? = null,
    ): RemoteStatusList {
        val now = clock.now()
        val cached = synchronized(cache) { cache[url] }?.takeIf { it.expiresAt > now }?.list
        val list = cached ?: fetchAndVerify(url, now)
        if (expectedIssuer != null && list.issuer != expectedIssuer) {
            throw failure(
                "STATUS_LIST_ISSUER_MISMATCH",
                "status list $url is issued by ${list.issuer}, not by the credential issuer $expectedIssuer",
                url,
            )
        }
        return list
    }

    private suspend fun fetchAndVerify(
        url: String,
        now: Instant,
    ): RemoteStatusList {
        val uri =
            try {
                URI(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw failure("STATUS_LIST_UNAVAILABLE", "status list URL is invalid: ${e.message}", url, e)
            }
        val body =
            try {
                fetcher.fetch(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw failure("STATUS_LIST_UNAVAILABLE", "could not fetch status list $url: ${e.message}", url, e)
            }
        val credential =
            try {
                json.decodeFromString(VerifiableCredential.serializer(), body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw failure("STATUS_LIST_MALFORMED", "status list $url is not a JSON verifiable credential: ${e.message}", url, e)
            }
        if (credential.type.none { it.value == "BitstringStatusListCredential" }) {
            throw failure("STATUS_LIST_MALFORMED", "credential at $url is not a BitstringStatusListCredential", url)
        }
        credential.id?.let { id ->
            if (id.value != url) throw failure("STATUS_LIST_MALFORMED", "status list credential id ${id.value} does not match $url", url)
        }
        val verification =
            try {
                verifier.verify(credential)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw failure("STATUS_LIST_VERIFICATION_FAILED", "verifying status list $url threw: ${e.message}", url, e)
            }
        if (verification !is VerificationResult.Valid) {
            throw failure(
                "STATUS_LIST_VERIFICATION_FAILED",
                "status list $url failed verification: ${(verification as VerificationResult.Invalid).allErrors}",
                url,
            )
        }

        val claims = credential.credentialSubject.claims
        if ((claims["type"] as? JsonPrimitive)?.content != "BitstringStatusList") {
            throw failure("STATUS_LIST_MALFORMED", "credentialSubject.type of $url is not BitstringStatusList", url)
        }
        val purpose = parsePurpose(claims["statusPurpose"], url)
        val encoded =
            (claims["encodedList"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw failure("STATUS_LIST_MALFORMED", "status list $url has no encodedList", url)
        val bytes = decodeEncodedList(encoded, maxDecodedBytes, url)
        if (bytes.size * 8 < BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS) {
            throw failure(
                "STATUS_LIST_LENGTH_ERROR",
                "status list $url decodes to ${bytes.size * 8} bits, below the spec minimum " +
                    "${BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS}",
                url,
            )
        }
        val list = RemoteStatusList(url, credential.issuer.id.value, purpose, bytes.size * 8, msbFirstBits(bytes))

        val ttlMs = (claims["ttl"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
        val ttl = ttlMs?.let { minOf(cacheTtl, kotlin.time.Duration.parse("${it}ms")) } ?: cacheTtl
        synchronized(cache) { cache[url] = CacheEntry(list, now + ttl) }
        return list
    }

    private fun parsePurpose(
        element: kotlinx.serialization.json.JsonElement?,
        url: String,
    ): StatusPurpose {
        val value =
            when (element) {
                is JsonPrimitive -> element.content
                is JsonArray -> (element.singleOrNull() as? JsonPrimitive)?.content
                else -> null
            } ?: throw failure("STATUS_LIST_MALFORMED", "status list $url must declare exactly one statusPurpose", url)
        return StatusPurpose.entries.firstOrNull { it.stringValue == value }
            ?: throw failure("STATUS_LIST_UNSUPPORTED_PURPOSE", "status purpose '$value' of $url is not supported", url)
    }

    companion object {
        /** 16 MiB of decoded bitstring = 134,217,728 entries. */
        const val DEFAULT_MAX_DECODED_BYTES: Int = 16 * 1024 * 1024

        private val json =
            Json {
                serializersModule = SerializationModule.default
                ignoreUnknownKeys = true
            }

        internal fun decodeEncodedList(
            encoded: String,
            maxDecodedBytes: Int,
            url: String,
        ): ByteArray {
            if (!encoded.startsWith("u")) {
                throw failure("STATUS_LIST_MALFORMED", "encodedList of $url is not multibase base64url ('u' prefix)", url)
            }
            val gzipped =
                try {
                    Base64.getUrlDecoder().decode(encoded.substring(1))
                } catch (e: IllegalArgumentException) {
                    throw failure("STATUS_LIST_MALFORMED", "encodedList of $url is not base64url", url, e)
                }
            return try {
                GZIPInputStream(gzipped.inputStream()).use { HttpsStatusListCredentialFetcher.readCapped(it, maxDecodedBytes) }
            } catch (e: IllegalStateException) {
                throw failure("STATUS_LIST_TOO_LARGE", "encodedList of $url exceeds $maxDecodedBytes decoded bytes", url, e)
            } catch (e: java.io.IOException) {
                throw failure("STATUS_LIST_MALFORMED", "encodedList of $url is not valid GZIP: ${e.message}", url, e)
            }
        }

        private fun msbFirstBits(bytes: ByteArray): BitSet {
            val bits = BitSet(bytes.size * 8)
            for (i in bytes.indices) {
                val b = bytes[i].toInt()
                if (b == 0) continue
                for (j in 0..7) if ((b and (1 shl (7 - j))) != 0) bits.set(i * 8 + j)
            }
            return bits
        }

        private fun failure(
            code: String,
            message: String,
            url: String,
            cause: Throwable? = null,
        ) = TrustWeaveException.InvalidState(
            code = code,
            message = "$message. Failing closed: credential status is unknown, not valid.",
            context = mapOf("statusListCredential" to url),
            cause = cause,
        )
    }
}
