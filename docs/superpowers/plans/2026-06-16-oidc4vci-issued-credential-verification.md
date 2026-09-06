# OID4VCI Issued-Credential Verification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `Oidc4VciService.issueCredential` parse and cryptographically verify the issuer's *actual* returned credential (signature, expected-issuer, holder binding) before returning it, failing closed on anything it cannot verify.

**Architecture:** Add an optional `didResolver` to `Oidc4VciService` (mirrors `Oidc4VpService`); build the existing `credential-api` verifier from it. A private `verifyIssuedCredential(...)` parses the issuer response into a `VerifiableCredential`, calls `CredentialService.verify`, cross-checks the issuer against the offer, and enforces `credentialSubject.id == holderDid`. The vestigial placeholder `credential` parameter is removed.

**Tech Stack:** Kotlin, OkHttp + MockWebServer (tests), `credentials/credential-api` (`credentialService`, `verify`, transformers), testkit `DidKeyMockMethod` + `InMemoryKeyManagementService`.

**Scope note:** VC-LD only in this plan. SD-JWT-VC has **no compact→model parser** in `credential-api` today, so it is a documented follow-up; unparseable/unsupported formats fail closed (`verify` returns `Invalid.UnsupportedFormat`). VC-JWT is rejected by the verifier automatically.

**Build/test:** this box needs `--no-daemon --max-workers=3`. Module test: `./gradlew :credentials:plugins:oidc4vci:test --no-daemon --max-workers=3`. Lint (root only): `./gradlew ktlintCheck --no-daemon --max-workers=3`.

---

## File structure

- **Modify** `credentials/plugins/oidc4vci/.../exception/Oidc4VciException.kt` — add `CredentialVerificationFailed`.
- **Modify** `credentials/plugins/oidc4vci/.../Oidc4VciService.kt` — `didResolver` param + internal verifier; `verifyIssuedCredential` + `detectAndParse`; rewire immediate + deferred branches; remove placeholder `credential` param from `issueCredential`.
- **Modify** `credentials/plugins/oidc4vci/.../exchange/Oidc4VciExchangeProtocol.kt` — drop the `credential = request.credential` argument.
- **Modify** `credentials/plugins/oidc4vci/.../exchange/spi/Oidc4VciExchangeProtocolProvider.kt` — read `options["didResolver"]` and pass it.
- **Modify** `credentials/plugins/oidc4vci/src/test/.../Oidc4VciServiceTest.kt` — update 5 `issueCredential(...)` call sites; add verification tests + a `mintVcLd(...)` helper.

---

### Task 1: Add the `CredentialVerificationFailed` exception

**Files:** Modify `credentials/plugins/oidc4vci/src/main/kotlin/org/trustweave/credential/oidc4vci/exception/Oidc4VciException.kt`

- [ ] **Step 1: Add the variant** (after `CredentialRequestFailed`, mirroring its style)

```kotlin
data class CredentialVerificationFailed(
    val reason: String,
    val credentialIssuer: String? = null,
    override val cause: Throwable? = null,
) : Oidc4VciException(
    code = "OIDC4VCI_CREDENTIAL_VERIFICATION_FAILED",
    message = "OIDC4VCI credential verification failed: $reason",
    context = mapOf("reason" to reason, "credentialIssuer" to credentialIssuer).filterValues { it != null },
    cause = cause,
)
```

- [ ] **Step 2: Compile** — `./gradlew :credentials:plugins:oidc4vci:compileKotlin --no-daemon --max-workers=3` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `git add credentials/plugins/oidc4vci/.../exception/Oidc4VciException.kt && git commit -m "feat(oidc4vci): add CredentialVerificationFailed exception"`

---

### Task 2: Wire the verifier + remove the placeholder param (structural; keep tests compiling)

**Files:** Modify `Oidc4VciService.kt`, `exchange/Oidc4VciExchangeProtocol.kt`, `exchange/spi/Oidc4VciExchangeProtocolProvider.kt`, `Oidc4VciServiceTest.kt`

- [ ] **Step 1: Add imports + constructor param + internal verifier** to `Oidc4VciService.kt`

