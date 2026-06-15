# AVP-Micro Quote-Binding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bind a verified `PaymentAuthorization` to the payee-signed `PaymentQuote` it references, so an `allow` attests the payment matches a quoted price/payee/route — closing the quote-binding gap documented in the AVP-Micro Authorization Service design.

**Architecture:** Quote binding is pure stateless verification, so it extends the `avp-micro` plugin's `PaymentVerifier` (reusing `Jcs`/`EcdsaJcs2022`) with an optional `quote` parameter; the `avp-authorization-server` engine passes the quote through and the endpoint's request body becomes `{ authorization, quote }`. The binding rules mirror the canonical Python harness `sim.py` exactly.

**Tech Stack:** Kotlin 2.3.21 (JVM), kotlinx-serialization-json, JCA SHA-256 + `java.util.Base64`, Ktor (server + test host), JUnit 5 / kotlin-test.

---

## Binding contract (from `sim.py`, lines 450–460)

Given the authorization `A` (self-contained, credential embedded in `vp`) and the payee-signed quote `Q` supplied alongside it, when a quote is present the verifier checks, in order:

1. `EcdsaJcs2022.verify(Q)` — quote proof valid → else `QUOTE_PROOF_INVALID`.
2. `controllerOf(Q) == Q.payee` — the payee signed its own quote → else `QUOTE_PROOF_INVALID`.
3. `Q.expires > now` (± skew) → else `QUOTE_EXPIRED`.
4. `A.quoteDigest == jcsDigest(Q)` → else `QUOTE_MISMATCH`. **`jcsDigest` is over the WHOLE signed quote, including its `proof`** (unlike proof verification, which strips the proof). Format: `"sha-256:" + base64url_nopad(sha256(JCS(Q)))`.
5. `A.payee == Q.payee` and `A.requestHash == Q.requestHash` → else `QUOTE_MISMATCH`.
6. `A.amount == Q.amount` and `A.currency == Q.currency` → else `AMOUNT_MISMATCH`.
7. `A.settlementMethod == Q.settlementMethod` and `A.settlementTarget == Q.settlementTarget` → else `QUOTE_MISMATCH`.

These map onto `sim.py`'s `quoteMismatch` / `amountMismatch`. The genuine vectors `02-payment-authorization.json` and `01-payment-quote.json` bind cleanly (same `quoteDigest`, payee, amount, requestHash, settlement). Known-answer value: `jcsDigest(01-payment-quote.json) == "sha-256:EuScAr2qixUd_K3KSPlSR0HAlToOR7tuPZODlQZMbFg"`.

## Scope note

LEAN: the *value-mismatch* negatives (`QUOTE_MISMATCH` by digest, `AMOUNT_MISMATCH`) require a validly-signed-but-divergent quote/authorization, which needs a deterministic `ecdsa-jcs-2022` **signer** — deferred (same as the existing SUBJECT_MISMATCH/CURRENCY/PAYEE verifier gaps). This plan ships quote binding with: the `jcsDigest` KAT, the happy-path (genuine authz + genuine quote → `Valid`), and `QUOTE_PROOF_INVALID` (tampered quote, no signer needed). The remaining negatives land when the signer is added.

## File Structure

**`credentials/plugins/avp-micro`** (the stateless verify core):
- Create `crypto/Digests.kt` — `jcsDigest`.
- Modify `model/PolicyViews.kt` — add `requestHash`/`settlementMethod`/`settlementTarget` to `AuthorizationView`; add `QuoteView`.
- Modify `verification/PaymentVerifier.kt` — new `VerificationFailure` values, `quote` param, `verifyQuoteBinding` helper.
- Modify `AvpMicro.kt` — `quote` param on `verifyPayment`.
- Tests: `crypto/DigestsTest.kt`, extend `model/PolicyViewsTest.kt`, extend `verification/PaymentVerifierTest.kt`.

**`credentials/avp-authorization-server`**:
- Modify `engine/AuthorizationEngine.kt` — `quote` param on `decide`, passed through.
- Modify `AvpAuthorizationRoutes.kt` — request body becomes `{ authorization, quote }`, both required.
- Vendor `01-payment-quote.json` into the server's test resources; update `HttpTest.kt`.

---

## Task 1: `Digests.jcsDigest` (byte-exact KAT)

