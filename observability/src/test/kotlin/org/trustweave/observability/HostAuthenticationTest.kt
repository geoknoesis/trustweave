package org.trustweave.observability

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The embedded servers this SDK ships perform key custody operations and previously carried no
 * authentication primitive at all. These tests pin the two properties that matter: an
 * unconfigured server refuses to mutate, and a configured one refuses the wrong caller.
 */
class HostAuthenticationTest {
    private val token = "t".repeat(40)

    private fun Application.echoRoutes() {
        routing {
            route("/thing") {
                get { call.respond(HttpStatusCode.OK, "read") }
                post { call.respond(HttpStatusCode.Created, "written") }
                delete { call.respond(HttpStatusCode.OK, "deleted") }
            }
            get("/internal/metrics") { call.respond(HttpStatusCode.OK, "metrics") }
        }
    }

    @Test
    fun `an unconfigured server refuses mutations and keeps serving reads`() =
        testApplication {
            application {
                HostAuthentication.Unconfigured("The DID registrar").install(this)
                echoRoutes()
            }
            assertEquals(HttpStatusCode.OK, client.request("/thing") { method = HttpMethod.Get }.status)
            for (verb in listOf(HttpMethod.Post, HttpMethod.Delete)) {
                val response = client.request("/thing") { method = verb }
                assertEquals(HttpStatusCode.ServiceUnavailable, response.status, "$verb must be refused")
                val body = response.body<String>()
                assertTrue("authentication_not_configured" in body, body)
                assertTrue("withAuthentication" in body, "the refusal must say how to fix it: $body")
            }
        }