Imports (top of file):
```kotlin
import org.trustweave.credential.CredentialService
import org.trustweave.credential.credentialService
import org.trustweave.did.resolver.DidResolver
```
Constructor (currently `class Oidc4VciService(credentialIssuerUrl, kms, httpClient = org.trustweave.core.net.ssrfGuardedOkHttpClient())`):
```kotlin
class Oidc4VciService(
    private val credentialIssuerUrl: String,
    private val kms: KeyManagementService,
    private val httpClient: OkHttpClient = org.trustweave.core.net.ssrfGuardedOkHttpClient(),
    didResolver: DidResolver? = null,
) {
    // Holder-side verifier for issuer-returned credentials. Null ⇒ issueCredential fails closed.
    private val verifier: CredentialService? = didResolver?.let { credentialService(it) }
```

- [ ] **Step 2: Change `issueCredential` signature** — remove the `credential` parameter:
```kotlin
suspend fun issueCredential(
    issuerDid: String,
    holderDid: String,
    requestId: String,
): Oidc4VciIssueResult = withContext(Dispatchers.IO) {
```

- [ ] **Step 3: Rewrite the immediate-issuance branch** (`Oidc4VciService.kt` ~322-329) to verify the issuer's credential:
```kotlin
} else {
    // Immediate issuance — parse and verify the issuer's actual returned credential.
    val verified = verifyIssuedCredential(credentialResponse, expectedIssuer = issuerDid, holderDid = holderDid)
    Oidc4VciIssueResult(issueId = issueId, credential = verified, transactionId = null, credentialResponse = credentialResponse)
}
```

- [ ] **Step 4: Add `verifyIssuedCredential` + `parseIssuedCredential`** (new private methods in `Oidc4VciService.kt`):
```kotlin
private suspend fun verifyIssuedCredential(
    response: Map<String, Any?>,
    expectedIssuer: String,
    holderDid: String,
): VerifiableCredential {
    val service = verifier ?: throw Oidc4VciException.CredentialVerificationFailed(
        reason = "no DID resolver configured; cannot verify the issued credential",
        credentialIssuer = expectedIssuer,
    )
    val parsed = parseIssuedCredential(response, expectedIssuer)

    val result = service.verify(
        parsed,
        options = VerificationOptions(
            checkExpiration = true,
            checkNotBefore = true,
            resolveIssuerDid = true,
            revocationFailurePolicy = RevocationFailurePolicy.FAIL_CLOSED,
        ),
    )
    if (result !is VerificationResult.Valid) {
        throw Oidc4VciException.CredentialVerificationFailed(
            reason = "issuer credential failed verification: ${result::class.simpleName}",
            credentialIssuer = expectedIssuer,
        )
    }
    if (parsed.issuer.id.value != expectedIssuer) {
        throw Oidc4VciException.CredentialVerificationFailed(
            reason = "credential issuer ${parsed.issuer.id.value} does not match the offer issuer $expectedIssuer",
            credentialIssuer = expectedIssuer,
        )
    }
    if (parsed.credentialSubject.id?.value != holderDid) {
        throw Oidc4VciException.CredentialVerificationFailed(
            reason = "credential subject ${parsed.credentialSubject.id?.value} is not the holder $holderDid",
            credentialIssuer = expectedIssuer,
        )
    }
    return parsed
}

private suspend fun parseIssuedCredential(response: Map<String, Any?>, expectedIssuer: String): VerifiableCredential {
    val raw = response["credential"] ?: throw Oidc4VciException.CredentialVerificationFailed(
        reason = "issuer response contained no 'credential'", credentialIssuer = expectedIssuer,
    )
    return try {
        when (raw) {
            is JsonObject -> raw.toCredential() // VC-LD (JSON-LD)
            is Map<*, *> -> JsonObject(raw.entries.associate { (k, v) -> k.toString() to (v as JsonElement) }).toCredential()
            is String -> throw Oidc4VciException.CredentialVerificationFailed(
                reason = "compact (JWT/SD-JWT-VC) issued credentials are not yet supported; only VC-LD is verified",
                credentialIssuer = expectedIssuer,
            )
            else -> throw Oidc4VciException.CredentialVerificationFailed(
                reason = "unsupported 'credential' representation: ${raw::class.simpleName}", credentialIssuer = expectedIssuer,
            )
        }
    } catch (e: Oidc4VciException) {
        throw e
    } catch (e: Exception) {
        throw Oidc4VciException.CredentialVerificationFailed(
            reason = "failed to parse issued credential: ${e.message}", credentialIssuer = expectedIssuer, cause = e,
        )
    }
}
```
Add imports: `org.trustweave.credential.transform.toCredential`, `org.trustweave.credential.requests.VerificationOptions`, `org.trustweave.credential.requests.RevocationFailurePolicy`, `org.trustweave.credential.results.VerificationResult`, `org.trustweave.credential.model.vc.VerifiableCredential`, `kotlinx.serialization.json.JsonObject`, `kotlinx.serialization.json.JsonElement`. (Confirm `toCredential` import path against `transform/CredentialTransformerExtensions.kt:128`.)