**Files:**
- Create: `credentials/plugins/avp-micro/src/main/kotlin/org/trustweave/credential/avpmicro/crypto/Digests.kt`
- Test: `credentials/plugins/avp-micro/src/test/kotlin/org/trustweave/credential/avpmicro/crypto/DigestsTest.kt`

- [ ] **Step 1: Write the failing test** (`01-payment-quote.json` is already vendored in the plugin test resources from prior work)

`DigestsTest.kt`:
```kotlin
package org.trustweave.credential.avpmicro.crypto

import org.trustweave.credential.avpmicro.Vectors
import kotlin.test.Test
import kotlin.test.assertEquals

class DigestsTest {
    @Test
    fun `jcsDigest of the quote matches the authorization quoteDigest`() {
        // The authorization references the quote by this exact digest (sha-256 + base64url-nopad).
        assertEquals(
            "sha-256:EuScAr2qixUd_K3KSPlSR0HAlToOR7tuPZODlQZMbFg",
            Digests.jcsDigest(Vectors.paymentQuote),
        )
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*DigestsTest"`
Expected: FAIL — `Digests` unresolved.

- [ ] **Step 3: Implement `Digests.kt`**

```kotlin
package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest
import java.util.Base64

/** AVP-Micro content digests. Format matches avp_crypto.py::jcs_digest. */
object Digests {
    /** `"sha-256:" + base64url-nopad(sha256(JCS(obj)))` over the WHOLE object (proof included). */
    fun jcsDigest(obj: JsonObject): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(Jcs.canonicalize(obj))
        return "sha-256:" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*DigestsTest"`
