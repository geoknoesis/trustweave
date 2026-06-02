# Plan 2b — TrustWeave Subscriber Lifecycle, Entitlement Sync & Inbound Webhook

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let TrustWeave provision each `Organization` as an Accountly subscriber, subscribe/upgrade/cancel it on the tier plans, cache its entitlement snapshot, and keep its `OrganizationTier` in sync via an HMAC-verified inbound webhook from Accountly — while deprecating (not yet deleting) the Stripe subscription path.

**Architecture:** Builds on Plan 2a's `AccountlyBillingClient`/`CatalogReconciler`. Adds subscriber-lifecycle client methods, billing-link fields on `Organization`, an `OrganizationBillingService` orchestrating provision/upgrade/cancel, a pure HMAC verifier, and an `AccountlyWebhookController`. Stripe classes are marked `@Deprecated`; their removal is a later verifiable cleanup (the SaaS `@SpringBootTest` context is currently red due to a PRE-EXISTING Flyway-in-test failure unrelated to billing, so every component here is tested without the full context: mockito for services, `@WebMvcTest` for the controller, plain unit tests for entity fields/verifier).

**Tech Stack:** Kotlin 2.2, Spring Boot 3.5.7 (servlet — `RestClient`, `@WebMvcTest`), JUnit 5 + WireMock 3.3.1 + mockito-kotlin. No Flyway at runtime (`ddl-auto: update`).

---

## Working directory & conventions

- **Repo: `c:\Users\steph\work\trustweave-saas`**, branch `feat/accountly-billing-client` (Plan 2a's branch — 2b stacks on it). Run Gradle from repo root.
- Filtered test runs MUST exclude the coverage gate: `./gradlew :server:test --tests "<FQCN>" -x jacocoTestCoverageVerification`.
- Package: `com.geoknoesis.trustweave.saas.server.billing.accountly` (extends Plan 2a's package). Webhook controller goes in `...server.webhook` next to `StripeWebhookController`.
- Conventions established in 2a: `@Configuration` → plain `class`; `@Component`/`@Service` → plain `class` unless a test subclass-stubs it (then explicit `open`). Services are unit-tested with mockito (mock the client/repo) — NO Spring context (the full context is red, see above). The controller is tested with `@WebMvcTest` (web slice only — no JPA/Flyway).
- `Organization` is an immutable `data class` (`val` fields) — "updates" use `.copy(...)` + `repository.save(...)`.
- Existing facts: `OrganizationRepository.findByOwnerId`; `Organization(id, name, tier, ownerId, stripeCustomerId?, stripeSubscriptionId?, ...)`; `OrganizationTier { FREE, PRO, ENTERPRISE }`. Accountly merchant endpoints (Plan 1): `POST /api/v1/applications/{appId}/subscribers`, `POST|PATCH|DELETE /api/v1/applications/{appId}/subscribers/{subId}/subscriptions[/{id}]`, `GET /api/v1/applications/{appId}/subscribers/{subId}/entitlement` (returns `{status, quotaEnvelopeJson}`). Accountly signs outbound webhooks `X-Accountly-Signature: sha256=<hmac-sha256-hex>` over the raw body.

## File structure

**Created**
- `billing/accountly/OrganizationExternalRef.kt` — `trustweave:org:<id>` ↔ orgId helper.
- `billing/accountly/AccountlyWebhookVerifier.kt` — HMAC-SHA256 signature verify (pure).
- `billing/accountly/OrganizationBillingService.kt` — provision/changeTier/cancel orchestration.
- `webhook/AccountlyWebhookController.kt` — inbound `subscription.updated`.
- Tests mirroring each.

**Modified**
- `billing/accountly/AccountlyDtos.kt` — add subscriber-lifecycle DTOs.
- `billing/accountly/AccountlyBillingClient.kt` — add subscriber-lifecycle methods.
- `domain/Organization.kt` — add billing-link fields.
- `billing/StripeService.kt`, `billing/SubscriptionService.kt` — add `@Deprecated`.

---

## Task 1: Client subscriber-lifecycle methods + DTOs

**Files:**
- Modify: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyDtos.kt`
- Modify: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClient.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClientSubscriberTest.kt`

- [ ] **Step 1: Write the failing test.** Create `AccountlyBillingClientSubscriberTest.kt`:

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

class AccountlyBillingClientSubscriberTest {

    private lateinit var server: WireMockServer
    private lateinit var client: AccountlyBillingClient

    @BeforeEach
    fun start() {
        server = WireMockServer(options().dynamicPort())
        server.start()
        val rest = RestClient.builder().baseUrl("http://localhost:${server.port()}").build()
        val tokens = object : AccountlyServiceTokenProvider(
            tokenEndpoint = "http://unused", clientId = "x", clientSecret = "y",
            restClient = RestClient.create(), clockMillis = { 0L },
        ) { override fun currentToken() = "test-token" }
        client = AccountlyBillingClient(rest, tokens)
    }

    @AfterEach
    fun stop() = server.stop()

    @Test
    fun `provisionSubscriber returns subscriberId and killbillAccountId`() {
        server.stubFor(post(urlEqualTo("/api/v1/applications/app-1/subscribers"))
            .withRequestBody(matchingJsonPath("$.externalRef", equalTo("trustweave:org:7")))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                .withBody("""{"subscriberId":"sub-1","killbillAccountId":"kb-1"}""")))
        val r = client.provisionSubscriber("app-1", ProvisionSubscriberRequest("trustweave:org:7", "Org 7"))
        assertEquals("sub-1", r.subscriberId)
        assertEquals("kb-1", r.killbillAccountId)
    }

    @Test
    fun `subscribe returns subscription_id`() {
        server.stubFor(post(urlEqualTo("/api/v1/applications/app-1/subscribers/sub-1/subscriptions"))
            .withRequestBody(matchingJsonPath("$.planId", equalTo("plan-free")))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                .withBody("""{"subscription_id":"acct-sub-1"}""")))
        assertEquals("acct-sub-1", client.subscribe("app-1", "sub-1", "plan-free"))
    }

    @Test
    fun `changePlan issues PATCH`() {
        server.stubFor(patch(urlEqualTo("/api/v1/applications/app-1/subscribers/sub-1/subscriptions/acct-sub-1"))
            .willReturn(aResponse().withStatus(204)))
        client.changePlan("app-1", "sub-1", "acct-sub-1", "plan-pro")
        server.verify(patchRequestedFor(urlEqualTo("/api/v1/applications/app-1/subscribers/sub-1/subscriptions/acct-sub-1"))
            .withRequestBody(matchingJsonPath("$.planId", equalTo("plan-pro"))))
    }

    @Test
    fun `cancel issues DELETE`() {
        server.stubFor(delete(urlEqualTo("/api/v1/applications/app-1/subscribers/sub-1/subscriptions/acct-sub-1"))
            .willReturn(aResponse().withStatus(204)))
        client.cancel("app-1", "sub-1", "acct-sub-1")
        server.verify(deleteRequestedFor(urlEqualTo("/api/v1/applications/app-1/subscribers/sub-1/subscriptions/acct-sub-1")))
    }

    @Test
    fun `getEntitlement parses status and quotaEnvelopeJson`() {
        server.stubFor(get(urlEqualTo("/api/v1/applications/app-1/subscribers/sub-1/entitlement"))
            .willReturn(okJson("""{"status":"Active","quotaEnvelopeJson":"{\"credential.issued\":{\"used\":12,\"limit\":500}}"}""")))
        val e = client.getEntitlement("app-1", "sub-1")
        assertEquals("Active", e.status)
        assertEquals("""{"credential.issued":{"used":12,"limit":500}}""", e.quotaEnvelopeJson)
    }
}
```

- [ ] **Step 2: Run → FAIL** (`ProvisionSubscriberRequest` / methods unresolved):
`./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyBillingClientSubscriberTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Add DTOs.** Append to `AccountlyDtos.kt`:

