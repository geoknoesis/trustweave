package org.trustweave.revocation.bitstring

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Remote (third-party) Bitstring Status List resolution through [RemoteStatusListResolver]. */
class RemoteStatusListTest {
    private val listUrl = "https://status.example.org/lists/1"
    private val remoteIssuer = "did:example:remote-issuer"
    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:remote_bitstring_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                username = "sa"
                password = ""
                maximumPoolSize = 3
            },
        )
    private val json =
        Json {
            serializersModule = SerializationModule.default
            ignoreUnknownKeys = true
        }

    @AfterTest
    fun tearDown() = dataSource.close()

    private fun encodedList(
        sizeBytes: Int,
        vararg setBits: Int,
    ): String {
        val bytes = ByteArray(sizeBytes)
        setBits.forEach { i -> bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (7 - i % 8))).toByte() }
        val gz =
            ByteArrayOutputStream().use { out ->
                GZIPOutputStream(out).use { it.write(bytes) }
                out.toByteArray()
            }
        return "u" + Base64.getUrlEncoder().withoutPadding().encodeToString(gz)
    }

    private fun statusListVc(
        encoded: String,
        purpose: String = "revocation",
        issuer: String = remoteIssuer,
        id: String? = listUrl,
    ): String =
        json.encodeToString(
            VerifiableCredential.serializer(),
            VerifiableCredential(
                id = id?.let { CredentialId(it) },
                type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("BitstringStatusListCredential")),
                issuer = Issuer.IriIssuer(Iri(issuer)),
                issuanceDate = Clock.System.now(),
                credentialSubject =
                    CredentialSubject(
                        id = Iri("$listUrl#list"),
                        claims =
                            mapOf(
                                "type" to JsonPrimitive("BitstringStatusList"),
                                "statusPurpose" to JsonPrimitive(purpose),
                                "encodedList" to JsonPrimitive(encoded),
                            ),
                    ),
            ),
        )

    private class CountingFetcher(
        val body: () -> String,
    ) : StatusListCredentialFetcher {
        var calls = 0

        override suspend fun fetch(url: URI): String {
            calls++
            return body()
        }
    }

    private val acceptAll =
        StatusListCredentialVerifier { vc ->
            VerificationResult.Valid(vc, vc.issuer.id, vc.credentialSubject.id, Clock.System.now(), null)
        }

    private fun manager(resolver: RemoteStatusListResolver?) =
        BitstringStatusListManager(dataSource, InMemoryKeyManagementService(), "did:example:local", remoteStatusLists = resolver)

    private fun credential(
        index: Int,
        purpose: StatusPurpose = StatusPurpose.REVOCATION,
        issuer: String = remoteIssuer,
    ) = VerifiableCredential(
        type = listOf(CredentialType.VerifiableCredential),
        issuer = Issuer.IriIssuer(Iri(issuer)),
        issuanceDate = Clock.System.now(),
        credentialSubject = CredentialSubject(id = Iri("did:example:holder"), claims = emptyMap()),
        credentialStatus =
            CredentialStatus(
                id = StatusListId("$listUrl#$index"),
                type = "BitstringStatusListEntry",
                statusPurpose = purpose,
                statusListIndex = index.toString(),
                statusListCredential = StatusListId(listUrl),
            ),
    )

    private fun assertFailsClosed(
        code: String,
        block: suspend () -> Unit,
    ) {
        val e = assertFailsWith<TrustWeaveException.InvalidState> { runBlocking { block() } }
        assertEquals(code, e.code)
    }

    @Test
    fun `revoked and unrevoked entries of a verified remote list are read correctly`() =
        runBlocking<Unit> {
            val fetcher = CountingFetcher { statusListVc(encodedList(16_384, 42)) }
            val m = manager(RemoteStatusListResolver(acceptAll, fetcher))
            assertTrue(m.checkRevocationStatus(credential(42)).revoked)
            assertFalse(m.checkRevocationStatus(credential(41)).revoked)
            assertTrue(m.checkStatusByIndex(StatusListId(listUrl), 42).revoked)
            assertEquals(1, fetcher.calls, "verified list should be served from cache")
        }

    @Test
    fun `a list that fails proof verification fails closed`() {
        val reject =
            StatusListCredentialVerifier { vc -> VerificationResult.Invalid.InvalidProof(vc, "bad signature") }
        val m = manager(RemoteStatusListResolver(reject, CountingFetcher { statusListVc(encodedList(16_384)) }))
        assertFailsClosed("STATUS_LIST_VERIFICATION_FAILED") { m.checkRevocationStatus(credential(1)) }
    }

    @Test
    fun `a list issued by someone else fails closed`() {
        val m =
            manager(
                RemoteStatusListResolver(acceptAll, CountingFetcher { statusListVc(encodedList(16_384), issuer = "did:example:other") }),
            )
        assertFailsClosed("STATUS_LIST_ISSUER_MISMATCH") { m.checkRevocationStatus(credential(1)) }
    }

    @Test
    fun `entry purpose must match the list purpose`() {
        val m = manager(RemoteStatusListResolver(acceptAll, CountingFetcher { statusListVc(encodedList(16_384), purpose = "suspension") }))
        assertFailsClosed("STATUS_PURPOSE_MISMATCH") { m.checkRevocationStatus(credential(1, StatusPurpose.REVOCATION)) }
    }

    @Test
    fun `a list shorter than the spec minimum fails closed`() {
        val m = manager(RemoteStatusListResolver(acceptAll, CountingFetcher { statusListVc(encodedList(1024)) }))
        assertFailsClosed("STATUS_LIST_LENGTH_ERROR") { m.checkRevocationStatus(credential(1)) }
    }

    @Test
    fun `a decompression bomb is refused`() {
        val m =
            manager(
                RemoteStatusListResolver(
                    acceptAll,
                    CountingFetcher { statusListVc(encodedList(1_000_000)) },
                    maxDecodedBytes = 100_000,
                ),
            )
        assertFailsClosed("STATUS_LIST_TOO_LARGE") { m.checkRevocationStatus(credential(1)) }
    }

    @Test
    fun `a fetch error fails closed`() {
        val m = manager(RemoteStatusListResolver(acceptAll, { throw java.io.IOException("connection reset") }))
        assertFailsClosed("STATUS_LIST_UNAVAILABLE") { m.checkRevocationStatus(credential(1)) }
    }

    @Test
    fun `without a resolver an unknown list still fails closed`() {
        assertFailsClosed("STATUS_LIST_UNAVAILABLE") { manager(null).checkRevocationStatus(credential(1)) }
    }

    @Test
    fun `https fetcher refuses non-https and non-public targets before connecting`() {
        val resolveTo = { ip: String -> HttpsStatusListCredentialFetcher(resolveHost = { listOf(InetAddress.getByName(ip)) }) }
        val url = URI("https://status.example.org/list")
        for (ip in listOf(
            "127.0.0.1",
            "10.1.2.3",
            "192.168.0.1",
            "172.16.0.1",
            "169.254.169.254",
            "100.64.0.1",
            "0.0.0.0",
            "::1",
            "fc00::1",
            "fe80::1",
            "::ffff:127.0.0.1",
        )) {
            assertFailsWith<IllegalArgumentException>("should refuse $ip") { resolveTo(ip).validateTarget(url) }
        }
        resolveTo("93.184.216.34").validateTarget(url)
        assertFailsWith<IllegalArgumentException> { resolveTo("93.184.216.34").validateTarget(URI("http://status.example.org/list")) }
        assertFailsWith<IllegalArgumentException> { resolveTo("93.184.216.34").validateTarget(URI("file:///etc/passwd")) }
        assertFailsWith<IllegalArgumentException> { resolveTo("93.184.216.34").validateTarget(URI("https://user:pw@status.example.org/")) }
    }

    @Test
    fun `a status list credential without an id fails closed`() {
        val m = manager(RemoteStatusListResolver(acceptAll, CountingFetcher { statusListVc(encodedList(16_384), id = null) }))
        assertFailsClosed("STATUS_LIST_MALFORMED") { m.checkRevocationStatus(credential(1)) }
    }

    @Test
    fun `a status list credential whose id differs from the fetched url fails closed`() {
        val m =
            manager(
                RemoteStatusListResolver(
                    acceptAll,
                    CountingFetcher { statusListVc(encodedList(16_384), id = "https://status.example.org/lists/other") },
                ),
            )
        assertFailsClosed("STATUS_LIST_MALFORMED") { m.checkRevocationStatus(credential(1)) }
    }

    @Test
    fun `concurrent misses for one url are fetched and verified once`() =
        runBlocking<Unit> {
            var verifications = 0
            val fetcher =
                object : StatusListCredentialFetcher {
                    var calls = 0

                    override suspend fun fetch(url: URI): String {
                        calls++
                        kotlinx.coroutines.delay(100)
                        return statusListVc(encodedList(16_384, 7))
                    }
                }
            val counting =
                StatusListCredentialVerifier { vc ->
                    verifications++
                    acceptAll.verify(vc)
                }
            val resolver = RemoteStatusListResolver(counting, fetcher)
            val results =
                (1..12)
                    .map { async(kotlinx.coroutines.Dispatchers.Default) { resolver.resolve(listUrl) } }
                    .map { it.await() }
            assertEquals(1, fetcher.calls)
            assertEquals(1, verifications)
            assertTrue(results.all { it.isSet(7) })
        }

    @Test
    fun `failures are negative cached briefly then retried`() =
        runBlocking<Unit> {
            var now = Clock.System.now()
            val clock =
                object : Clock {
                    override fun now() = now
                }
            var fail = true
            val fetcher =
                CountingFetcher {
                    if (fail) throw java.io.IOException("down") else statusListVc(encodedList(16_384, 3))
                }
            val resolver = RemoteStatusListResolver(acceptAll, fetcher, clock = clock, failureCacheTtl = kotlin.time.Duration.parse("30s"))
            repeat(3) {
                val e = assertFailsWith<TrustWeaveException.InvalidState> { resolver.resolve(listUrl) }
                assertEquals("STATUS_LIST_UNAVAILABLE", e.code)
            }
            assertEquals(1, fetcher.calls, "failure must be served from the negative cache")
            fail = false
            now += kotlin.time.Duration.parse("31s")
            assertTrue(resolver.resolve(listUrl).isSet(3))
            assertEquals(2, fetcher.calls)
        }

    @Test
    fun `a cancelled resolve is not cached as a failure`() =
        runBlocking<Unit> {
            var calls = 0
            val fetcher =
                object : StatusListCredentialFetcher {
                    override suspend fun fetch(url: URI): String {
                        if (calls++ == 0) kotlinx.coroutines.awaitCancellation()
                        return statusListVc(encodedList(16_384, 5))
                    }
                }
            val resolver = RemoteStatusListResolver(acceptAll, fetcher)
            val job = launch { resolver.resolve(listUrl) }
            yield()
            job.cancelAndJoin()
            assertTrue(resolver.resolve(listUrl).isSet(5))
            assertEquals(2, calls)
        }

    @Test
    fun `fromCredentialService verifies with revocation checking forced off`() =
        runBlocking<Unit> {
            var seen: org.trustweave.credential.requests.VerificationOptions? = null
            val service =
                object : org.trustweave.credential.CredentialService {
                    override suspend fun verify(
                        credential: VerifiableCredential,
                        trustPolicy: org.trustweave.credential.trust.TrustEvaluator?,
                        options: org.trustweave.credential.requests.VerificationOptions,
                    ): VerificationResult {
                        seen = options
                        return VerificationResult.Valid(
                            credential,
                            credential.issuer.id,
                            credential.credentialSubject.id,
                            Clock.System.now(),
                            null,
                        )
                    }

                    override suspend fun issue(request: org.trustweave.credential.requests.IssuanceRequest) = throw NotImplementedError()

                    override suspend fun createPresentation(
                        credentials: List<VerifiableCredential>,
                        request: org.trustweave.credential.requests.PresentationRequest,
                    ) = throw NotImplementedError()

                    override suspend fun verifyPresentation(
                        presentation: org.trustweave.credential.model.vc.VerifiablePresentation,
                        trustPolicy: org.trustweave.credential.trust.TrustEvaluator?,
                        options: org.trustweave.credential.requests.VerificationOptions,
                    ) = throw NotImplementedError()

                    override suspend fun status(
                        credential: VerifiableCredential,
                        clockSkewTolerance: kotlin.time.Duration,
                    ) = throw NotImplementedError()

                    override fun supports(format: org.trustweave.credential.format.ProofSuiteId) = false

                    override fun supportedFormats() = emptyList<org.trustweave.credential.format.ProofSuiteId>()

                    override fun supportsCapability(
                        format: org.trustweave.credential.format.ProofSuiteId,
                        capability: org.trustweave.credential.spi.proof.ProofEngineCapabilities.() -> Boolean,
                    ) = false
                }
            val resolver = RemoteStatusListResolver.create(service, fetcher = CountingFetcher { statusListVc(encodedList(16_384, 9)) })
            assertTrue(resolver.resolve(listUrl).isSet(9))
            assertEquals(false, seen?.checkRevocation)
        }

    @Test
    fun `documentation ranges and embedded private addresses are refused`() {
        val url = URI("https://status.example.org/list")
        for (ip in listOf("192.0.2.1", "198.51.100.7", "203.0.113.9", "64:ff9b::7f00:1", "2002:c0a8:101::1", "198.18.0.1")) {
            assertFailsWith<IllegalArgumentException>("should refuse $ip") {
                HttpsStatusListCredentialFetcher(resolveHost = { listOf(InetAddress.getByName(ip)) }).validateTarget(url)
            }
        }
    }

    @Test
    fun `a host with one internal record among public ones is refused`() {
        val fetcher =
            HttpsStatusListCredentialFetcher(
                resolveHost = { listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.5")) },
            )
        assertFailsWith<IllegalArgumentException> { fetcher.validateTarget(URI("https://status.example.org/list")) }
    }

    @Test
    fun `a host that rebinds to an internal address between check and connect is refused at connect time`() =
        runBlocking<Unit> {
            var lookups = 0
            val fetcher =
                HttpsStatusListCredentialFetcher(
                    resolveHost = {
                        lookups++
                        // 1st answer (pre-flight): public; 2nd answer (connect): metadata endpoint.
                        listOf(InetAddress.getByName(if (lookups == 1) "93.184.216.34" else "169.254.169.254"))
                    },
                )
            val e = assertFailsWith<Exception> { fetcher.fetch(URI("https://rebind.example.org/list")) }
            assertTrue(generateSequence<Throwable>(e) { it.cause }.any { it.message.orEmpty().contains("169.254.169.254") }, "was: $e")
            assertEquals(2, lookups)
        }

    @Test
    fun `the connect-time resolver hands the socket layer exactly the vetted addresses`() {
        val vetted = listOf(InetAddress.getByName("93.184.216.34"))
        var lookups = 0
        val dns =
            HttpsStatusListCredentialFetcher(resolveHost = {
                lookups++
                vetted
            }).pinnedDns()
        assertEquals(vetted, dns.lookup("status.example.org"))
        assertEquals(1, lookups)
    }

    @Test
    fun `a local status list shadows a remote one with the same url and is never fetched`() =
        runBlocking<Unit> {
            val fetcher = CountingFetcher { statusListVc(encodedList(16_384, 42)) }
            val m = manager(RemoteStatusListResolver(acceptAll, fetcher))
            val local = m.createStatusList("did:example:local", StatusPurpose.REVOCATION, customId = listUrl)
            assertEquals(listUrl, local.value)
            // The local row is authoritative: bit 42 is clear locally although the remote copy has it set.
            assertFalse(m.checkStatusByIndex(StatusListId(listUrl), 42).revoked)
            assertEquals(0, fetcher.calls)
        }

    @Test
    fun `creating a local list after a remote check switches to the local row on the same instance`() =
        runBlocking<Unit> {
            val fetcher = CountingFetcher { statusListVc(encodedList(16_384, 42)) }
            val m = manager(RemoteStatusListResolver(acceptAll, fetcher))
            assertTrue(m.checkStatusByIndex(StatusListId(listUrl), 42).revoked) // remote, now cached as non-local
            m.createStatusList("did:example:local", StatusPurpose.REVOCATION, customId = listUrl)
            assertFalse(m.checkStatusByIndex(StatusListId(listUrl), 42).revoked)
        }

    @Test
    fun `the factory and provider accept a remote resolver`() =
        runBlocking<Unit> {
            val resolver = RemoteStatusListResolver(acceptAll, CountingFetcher { statusListVc(encodedList(16_384, 7)) })
            val viaFactory =
                BitstringStatusListManagerFactory.create(
                    dataSource = dataSource,
                    kms = InMemoryKeyManagementService(),
                    issuerDid = "did:example:local",
                    remoteStatusLists = resolver,
                )
            assertTrue(viaFactory.checkStatusByIndex(StatusListId(listUrl), 7).revoked)

            val provider =
                org.trustweave.revocation.bitstring.spi.BitstringStatusListManagerProvider().also {
                    it.kms = InMemoryKeyManagementService()
                    it.remoteStatusLists = resolver
                }
            val viaProvider = provider.create("bitstring")
            assertTrue(viaProvider.checkStatusByIndex(StatusListId(listUrl), 7).revoked)
        }

    @Test
    fun `without a resolver the factory-built manager still fails closed on a remote list`() {
        val m = BitstringStatusListManagerFactory.create(dataSource, InMemoryKeyManagementService(), "did:example:local")
        assertFailsClosed("STATUS_LIST_UNAVAILABLE") { m.checkStatusByIndex(StatusListId(listUrl), 1) }
    }
}