    @Test
    fun `protected path prefixes gate reads under them and leave other reads open`() =
        testApplication {
            application {
                HostAuthentication.bearerToken(token).protectingPathPrefixes("/secret/").install(this)
                routing {
                    get("/secret/item") { call.respond(HttpStatusCode.OK, "secret") }
                    get("/open/item") { call.respond(HttpStatusCode.OK, "open") }
                }
            }
            assertEquals(HttpStatusCode.Unauthorized, client.request("/secret/item") { method = HttpMethod.Get }.status)
            assertEquals(HttpStatusCode.OK, client.request("/open/item") { method = HttpMethod.Get }.status)
            val authed =
                client.request("/secret/item") {
                    method = HttpMethod.Get
                    header("Authorization", "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, authed.status)
        }

    @Test
    fun `protected prefixes survive percent-encoding, dot segments and doubled slashes`() =
        testApplication {
            application {
                HostAuthentication.bearerToken(token).protectingPathPrefixes("/secret/").install(this)
                routing { get("/{...}") { call.respond(HttpStatusCode.OK, "reached") } }
            }
            val variants =
                listOf(
                    "/secret/item",
                    "/%73ecret/item",
                    "/SECRET/item",
                    "/secret//item",
                    "/open/../secret/item",
                    "/./secret/item",
                    "/secret%2Fitem",
                    "/open/%2e%2e/secret/item",
                    "/secret\\item",
                )
            for (path in variants) {
                val status = runCatching { client.request(path) { method = HttpMethod.Get }.status }.getOrNull()
                // A request the test client cannot even form is not a bypass; one that reaches the
                // route must have been gated.
                assertTrue(status == null || status == HttpStatusCode.Unauthorized, "$path -> $status")
            }
            assertEquals(HttpStatusCode.OK, client.request("/open/item") { method = HttpMethod.Get }.status)
        }

    @Test
    fun `a bearer token admits the right caller and refuses everyone else`() =
        testApplication {
            application {
                HostAuthentication.bearerToken(token).install(this)
                echoRoutes()
            }
            val admitted =
                client.request("/thing") {
                    method = HttpMethod.Post
                    header("Authorization", "Bearer $token")
                }
            assertEquals(HttpStatusCode.Created, admitted.status)
            for (header in listOf(null, "Bearer wrong", "Bearer ${token.dropLast(1)}", token)) {
                val response =
                    client.request("/thing") {
                        method = HttpMethod.Post
                        header?.let { header("Authorization", it) }
                    }
                assertEquals(HttpStatusCode.Unauthorized, response.status, "header=$header")
                assertEquals("Bearer", response.headers["WWW-Authenticate"])
            }
        }

    @Test
    fun `reads are open by default and gated when the host asks for it`() =
        testApplication {
            application {
                HostAuthentication.bearerToken(token, protect = HostAuthentication.ALL).install(this)
                echoRoutes()
            }
            assertEquals(HttpStatusCode.Unauthorized, client.request("/thing") { method = HttpMethod.Get }.status)
        }

    @Test
    fun `a token shorter than the minimum is refused at configuration time`() {
        for (candidate in listOf("", "short", "t".repeat(31), "t".repeat(257), "with space".repeat(5))) {
            assertFailsWith<IllegalArgumentException>("token=$candidate") {
                HostAuthentication.bearerToken(candidate)
            }
        }
    }

    @Test
    fun `an authenticator that throws is a refusal, not a server error`() =
        testApplication {
            application {
                HostAuthentication.custom({ throw IllegalStateException("directory unreachable") }).install(this)
                echoRoutes()
            }
            assertEquals(HttpStatusCode.Unauthorized, client.request("/thing") { method = HttpMethod.Post }.status)
        }

    @Test
    fun `frontedByProxy admits requests but has to say what protects them`() =
        testApplication {
            assertFailsWith<IllegalArgumentException> { HostAuthentication.frontedByProxy("  ") }
            val declared = HostAuthentication.frontedByProxy("mTLS terminated at the ingress")
            assertEquals("mTLS terminated at the ingress", declared.delegatedTo)
            assertNull(HostAuthentication.bearerToken(token).delegatedTo, "only a delegation carries a reason")
            application {
                declared.install(this)
                echoRoutes()
            }
            assertEquals(HttpStatusCode.Created, client.request("/thing") { method = HttpMethod.Post }.status)
        }

    @Test
    fun `the metrics path stays reachable so a refused server can still be observed`() =
        testApplication {
            application {
                HostAuthentication.bearerToken(token, protect = HostAuthentication.ALL).install(this)
                echoRoutes()
            }
            assertEquals(HttpStatusCode.OK, client.request("/internal/metrics") { method = HttpMethod.Get }.status)
        }

    @Test
    fun `a caller over its budget is refused with a retry hint`() =
        testApplication {
            application {
                HostAuthentication
                    .frontedByProxy("test", rateLimit = HostAuthentication.RateLimit(permits = 2))
                    .install(this)
                echoRoutes()
            }
            repeat(2) {
                assertEquals(HttpStatusCode.OK, client.request("/thing") { method = HttpMethod.Get }.status)
            }
            val refused = client.request("/thing") { method = HttpMethod.Get }
            assertEquals(HttpStatusCode.TooManyRequests, refused.status)
            assertEquals("60", refused.headers["Retry-After"])
        }

    @Test
    fun `the budget refills when the window rolls over`() {
        var now = 0L
        val limit = HostAuthentication.RateLimit(permits = 1, windowMillis = 1_000, clock = { now })
        assertTrue(limit.admit("caller"))
        assertTrue(!limit.admit("caller"))
        now = 1_000
        assertTrue(limit.admit("caller"), "a new window must restore the budget")
    }

    @Test
    fun `callers hold separate budgets`() {
        val limit = HostAuthentication.RateLimit(permits = 1)
        assertTrue(limit.admit("first"))
        assertTrue(limit.admit("second"), "one caller's spend must not refuse another")
        assertTrue(!limit.admit("first"))
    }

    @Test
    fun `tracking is bounded so a spray of callers cannot grow the map forever`() {
        var now = 0L
        val limit = HostAuthentication.RateLimit(permits = 1, windowMillis = 10, maxTrackedCallers = 8, clock = { now })
        repeat(64) {
            now += 20
            assertTrue(limit.admit("caller-$it"))
        }
        // Still enforcing after the sweep, which is the property that matters.
        now += 20
        assertTrue(limit.admit("final"))
        assertTrue(!limit.admit("final"))
    }

    @Test
    fun `a spray of distinct callers does not reset the budget of a tracked caller`() {
        val now = 0L
        val limit = HostAuthentication.RateLimit(permits = 1, windowMillis = 60_000, maxTrackedCallers = 4, clock = { now })
        assertTrue(limit.admit("legit"))
        assertTrue(!limit.admit("legit"))
        repeat(200) { limit.admit("attacker-$it") }
        assertTrue(!limit.admit("legit"), "spraying callers must not hand a tracked caller a fresh budget")
    }

    @Test
    fun `expired windows are reclaimed before any caller is pushed to the overflow window`() {
        var now = 0L
        val limit = HostAuthentication.RateLimit(permits = 1, windowMillis = 1_000, maxTrackedCallers = 2, clock = { now })
        assertTrue(limit.admit("a"))
        assertTrue(limit.admit("b"))
        now = 2_000
        assertTrue(limit.admit("c"), "idle callers are evicted to make room")
        assertTrue(limit.admit("d"), "and the budget of a new caller is its own")
        assertTrue(!limit.admit("c"))
    }

    @Test
    fun `untracked callers share one overflow window while tracked callers keep theirs`() {
        val now = 0L
        val limit = HostAuthentication.RateLimit(permits = 1, windowMillis = 60_000, maxTrackedCallers = 1, clock = { now })
        assertTrue(limit.admit("tracked"))
        assertTrue(limit.admit("overflow-1"), "the first untracked caller spends the shared window")
        assertTrue(!limit.admit("overflow-2"), "the next one finds it spent")
        assertTrue(!limit.admit("tracked"), "the tracked caller's own budget is intact")
    }

    @Test
    fun `a flood of bad credentials does not spend the budget of a caller with a valid one`() =
        testApplication {
            application {
                HostAuthentication
                    .bearerToken(
                        token,
                        rateLimit = HostAuthentication.RateLimit(permits = 3, failedAuthPermits = 2, attemptPermits = 1_000),
                    ).install(this)
                echoRoutes()
            }
            val statuses =
                List(5) {
                    client
                        .request("/thing") {
                            method = HttpMethod.Post
                            header("Authorization", "Bearer wrong")
                        }.status
                }
            assertEquals(
                listOf(
                    HttpStatusCode.Unauthorized,
                    HttpStatusCode.Unauthorized,
                    HttpStatusCode.TooManyRequests,
                    HttpStatusCode.TooManyRequests,
                    HttpStatusCode.TooManyRequests,
                ),
                statuses,
                "bad credentials hit their own, stricter budget",
            )
            // The same host, now with the right credential: its full budget is still there.
            repeat(3) {
                val ok =
                    client.request("/thing") {
                        method = HttpMethod.Post
                        header("Authorization", "Bearer $token")
                    }
                assertEquals(HttpStatusCode.Created, ok.status)
            }
            val over =
                client.request("/thing") {
                    method = HttpMethod.Post
                    header("Authorization", "Bearer $token")
                }
            assertEquals(HttpStatusCode.TooManyRequests, over.status)
        }

    @Test
    fun `the pre-authentication attempt cap still bounds anonymous work`() =
        testApplication {
            application {
                HostAuthentication
                    .bearerToken(token, rateLimit = HostAuthentication.RateLimit(permits = 2, failedAuthPermits = 100, attemptPermits = 3))
                    .install(this)
                echoRoutes()
            }
            val statuses = List(4) { client.request("/thing") { method = HttpMethod.Post }.status }
            assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
        }

    @Test
    fun `cancellation inside an authenticator is propagated, not turned into a refusal`() =
        testApplication {
            application {
                HostAuthentication.custom({ throw kotlinx.coroutines.CancellationException("caller went away") }).install(this)
                echoRoutes()
            }
            val outcome = runCatching { client.request("/thing") { method = HttpMethod.Post }.status }
            assertTrue(
                outcome.isFailure || outcome.getOrNull() != HttpStatusCode.Unauthorized,
                "a cancelled authorisation must not be reported as 401: $outcome",
            )
        }

    @Test
    fun `invalid rate limits are refused at configuration time`() {
        for (build in listOf<() -> HostAuthentication.RateLimit>(
            { HostAuthentication.RateLimit(permits = 0) },
            { HostAuthentication.RateLimit(permits = -1) },
            { HostAuthentication.RateLimit(permits = 1, windowMillis = 0) },
            { HostAuthentication.RateLimit(permits = 1, maxTrackedCallers = 0) },
            { HostAuthentication.RateLimit(permits = 1, failedAuthPermits = 0) },
            { HostAuthentication.RateLimit(permits = 1, attemptPermits = 0) },
        )) {
            assertFailsWith<IllegalArgumentException> { build() }
        }
    }

    @Test
    fun `mutating methods are exactly the state-changing verbs`() {
        assertEquals(
            setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete),
            HostAuthentication.MUTATING,
        )
        assertNull(HostAuthentication.MUTATING.firstOrNull { it == HttpMethod.Get })
    }
}