```kotlin
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProvisionSubscriberRequest(
    val externalRef: String,
    val displayName: String? = null,
    val email: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProvisionSubscriberResponse(
    val subscriberId: String,
    val killbillAccountId: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SubscribeRequest(val planId: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EntitlementSnapshotDto(
    val status: String,
    val quotaEnvelopeJson: String,
)
```

- [ ] **Step 4: Add client methods.** Append these methods inside `AccountlyBillingClient`:

```kotlin
    fun provisionSubscriber(applicationId: String, request: ProvisionSubscriberRequest): ProvisionSubscriberResponse =
        restClient.post().uri("/api/v1/applications/{appId}/subscribers", applicationId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve().body(ProvisionSubscriberResponse::class.java)
            ?: error("Accountly provisionSubscriber returned no body")

    fun subscribe(applicationId: String, subscriberId: String, planId: String): String {
        val body = restClient.post()
            .uri("/api/v1/applications/{appId}/subscribers/{subId}/subscriptions", applicationId, subscriberId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .contentType(MediaType.APPLICATION_JSON)
            .body(SubscribeRequest(planId))
            .retrieve().body<Map<String, Any?>>()
        return (body?.get("subscription_id") as? String) ?: error("Accountly subscribe returned no subscription_id")
    }

    fun changePlan(applicationId: String, subscriberId: String, subscriptionId: String, planId: String) {
        restClient.patch()
            .uri("/api/v1/applications/{appId}/subscribers/{subId}/subscriptions/{id}", applicationId, subscriberId, subscriptionId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .contentType(MediaType.APPLICATION_JSON)
            .body(SubscribeRequest(planId))
            .retrieve().toBodilessEntity()
    }

    fun cancel(applicationId: String, subscriberId: String, subscriptionId: String) {
        restClient.delete()
            .uri("/api/v1/applications/{appId}/subscribers/{subId}/subscriptions/{id}", applicationId, subscriberId, subscriptionId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .retrieve().toBodilessEntity()
    }

    fun getEntitlement(applicationId: String, subscriberId: String): EntitlementSnapshotDto =
        restClient.get()
            .uri("/api/v1/applications/{appId}/subscribers/{subId}/entitlement", applicationId, subscriberId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .retrieve().body(EntitlementSnapshotDto::class.java)
            ?: error("Accountly getEntitlement returned no body")
```

