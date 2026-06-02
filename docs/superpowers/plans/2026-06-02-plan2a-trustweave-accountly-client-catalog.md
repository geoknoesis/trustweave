# Plan 2a — TrustWeave → Accountly Service Auth, Client & Catalog Reconciliation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give TrustWeave SaaS the foundation to act as the Accountly *merchant*: obtain a Keycloak client-credentials service token, call Accountly's `/api/v1` catalog endpoints through a typed client, and reconcile TrustWeave's FREE/PRO/ENTERPRISE tier catalog (plans + quotas + Kill Bill mapping + usage variables) into Accountly idempotently.

**Architecture:** A `RestClient`-based `AccountlyBillingClient` authenticates every call with a cached client-credentials token from `AccountlyServiceTokenProvider` (Keycloak). A declarative `TrustWeaveTierCatalog` is pushed into Accountly by `CatalogReconciler` using create-or-find semantics against the Plan 1 contract. This sub-plan touches only the catalog surface — subscriber lifecycle, usage, and entitlement come in Plans 2b/2c.

**Tech Stack:** Kotlin 2.2, Spring Boot 3.5.7 (servlet stack — use `RestClient`, not WebClient), JUnit 5 + WireMock 3.3.1 + mockito-kotlin. No Flyway at runtime (`flyway.enabled: false`, `ddl-auto: update`).

---

## Working directory & conventions

- **Repo: `c:\Users\steph\work\trustweave-saas`.** All paths are relative to that root.
- **Run Gradle from the repo root** (Bash): `cd /c/Users/steph/work/trustweave-saas && ./gradlew :server:test`.
- Single test class: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyBillingClientTest"`.
- Package root for new code: `com.geoknoesis.trustweave.saas.server.billing.accountly` → directory `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/`. Tests mirror under `server/src/test/...`.
- Config style in this codebase is `@Value`-injection (see `onboarding/llm/KoogConfig.kt`). This plan introduces ONE typed `@ConfigurationProperties` class registered via `@EnableConfigurationProperties` on a small `@Configuration` (no edit to `Application.kt`).
- These tests use **WireMock instantiated directly** (no Spring context, no Testcontainers) — fast and focused. WireMock 3 classes are under `com.github.tomakehurst.wiremock.*` even though the artifact is `org.wiremock:wiremock-standalone`.
- Conventional Commits. The module compiles today (`./gradlew :server:compileKotlin` → exit 0); keep it green.

## File structure (created)

- `billing/accountly/AccountlyBillingProperties.kt` — typed config (base URL, application name, request timeout) bound from the `accountly:` block.
- `billing/accountly/AccountlyBillingConfig.kt` — `@Configuration` that registers the properties and exposes the `RestClient` bean for Accountly.
- `billing/accountly/AccountlyServiceTokenProvider.kt` — Keycloak client-credentials token fetch + in-memory cache with early-expiry refresh.
- `billing/accountly/AccountlyDtos.kt` — request/response DTOs for the catalog surface.
- `billing/accountly/AccountlyBillingClient.kt` — typed client: ensure application, list/create plans, set KB mapping, create quota, declare usage variable.
- `billing/accountly/TrustWeaveTierCatalog.kt` — the declarative FREE/PRO/ENTERPRISE catalog definition (data only).
- `billing/accountly/CatalogReconciler.kt` — idempotent reconcile of the definition into Accountly.
- Tests mirror each under `server/src/test/.../billing/accountly/`.

---

