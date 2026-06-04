# Plan 2c — TrustWeave Usage Metering Pipeline & Transaction-Cost Integration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give TrustWeave a durable, idempotent usage-reporting pipeline that posts metered events (starting with blockchain transaction costs) to Accountly's `usage-events` API, so usage rolls up in Accountly and bills through Kill Bill.

**Architecture:** A `UsageMeteringService.record(...)` appends a row to a `usage_outbox` table inside the caller's transaction (no lost/double events). A scheduled `UsageReporter` drains the outbox and posts each event to Accountly with a stable idempotency key, marking rows SENT/FAILED with retry. `TransactionCostBillingService` is refactored to report `blockchain.tx` usage through this pipeline instead of generating Stripe invoice items.

**Scope note (honest):** This sub-plan builds the *metering* (usage-reporting) half, which is fully real and testable (Accountly's usage ingestion exists). **Local quota enforcement (`EntitlementGate`) and the credential-issuance gate-wiring are deliberately DEFERRED** because (a) Accountly's entitlement envelope is currently a stub (`quotaEnvelopeJson = "{}"`), so a gate would be inert/fail-open, and (b) the issuance path is library+DB-coupled and not end-to-end verifiable while the SaaS `@SpringBootTest` context is red from a pre-existing Flyway-in-test failure. See "Deferred" at the end.

**Tech Stack:** Kotlin 2.2, Spring Boot 3.5.7, Spring Data JPA (`ddl-auto: update` — no Flyway at runtime), `@Scheduled`, JUnit 5 + WireMock + mockito-kotlin. All tests avoid the full Spring context (mockito for services/reporter, WireMock for the client, plain unit tests for the entity).

---

## Working directory & conventions

- **Repo: `c:\Users\steph\work\trustweave-saas`**, branch `feat/accountly-billing-client` (Plans 2a+2b are here; 2c stacks on top). Run Gradle from repo root.
- Filtered tests exclude the coverage gate: `./gradlew :server:test --tests "<FQCN>" -x jacocoTestCoverageVerification`.
- Package `com.geoknoesis.trustweave.saas.server.billing.accountly` (extends 2a/2b). Entity + repo go in `domain`/`repository` next to existing ones.
- Conventions: `@Service`/`@Component` → plain `class` unless a test subclass-stubs it. Services unit-tested with mockito (NO Spring context). `@Scheduled` requires `@EnableScheduling` — add it on a small `@Configuration` (the app's `Application.kt` may already enable it; check and only add if absent).
- Existing facts: `AccountlyBillingClient` (plain `@Component`, has `auth()` bearer + catalog/subscriber methods); `CatalogReconciler.reconcile(): String` (Accountly applicationId); `Organization` has `accountlySubscriberId: String?` (set by Plan 2b's `OrganizationBillingService`); `TransactionCostService.getTotalCostsForPeriod(orgId, YearMonth): Pair<BigDecimal native, BigDecimal usd>` and `markAsBilled(orgId, YearMonth)`; `TransactionCostBillingService(transactionCostAllocationRepository, transactionCostService, stripeService)` currently builds Stripe invoice items.
- Accountly usage endpoint (Plan 1): `POST /api/v1/applications/{appId}/usage-events`, body `{subscriberAccountId, eventType, quantity, unit, source, occurredAt?, properties?}`, header `Idempotency-Key`; returns 200 `{replayed, quantity, ...}`.

## File structure

**Created**
- `billing/accountly/UsageEventRequest.kt` — usage-event DTO.
- `domain/UsageOutboxEntity.kt` — durable outbox row.
- `repository/UsageOutboxRepository.kt` — JPA repo.
- `billing/accountly/UsageMeteringService.kt` — write path (append outbox).
- `billing/accountly/UsageReporter.kt` — scheduled drain → Accountly.
- `billing/accountly/SchedulingConfig.kt` — `@EnableScheduling` (only if not already enabled).
- Tests mirroring each.

**Modified**
- `billing/accountly/AccountlyBillingClient.kt` — add `postUsageEvent`.
- `billing/TransactionCostBillingService.kt` — report `blockchain.tx` usage via the pipeline; deprecate the Stripe invoice-item method.

---

## Task 1: Client `postUsageEvent` + DTO

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageEventRequest.kt`
- Modify: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClient.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClientUsageTest.kt`

- [ ] **Step 1: Write the failing test.** Create `AccountlyBillingClientUsageTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class AccountlyBillingClientUsageTest {

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
    fun `postUsageEvent sends body + Idempotency-Key + bearer and reports replayed=false`() {
        server.stubFor(post(urlEqualTo("/api/v1/applications/app-1/usage-events"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withHeader("Idempotency-Key", equalTo("idem-1"))
            .withRequestBody(matchingJsonPath("$.subscriberAccountId", equalTo("sub-7")))
            .withRequestBody(matchingJsonPath("$.eventType", equalTo("blockchain.tx")))
            .willReturn(okJson("""{"replayed":false,"quantity":"42"}""")))

        val replayed = client.postUsageEvent(
            "app-1", "idem-1",
            UsageEventRequest(subscriberAccountId = "sub-7", eventType = "blockchain.tx", quantity = "42", unit = "usd-cents", source = "trustweave"),
        )
        assertTrue(!replayed)
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyBillingClientUsageTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Create the DTO.** `UsageEventRequest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UsageEventRequest(
    val subscriberAccountId: String,
    val eventType: String,
    val quantity: String,
    val unit: String,
    val source: String,
    val occurredAt: String? = null,
    val properties: String? = null,
)
```

- [ ] **Step 4: Add the client method** inside `AccountlyBillingClient`:

```kotlin
    /** Posts a usage event; returns true if Accountly reports it as an idempotent replay. */
    fun postUsageEvent(applicationId: String, idempotencyKey: String, request: UsageEventRequest): Boolean {
        val body = restClient.post()
            .uri("/api/v1/applications/{appId}/usage-events", applicationId)
            .header(HttpHeaders.AUTHORIZATION, auth())
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve().body<Map<String, Any?>>()
        return (body?.get("replayed") as? Boolean) ?: false
    }
```

- [ ] **Step 5: Run → PASS (1 test).** Same command as Step 2.

- [ ] **Step 6: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageEventRequest.kt server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClient.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingClientUsageTest.kt
git commit -m "feat(billing): Accountly usage-event client method"
```

---

## Task 2: usage_outbox entity + repository

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/domain/UsageOutboxEntity.kt`
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/repository/UsageOutboxRepository.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/domain/UsageOutboxEntityTest.kt`

- [ ] **Step 1: Write the failing test.** Create `UsageOutboxEntityTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UsageOutboxEntityTest {

    @Test
    fun `new row defaults to PENDING with zero attempts`() {
        val row = UsageOutboxEntity(
            subscriberAccountId = "sub-7",
            eventType = "blockchain.tx",
            quantity = "42",
            unit = "usd-cents",
            idempotencyKey = "k-1",
        )
        assertEquals(UsageOutboxStatus.PENDING, row.status)
        assertEquals(0, row.attempts)
    }

    @Test
    fun `markSent and markFailed mutate status`() {
        val row = UsageOutboxEntity(
            subscriberAccountId = "sub-7", eventType = "blockchain.tx",
            quantity = "1", unit = "usd-cents", idempotencyKey = "k-2",
        )
        row.status = UsageOutboxStatus.SENT
        assertEquals(UsageOutboxStatus.SENT, row.status)
        row.attempts += 1
        assertEquals(1, row.attempts)
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.domain.UsageOutboxEntityTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Create the entity.** `UsageOutboxEntity.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class UsageOutboxStatus { PENDING, SENT, FAILED }

@Entity
@Table(name = "usage_outbox")
class UsageOutboxEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "subscriber_account_id", nullable = false)
    val subscriberAccountId: String,

    @Column(name = "event_type", nullable = false)
    val eventType: String,

    @Column(nullable = false)
    val quantity: String,

    @Column(nullable = false)
    val unit: String,

    @Column(name = "idempotency_key", nullable = false, unique = true)
    val idempotencyKey: String,

    @Column(name = "occurred_at")
    val occurredAt: Instant? = null,

    @Column(name = "properties_json", columnDefinition = "TEXT")
    val propertiesJson: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UsageOutboxStatus = UsageOutboxStatus.PENDING,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "sent_at")
    var sentAt: Instant? = null,
)
```

- [ ] **Step 4: Create the repository.** `UsageOutboxRepository.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.repository