- [ ] **Step 5: Run → PASS (5 tests).** Same command as Step 2.

- [ ] **Step 6: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyDtos.kt server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClient.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClientSubscriberTest.kt
git commit -m "feat(billing): Accountly subscriber-lifecycle client methods"
```

---

## Task 2: Organization billing-link fields

**Files:**
- Modify: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/domain/Organization.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/domain/OrganizationBillingFieldsTest.kt`

- [ ] **Step 1: Write the failing test.** Create `OrganizationBillingFieldsTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OrganizationBillingFieldsTest {

    @Test
    fun `billing-link fields default to null and survive copy`() {
        val org = Organization(name = "Org 7", ownerId = 1L)
        assertNull(org.accountlySubscriberId)
        assertNull(org.accountlySubscriptionId)
        assertNull(org.killbillAccountId)
        assertNull(org.entitlementSnapshotJson)
        assertNull(org.snapshotRefreshedAt)

        val linked = org.copy(
            accountlySubscriberId = "sub-1",
            accountlySubscriptionId = "acct-sub-1",
            killbillAccountId = "kb-1",
            entitlementSnapshotJson = "{}",
        )
        assertEquals("sub-1", linked.accountlySubscriberId)
        assertEquals("acct-sub-1", linked.accountlySubscriptionId)
        assertEquals("kb-1", linked.killbillAccountId)
    }
}
```

- [ ] **Step 2: Run → FAIL** (fields unresolved):
`./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.domain.OrganizationBillingFieldsTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Add the fields.** In `Organization.kt`, add to the constructor (after `transactionCostTrackingEnabled`, before `settings`/`createdAt` — keep valid ordering with defaults). Insert:

```kotlin
    @Column(name = "accountly_subscriber_id")
    val accountlySubscriberId: String? = null,

    @Column(name = "accountly_subscription_id")
    val accountlySubscriptionId: String? = null,

    @Column(name = "accountly_killbill_account_id")
    val killbillAccountId: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entitlement_snapshot", columnDefinition = "JSONB")
    val entitlementSnapshotJson: String? = null,

    @Column(name = "snapshot_refreshed_at")
    val snapshotRefreshedAt: Instant? = null,
```

(These are new nullable columns; `ddl-auto: update` adds them automatically — no migration. They mirror the existing JSONB `settings` field's annotations.)

- [ ] **Step 4: Run → PASS (1 test).** Same command as Step 2.

- [ ] **Step 5: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/domain/Organization.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/domain/OrganizationBillingFieldsTest.kt
git commit -m "feat(billing): Accountly billing-link fields on Organization"
```

---