- [ ] **Step 5: Update the deferred-issuance branch** (`pollDeferredCredential`) — when the poll returns a `credential`, route it through `verifyIssuedCredential(credentialResponse, expectedIssuer = <deferred request issuer>, holderDid = <deferred request holder>)`; otherwise leave `credential = null`. (Use the same pattern as Step 3.)

- [ ] **Step 6: Drop the placeholder argument at the call site** in `exchange/Oidc4VciExchangeProtocol.kt:141-146`:
```kotlin
val issueResult = oidc4vciService.issueCredential(
    issuerDid = issuerDid,
    holderDid = holderDid,
    requestId = requestId,
)
```

- [ ] **Step 7: Pass `didResolver` from the provider** — `exchange/spi/Oidc4VciExchangeProtocolProvider.kt`, after the `httpClient` line:
```kotlin
val didResolver = options["didResolver"] as? org.trustweave.did.resolver.DidResolver
val oidc4vciService = Oidc4VciService(
    credentialIssuerUrl = credentialIssuerUrl,
    kms = kms,
    httpClient = httpClient,
    didResolver = didResolver,
)
```

- [ ] **Step 8: Fix the 5 existing test call sites** in `Oidc4VciServiceTest.kt` (lines ~108, 192, 245, 286, 318) — drop the `createTestCredential()` argument:
```kotlin
service.issueCredential(issuerDid, holderDid, request.requestId)
```

- [ ] **Step 9: Compile tests** — `./gradlew :credentials:plugins:oidc4vci:compileTestKotlin --no-daemon --max-workers=3` → BUILD SUCCESSFUL (no unresolved references). Existing pre-auth tests that reach `issueCredential` with no verifier configured will now throw `CredentialVerificationFailed` on the immediate path — adjust those assertions in Task 3, or confirm they fail before reaching it (the `pre-authorized code flow without token` test fails at token exchange first).

- [ ] **Step 10: Commit** — `git add -A credentials/plugins/oidc4vci && git commit -m "feat(oidc4vci): wire verifier + remove placeholder credential param"`

---

### Task 3: Happy-path — a valid issuer credential is verified and returned (TDD)

**Files:** Modify `Oidc4VciServiceTest.kt`

- [ ] **Step 1: Add a `mintVcLd` helper + shared resolver** (model on `credential-api/.../CredentialLifecycleIntegrationTest.kt:80-163`). In `setUp`, create a shared `DidKeyMockMethod(kms)`; create issuer + holder did:keys through it; construct the OID4VCI `service` with `didResolver = thatResolver`. Helper:
```kotlin
private suspend fun mintVcLd(issuerDid: String, issuerKeyId: String, holderDid: String): JsonObject {
    val credService = org.trustweave.credential.CredentialServices.createCredentialService(kms, didResolver, listOf(ProofSuiteId.VC_LD))
    val cred = (credService.issue(
        IssuanceRequest(
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(Did(issuerDid)),
            issuerKeyId = issuerKeyId,
            credentialSubject = CredentialSubject.fromDid(Did(holderDid), mapOf("name" to JsonPrimitive("Alice"))),
            types = listOf(CredentialType.VerifiableCredential, CredentialType.Person),
        ),
    ) as IssuanceResult.Success).credential
    return cred.toJsonLd() // confirm serializer; or vpJson.encodeToJsonElement(cred).jsonObject
}
```
(Confirm `createCredentialService` arity, `IssuanceRequest` field names, `IssuanceResult.Success.credential`, and the credential→JsonObject serializer against `CredentialLifecycleIntegrationTest.kt` during execution.)