## Task 1: AccountlyBillingProperties + config + RestClient bean

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingProperties.kt`
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingConfig.kt`
- Modify: `server/src/main/resources/application.yml`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingPropertiesTest.kt`

- [ ] **Step 1: Write the failing test**

Create `AccountlyBillingPropertiesTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class AccountlyBillingPropertiesTest {

    @Test
    fun `defaults are sensible`() {
        val p = AccountlyBillingProperties()
        assertEquals("http://127.0.0.1:9393", p.baseUrl)
        assertEquals("TrustWeave SaaS", p.applicationName)
        assertEquals(Duration.ofSeconds(20), p.requestTimeout)
    }

    @Test
    fun `trims trailing slash from base url`() {
        val p = AccountlyBillingProperties(baseUrl = "http://accountly:9393/")
        assertEquals("http://accountly:9393", p.normalizedBaseUrl())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyBillingPropertiesTest"`
Expected: FAIL — `AccountlyBillingProperties` unresolved.

- [ ] **Step 3: Create the properties**

Create `AccountlyBillingProperties.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "accountly")
data class AccountlyBillingProperties(
    /** Base URL of the Accountly API (`/api/v1` paths are appended by the client). */
    var baseUrl: String = "http://127.0.0.1:9393",
    /** Name of the Accountly Application that represents TrustWeave SaaS (the merchant). */
    var applicationName: String = "TrustWeave SaaS",
    /** HTTP timeout for Accountly calls. */
    var requestTimeout: Duration = Duration.ofSeconds(20),
) {
    fun normalizedBaseUrl(): String = baseUrl.trimEnd('/')
}
```

- [ ] **Step 4: Create the config (registers properties + RestClient bean)**

Create `AccountlyBillingConfig.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(AccountlyBillingProperties::class)
open class AccountlyBillingConfig {

    @Bean(name = ["accountlyRestClient"])
    open fun accountlyRestClient(props: AccountlyBillingProperties): RestClient {
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(props.requestTimeout.toMillis().toInt())
        factory.setReadTimeout(props.requestTimeout.toMillis().toInt())
        return RestClient.builder()
            .baseUrl(props.normalizedBaseUrl())
            .requestFactory(factory)
            .build()
    }
}
```

- [ ] **Step 5: Add the `accountly` config block to application.yml**

In `server/src/main/resources/application.yml`, add a top-level block (e.g. after the `stripe:` block):

```yaml
accountly:
  base-url: ${ACCOUNTLY_BASE_URL:http://127.0.0.1:9393}
  application-name: ${ACCOUNTLY_APPLICATION_NAME:TrustWeave SaaS}
  request-timeout: ${ACCOUNTLY_REQUEST_TIMEOUT:20s}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyBillingPropertiesTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingProperties.kt \
        server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingConfig.kt \
        server/src/main/resources/application.yml \
        server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingPropertiesTest.kt
git commit -m "feat(billing): Accountly client config + RestClient bean"
```

---

## Task 2: Keycloak client-credentials service token provider

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyServiceTokenProvider.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyServiceTokenProviderTest.kt`

The provider fetches a token from Keycloak's token endpoint via `client_credentials` and caches it until shortly before expiry. It is constructed with an explicit token-endpoint URL + client id/secret + a `RestClient` and a monotonic clock supplier (so the cache is testable without real time).

- [ ] **Step 1: Write the failing test**

Create `AccountlyServiceTokenProviderTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

class AccountlyServiceTokenProviderTest {

    private lateinit var server: WireMockServer
    private lateinit var tokenUrl: String

    @BeforeEach
    fun start() {
        server = WireMockServer(options().dynamicPort())
        server.start()
        tokenUrl = "http://localhost:${server.port()}/realms/trustweave-saas/protocol/openid-connect/token"
    }

    @AfterEach
    fun stop() = server.stop()

    private fun provider(now: AtomicLong) =
        AccountlyServiceTokenProvider(
            tokenEndpoint = tokenUrl,
            clientId = "svc",
            clientSecret = "shh",
            restClient = RestClient.create(),
            clockMillis = { now.get() },
        )

    @Test
    fun `fetches a token and caches it until near expiry`() {
        server.stubFor(
            post(urlEqualTo("/realms/trustweave-saas/protocol/openid-connect/token"))
                .willReturn(okJson("""{"access_token":"tok-1","expires_in":300}""")),
        )
        val now = AtomicLong(0L)
        val p = provider(now)

        assertEquals("tok-1", p.currentToken())
        // Second call within the cache window must NOT hit Keycloak again.
        assertEquals("tok-1", p.currentToken())
        server.verify(1, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo("/realms/trustweave-saas/protocol/openid-connect/token")))
    }

    @Test
    fun `refreshes after the token expires`() {
        server.stubFor(
            post(urlEqualTo("/realms/trustweave-saas/protocol/openid-connect/token"))
                .willReturn(okJson("""{"access_token":"tok-1","expires_in":300}""")),
        )
        val now = AtomicLong(0L)
        val p = provider(now)
        assertEquals("tok-1", p.currentToken())

        // Re-stub to return a new token, then advance the clock past expiry (minus the 30s skew).
        server.stubFor(
            post(urlEqualTo("/realms/trustweave-saas/protocol/openid-connect/token"))
                .willReturn(okJson("""{"access_token":"tok-2","expires_in":300}""")),
        )
        now.set(300_000L) // 300s later
        assertEquals("tok-2", p.currentToken())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyServiceTokenProviderTest"`
Expected: FAIL — `AccountlyServiceTokenProvider` unresolved.

- [ ] **Step 3: Implement the provider**

Create `AccountlyServiceTokenProvider.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/**
 * Supplies a Keycloak client-credentials access token for calling Accountly as the
 * merchant service identity. Caches the token until 30s before expiry.
 */
@Component
open class AccountlyServiceTokenProvider(
    private val tokenEndpoint: String,
    private val clientId: String,
    private val clientSecret: String,
    private val restClient: RestClient,
    private val clockMillis: () -> Long,
) {
    @Volatile private var cachedToken: String? = null
    @Volatile private var expiresAtMillis: Long = 0L

    /** Spring wiring: derive the token endpoint from the existing keycloak.* properties. */
    constructor(
        @Value("\${keycloak.server-url:http://localhost:8090}") serverUrl: String,
        @Value("\${keycloak.realm:trustweave-saas}") realm: String,
        @Value("\${keycloak.client-id:trustweave-saas-server}") clientId: String,
        @Value("\${keycloak.client-secret:}") clientSecret: String,
    ) : this(
        tokenEndpoint = "${serverUrl.trimEnd('/')}/realms/$realm/protocol/openid-connect/token",
        clientId = clientId,
        clientSecret = clientSecret,
        restClient = RestClient.create(),
        clockMillis = { System.currentTimeMillis() },
    )

    @Synchronized
    open fun currentToken(): String {
        val now = clockMillis()
        val token = cachedToken
        if (token != null && now < expiresAtMillis) return token

        val form = LinkedMultiValueMap<String, String>()
        form.add("grant_type", "client_credentials")
        form.add("client_id", clientId)
        form.add("client_secret", clientSecret)

        val resp =
            restClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse::class.java)
                ?: error("Keycloak returned no token response")

        cachedToken = resp.accessToken
        // Refresh 30s early to avoid edge-of-expiry races.
        expiresAtMillis = now + (resp.expiresIn - 30).coerceAtLeast(0) * 1000L
        return resp.accessToken
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TokenResponse(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("expires_in") val expiresIn: Long,
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyServiceTokenProviderTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyServiceTokenProvider.kt \
        server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyServiceTokenProviderTest.kt
git commit -m "feat(billing): Keycloak client-credentials token provider for Accountly"
```

---

## Task 3: AccountlyBillingClient — catalog surface + DTOs

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyDtos.kt`
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClient.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClientTest.kt`

The client wraps the Accountly Plan 1 catalog endpoints and attaches the bearer token on every call. Methods for this sub-plan: `listApplications`, `createApplication`, `listPlans`, `createPlan`, `updateKillbillMapping`, `createPlanQuota`, `declareUsageVariable`.

- [ ] **Step 1: Write the failing test**

Create `AccountlyBillingClientTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class AccountlyBillingClientTest {

    private lateinit var server: WireMockServer
    private lateinit var client: AccountlyBillingClient

    @BeforeEach
    fun start() {
        server = WireMockServer(options().dynamicPort())
        server.start()
        val rest = RestClient.builder().baseUrl("http://localhost:${server.port()}").build()
        // Token provider stubbed to a fixed token (its own behavior is tested separately).
        val tokens = object : AccountlyServiceTokenProvider(
            tokenEndpoint = "http://unused", clientId = "x", clientSecret = "y",
            restClient = RestClient.create(), clockMillis = { 0L },
        ) {
            override fun currentToken() = "test-token"
        }
        client = AccountlyBillingClient(rest, tokens)
    }

    @AfterEach
    fun stop() = server.stop()

    @Test
    fun `createApplication posts name and returns id, with bearer auth`() {
        server.stubFor(
            post(urlEqualTo("/api/v1/applications"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("TrustWeave SaaS")))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                    .withBody("""{"id":"app-1"}""")),
        )

        val id = client.createApplication("TrustWeave SaaS", "Merchant")
        assertEquals("app-1", id)
    }

    @Test
    fun `listApplications parses the array`() {
        server.stubFor(
            get(urlEqualTo("/api/v1/applications"))
                .willReturn(okJson("""[{"id":"app-1","name":"TrustWeave SaaS","description":null,"createdAt":"2026-06-01T00:00:00Z"}]""")),
        )
        val apps = client.listApplications()
        assertEquals(1, apps.size)
        assertEquals("app-1", apps[0].id)
        assertEquals("TrustWeave SaaS", apps[0].name)
    }

    @Test
    fun `createPlan returns plan_id`() {
        server.stubFor(
            post(urlEqualTo("/api/v1/applications/app-1/plans"))
                .withRequestBody(matchingJsonPath("$.pricingModel", equalTo("FlatRate")))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                    .withBody("""{"plan_id":"plan-1"}""")),
        )
        val planId = client.createPlan("app-1", CreatePlanRequest(name = "tw-pro", pricingModel = "FlatRate", monthlyFee = "99", level = 2, isDefault = false))
        assertEquals("plan-1", planId)
    }

    @Test
    fun `listPlans parses plan list with quotas`() {
        server.stubFor(
            get(urlEqualTo("/api/v1/applications/app-1/plans"))
                .willReturn(okJson("""[{"planId":"plan-1","applicationId":"app-1","applicationName":"TrustWeave SaaS","name":"tw-pro","pricingModel":"FlatRate","monthlyFee":"99","level":2,"isDefault":false,"killbillProductName":"trustweave-pro","killbillPlanName":"trustweave-pro-monthly","killbillPriceList":"DEFAULT","createdAt":"2026-06-01T00:00:00Z","quotas":[{"quotaId":"q1","eventType":"credential.issued","hardLimit":500,"period":"Month","expression":null}]}]""")),
        )
        val plans = client.listPlans("app-1")
        assertEquals(1, plans.size)
        assertEquals("tw-pro", plans[0].name)
        assertEquals("credential.issued", plans[0].quotas[0].eventType)
    }

    @Test
    fun `updateKillbillMapping issues a PATCH`() {
        server.stubFor(patch(urlEqualTo("/api/v1/plans/plan-1/killbill-mapping")).willReturn(aResponse().withStatus(204)))
        client.updateKillbillMapping("plan-1", KillbillMappingRequest("trustweave-pro-monthly", "trustweave-pro", "DEFAULT"))
        server.verify(patchRequestedFor(urlEqualTo("/api/v1/plans/plan-1/killbill-mapping"))
            .withRequestBody(matchingJsonPath("$.killbillPlanName", equalTo("trustweave-pro-monthly"))))
    }

    @Test
    fun `createPlanQuota returns quota_id`() {
        server.stubFor(post(urlEqualTo("/api/v1/plans/plan-1/quotas"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""{"quota_id":"q1"}""")))
        val id = client.createPlanQuota("plan-1", CreateQuotaRequest("credential.issued", 500, "Month"))
        assertEquals("q1", id)
    }

    @Test
    fun `declareUsageVariable returns id`() {
        server.stubFor(post(urlEqualTo("/api/v1/applications/app-1/usage-variables"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""{"id":"uv1"}""")))
        val id = client.declareUsageVariable("app-1", DeclareUsageVariableRequest("credential.issued", "credential"))
        assertEquals("uv1", id)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyBillingClientTest"`
Expected: FAIL — `AccountlyBillingClient` / DTOs unresolved.

- [ ] **Step 3: Create the DTOs**

Create `AccountlyDtos.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApplicationDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateApplicationRequest(val name: String, val description: String? = null)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreatePlanRequest(
    val name: String,
    val pricingModel: String,
    val monthlyFee: String? = null,
    val level: Int? = null,
    val isDefault: Boolean? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class KillbillMappingRequest(
    val killbillPlanName: String? = null,
    val killbillProductName: String? = null,
    val killbillPriceList: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateQuotaRequest(
    val eventType: String,
    val hardLimit: Long,
    val period: String,
    val expression: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeclareUsageVariableRequest(
    val eventType: String,
    val unit: String,
    val description: String? = null,
    val defaultQuantity: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlanQuotaDto(
    val quotaId: String,
    val eventType: String,
    val hardLimit: Long,
    val period: String,
    val expression: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlanDto(
    val planId: String,
    val applicationId: String,
    val name: String,
    val pricingModel: String,
    val monthlyFee: String? = null,
    val level: Int = 1,
    val isDefault: Boolean = false,
    val killbillProductName: String? = null,
    val killbillPlanName: String? = null,
    val killbillPriceList: String? = null,
    val quotas: List<PlanQuotaDto> = emptyList(),
)
```

- [ ] **Step 4: Create the client**

Create `AccountlyBillingClient.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
open class AccountlyBillingClient(
    @org.springframework.beans.factory.annotation.Qualifier("accountlyRestClient")
    private val restClient: RestClient,
    private val tokens: AccountlyServiceTokenProvider,
) {
    private fun auth() = "Bearer ${tokens.currentToken()}"

    open fun listApplications(): List<ApplicationDto> =
        restClient.get().uri("/api/v1/applications")
            .header(HttpHeaders.AUTHORIZATION, auth())
            .retrieve().body<List<ApplicationDto>>() ?: emptyList()

    open fun createApplication(name: String, description: String?): String {
        val body = restClient.post().uri("/api/v1/applications")
            .header(HttpHeaders.AUTHORIZATION, auth())
            .contentType(MediaType.APPLICATION_JSON)
            .body(CreateApplicationRequest(name, description))
            .retrieve().body<Map<String, Any?>>()
        return (body?.get("id") as? String) ?: error("Accountly createApplication returned no id")
    }

    open fun listPlans(applicationId: String): List<PlanDto> =
        restClient.get().uri("/api/v1/applications/{appId}/plans", applicationId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .retrieve().body<List<PlanDto>>() ?: emptyList()

    open fun createPlan(applicationId: String, request: CreatePlanRequest): String {
        val body = restClient.post().uri("/api/v1/applications/{appId}/plans", applicationId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve().body<Map<String, Any?>>()
        return (body?.get("plan_id") as? String) ?: error("Accountly createPlan returned no plan_id")
    }

    open fun updateKillbillMapping(planId: String, request: KillbillMappingRequest) {
        restClient.patch().uri("/api/v1/plans/{planId}/killbill-mapping", planId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve().toBodilessEntity()
    }

    open fun createPlanQuota(planId: String, request: CreateQuotaRequest): String {
        val body = restClient.post().uri("/api/v1/plans/{planId}/quotas", planId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve().body<Map<String, Any?>>()
        return (body?.get("quota_id") as? String) ?: error("Accountly createPlanQuota returned no quota_id")
    }

    open fun declareUsageVariable(applicationId: String, request: DeclareUsageVariableRequest): String {
        val body = restClient.post().uri("/api/v1/applications/{appId}/usage-variables", applicationId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve().body<Map<String, Any?>>()
        return (body?.get("id") as? String) ?: error("Accountly declareUsageVariable returned no id")
    }
}
```

> Note: `org.springframework.web.client.body` is the Kotlin extension enabling `retrieve().body<List<ApplicationDto>>()` (reified generic). Keep that import.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyBillingClientTest"`
Expected: PASS (7 tests).

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyDtos.kt \
        server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClient.kt \
        server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClientTest.kt
git commit -m "feat(billing): typed Accountly catalog client over RestClient"
```

---

## Task 4: TrustWeave tier catalog definition

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/TrustWeaveTierCatalog.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/TrustWeaveTierCatalogTest.kt`

A pure data definition of the tiers (matches `OrganizationTier` FREE/PRO/ENTERPRISE) with their Kill Bill mapping, monthly fee, level, and per-tier quotas + the declared usage variables. This is the single source of truth the reconciler pushes.

- [ ] **Step 1: Write the failing test**

Create `TrustWeaveTierCatalogTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.geoknoesis.trustweave.saas.server.domain.OrganizationTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrustWeaveTierCatalogTest {

    @Test
    fun `defines all three tiers mapped to kill bill plans`() {
        val tiers = TrustWeaveTierCatalog.TIERS
        assertEquals(3, tiers.size)
        assertEquals(setOf(OrganizationTier.FREE, OrganizationTier.PRO, OrganizationTier.ENTERPRISE), tiers.map { it.tier }.toSet())
        val free = tiers.first { it.tier == OrganizationTier.FREE }
        assertEquals("trustweave-free-monthly", free.killbillPlanName)
        assertTrue(free.isDefault)
    }

    @Test
    fun `free tier caps credential issuance at 500 per month`() {
        val free = TrustWeaveTierCatalog.TIERS.first { it.tier == OrganizationTier.FREE }
        val quota = free.quotas.first { it.eventType == "credential.issued" }
        assertEquals(500, quota.hardLimit)
        assertEquals("Month", quota.period)
    }

    @Test
    fun `declares the four metered usage variables`() {
        assertEquals(
            setOf("credential.issued", "credential.verified", "blockchain.tx", "wallet.seat"),
            TrustWeaveTierCatalog.USAGE_VARIABLES.map { it.eventType }.toSet(),
        )
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.TrustWeaveTierCatalogTest"`
Expected: FAIL — `TrustWeaveTierCatalog` unresolved.

- [ ] **Step 3: Implement the catalog definition**

Create `TrustWeaveTierCatalog.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.geoknoesis.trustweave.saas.server.domain.OrganizationTier

/** Declarative TrustWeave billing catalog — the source of truth reconciled into Accountly. */
object TrustWeaveTierCatalog {

    data class QuotaDef(val eventType: String, val hardLimit: Long, val period: String = "Month")

    data class TierDef(
        val tier: OrganizationTier,
        val planName: String,
        val monthlyFee: String,
        val level: Int,
        val isDefault: Boolean,
        val killbillProductName: String,
        val killbillPlanName: String,
        val quotas: List<QuotaDef>,
    )

    data class UsageVariableDef(val eventType: String, val unit: String, val description: String)

    val USAGE_VARIABLES = listOf(
        UsageVariableDef("credential.issued", "credential", "Verifiable credentials issued"),
        UsageVariableDef("credential.verified", "verification", "Credential verifications performed"),
        UsageVariableDef("blockchain.tx", "usd-cents", "Blockchain anchoring transaction cost (USD cents)"),
        UsageVariableDef("wallet.seat", "seat", "Active holder wallets / DIDs"),
    )

    val TIERS = listOf(
        TierDef(
            tier = OrganizationTier.FREE,
            planName = "tw-free",
            monthlyFee = "0",
            level = 1,
            isDefault = true,
            killbillProductName = "trustweave-free",
            killbillPlanName = "trustweave-free-monthly",
            quotas = listOf(
                QuotaDef("credential.issued", 500),
                QuotaDef("credential.verified", 2000),
            ),
        ),
        TierDef(
            tier = OrganizationTier.PRO,
            planName = "tw-pro",
            monthlyFee = "99",
            level = 2,
            isDefault = false,
            killbillProductName = "trustweave-pro",
            killbillPlanName = "trustweave-pro-monthly",
            quotas = listOf(
                QuotaDef("credential.issued", 50_000),
                QuotaDef("credential.verified", 200_000),
            ),
        ),
        TierDef(
            tier = OrganizationTier.ENTERPRISE,
            planName = "tw-enterprise",
            monthlyFee = "499",
            level = 3,
            isDefault = false,
            killbillProductName = "trustweave-enterprise",
            killbillPlanName = "trustweave-enterprise-monthly",
            quotas = listOf(
                QuotaDef("credential.issued", 1_000_000),
                QuotaDef("credential.verified", 5_000_000),
            ),
        ),
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.TrustWeaveTierCatalogTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/TrustWeaveTierCatalog.kt \
        server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/TrustWeaveTierCatalogTest.kt
git commit -m "feat(billing): declarative TrustWeave tier catalog definition"
```

---

## Task 5: CatalogReconciler — idempotent push into Accountly

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/CatalogReconciler.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/CatalogReconcilerTest.kt`

Reconcile logic (idempotent, create-or-find):
1. Find the Application by `applicationName` in `listApplications()`; create it if absent → `applicationId`.
2. `listPlans(applicationId)` once; index existing plans by name.
3. For each `TierDef`: if a plan with that `planName` does not exist, `createPlan` + `updateKillbillMapping` + create each quota; if it exists, skip creation (already reconciled). 
4. Declare each usage variable; ignore the "already declared" 400 from Accountly (treat as success).

The reconciler returns the resolved `applicationId`. It is invoked by an app-ready hook in Plan 2b; here we test the logic directly against WireMock.

- [ ] **Step 1: Write the failing test**

Create `CatalogReconcilerTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class CatalogReconcilerTest {

    private lateinit var server: WireMockServer
    private lateinit var reconciler: CatalogReconciler

    @BeforeEach
    fun start() {
        server = WireMockServer(options().dynamicPort())
        server.start()
        val rest = RestClient.builder().baseUrl("http://localhost:${server.port()}").build()
        val tokens = object : AccountlyServiceTokenProvider(
            tokenEndpoint = "http://unused", clientId = "x", clientSecret = "y",
            restClient = RestClient.create(), clockMillis = { 0L },
        ) { override fun currentToken() = "test-token" }
        val client = AccountlyBillingClient(rest, tokens)
        reconciler = CatalogReconciler(client, AccountlyBillingProperties(applicationName = "TrustWeave SaaS"))
    }

    @AfterEach
    fun stop() = server.stop()

    @Test
    fun `creates application, all tier plans with mapping and quotas, and usage variables on an empty Accountly`() {
        // No application yet -> created.
        server.stubFor(get(urlEqualTo("/api/v1/applications")).willReturn(okJson("[]")))
        server.stubFor(post(urlEqualTo("/api/v1/applications"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""{"id":"app-1"}""")))
        // No plans yet.
        server.stubFor(get(urlEqualTo("/api/v1/applications/app-1/plans")).willReturn(okJson("[]")))
        // Plan creation returns sequential ids.
        server.stubFor(post(urlEqualTo("/api/v1/applications/app-1/plans"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""{"plan_id":"plan-x"}""")))
        server.stubFor(patch(urlPathMatching("/api/v1/plans/.*/killbill-mapping")).willReturn(aResponse().withStatus(204)))
        server.stubFor(post(urlPathMatching("/api/v1/plans/.*/quotas"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""{"quota_id":"q"}""")))
        server.stubFor(post(urlEqualTo("/api/v1/applications/app-1/usage-variables"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""{"id":"uv"}""")))

        val appId = reconciler.reconcile()

        assertEquals("app-1", appId)
        server.verify(1, postRequestedFor(urlEqualTo("/api/v1/applications")))
        // 3 tiers -> 3 plan creates, 3 mapping patches.
        server.verify(3, postRequestedFor(urlEqualTo("/api/v1/applications/app-1/plans")))
        server.verify(3, patchRequestedFor(urlPathMatching("/api/v1/plans/.*/killbill-mapping")))
        // FREE(2)+PRO(2)+ENTERPRISE(2) = 6 quota posts.
        server.verify(6, postRequestedFor(urlPathMatching("/api/v1/plans/.*/quotas")))
        // 4 usage variables.
        server.verify(4, postRequestedFor(urlEqualTo("/api/v1/applications/app-1/usage-variables")))
    }

    @Test
    fun `is idempotent - existing application and plans are not recreated`() {
        server.stubFor(get(urlEqualTo("/api/v1/applications"))
            .willReturn(okJson("""[{"id":"app-1","name":"TrustWeave SaaS","description":null,"createdAt":"x"}]""")))
        // All three plans already exist.
        server.stubFor(get(urlEqualTo("/api/v1/applications/app-1/plans")).willReturn(okJson(
            """[{"planId":"p1","applicationId":"app-1","name":"tw-free","pricingModel":"FlatRate","quotas":[]},
                {"planId":"p2","applicationId":"app-1","name":"tw-pro","pricingModel":"FlatRate","quotas":[]},
                {"planId":"p3","applicationId":"app-1","name":"tw-enterprise","pricingModel":"FlatRate","quotas":[]}]""")))
        server.stubFor(post(urlEqualTo("/api/v1/applications/app-1/usage-variables"))
            .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                .withBody("""{"detail":"event_type ... is already declared"}""")))

        val appId = reconciler.reconcile()

        assertEquals("app-1", appId)
        server.verify(0, postRequestedFor(urlEqualTo("/api/v1/applications")))
        server.verify(0, postRequestedFor(urlEqualTo("/api/v1/applications/app-1/plans")))
        // usage-variable re-declare 400s are swallowed (no exception) — 4 attempts still made.
        server.verify(4, postRequestedFor(urlEqualTo("/api/v1/applications/app-1/usage-variables")))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.CatalogReconcilerTest"`
Expected: FAIL — `CatalogReconciler` unresolved.

- [ ] **Step 3: Implement the reconciler**

Create `CatalogReconciler.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException

/** Reconciles [TrustWeaveTierCatalog] into Accountly idempotently. Returns the Accountly applicationId. */
@Component
open class CatalogReconciler(
    private val client: AccountlyBillingClient,
    private val properties: AccountlyBillingProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    open fun reconcile(): String {
        val appName = properties.applicationName
        val applicationId =
            client.listApplications().firstOrNull { it.name == appName }?.id
                ?: client.createApplication(appName, "TrustWeave SaaS billing merchant").also {
                    log.info("Created Accountly application '{}' = {}", appName, it)
                }

        val existingByName = client.listPlans(applicationId).associateBy { it.name }

        for (tier in TrustWeaveTierCatalog.TIERS) {
            if (existingByName.containsKey(tier.planName)) {
                log.debug("Plan '{}' already present; skipping", tier.planName)
                continue
            }
            val planId = client.createPlan(
                applicationId,
                CreatePlanRequest(
                    name = tier.planName,
                    pricingModel = "FlatRate",
                    monthlyFee = tier.monthlyFee,
                    level = tier.level,
                    isDefault = tier.isDefault,
                ),
            )
            client.updateKillbillMapping(
                planId,
                KillbillMappingRequest(
                    killbillPlanName = tier.killbillPlanName,
                    killbillProductName = tier.killbillProductName,
                    killbillPriceList = "DEFAULT",
                ),
            )
            for (q in tier.quotas) {
                client.createPlanQuota(planId, CreateQuotaRequest(q.eventType, q.hardLimit, q.period))
            }
            log.info("Reconciled plan '{}' = {}", tier.planName, planId)
        }

        for (uv in TrustWeaveTierCatalog.USAGE_VARIABLES) {
            try {
                client.declareUsageVariable(
                    applicationId,
                    DeclareUsageVariableRequest(eventType = uv.eventType, unit = uv.unit, description = uv.description),
                )
            } catch (ex: HttpClientErrorException) {
                // 400 = already declared; any other 4xx is unexpected.
                if (ex.statusCode.value() == 400) {
                    log.debug("Usage variable '{}' already declared", uv.eventType)
                } else {
                    throw ex
                }
            }
        }
        return applicationId
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.CatalogReconcilerTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/CatalogReconciler.kt \
        server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/CatalogReconcilerTest.kt
git commit -m "feat(billing): idempotent CatalogReconciler pushing tiers into Accountly"
```

---

## Task 6: Full module verification

- [ ] **Step 1: Compile + run the whole server module**

Run: `cd /c/Users/steph/work/trustweave-saas && ./gradlew :server:test`
Expected: BUILD SUCCESSFUL — all prior tests plus the new `billing.accountly` tests (Properties 2, TokenProvider 2, Client 7, TierCatalog 3, Reconciler 2).

- [ ] **Step 2: Confirm the new beans don't break context startup**

The new `@Component`/`@Configuration` beans must coexist with the existing Stripe beans (not yet removed). If a Spring context test exists and fails on the new `AccountlyServiceTokenProvider` (it has two constructors), confirm Spring picks the `@Value`/4-arg constructor — annotate that constructor with `@org.springframework.beans.factory.annotation.Autowired` if Spring cannot choose. Re-run `:server:test`.

---

## Self-Review

**Spec coverage** (design spec §4 "catalog-as-code" + §2 `AccountlyBillingClient`, `CatalogReconciler`):
- Service-token auth (shared-Keycloak client-credentials) → Task 2. ✓
- Typed Accountly client over the Plan 1 catalog contract → Task 3. ✓
- TrustWeave-declared tier catalog → Task 4. ✓
- Idempotent reconcile into Accountly → Task 5. ✓
- Config/wiring → Task 1. ✓
- Out of scope here (separate sub-plans): `Organization` billing fields, provision/subscribe/upgrade/cancel, inbound webhook, retire Stripe (**Plan 2b**); usage outbox/reporter, `EntitlementGate`, `TransactionCostBillingService` refactor (**Plan 2c**). The plan widgets/proxy are **Plan 3**.

**Placeholder scan:** none — every step has concrete code/commands.

**Type consistency:** DTOs (`ApplicationDto`, `CreatePlanRequest`, `KillbillMappingRequest`, `CreateQuotaRequest`, `DeclareUsageVariableRequest`, `PlanDto`, `PlanQuotaDto`) are defined in Task 3 and used identically in Tasks 3 & 5. Client method names (`listApplications`, `createApplication`, `listPlans`, `createPlan`, `updateKillbillMapping`, `createPlanQuota`, `declareUsageVariable`) match between client (Task 3) and reconciler (Task 5). `AccountlyServiceTokenProvider.currentToken()` is `open` so tests can subclass-stub it; `AccountlyBillingClient` is `open` and methods `open` (kotlin-spring allopen covers `@Component`, but explicit `open` matches the subclass-stub pattern used in tests).

**Known caveats:**
- `AccountlyServiceTokenProvider` has two constructors (test 5-arg + Spring `@Value` 4-arg). If Spring ambiguity arises at context load, mark the `@Value` constructor `@Autowired` (noted in Task 6 Step 2).
- WireMock 3 ships classes under `com.github.tomakehurst.wiremock.*`; the artifact is `org.wiremock:wiremock-standalone:3.3.1` (already a test dependency).
- The reconciler relies on Accountly returning HTTP 400 for an already-declared usage variable (Plan 1 behavior) and swallows exactly that.

---

## Next sub-plans (written when this one is executed & merged)
- **Plan 2b — subscriber lifecycle + entitlement sync:** `Organization` billing fields; `OrganizationBillingService` (provision subscriber → subscribe default FREE → upgrade/downgrade/cancel); `AccountlyWebhookController` (HMAC-verified inbound `subscription.updated` → update `OrganizationTier` + cache snapshot); retire `StripeService` + Stripe `SubscriptionService`.
- **Plan 2c — usage metering + enforcement:** `usage_outbox` entity + `UsageReporter` (async, idempotent, retry); `EntitlementGate` (local quota check against cached snapshot) wired into credential issuance/verification; refactor `TransactionCostBillingService` to emit `blockchain.tx` usage events.
