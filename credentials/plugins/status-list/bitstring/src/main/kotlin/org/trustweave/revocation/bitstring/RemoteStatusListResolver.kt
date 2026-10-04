package org.trustweave.revocation.bitstring

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.net.PrivateNetworkGuard
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.CredentialService
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.trust.TrustEvaluator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.time.Duration
import java.util.Base64
import java.util.BitSet
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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

    companion object {
        /**
         * Default adapter over [CredentialService.verify] with `checkRevocation = false` (the status
         * list credential is not itself status-checked, which would recurse). The proof, issuer DID
         * resolution and validity period are verified by the service; pass a [trustPolicy] to also
         * require the status list issuer to be trusted. [options] is applied with revocation
         * checking forced off.
         */
        @JvmStatic
        @JvmOverloads
        fun fromCredentialService(
            service: CredentialService,
            trustPolicy: TrustEvaluator? = null,
            options: VerificationOptions = VerificationOptions(),
        ): StatusListCredentialVerifier {
            val effective = options.copy(checkRevocation = false)
            return StatusListCredentialVerifier { service.verify(it, trustPolicy, effective) }
        }
    }
}

/**
 * HTTPS fetcher with SSRF-safe defaults.
 *
 * - only `https:` URLs without user-info are fetched;
 * - every address the host resolves to must be public — loopback, private (RFC 1918, ULA),
 *   link-local (incl. cloud metadata `169.254.169.254`), CGNAT, documentation (TEST-NET),
 *   reserved, multicast, unspecified and IPv6-embedded-IPv4 (NAT64, 6to4, compatible) addresses
 *   are refused (see [org.trustweave.core.net.PrivateNetworkGuard]);
 * - **the connection is pinned to the vetted addresses**: the host is resolved exactly once, by a
 *   [Dns] hook that validates every record and hands the same [InetAddress] list to the socket
 *   layer, so a DNS-rebinding attacker cannot return a public address to the check and an internal
 *   one to the connect. TLS still uses the original host name for SNI and certificate
 *   verification;
 * - the request is cancellable: cancelling the coroutine cancels the underlying OkHttp call;
 * - redirects are not followed, system proxies are not used (a proxy would resolve the host
 *   itself and bypass the pin), the response must be `200`, and the body is capped at
 *   [maxResponseBytes].
 */