Expected: PASS. (This is the binding's correctness gate — byte-exact with the harness.)

- [ ] **Step 5: Commit**

```bash
git add credentials/plugins/avp-micro/src
git commit -m "feat(avp-micro): jcsDigest (sha-256 + base64url) for quote binding"
```

---

## Task 2: Quote view + authorization binding fields

**Files:**
- Modify: `credentials/plugins/avp-micro/src/main/kotlin/org/trustweave/credential/avpmicro/model/PolicyViews.kt`
- Test: `credentials/plugins/avp-micro/src/test/kotlin/org/trustweave/credential/avpmicro/model/PolicyViewsTest.kt`

- [ ] **Step 1: Write the failing test** (append to the existing `PolicyViewsTest` class)

```kotlin
    @Test
    fun `reads authorization binding fields`() {
        val auth = AuthorizationView.from(Vectors.paymentAuthorization)
        assertEquals("sha-256:-DqOvoa765J-FBX-pPUw99dctpyyMeGTKO6vC6IO_0M", auth.requestHash)
        assertEquals("internal-ledger", auth.settlementMethod)
        assertEquals("https://wallet.example.com/tool-api-addr", auth.settlementTarget)
    }

    @Test
    fun `reads the quote view`() {
        val q = QuoteView(Vectors.paymentQuote)
        assertEquals("did:key:zDnaenNXPt8JM5YYhrjp23T2ZsgGjyEhVVSC7dhFjbQwdrxEN", q.payee)
        assertEquals(java.math.BigDecimal("0.001"), q.amount)
        assertEquals("USD", q.currency)
        assertEquals("sha-256:-DqOvoa765J-FBX-pPUw99dctpyyMeGTKO6vC6IO_0M", q.requestHash)
        assertEquals("internal-ledger", q.settlementMethod)
        assertEquals("https://wallet.example.com/tool-api-addr", q.settlementTarget)
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*PolicyViewsTest"`
Expected: FAIL — `QuoteView` unresolved and `auth.requestHash` unresolved.

- [ ] **Step 3: Add the binding fields to `AuthorizationView` and add `QuoteView`**

In `PolicyViews.kt`, inside `class AuthorizationView`, immediately after the existing line:
```kotlin
    val quoteDigest: String? = raw.str("quoteDigest")
```
add:
```kotlin
    val requestHash: String? = raw.str("requestHash")
    val settlementMethod: String? = raw.str("settlementMethod")
    val settlementTarget: String? = raw.str("settlementTarget")
```

Then add this new class at the end of the file (it uses the file-private `str` helper already defined at the top of `PolicyViews.kt`):
```kotlin
/** Read-only view over a payee-signed PaymentQuote. */
class QuoteView(val raw: JsonObject) {
    val payer: String? = raw.str("payer")
    val payee: String = raw.getValue("payee").jsonPrimitive.content
    val amount: BigDecimal = BigDecimal(raw.getValue("amount").jsonPrimitive.content)
    val currency: String = raw.getValue("currency").jsonPrimitive.content
    val requestHash: String? = raw.str("requestHash")
    val settlementMethod: String? = raw.str("settlementMethod")
    val settlementTarget: String? = raw.str("settlementTarget")
    val expires: Instant? = raw.str("expires")?.let(Instant::parse)
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*PolicyViewsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add credentials/plugins/avp-micro/src
git commit -m "feat(avp-micro): QuoteView + authorization binding fields"
```

---

## Task 3: Quote binding in `PaymentVerifier` + `AvpMicro.verifyPayment`

**Files:**
- Modify: `credentials/plugins/avp-micro/src/main/kotlin/org/trustweave/credential/avpmicro/verification/PaymentVerifier.kt`
- Modify: `credentials/plugins/avp-micro/src/main/kotlin/org/trustweave/credential/avpmicro/AvpMicro.kt`
- Test: `credentials/plugins/avp-micro/src/test/kotlin/org/trustweave/credential/avpmicro/verification/PaymentVerifierTest.kt`

- [ ] **Step 1: Write the failing tests** (append to the existing `PaymentVerifierTest` class)

```kotlin
    @Test
    fun `valid authorization with matching quote passes`() {
        val r = AvpMicro.verifyPayment(Vectors.paymentAuthorization, now, quote = Vectors.paymentQuote)
        assertTrue(r is PaymentVerificationResult.Valid, "expected Valid, got $r")
    }

    @Test
    fun `tampered quote fails with QUOTE_PROOF_INVALID`() {
        val tamperedQuote = JsonObject(
            Vectors.paymentQuote.toMutableMap().apply { put("amount", JsonPrimitive("0.002")) }
        )
        val r = AvpMicro.verifyPayment(Vectors.paymentAuthorization, now, quote = tamperedQuote)
        assertEquals(VerificationFailure.QUOTE_PROOF_INVALID, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `no quote supplied still verifies caps only`() {
        val r = AvpMicro.verifyPayment(Vectors.paymentAuthorization, now)
        assertTrue(r is PaymentVerificationResult.Valid)
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*PaymentVerifierTest"`
Expected: FAIL — `verifyPayment` has no `quote` param; `QUOTE_PROOF_INVALID` unresolved.

- [ ] **Step 3: Extend `VerificationFailure`, add the `quote` param and binding helper to `PaymentVerifier`**

In `PaymentVerifier.kt`:

(a) Add these imports near the existing ones:
```kotlin
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.credential.avpmicro.crypto.Digests
import org.trustweave.credential.avpmicro.model.QuoteView
```

(b) Add three values to the `VerificationFailure` enum (after `QUOTE_EXPIRED`):
```kotlin
    QUOTE_PROOF_INVALID, QUOTE_MISMATCH, AMOUNT_MISMATCH,
```

(c) Add a `quote` parameter to `verify` (place it before `amountOverride` so the existing `checkConstraints` named call is unaffected):
```kotlin
    fun verify(
        authorization: JsonObject,
        now: Instant,
        clockSkewSeconds: Long = 300,
        statusResolver: StatusResolver? = null,
        quote: JsonObject? = null,
        amountOverride: BigDecimal? = null,
    ): PaymentVerificationResult {
```

(d) Immediately before the final `return PaymentVerificationResult.Valid(view)` line, insert:
```kotlin
        if (quote != null) {
            val bindingFailure = verifyQuoteBinding(view, quote, now, clockSkewSeconds)
            if (bindingFailure != null) return bindingFailure
        }
```

(e) Add this private helper inside `object PaymentVerifier` (next to `fail`):
```kotlin
    private fun verifyQuoteBinding(
        view: org.trustweave.credential.avpmicro.model.AuthorizationView,
        quote: JsonObject,
        now: Instant,
        clockSkewSeconds: Long,
    ): PaymentVerificationResult? {
        val q = try { QuoteView(quote) } catch (e: Exception) {
            return fail(VerificationFailure.QUOTE_MISMATCH, "malformed quote: ${e.message}")
        }
        if (!EcdsaJcs2022.verify(quote))
            return fail(VerificationFailure.QUOTE_PROOF_INVALID, "quote signature invalid")
        val signer = quote["proof"]?.jsonObject?.get("verificationMethod")?.jsonPrimitive?.content?.substringBefore('#')
        if (signer != q.payee)
            return fail(VerificationFailure.QUOTE_PROOF_INVALID, "quote not signed by its payee")
        q.expires?.let {
            if (now.isAfter(it.plusSeconds(clockSkewSeconds)))
                return fail(VerificationFailure.QUOTE_EXPIRED, "quote expired")
        }
        if (view.quoteDigest != Digests.jcsDigest(quote))
            return fail(VerificationFailure.QUOTE_MISMATCH, "quoteDigest does not match the supplied quote")
        if (view.payee != q.payee || view.requestHash != q.requestHash)
            return fail(VerificationFailure.QUOTE_MISMATCH, "payee/requestHash differ from quote")
        if (view.amount.compareTo(q.amount) != 0 || view.currency != q.currency)
            return fail(VerificationFailure.AMOUNT_MISMATCH, "amount/currency differ from quote")
        if (view.settlementMethod != q.settlementMethod || view.settlementTarget != q.settlementTarget)
            return fail(VerificationFailure.QUOTE_MISMATCH, "settlement routing differs from quote")
        return null
    }
```

In `AvpMicro.kt`, add the `quote` param to `verifyPayment` and pass it through:
```kotlin
    fun verifyPayment(
        authorization: JsonObject,
        now: Instant,
        clockSkewSeconds: Long = 300,
        statusResolver: StatusResolver? = null,
        quote: JsonObject? = null,
    ): PaymentVerificationResult =
        PaymentVerifier.verify(authorization, now, clockSkewSeconds, statusResolver, quote = quote)
```

- [ ] **Step 4: Run it to verify it passes**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*PaymentVerifierTest"`
Expected: PASS (3 new + existing). Then run the whole module:
Run: `.\gradlew :credentials:plugins:avp-micro:test`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add credentials/plugins/avp-micro/src
git commit -m "feat(avp-micro): quote binding in PaymentVerifier"
```

---

## Task 4: Pass the quote through the engine + endpoint

**Files:**
- Modify: `credentials/avp-authorization-server/src/main/kotlin/org/trustweave/credential/avpauth/engine/AuthorizationEngine.kt`
- Modify: `credentials/avp-authorization-server/src/main/kotlin/org/trustweave/credential/avpauth/AvpAuthorizationRoutes.kt`
- Vendor: `credentials/avp-authorization-server/src/test/resources/vectors/01-payment-quote.json`
- Test: `credentials/avp-authorization-server/src/test/kotlin/org/trustweave/credential/avpauth/HttpTest.kt`

- [ ] **Step 1: Vendor the quote vector into the server test resources**

PowerShell:
```powershell
Set-Location C:\Users\steph\work\trustweave
Copy-Item "C:\Users\steph\work\avp-micro-spec\spec\payments\test-vectors\01-payment-quote.json" `
  "credentials\avp-authorization-server\src\test\resources\vectors\01-payment-quote.json"
```

- [ ] **Step 2: Write the failing HTTP tests** (REPLACE `HttpTest.kt` with the wrapper-shaped version)

```kotlin
package org.trustweave.credential.avpauth

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.trustweave.credential.avpauth.dto.VerifyResponse
import org.trustweave.credential.avpauth.engine.AuthorizationEngine
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpTest {
    private fun res(name: String) = requireNotNull(this::class.java.getResource("/vectors/$name")) {
        "Test vector /vectors/$name missing from test resources"
    }.readText()

    private val authz = res("02-payment-authorization.json")
    private val quote = res("01-payment-quote.json")
    private val now = Instant.parse("2026-03-25T21:30:30Z")
    private val json = Json { ignoreUnknownKeys = true }

    private fun wrapper(a: String = authz, q: String = quote) = """{"authorization":$a,"quote":$q}"""
    private fun body(text: String): VerifyResponse = json.decodeFromString(VerifyResponse.serializer(), text)

    @Test fun `valid authorization with quote returns 200 allow`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val r = client.post("/v1/authorizations/verify") { contentType(ContentType.Application.Json); setBody(wrapper()) }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("allow", body(r.bodyAsText()).decision)
    }

    @Test fun `replayed authorization returns 200 reject NONCE_REUSE`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        client.post("/v1/authorizations/verify") { contentType(ContentType.Application.Json); setBody(wrapper()) }
        val r = client.post("/v1/authorizations/verify") { contentType(ContentType.Application.Json); setBody(wrapper()) }
        assertEquals(HttpStatusCode.OK, r.status)
        val decoded = body(r.bodyAsText())
        assertEquals("reject", decoded.decision)
        assertEquals("NONCE_REUSE", decoded.reason)
    }

    @Test fun `missing quote returns 400`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val r = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json); setBody("""{"authorization":$authz}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test fun `malformed body returns 400`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val r = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json); setBody("{ not json ")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test fun `structurally incomplete authorization returns 400`() = testApplication {
        application { configureAuthorization(AuthorizationEngine(clock = { now })) }
        val r = client.post("/v1/authorizations/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"authorization":{"type":"PaymentAuthorization"},"quote":$quote}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `.\gradlew :credentials:avp-authorization-server:test --tests "*HttpTest"`
Expected: FAIL — `engine.decide` has no `quote` param / route still reads the bare body.

- [ ] **Step 4: Add `quote` to the engine and the wrapper parsing to the route**

In `AuthorizationEngine.kt`, change the `decide` signature and the verify call:
```kotlin
    suspend fun decide(authorization: JsonObject, quote: JsonObject? = null): AuthorizationVerdict {
        val now = clock()

        // 1. stateless gate (proofs + spending constraints + quote binding); reuse the parsed view
        val result = AvpMicro.verifyPayment(authorization, now, quote = quote)
```
(Leave the rest of `decide` unchanged.)

In `AvpAuthorizationRoutes.kt`, replace the body of the `post("/v1/authorizations/verify")` handler with one that requires both `authorization` and `quote`:
```kotlin
    post("/v1/authorizations/verify") {
        val parsed = try {
            lenientJson.parseToJsonElement(call.receiveText()).jsonObject
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_REQUEST", e.message ?: "malformed JSON"))
            return@post
        }
        val authorization = parsed["authorization"]?.jsonObject
        val quote = parsed["quote"]?.jsonObject
        if (authorization == null || quote == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_REQUEST", "request must contain 'authorization' and 'quote' objects"),
            )
            return@post
        }
        when (val v = engine.decide(authorization, quote)) {
            is AuthorizationVerdict.Allow ->
                call.respond(HttpStatusCode.OK, VerifyResponse("allow", payer = v.payer, payee = v.payee, amount = v.amount))
            is AuthorizationVerdict.Reject ->
                if (v.reason == VerificationFailure.MALFORMED_REQUEST.name) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_REQUEST", v.detail))
                } else {
                    call.respond(HttpStatusCode.OK, VerifyResponse("reject", reason = v.reason, detail = v.detail))
                }
        }
    }