## Task 3: OrganizationExternalRef helper

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/OrganizationExternalRef.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/OrganizationExternalRefTest.kt`

- [ ] **Step 1: Write the failing test.** Create `OrganizationExternalRefTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OrganizationExternalRefTest {

    @Test
    fun `formats and parses org external ref`() {
        assertEquals("trustweave:org:42", OrganizationExternalRef.forOrg(42L))
        assertEquals(42L, OrganizationExternalRef.parseOrgId("trustweave:org:42"))
    }

    @Test
    fun `returns null for foreign or malformed refs`() {
        assertNull(OrganizationExternalRef.parseOrgId("stripe:cus:42"))
        assertNull(OrganizationExternalRef.parseOrgId("trustweave:org:abc"))
        assertNull(OrganizationExternalRef.parseOrgId(""))
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.OrganizationExternalRefTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Implement.** Create `OrganizationExternalRef.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

/** Stable Accountly subscriber external reference for a TrustWeave Organization. */
object OrganizationExternalRef {
    private const val PREFIX = "trustweave:org:"

    fun forOrg(organizationId: Long): String = "$PREFIX$organizationId"

    fun parseOrgId(externalRef: String): Long? =
        externalRef.removePrefix(PREFIX)
            .takeIf { it != externalRef && it.isNotEmpty() }
            ?.toLongOrNull()
}
```

- [ ] **Step 4: Run → PASS (2 tests).** Same command as Step 2.

- [ ] **Step 5: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/OrganizationExternalRef.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/OrganizationExternalRefTest.kt
git commit -m "feat(billing): Organization external-ref helper"
```

---

## Task 4: OrganizationBillingService

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/OrganizationBillingService.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/OrganizationBillingServiceTest.kt`

Orchestrates provisioning + tier changes. Resolves the Accountly applicationId via `CatalogReconciler.reconcile()` and the plan id by matching `TrustWeaveTierCatalog` `planName` against `client.listPlans(appId)`. Persists the linkage on `Organization` (copy + save). Unit-tested with mockito (mock client, reconciler, repository) — NO Spring context.

- [ ] **Step 1: Write the failing test.** Create `OrganizationBillingServiceTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.geoknoesis.trustweave.saas.server.domain.Organization
import com.geoknoesis.trustweave.saas.server.domain.OrganizationTier
import com.geoknoesis.trustweave.saas.server.repository.OrganizationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OrganizationBillingServiceTest {

    private val client = mock<AccountlyBillingClient>()
    private val reconciler = mock<CatalogReconciler>()
    private val orgs = mock<OrganizationRepository>()
    private val service = OrganizationBillingService(client, reconciler, orgs)

    private val plans = listOf(
        PlanDto(planId = "plan-free", applicationId = "app-1", name = "tw-free", pricingModel = "FlatRate"),
        PlanDto(planId = "plan-pro", applicationId = "app-1", name = "tw-pro", pricingModel = "FlatRate"),
    )

    @Test
    fun `provision creates subscriber, subscribes to FREE, and stores linkage`() {
        val org = Organization(id = 7L, name = "Org 7", ownerId = 1L)
        whenever(reconciler.reconcile()).thenReturn("app-1")
        whenever(client.provisionSubscriber(eq("app-1"), any()))
            .thenReturn(ProvisionSubscriberResponse(subscriberId = "sub-7", killbillAccountId = "kb-7"))
        whenever(client.listPlans("app-1")).thenReturn(plans)
        whenever(client.subscribe("app-1", "sub-7", "plan-free")).thenReturn("acct-sub-7")
        whenever(orgs.save(any<Organization>())).thenAnswer { it.arguments[0] as Organization }

        val result = service.provision(org)

        val captor = argumentCaptor<Organization>()
        verify(orgs).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("sub-7", saved.accountlySubscriberId)
        assertEquals("acct-sub-7", saved.accountlySubscriptionId)
        assertEquals("kb-7", saved.killbillAccountId)
        assertEquals("sub-7", result.accountlySubscriberId)
        // externalRef passed was trustweave:org:7
        val req = argumentCaptor<ProvisionSubscriberRequest>()
        verify(client).provisionSubscriber(eq("app-1"), req.capture())
        assertEquals("trustweave:org:7", req.firstValue.externalRef)
    }

    @Test
    fun `changeTier switches the Accountly plan and updates the local tier`() {
        val org = Organization(
            id = 7L, name = "Org 7", ownerId = 1L,
            accountlySubscriberId = "sub-7", accountlySubscriptionId = "acct-sub-7",
        )
        whenever(reconciler.reconcile()).thenReturn("app-1")
        whenever(client.listPlans("app-1")).thenReturn(plans)
        whenever(orgs.save(any<Organization>())).thenAnswer { it.arguments[0] as Organization }

        val result = service.changeTier(org, OrganizationTier.PRO)

        verify(client).changePlan("app-1", "sub-7", "acct-sub-7", "plan-pro")
        assertEquals(OrganizationTier.PRO, result.tier)
    }

    @Test
    fun `cancel cancels the Accountly subscription and resets tier to FREE`() {
        val org = Organization(
            id = 7L, name = "Org 7", ownerId = 1L, tier = OrganizationTier.PRO,
            accountlySubscriberId = "sub-7", accountlySubscriptionId = "acct-sub-7",
        )
        whenever(reconciler.reconcile()).thenReturn("app-1")
        whenever(orgs.save(any<Organization>())).thenAnswer { it.arguments[0] as Organization }

        val result = service.cancel(org)

        verify(client).cancel("app-1", "sub-7", "acct-sub-7")
        assertEquals(OrganizationTier.FREE, result.tier)
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.OrganizationBillingServiceTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Implement.** Create `OrganizationBillingService.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.geoknoesis.trustweave.saas.server.domain.Organization
import com.geoknoesis.trustweave.saas.server.domain.OrganizationTier
import com.geoknoesis.trustweave.saas.server.repository.OrganizationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OrganizationBillingService(
    private val client: AccountlyBillingClient,
    private val reconciler: CatalogReconciler,
    private val organizations: OrganizationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Provision the org as an Accountly subscriber and subscribe it to the default FREE plan. */
    fun provision(org: Organization): Organization {
        val appId = reconciler.reconcile()
        val provisioned = client.provisionSubscriber(
            appId,
            ProvisionSubscriberRequest(externalRef = OrganizationExternalRef.forOrg(org.id), displayName = org.name),
        )
        val freePlanId = planIdFor(appId, OrganizationTier.FREE)
        val subscriptionId = client.subscribe(appId, provisioned.subscriberId, freePlanId)
        log.info("Provisioned org {} as Accountly subscriber {} (sub {})", org.id, provisioned.subscriberId, subscriptionId)
        return organizations.save(
            org.copy(
                tier = OrganizationTier.FREE,
                accountlySubscriberId = provisioned.subscriberId,
                accountlySubscriptionId = subscriptionId,
                killbillAccountId = provisioned.killbillAccountId,
            ),
        )
    }

    /** Change the org's plan in Accountly and update the local tier cache. */
    fun changeTier(org: Organization, newTier: OrganizationTier): Organization {
        val appId = reconciler.reconcile()
        val subscriberId = requireNotNull(org.accountlySubscriberId) { "Organization ${org.id} has no Accountly subscriber" }
        val subscriptionId = requireNotNull(org.accountlySubscriptionId) { "Organization ${org.id} has no Accountly subscription" }
        client.changePlan(appId, subscriberId, subscriptionId, planIdFor(appId, newTier))
        return organizations.save(org.copy(tier = newTier))
    }

    /** Cancel the org's Accountly subscription and reset the local tier to FREE. */
    fun cancel(org: Organization): Organization {
        val appId = reconciler.reconcile()
        val subscriberId = requireNotNull(org.accountlySubscriberId) { "Organization ${org.id} has no Accountly subscriber" }
        val subscriptionId = requireNotNull(org.accountlySubscriptionId) { "Organization ${org.id} has no Accountly subscription" }
        client.cancel(appId, subscriberId, subscriptionId)
        return organizations.save(org.copy(tier = OrganizationTier.FREE))
    }

    private fun planIdFor(applicationId: String, tier: OrganizationTier): String {
        val planName = TrustWeaveTierCatalog.TIERS.first { it.tier == tier }.planName
        return client.listPlans(applicationId).firstOrNull { it.name == planName }?.planId
            ?: error("Accountly plan '$planName' not found for application $applicationId (run catalog reconciliation)")
    }
}
```

- [ ] **Step 4: Run → PASS (3 tests).** Same command as Step 2.

- [ ] **Step 5: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/OrganizationBillingService.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/OrganizationBillingServiceTest.kt
git commit -m "feat(billing): OrganizationBillingService provision/changeTier/cancel"
```

---

## Task 5: Inbound webhook HMAC verifier

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyWebhookVerifier.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyWebhookVerifierTest.kt`

Verifies the `X-Accountly-Signature: sha256=<hex>` header (HMAC-SHA256 over the raw body), matching Accountly's `MerchantWebhookSigner`. Constant-time compare.

- [ ] **Step 1: Write the failing test.** Create `AccountlyWebhookVerifierTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccountlyWebhookVerifierTest {

    private val verifier = AccountlyWebhookVerifier()

    // Same RFC HMAC-SHA256 vector Accountly's signer uses.
    private val secret = "key"
    private val body = "The quick brown fox jumps over the lazy dog"
    private val goodSig = "sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"

    @Test
    fun `accepts a valid signature`() {
        assertTrue(verifier.isValid(secret, body, goodSig))
    }

    @Test
    fun `rejects a wrong signature or wrong secret`() {
        assertFalse(verifier.isValid(secret, body, "sha256=deadbeef"))
        assertFalse(verifier.isValid("other", body, goodSig))
        assertFalse(verifier.isValid(secret, body, ""))
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyWebhookVerifierTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Implement.** Create `AccountlyWebhookVerifier.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Verifies Accountly's `X-Accountly-Signature: sha256=<hmac-sha256-hex>` over the raw body. */
@Component
class AccountlyWebhookVerifier {

    fun sign(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return "sha256=" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun isValid(secret: String, payload: String, signatureHeader: String): Boolean {
        if (signatureHeader.isBlank()) return false
        val expected = sign(secret, payload)
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            signatureHeader.toByteArray(StandardCharsets.UTF_8),
        )
    }
}
```

- [ ] **Step 4: Run → PASS (2 tests).** Same command as Step 2.

- [ ] **Step 5: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyWebhookVerifier.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyWebhookVerifierTest.kt
git commit -m "feat(billing): inbound Accountly webhook HMAC verifier"
```

---

## Task 6: AccountlyWebhookController + tier-sync handler

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyWebhookHandler.kt`
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/webhook/AccountlyWebhookController.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyWebhookHandlerTest.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/webhook/AccountlyWebhookControllerTest.kt`

Split the logic (handler, mockito-tested) from the HTTP concern (controller, `@WebMvcTest`-tested) so neither needs the broken full context. The handler maps the inbound `subscription.updated` (`externalRef` → orgId, `planName` → tier, `status`) and updates the `Organization`.

- [ ] **Step 1: Write the failing handler test.** Create `AccountlyWebhookHandlerTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.geoknoesis.trustweave.saas.server.domain.Organization
import com.geoknoesis.trustweave.saas.server.domain.OrganizationTier
import com.geoknoesis.trustweave.saas.server.repository.OrganizationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class AccountlyWebhookHandlerTest {

    private val orgs = mock<OrganizationRepository>()
    private val handler = AccountlyWebhookHandler(orgs)

    @Test
    fun `subscription updated to tw-pro upgrades the org tier`() {
        val org = Organization(id = 7L, name = "Org 7", ownerId = 1L, tier = OrganizationTier.FREE)
        whenever(orgs.findById(7L)).thenReturn(Optional.of(org))
        whenever(orgs.save(any<Organization>())).thenAnswer { it.arguments[0] as Organization }

        handler.handle(AccountlyWebhookEvent(
            event = "subscription.updated", externalRef = "trustweave:org:7",
            planName = "tw-pro", status = "Active",
        ))

        val captor = argumentCaptor<Organization>()
        verify(orgs).save(captor.capture())
        assertEquals(OrganizationTier.PRO, captor.firstValue.tier)
    }

    @Test
    fun `canceled status resets the org to FREE`() {
        val org = Organization(id = 7L, name = "Org 7", ownerId = 1L, tier = OrganizationTier.PRO)
        whenever(orgs.findById(7L)).thenReturn(Optional.of(org))
        whenever(orgs.save(any<Organization>())).thenAnswer { it.arguments[0] as Organization }

        handler.handle(AccountlyWebhookEvent("subscription.updated", "trustweave:org:7", "tw-pro", "Canceled"))

        val captor = argumentCaptor<Organization>()
        verify(orgs).save(captor.capture())
        assertEquals(OrganizationTier.FREE, captor.firstValue.tier)
    }

    @Test
    fun `unknown org or foreign ref is ignored`() {
        whenever(orgs.findById(7L)).thenReturn(Optional.empty())
        handler.handle(AccountlyWebhookEvent("subscription.updated", "trustweave:org:7", "tw-pro", "Active"))
        handler.handle(AccountlyWebhookEvent("subscription.updated", "stripe:cus:7", "tw-pro", "Active"))
        verify(orgs, never()).save(any())
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyWebhookHandlerTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Implement the handler + event.** Create `AccountlyWebhookHandler.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.geoknoesis.trustweave.saas.server.domain.OrganizationTier
import com.geoknoesis.trustweave.saas.server.repository.OrganizationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountlyWebhookEvent(
    val event: String? = null,
    val externalRef: String? = null,
    val planName: String? = null,
    val status: String? = null,
)

@Component
class AccountlyWebhookHandler(
    private val organizations: OrganizationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(event: AccountlyWebhookEvent) {
        val orgId = event.externalRef?.let { OrganizationExternalRef.parseOrgId(it) } ?: return
        val org = organizations.findById(orgId).orElse(null) ?: run {
            log.warn("Accountly webhook for unknown org {}", orgId); return
        }
        val newTier = resolveTier(event)
        organizations.save(org.copy(tier = newTier))
        log.info("Org {} tier synced to {} from Accountly webhook (status={})", orgId, newTier, event.status)
    }

    private fun resolveTier(event: AccountlyWebhookEvent): OrganizationTier {
        if (event.status.equals("Canceled", ignoreCase = true)) return OrganizationTier.FREE
        return TrustWeaveTierCatalog.TIERS.firstOrNull { it.planName == event.planName }?.tier
            ?: OrganizationTier.FREE
    }
}
```

- [ ] **Step 4: Run → PASS (3 handler tests).** Same command as Step 2.

- [ ] **Step 5: Write the failing controller test.** Create `AccountlyWebhookControllerTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.webhook

import com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyWebhookEvent
import com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyWebhookHandler
import com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyWebhookVerifier
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [AccountlyWebhookController::class])
@Import(AccountlyWebhookVerifier::class)
@org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class.let { }
class AccountlyWebhookControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockBean private lateinit var handler: AccountlyWebhookHandler

    private val secret = "test-accountly-webhook-secret"
    private val body = """{"event":"subscription.updated","externalRef":"trustweave:org:7","planName":"tw-pro","status":"Active"}"""

    private fun sign(payload: String) = AccountlyWebhookVerifier().sign(secret, payload)

    @Test
    @WithMockUser
    fun `valid signature is processed`() {
        mockMvc.perform(
            post("/api/webhooks/accountly")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .header("X-Accountly-Signature", sign(body))
                .contentType("application/json").content(body),
        ).andExpect(status().isOk)
        verify(handler).handle(any<AccountlyWebhookEvent>())
    }

    @Test
    @WithMockUser
    fun `invalid signature is rejected and not processed`() {
        mockMvc.perform(
            post("/api/webhooks/accountly")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .header("X-Accountly-Signature", "sha256=bad")
                .contentType("application/json").content(body),
        ).andExpect(status().isBadRequest)
        verify(handler, never()).handle(any())
    }
}
```

> Note: `@WebMvcTest` loads only the web layer (no JPA/Flyway), so it is unaffected by the pre-existing Flyway-in-test failure. The controller reads the webhook secret from `accountly.webhook-secret`; the test sets it via the property below. If the project's security config rejects unauthenticated POSTs, the `csrf()` + `@WithMockUser` cover it; if instead the webhook path must be permitted without auth, the controller test may need `@AutoConfigureMockMvc(addFilters = false)` — apply that and report if security blocks the request.

Also set the test secret. The simplest is an inline `@TestPropertySource`. Replace the class-level annotations block with:

```kotlin
@WebMvcTest(controllers = [AccountlyWebhookController::class])
@Import(AccountlyWebhookVerifier::class)
@org.springframework.test.context.TestPropertySource(properties = ["accountly.webhook-secret=test-accountly-webhook-secret"])
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class AccountlyWebhookControllerTest {
```

and remove the `@WithMockUser` / `csrf()` usages (with `addFilters = false` security filters are off, so the POSTs go straight through — simpler and avoids guessing the security config). The final test class should: build mockMvc, POST with a valid signature → 200 and `verify(handler).handle(...)`; POST with a bad signature → 400 and `verify(handler, never())`.

- [ ] **Step 6: Run the controller test → FAIL** (`AccountlyWebhookController` unresolved):
`./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.webhook.AccountlyWebhookControllerTest" -x jacocoTestCoverageVerification`

- [ ] **Step 7: Implement the controller.** Create `webhook/AccountlyWebhookController.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyWebhookEvent
import com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyWebhookHandler
import com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyWebhookVerifier
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/webhooks/accountly")
class AccountlyWebhookController(
    private val verifier: AccountlyWebhookVerifier,
    private val handler: AccountlyWebhookHandler,
    private val objectMapper: ObjectMapper,
    @Value("\${accountly.webhook-secret:}") private val webhookSecret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun handle(
        @RequestBody payload: String,
        @RequestHeader(name = "X-Accountly-Signature", required = false) signature: String?,
    ): ResponseEntity<String> {
        if (webhookSecret.isBlank()) {
            log.error("accountly.webhook-secret is not configured")
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Webhook secret not configured")
        }
        if (signature == null || !verifier.isValid(webhookSecret, payload, signature)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature")
        }
        val event = objectMapper.readValue(payload, AccountlyWebhookEvent::class.java)
        handler.handle(event)
        return ResponseEntity.ok("ok")
    }
}
```

- [ ] **Step 8: Add the `accountly.webhook-secret` config to application.yml.** Under the existing `accountly:` block add:

```yaml
  webhook-secret: ${ACCOUNTLY_WEBHOOK_SECRET:}
```

- [ ] **Step 9: Run both new test classes → PASS (3 handler + 2 controller).**
`./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyWebhookHandlerTest" --tests "com.geoknoesis.trustweave.saas.server.webhook.AccountlyWebhookControllerTest" -x jacocoTestCoverageVerification`

- [ ] **Step 10: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyWebhookHandler.kt server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/webhook/AccountlyWebhookController.kt server/src/main/resources/application.yml server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyWebhookHandlerTest.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/webhook/AccountlyWebhookControllerTest.kt
git commit -m "feat(billing): inbound Accountly webhook controller + tier-sync handler"
```

---

## Task 7: Deprecate the Stripe subscription path

**Files:**
- Modify: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/StripeService.kt`
- Modify: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/SubscriptionService.kt`

Non-breaking: annotate the classes `@Deprecated` pointing at `OrganizationBillingService`, so new code is steered away while the existing Stripe wiring keeps compiling. Actual deletion (and the `BillingController`/`StripeWebhookController` rewrite) is a later cleanup, done once the pre-existing Flyway-in-test failure is fixed so the change is verifiable end-to-end.

- [ ] **Step 1: Annotate `StripeService`.** Change its class declaration:

```kotlin
@Service
@Deprecated("Billing is moving to Accountly + Kill Bill; use OrganizationBillingService. Stripe will be removed once the Accountly path is verified end-to-end.")
class StripeService {
```

- [ ] **Step 2: Annotate the Stripe `SubscriptionService`.** Change its class declaration:

```kotlin
@Service
@Deprecated("Stripe-coupled. Use OrganizationBillingService (Accountly + Kill Bill). Scheduled for removal.")
class SubscriptionService(
```

- [ ] **Step 3: Compile (callers of deprecated classes will warn, not error).**
Run: `cd /c/Users/steph/work/trustweave-saas && ./gradlew :server:compileKotlin`
Expected: BUILD SUCCESSFUL (deprecation warnings on `BillingController`/`StripeWebhookController`/`SubscriptionService` are expected and acceptable).

- [ ] **Step 4: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/StripeService.kt server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/SubscriptionService.kt
git commit -m "chore(billing): deprecate Stripe subscription path in favor of Accountly"
```

---

## Task 8: Verification

- [ ] **Step 1: Run all billing.accountly + webhook tests for this sub-plan.**
Run: `cd /c/Users/steph/work/trustweave-saas && ./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.*" --tests "com.geoknoesis.trustweave.saas.server.webhook.AccountlyWebhookControllerTest" -x jacocoTestCoverageVerification`
Expected: BUILD SUCCESSFUL — Plan 2a's 16 tests plus 2b's new ones (client subscriber 5, Organization fields 1, externalRef 2, OrganizationBillingService 3, verifier 2, webhook handler 3, webhook controller 2 = 18 new).

- [ ] **Step 2: Confirm the module still compiles as a whole.**
Run: `./gradlew :server:compileKotlin -x jacocoTestCoverageVerification`
Expected: BUILD SUCCESSFUL.

---

## Self-Review

**Spec coverage** (design spec §7 TrustWeave changes + §2 components, for the subscriber/entitlement slice):
- Subscriber-lifecycle client methods → Task 1. ✓
- `Organization` billing fields → Task 2. ✓
- `OrganizationBillingService` provision/subscribe/upgrade/cancel → Tasks 3, 4. ✓
- Inbound HMAC webhook + tier sync → Tasks 5, 6. ✓
- Retire Stripe → Task 7 (DEPRECATE now; deletion deferred — see below). ◑
- Out of scope (Plan 2c): `usage_outbox`/`UsageReporter`, `EntitlementGate` in the issuance path, `TransactionCostBillingService` refactor. The plan widgets/proxy are Plan 3.

**Intentional deviations (documented):**
- **Stripe is deprecated, not deleted.** Full removal touches `StripeService`/`SubscriptionService`/`BillingController`/`StripeWebhookController` + their `@SpringBootTest` integration tests, which are currently red due to a PRE-EXISTING Flyway-in-test failure (`relation "issued_credentials" does not exist`) unrelated to billing. Deleting Stripe blind (without runnable integration tests) is unsafe; it is deferred to a focused cleanup after that Flyway issue is fixed.
- **Accountly subscription id stored on `Organization`** (not on a refactored `Subscription` entity) to keep this sub-plan additive and avoid touching the Stripe-coupled `Subscription` entity.
- **All tests avoid the full Spring context** (mockito for services, `@WebMvcTest` for the controller, plain unit tests for entity/verifier) precisely because that context is red from the pre-existing Flyway issue.

**Placeholder scan:** none — every step has concrete code. The controller-test note about `addFilters=false` is a contingency with the exact change to apply, not a placeholder.

**Type consistency:** `ProvisionSubscriberRequest/Response`, `SubscribeRequest`, `EntitlementSnapshotDto` defined in Task 1, used in Task 4. `AccountlyWebhookEvent` defined in Task 6 (handler), used in the controller + tests. `OrganizationExternalRef.forOrg/parseOrgId` (Task 3) used in Tasks 4 & 6. `OrganizationBillingService(client, reconciler, organizations)` ctor matches its test. `Organization.copy(...)` new fields match Task 2. `AccountlyWebhookVerifier.sign/isValid` (Task 5) used by the controller + its test.

**Known caveat to verify during execution:** the `@WebMvcTest` security behavior — if the project's Spring Security blocks the unauthenticated webhook POST, use `@AutoConfigureMockMvc(addFilters = false)` (already specified in Task 6 Step 5). Report if security config requires a different approach.

---

## Next sub-plan
- **Plan 2c — usage metering + enforcement:** `usage_outbox` entity + `UsageReporter` (async, idempotent, retry) posting to Accountly `usage-events`; `EntitlementGate` (local quota check against the cached `entitlementSnapshotJson`) wired into credential issuance/verification; refactor `TransactionCostBillingService` to emit `blockchain.tx` usage events. Then the deferred **Stripe removal** cleanup (best after the Flyway-in-test fix).