class HttpsStatusListCredentialFetcher(
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    private val timeout: Duration = Duration.ofSeconds(10),
    private val resolveHost: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
) : StatusListCredentialFetcher {
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .proxy(Proxy.NO_PROXY)
            .dns(pinnedDns())
            // No idle connection reuse: every fetch re-resolves and re-validates.
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .connectTimeout(Duration.ofSeconds(5))
            .callTimeout(timeout)
            .build()

    /**
     * The single resolution step: resolves [host] once and refuses the lookup unless every
     * address is public. The returned list is exactly what the HTTP client connects to.
     */
    internal fun pinnedDns(): Dns =
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = resolveHost(hostname.removePrefix("[").removeSuffix("]"))
                require(addresses.isNotEmpty()) { "status list host $hostname did not resolve" }
                addresses.forEach { address ->
                    require(isPublic(address)) { "status list host $hostname resolves to non-public address ${address.hostAddress}" }
                }
                return addresses
            }
        }

    override suspend fun fetch(url: URI): String =
        withContext(Dispatchers.IO) {
            validateTarget(url)
            val request =
                Request
                    .Builder()
                    .url(url.toString())
                    .header("Accept", "application/vc+ld+json, application/vc, application/ld+json, application/json")
                    .get()
                    .build()
            client.newCall(request).awaitBody(maxResponseBytes)
        }

    /** Throws [IllegalArgumentException] when [url] is not a permitted public HTTPS target. */
    fun validateTarget(url: URI) {
        require(url.scheme.equals("https", ignoreCase = true)) { "status list URL must use https: $url" }
        require(url.rawUserInfo == null) { "status list URL must not carry user info" }
        val host = url.host ?: throw IllegalArgumentException("status list URL has no host: $url")
        // IP-literal hosts never reach the Dns hook, so they are vetted here as well.
        pinnedDns().lookup(host)
    }

    companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES: Int = 2 * 1024 * 1024

        /**
         * Runs [this] call asynchronously and suspends for its (capped) body. Cancelling the
         * coroutine calls [Call.cancel], which aborts the connect, the request and the body read, so
         * a cancelled or timed-out caller does not leave a thread blocked on a stalled endpoint.
         */
        internal suspend fun Call.awaitBody(maxBytes: Int): String =
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { runCatching { cancel() } }
                enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            continuation.resumeWithException(e)
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            val outcome =
                                runCatching {
                                    response.use {
                                        if (it.code != 200) {
                                            throw IllegalStateException("status list fetch returned HTTP ${it.code}")
                                        }
                                        val body = it.body ?: throw IllegalStateException("status list fetch returned no body")
                                        String(readCapped(body.byteStream(), maxBytes), Charsets.UTF_8)
                                    }
                                }
                            outcome.fold(
                                onSuccess = { continuation.resume(it) },
                                onFailure = { continuation.resumeWithException(it) },
                            )
                        }
                    },
                )
            }

        /** Documentation ranges (RFC 5737) that [PrivateNetworkGuard] does not list. */
        private fun isDocumentationIpv4(address: InetAddress): Boolean {
            val b = address.address
            if (b.size != 4) return false
            val o0 = b[0].toInt() and 0xff
            val o1 = b[1].toInt() and 0xff
            val o2 = b[2].toInt() and 0xff
            return (o0 == 192 && o1 == 0 && o2 == 2) ||
                (o0 == 198 && o1 == 51 && o2 == 100) ||
                (o0 == 203 && o1 == 0 && o2 == 113)
        }

        internal fun isPublic(address: InetAddress): Boolean {
            if (PrivateNetworkGuard.isDisallowed(address) || isDocumentationIpv4(address)) return false
            // An IPv6 form embedding a TEST-NET address (NAT64 / 6to4 / compatible) is not public either.
            val b = address.address
            if (b.size == 16) {
                val embedded =
                    when {
                        (0..11).all { b[it].toInt() == 0 } -> 12
                        b[0].toInt() == 0x00 &&
                            b[1].toInt() == 0x64 &&
                            (b[2].toInt() and 0xff) == 0xff &&
                            (b[3].toInt() and 0xff) == 0x9b -> 12
                        (b[0].toInt() and 0xff) == 0x20 && b[1].toInt() == 0x02 -> 2
                        else -> -1
                    }
                if (embedded >= 0 && isDocumentationIpv4(InetAddress.getByAddress(b.copyOfRange(embedded, embedded + 4)))) return false
            }
            return true
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
 * type mismatch, a status list credential whose `id` is missing or differs from the URL it was
 * fetched from, a list shorter than the spec minimum — throws [TrustWeaveException.InvalidState],
 * so the status check fails closed.
 *
 * **Caching and revocation latency.** Verified lists are cached for [cacheTtl] (default 5 minutes,
 * or the credential's own `ttl` if shorter) in a bounded in-memory cache. A credential revoked at
 * its issuer therefore keeps verifying as "not revoked" here for up to that long; lower [cacheTtl]
 * where that latency is unacceptable. Failures are negative-cached for [failureCacheTtl] (default
 * 30 seconds, `0` disables) so an unavailable or hostile endpoint is not hammered; during that
 * window the same failure is rethrown without a network call.
 *
 * **Concurrency.** Concurrent misses for the same URL are single-flighted: one caller fetches and
 * verifies, the others wait and read the outcome from the cache.
 *
 * **Enabling it.** Remote resolution is off unless a resolver is passed to the manager, so unknown
 * status lists fail closed by default. Build one with [create] (from a `CredentialService`) or the
 * constructor (from any [StatusListCredentialVerifier]) and hand it to
 * [BitstringStatusListManagerFactory.create] / `BitstringStatusListManagerProvider.remoteStatusLists`.
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
    private val failureCacheTtl: kotlin.time.Duration = 30.seconds,
) {
    private data class CacheEntry(
        val list: RemoteStatusList,
        val expiresAt: Instant,
    )

    private data class FailureEntry(
        val error: TrustWeaveException.InvalidState,
        val expiresAt: Instant,
    )

    private class Flight {
        val mutex = Mutex()
        var refs = 0
    }

    private val cache =
        object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > maxCacheEntries
        }

    private val failures =
        object : LinkedHashMap<String, FailureEntry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FailureEntry>?): Boolean = size > maxCacheEntries
        }

    private val flights = HashMap<String, Flight>()

    /**
     * Fetches, verifies and decodes the status list credential at [url].
     *
     * @param expectedIssuer When set, the status list credential must be issued by this DID/IRI.
     */
    suspend fun resolve(
        url: String,
        expectedIssuer: String? = null,
    ): RemoteStatusList {
        val list = cachedOrFail(url) ?: singleFlight(url) { cachedOrFail(url) ?: fetchAndVerifyCaching(url) }
        if (expectedIssuer != null && list.issuer != expectedIssuer) {
            throw failure(
                "STATUS_LIST_ISSUER_MISMATCH",
                "status list $url is issued by ${list.issuer}, not by the credential issuer $expectedIssuer",
                url,
            )
        }
        return list
    }

    /** The cached list, a rethrow of a recent cached failure, or `null` when neither is held. */
    private fun cachedOrFail(url: String): RemoteStatusList? {
        val now = clock.now()
        synchronized(cache) { cache[url] }?.takeIf { it.expiresAt > now }?.let { return it.list }
        val recent = synchronized(failures) { failures[url] }?.takeIf { it.expiresAt > now }
        if (recent != null) {
            throw TrustWeaveException.InvalidState(
                code = recent.error.code,
                message = recent.error.message,
                context = recent.error.context,
                cause = recent.error,
            )
        }
        return null
    }

    private suspend fun <T> singleFlight(
        url: String,
        block: suspend () -> T,
    ): T {
        val flight = synchronized(flights) { flights.getOrPut(url) { Flight() }.also { it.refs++ } }
        try {
            return flight.mutex.withLock { block() }
        } finally {
            synchronized(flights) { if (--flight.refs == 0) flights.remove(url) }
        }
    }

    private suspend fun fetchAndVerifyCaching(url: String): RemoteStatusList =
        try {
            fetchAndVerify(url, clock.now())
        } catch (e: TrustWeaveException.InvalidState) {
            if (failureCacheTtl.isPositive()) {
                synchronized(failures) { failures[url] = FailureEntry(e, clock.now() + failureCacheTtl) }
            }
            throw e
        }

    private suspend fun fetchAndVerify(
        url: String,
        now: Instant,
    ): RemoteStatusList {
        val uri =
            try {
                URI(url)
            } catch (e: java.net.URISyntaxException) {
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
            } catch (e: kotlinx.serialization.SerializationException) {
                throw failure("STATUS_LIST_MALFORMED", "status list $url is not a JSON verifiable credential: ${e.message}", url, e)
            } catch (e: IllegalArgumentException) {
                throw failure("STATUS_LIST_MALFORMED", "status list $url is not a JSON verifiable credential: ${e.message}", url, e)
            }
        if (credential.type.none { it.value == "BitstringStatusListCredential" }) {
            throw failure("STATUS_LIST_MALFORMED", "credential at $url is not a BitstringStatusListCredential", url)
        }
        // The id binds the signed credential to the location it was published at; without it a
        // validly signed list for a different URL (or an id-less one) could be replayed here.
        val id = credential.id?.value
        if (id == null) {
            throw failure(
                "STATUS_LIST_MALFORMED",
                "status list credential at $url has no id; it must equal the URL it is fetched from",
                url,
            )
        }
        if (id != url) {
            throw failure("STATUS_LIST_MALFORMED", "status list credential id $id does not match $url", url)
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
        /**
         * Ready-to-use resolver: fetches over SSRF-hardened HTTPS ([HttpsStatusListCredentialFetcher])
         * and verifies each status list credential with [service]
         * (`verify(checkRevocation = false)`, see [StatusListCredentialVerifier.fromCredentialService]).
         */
        @JvmStatic
        @JvmOverloads
        fun create(
            service: CredentialService,
            trustPolicy: TrustEvaluator? = null,
            cacheTtl: kotlin.time.Duration = 5.minutes,
            fetcher: StatusListCredentialFetcher = HttpsStatusListCredentialFetcher(),
        ): RemoteStatusListResolver =
            RemoteStatusListResolver(
                verifier = StatusListCredentialVerifier.fromCredentialService(service, trustPolicy),
                fetcher = fetcher,
                cacheTtl = cacheTtl,
            )

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