```
(The `VerificationFailure` import added in the earlier MALFORMED→400 work stays; `jsonObject` is already imported in this file.)

- [ ] **Step 5: Run it to verify it passes**

Run: `.\gradlew :credentials:avp-authorization-server:test --tests "*HttpTest"`
Expected: PASS (5 tests). Then run the whole module:
Run: `.\gradlew :credentials:avp-authorization-server:test`
Expected: all green (`ParityTest` still passes — it calls `engine.decide(vector())` with no quote, which now skips binding and behaves as before).

- [ ] **Step 6: Commit**

```bash
git add credentials/avp-authorization-server/src
git commit -m "feat(avp-auth): require and bind the PaymentQuote at the endpoint"
```

---

## Done criteria

- `.\gradlew :credentials:plugins:avp-micro:test :credentials:avp-authorization-server:test` is green.
- `jcsDigest(01-payment-quote.json)` matches the authorization's `quoteDigest` byte-for-byte (KAT).
- A genuine authorization + its genuine quote verify to `Valid`/`allow`; a tampered quote → `QUOTE_PROOF_INVALID`; the endpoint requires `{ authorization, quote }` (missing quote → `400`).
- `verifyPayment(quote = null)` still works (caps-only), so the engine's no-quote path is unchanged.

## Deferred (unchanged from the parent design)

- The value-mismatch negatives (`QUOTE_MISMATCH` by digest, `AMOUNT_MISMATCH`, settlement mismatch) — need a deterministic `ecdsa-jcs-2022` signer to mint validly-signed divergent quotes/authorizations. Land them with the signer (which also closes the sign-reproduction KAT).
- Issuer trust (trust-registry / issuer allowlist) — product #2.