- [ ] **Step 2: Write the failing test**
```kotlin
@Test
fun `immediate issuance verifies and returns the issuer's credential`() = runBlocking {
    val offer = service.createCredentialOffer(issuerDid, listOf("PersonCredential"), issuerUrl,
        grants = mapOf(Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE to mapOf("pre-authorized_code" to "code-123")))
    enqueueMetadata(); enqueueTokenResponse("tok-1", "nonce-abc")
    val credentialJson = mintVcLd(issuerDid, issuerKeyId, holderDid)
    mockWebServer.enqueue(MockResponse().setResponseCode(200)
        .setBody(buildJsonObject { put("credential", credentialJson) }.toString())
        .setHeader("Content-Type", "application/json"))

    val req = service.createCredentialRequest(holderDid = holderDid, offerId = offer.offerId, txCodeValue = "1234")
    val result = service.issueCredential(issuerDid, holderDid, req.requestId)

    assertNotNull(result.credential)
    assertEquals(issuerDid, result.credential!!.issuer.id.value)
    assertEquals(holderDid, result.credential!!.credentialSubject.id?.value)
}
```

- [ ] **Step 2b: Run → FAIL** (verification path not yet reached / parsing mismatch): `./gradlew :credentials:plugins:oidc4vci:test --tests "*Oidc4VciServiceTest" --no-daemon --max-workers=3`.
- [ ] **Step 3:** Reconcile against Task 2's code (e.g. the credential→JsonObject serializer in `mintVcLd`, the `toCredential` import) until the test passes.
- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -am "test(oidc4vci): verify and return the issuer's credential on immediate issuance"`

---

### Task 4: Fail-closed cases (TDD)

**Files:** Modify `Oidc4VciServiceTest.kt`. Each test mints/serves a response, calls `issueCredential`, and asserts `assertFailsWith<Oidc4VciException.CredentialVerificationFailed>`.

- [ ] **Step 1: Write the tests** (one per case), then run → they should already PASS given Task 2/3 (these pin the failure paths):
  - **no verifier:** construct a second `Oidc4VciService` with `didResolver = null`; immediate issuance throws.
  - **wrong issuer:** mint with a *different* issuer did:key than the offer's `issuerDid`.
  - **wrong holder:** mint with `credentialSubject` = a different holder did:key.
  - **tampered proof:** mint a valid credential, then mutate one `credentialSubject` claim in the served JSON before enqueueing → `verify` returns `InvalidProof`.
  - **missing/compact credential:** serve `{"credential": "<some.jwt.string>"}` → `CredentialVerificationFailed` ("compact … not yet supported"); serve `{}` (no credential) → fails.
- [ ] **Step 2: Run → PASS** for all. `./gradlew :credentials:plugins:oidc4vci:test --no-daemon --max-workers=3`.
- [ ] **Step 3: Adjust any pre-existing test** that now reaches the verified immediate path without a verifier (e.g. assert it throws `CredentialVerificationFailed` instead of returning a credential), if not already failing earlier at token exchange.
- [ ] **Step 4: Lint** — `./gradlew ktlintCheck --no-daemon --max-workers=3` → BUILD SUCCESSFUL; fix any findings in touched files.
- [ ] **Step 5: Commit** — `git commit -am "test(oidc4vci): fail closed on unverifiable issued credentials"`

---

### Task 5: Full module run + docs

- [ ] **Step 1:** `./gradlew :credentials:plugins:oidc4vci:test --no-daemon --max-workers=3` → BUILD SUCCESSFUL; confirm via the JUnit XML that the new tests ran and passed.
- [ ] **Step 2:** Update `credentials/plugins/oidc4vci/README.md` (if present) / the docs-site oidc4vci entry: note that `Oidc4VciService` now verifies issuer credentials (VC-LD) and fails closed without a `didResolver`; SD-JWT-VC verification is a follow-up.
- [ ] **Step 3: Commit** — `git commit -am "docs(oidc4vci): document issuer-credential verification"`

---

## Self-review notes
- **Spec coverage:** wiring (T2), parse+verify+issuer+holder (T2/T3), fail-closed incl. no-verifier/unsupported/tamper/issuer/holder (T4), param removal + cascade (T2), VC-LD-only with SD-JWT-VC follow-up (T2 parse), testing (T3/T4). ✓
- **Confirm-during-execution (not placeholders — concrete fallbacks given):** the credential→`JsonObject` serializer and `toCredential` import path (T2.4/T3.1); `createCredentialService`/`IssuanceRequest`/`IssuanceResult.Success` exact shapes (T3.1) — all have a named source file to copy from (`CredentialLifecycleIntegrationTest.kt`).
- **Type consistency:** `verifyIssuedCredential`/`parseIssuedCredential`/`CredentialVerificationFailed`/`verifier` names are used consistently across T1–T4.