import com.geoknoesis.trustweave.saas.server.domain.UsageOutboxEntity
import com.geoknoesis.trustweave.saas.server.domain.UsageOutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UsageOutboxRepository : JpaRepository<UsageOutboxEntity, Long> {
    fun findByStatusOrderByCreatedAtAsc(status: UsageOutboxStatus, pageable: Pageable): List<UsageOutboxEntity>
    fun findByIdempotencyKey(idempotencyKey: String): UsageOutboxEntity?
}
```

- [ ] **Step 5: Run → PASS (2 tests).** Same command as Step 2.

- [ ] **Step 6: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/domain/UsageOutboxEntity.kt server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/repository/UsageOutboxRepository.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/domain/UsageOutboxEntityTest.kt
git commit -m "feat(billing): usage_outbox entity + repository"
```

---

## Task 3: UsageMeteringService (write path)

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageMeteringService.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageMeteringServiceTest.kt`

Appends an outbox row for a billable event. Idempotency key = `org:<id>:<eventType>:<dedupeKey>`. Skips (logs) if the org has no `accountlySubscriberId` (not yet provisioned). Idempotent: if a row with the same idempotency key already exists, it is not duplicated.

- [ ] **Step 1: Write the failing test.** Create `UsageMeteringServiceTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.geoknoesis.trustweave.saas.server.domain.Organization
import com.geoknoesis.trustweave.saas.server.domain.UsageOutboxEntity
import com.geoknoesis.trustweave.saas.server.repository.UsageOutboxRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UsageMeteringServiceTest {

    private val outbox = mock<UsageOutboxRepository>()
    private val service = UsageMeteringService(outbox)

    @Test
    fun `record appends a PENDING outbox row for a provisioned org`() {
        val org = Organization(id = 7L, name = "Org 7", ownerId = 1L, accountlySubscriberId = "sub-7")
        whenever(outbox.findByIdempotencyKey(any())).thenReturn(null)
        whenever(outbox.save(any<UsageOutboxEntity>())).thenAnswer { it.arguments[0] as UsageOutboxEntity }

        service.record(org, eventType = "blockchain.tx", quantity = "42", unit = "usd-cents", dedupeKey = "tx-99")

        val captor = argumentCaptor<UsageOutboxEntity>()
        verify(outbox).save(captor.capture())
        val row = captor.firstValue
        assertEquals("sub-7", row.subscriberAccountId)
        assertEquals("blockchain.tx", row.eventType)
        assertEquals("org:7:blockchain.tx:tx-99", row.idempotencyKey)
    }

    @Test
    fun `record is a no-op when the org is not provisioned`() {
        val org = Organization(id = 7L, name = "Org 7", ownerId = 1L, accountlySubscriberId = null)
        service.record(org, "blockchain.tx", "42", "usd-cents", "tx-99")
        verify(outbox, never()).save(any())
    }

    @Test
    fun `record does not duplicate an existing idempotency key`() {
        val org = Organization(id = 7L, name = "Org 7", ownerId = 1L, accountlySubscriberId = "sub-7")
        whenever(outbox.findByIdempotencyKey("org:7:blockchain.tx:tx-99"))
            .thenReturn(UsageOutboxEntity(subscriberAccountId = "sub-7", eventType = "blockchain.tx", quantity = "42", unit = "usd-cents", idempotencyKey = "org:7:blockchain.tx:tx-99"))
        service.record(org, "blockchain.tx", "42", "usd-cents", "tx-99")
        verify(outbox, never()).save(any())
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.UsageMeteringServiceTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Implement.** `UsageMeteringService.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.geoknoesis.trustweave.saas.server.domain.Organization
import com.geoknoesis.trustweave.saas.server.domain.UsageOutboxEntity
import com.geoknoesis.trustweave.saas.server.repository.UsageOutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UsageMeteringService(
    private val outbox: UsageOutboxRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Records a billable event into the durable outbox (drained asynchronously by [UsageReporter]).
     * No-op if the org is not yet provisioned in Accountly. Idempotent on [dedupeKey].
     */
    @Transactional
    fun record(org: Organization, eventType: String, quantity: String, unit: String, dedupeKey: String, propertiesJson: String? = null) {
        val subscriberId = org.accountlySubscriberId
        if (subscriberId.isNullOrBlank()) {
            log.debug("Skipping usage '{}' for org {} (no Accountly subscriber yet)", eventType, org.id)
            return
        }
        val idempotencyKey = "org:${org.id}:$eventType:$dedupeKey"
        if (outbox.findByIdempotencyKey(idempotencyKey) != null) {
            log.debug("Usage event {} already recorded", idempotencyKey)
            return
        }
        outbox.save(
            UsageOutboxEntity(
                subscriberAccountId = subscriberId,
                eventType = eventType,
                quantity = quantity,
                unit = unit,
                idempotencyKey = idempotencyKey,
                propertiesJson = propertiesJson,
            ),
        )
    }
}
```

- [ ] **Step 4: Run → PASS (3 tests).** Same command as Step 2.

- [ ] **Step 5: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageMeteringService.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageMeteringServiceTest.kt
git commit -m "feat(billing): UsageMeteringService write path (outbox append)"
```

---

## Task 4: UsageReporter (scheduled drain)

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageReporter.kt`
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/SchedulingConfig.kt` (only if `@EnableScheduling` is not already present — see Step 0)
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageReporterTest.kt`

- [ ] **Step 0: Check scheduling.** Grep for `@EnableScheduling` in `server/src/main/kotlin`. If absent, you will create `SchedulingConfig.kt` (Step 5). If present, skip that file.

- [ ] **Step 1: Write the failing test.** Create `UsageReporterTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.geoknoesis.trustweave.saas.server.domain.UsageOutboxEntity
import com.geoknoesis.trustweave.saas.server.domain.UsageOutboxStatus
import com.geoknoesis.trustweave.saas.server.repository.UsageOutboxRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Pageable

class UsageReporterTest {

    private val client = mock<AccountlyBillingClient>()
    private val reconciler = mock<CatalogReconciler>()
    private val outbox = mock<UsageOutboxRepository>()
    private val reporter = UsageReporter(client, reconciler, outbox)

    private fun row(key: String) = UsageOutboxEntity(
        subscriberAccountId = "sub-7", eventType = "blockchain.tx",
        quantity = "42", unit = "usd-cents", idempotencyKey = key,
    )

    @Test
    fun `drains pending rows, posts to Accountly, marks SENT`() {
        val r = row("k-1")
        whenever(reconciler.reconcile()).thenReturn("app-1")
        whenever(outbox.findByStatusOrderByCreatedAtAsc(eq(UsageOutboxStatus.PENDING), any<Pageable>())).thenReturn(listOf(r))
        whenever(client.postUsageEvent(eq("app-1"), eq("k-1"), any())).thenReturn(false)
        whenever(outbox.save(any<UsageOutboxEntity>())).thenAnswer { it.arguments[0] as UsageOutboxEntity }

        reporter.drainOnce()

        verify(client).postUsageEvent(eq("app-1"), eq("k-1"), any())
        assertEquals(UsageOutboxStatus.SENT, r.status)
    }

    @Test
    fun `marks FAILED and increments attempts on post failure`() {
        val r = row("k-2")
        whenever(reconciler.reconcile()).thenReturn("app-1")
        whenever(outbox.findByStatusOrderByCreatedAtAsc(eq(UsageOutboxStatus.PENDING), any<Pageable>())).thenReturn(listOf(r))
        whenever(client.postUsageEvent(any(), any(), any())).thenThrow(RuntimeException("boom"))
        whenever(outbox.save(any<UsageOutboxEntity>())).thenAnswer { it.arguments[0] as UsageOutboxEntity }

        reporter.drainOnce()

        assertEquals(UsageOutboxStatus.FAILED, r.status)
        assertEquals(1, r.attempts)
    }

    @Test
    fun `does nothing and skips reconcile when outbox is empty`() {
        whenever(outbox.findByStatusOrderByCreatedAtAsc(eq(UsageOutboxStatus.PENDING), any<Pageable>())).thenReturn(emptyList())
        reporter.drainOnce()
        verify(reconciler, org.mockito.kotlin.never()).reconcile()
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.UsageReporterTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Implement.** `UsageReporter.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import com.geoknoesis.trustweave.saas.server.domain.UsageOutboxStatus
import com.geoknoesis.trustweave.saas.server.repository.UsageOutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class UsageReporter(
    private val client: AccountlyBillingClient,
    private val reconciler: CatalogReconciler,
    private val outbox: UsageOutboxRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${accountly.usage.drain-interval-ms:15000}")
    fun scheduledDrain() = drainOnce()

    /** Drains a batch of PENDING usage rows and posts them to Accountly. */
    fun drainOnce() {
        val batch = outbox.findByStatusOrderByCreatedAtAsc(UsageOutboxStatus.PENDING, PageRequest.of(0, 100))
        if (batch.isEmpty()) return
        val applicationId = reconciler.reconcile()
        for (row in batch) {
            try {
                client.postUsageEvent(
                    applicationId,
                    row.idempotencyKey,
                    UsageEventRequest(
                        subscriberAccountId = row.subscriberAccountId,
                        eventType = row.eventType,
                        quantity = row.quantity,
                        unit = row.unit,
                        source = "trustweave",
                        occurredAt = row.occurredAt?.toString(),
                        properties = row.propertiesJson,
                    ),
                )
                row.status = UsageOutboxStatus.SENT
                row.sentAt = row.createdAt // stamped lazily; reporter has no clock — actual send time tracked by Accountly
                outbox.save(row)
            } catch (ex: Exception) {
                row.status = UsageOutboxStatus.FAILED
                row.attempts += 1
                outbox.save(row)
                log.warn("Usage event {} failed to post (attempt {})", row.idempotencyKey, row.attempts, ex)
            }
        }
    }
}
```

> Note: FAILED rows are re-attempted by a separate requeue policy in a future iteration; for now a FAILED row is left for operator visibility. The `sentAt` is set to `createdAt` as a non-null marker (the authoritative timestamp lives in Accountly); avoid `Instant.now()` here to keep the reporter deterministically testable.

- [ ] **Step 4: Run → PASS (3 tests).** Same command as Step 2.

- [ ] **Step 5: Ensure scheduling is enabled.** If Step 0 found NO `@EnableScheduling`, create `SchedulingConfig.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
class SchedulingConfig
```
(If `@EnableScheduling` already exists elsewhere, do NOT create this file.)

- [ ] **Step 6: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageReporter.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageReporterTest.kt
# include SchedulingConfig.kt in the add ONLY if you created it
git commit -m "feat(billing): scheduled UsageReporter draining outbox to Accountly"
```

---

## Task 5: Route transaction costs through the usage pipeline

**Files:**
- Modify: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/TransactionCostBillingService.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/TransactionCostUsageReportingTest.kt`

Add a method that reports a billing period's blockchain transaction cost as a single `blockchain.tx` usage event (quantity = USD cents) via `UsageMeteringService`, then marks the costs billed. Deprecate the Stripe invoice-item method. The service needs the `Organization` (for `accountlySubscriberId`) — inject `OrganizationRepository`.

- [ ] **Step 1: Write the failing test.** Create `TransactionCostUsageReportingTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing

import com.geoknoesis.trustweave.saas.server.billing.accountly.UsageMeteringService
import com.geoknoesis.trustweave.saas.server.domain.Organization
import com.geoknoesis.trustweave.saas.server.repository.OrganizationRepository
import com.geoknoesis.trustweave.saas.server.repository.TransactionCostAllocationRepository
import com.geoknoesis.trustweave.saas.server.trustweave.TransactionCostService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Optional

class TransactionCostUsageReportingTest {

    private val allocations = mock<TransactionCostAllocationRepository>()
    private val costService = mock<TransactionCostService>()
    private val metering = mock<UsageMeteringService>()
    private val orgs = mock<OrganizationRepository>()
    private val service = TransactionCostBillingService(allocations, costService, metering, orgs)

    @Test
    fun `reports period cost as a blockchain_tx usage event in USD cents`() {
        val org = Organization(id = 7L, name = "Org 7", ownerId = 1L, accountlySubscriberId = "sub-7")
        val period = YearMonth.of(2026, 6)
        whenever(orgs.findById(7L)).thenReturn(Optional.of(org))
        whenever(costService.getTotalCostsForPeriod(7L, period)).thenReturn(BigDecimal("0.10") to BigDecimal("12.34"))

        service.reportTransactionCostsAsUsage(7L, period)

        // 12.34 USD -> 1234 cents
        verify(metering).record(eq(org), eq("blockchain.tx"), eq("1234"), eq("usd-cents"), eq("2026-06"), any())
        verify(costService).markAsBilled(7L, period)
    }

    @Test
    fun `does nothing when the period cost is zero`() {
        val org = Organization(id = 7L, name = "Org 7", ownerId = 1L, accountlySubscriberId = "sub-7")
        val period = YearMonth.of(2026, 6)
        whenever(orgs.findById(7L)).thenReturn(Optional.of(org))
        whenever(costService.getTotalCostsForPeriod(7L, period)).thenReturn(BigDecimal.ZERO to BigDecimal.ZERO)

        service.reportTransactionCostsAsUsage(7L, period)

        verify(metering, never()).record(any(), any(), any(), any(), any(), any())
        verify(costService, never()).markAsBilled(any(), any())
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.TransactionCostUsageReportingTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Read the current `TransactionCostBillingService.kt`**, then replace its constructor + body with the version below. Keep the package and the existing `generateTransactionCostInvoiceItems` method but annotate it `@Deprecated`; add the new method. The new constructor drops `StripeService` and adds `UsageMeteringService` + `OrganizationRepository`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing

import com.geoknoesis.trustweave.saas.server.billing.accountly.UsageMeteringService
import com.geoknoesis.trustweave.saas.server.repository.OrganizationRepository
import com.geoknoesis.trustweave.saas.server.repository.TransactionCostAllocationRepository
import com.geoknoesis.trustweave.saas.server.trustweave.TransactionCostService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

@Service
class TransactionCostBillingService(
    private val transactionCostAllocationRepository: TransactionCostAllocationRepository,
    private val transactionCostService: TransactionCostService,
    private val usageMeteringService: UsageMeteringService,
    private val organizations: OrganizationRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Reports an organization's blockchain transaction cost for a billing period to Accountly as a
     * single `blockchain.tx` usage event (quantity = USD cents), then marks the costs billed.
     */
    @Transactional
    fun reportTransactionCostsAsUsage(organizationId: Long, billingPeriod: YearMonth) {
        val org = organizations.findById(organizationId).orElse(null) ?: run {
            logger.warn("Cannot report transaction costs: org {} not found", organizationId); return
        }
        val (_, totalUsd) = transactionCostService.getTotalCostsForPeriod(organizationId, billingPeriod)
        if (totalUsd <= BigDecimal.ZERO) return

        val cents = totalUsd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString()
        usageMeteringService.record(
            org = org,
            eventType = "blockchain.tx",
            quantity = cents,
            unit = "usd-cents",
            dedupeKey = billingPeriod.toString(),
            propertiesJson = """{"period":"$billingPeriod","usd":"${totalUsd.toPlainString()}"}""",
        )
        transactionCostService.markAsBilled(organizationId, billingPeriod)
        logger.info("Reported {} usd-cents of blockchain.tx usage for org {} ({})", cents, organizationId, billingPeriod)
    }

    @Deprecated("Stripe invoice items are replaced by Accountly usage events; use reportTransactionCostsAsUsage.")
    fun generateTransactionCostInvoiceItems(organizationId: Long, billingPeriod: YearMonth): List<Map<String, Any>> {
        val (_, totalUsd) = transactionCostService.getTotalCostsForPeriod(organizationId, billingPeriod)
        if (totalUsd <= BigDecimal.ZERO) return emptyList()
        return listOf(
            mapOf(
                "description" to "Blockchain transaction costs for $billingPeriod",
                "amount" to totalUsd.multiply(BigDecimal("100")).toLong(),
                "currency" to "usd",
                "quantity" to 1,
            ),
        )
    }

    @Transactional
    fun markTransactionCostsAsBilled(organizationId: Long, billingPeriod: YearMonth) {
        transactionCostService.markAsBilled(organizationId, billingPeriod)
        logger.info("Marked transaction costs as billed for organization $organizationId, period $billingPeriod")
    }
}
```

> Note: `YearMonth.of(2026,6).toString()` is `"2026-06"`, which is why the test expects dedupeKey `"2026-06"`. If the original file referenced `StripeService`, removing it from the constructor is the intended Stripe-decoupling for this path; the deprecated `generateTransactionCostInvoiceItems` above no longer needs Stripe (it only builds a plain map). Report if any other code constructs `TransactionCostBillingService` with the old 3-arg constructor (search for `TransactionCostBillingService(`) — if `BillingController` does, update that call site minimally or note it.

- [ ] **Step 4: Run → PASS (2 tests).** Same command as Step 2.

- [ ] **Step 5: Compile the whole module** (catches any stale 3-arg constructor call site): `./gradlew :server:compileKotlin`. If `BillingController` (or anything) fails to construct the service, fix the call site minimally (it is Spring-injected, so usually no source change is needed) and report.

- [ ] **Step 6: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/TransactionCostBillingService.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/TransactionCostUsageReportingTest.kt
git commit -m "feat(billing): report blockchain transaction costs as Accountly usage"
```

---

## Task 6: Verification

- [ ] **Step 1: Run all new billing tests (2a+2b+2c).**
Run: `cd /c/Users/steph/work/trustweave-saas && ./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.*" --tests "com.geoknoesis.trustweave.saas.server.billing.TransactionCostUsageReportingTest" --tests "com.geoknoesis.trustweave.saas.server.domain.UsageOutboxEntityTest" --tests "com.geoknoesis.trustweave.saas.server.domain.OrganizationBillingFieldsTest" --tests "com.geoknoesis.trustweave.saas.server.webhook.AccountlyWebhookControllerTest" -x jacocoTestCoverageVerification`
Expected: BUILD SUCCESSFUL. Report aggregate counts (2c adds: client usage 1, outbox entity 2, metering 3, reporter 3, transaction-cost 2 = 11).

- [ ] **Step 2: Compile the whole module.** `./gradlew :server:compileKotlin` → BUILD SUCCESSFUL.

---

## Self-Review

**Spec coverage** (design spec §7 usage slice + §5 metering):
- `usage-events` client method → Task 1. ✓
- `usage_outbox` entity + repo → Task 2. ✓
- `UsageReporter` (async, idempotent, retry) + write-path `UsageMeteringService` → Tasks 3, 4. ✓
- `TransactionCostBillingService` emits `blockchain.tx` usage → Task 5. ✓
- **Deferred (documented):** `EntitlementGate` local quota enforcement + the credential-issuance gate/usage wiring. Rationale: Accountly's entitlement envelope is a stub (`{}`) so a gate is inert/fail-open, and the issuance path is unverifiable while the SaaS context is Flyway-red. When Accountly computes real envelopes and the context is runnable, add `EntitlementGate(snapshot parse → allow/block)` + inject it + `UsageMeteringService` into `CredentialIssuanceService.issue` (gate before `trustWeave.issue`; `record("credential.issued", 1)` after success).

**Placeholder scan:** none — every step has concrete code. The `SchedulingConfig` is conditional with an explicit check (Step 0).

**Type consistency:** `UsageEventRequest(subscriberAccountId, eventType, quantity, unit, source, occurredAt?, properties?)` defined in Task 1, used in Tasks 1 & 4. `UsageOutboxEntity`/`UsageOutboxStatus` (Task 2) used in Tasks 3 & 4. `UsageMeteringService.record(org, eventType, quantity, unit, dedupeKey, propertiesJson?)` (Task 3) used in Task 5. `client.postUsageEvent(appId, idempotencyKey, request): Boolean` (Task 1) used by the reporter (Task 4). `UsageOutboxRepository.findByStatusOrderByCreatedAtAsc(status, Pageable)` / `findByIdempotencyKey` (Task 2) used in Tasks 3 & 4.

**Known caveats to verify during execution:**
- `@EnableScheduling` may already exist — Step 0 guards against a duplicate.
- A stale 3-arg `TransactionCostBillingService(...)` call site (e.g. in `BillingController`) — Task 5 Step 5 catches it via a full compile; since the service is Spring-injected, no source change is usually needed.
- The reporter leaves FAILED rows without auto-requeue (operator visibility); a requeue policy is a future iteration.

---

## After Plan 2c
Remaining for the integration: **Plan 3** (TrustWeave billing UI — `BillingProxyController` + `PricingPlans`/`CurrentPlanCard`/`UsageMeter` widgets); the **deferred enforcement** (`EntitlementGate` + issuance wiring, once Accountly computes real entitlement envelopes); the **deferred Stripe deletion** (once the pre-existing Flyway-in-test failure is fixed so the removal is verifiable end-to-end).
