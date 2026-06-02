# Plan 1 — Accountly Merchant Billing API + Kill Bill Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the merchant-mediated billing surface to Accountly so an external merchant (TrustWeave's service identity) can provision subscribers by external reference, subscribe/change/cancel them on plans it owns, emit signed outbound webhooks on subscription state changes, and reference a matching Kill Bill catalog.

**Architecture:** Accountly stays the system of record. New owner-authenticated endpoints under `/api/v1/applications/{appId}/...` let the merchant create subscribers (identified by a stable `external_ref`, not a JWT subject) and manage their subscriptions without the existing self-serve `subscriber == owner` demo guard. Subscription state changes fan out to the merchant via a best-effort HMAC-signed outbound webhook. A Kill Bill `catalog.xml` provides the priced products the Accountly plans reference.

**Tech Stack:** Kotlin 2.1, Spring Boot 3.4 (JVM 21), Spring Data JPA + Flyway (Postgres prod / H2 `create-drop` test), Spring Security OAuth2 resource server, JUnit 5 + MockMvc + mockito-kotlin, Kill Bill 0.24.

---

## Working directory & conventions

- **All paths in this plan are relative to the Accountly repo root: `d:\work\accountly`.**
- **Run Gradle from `d:\work\accountly\backend`** (the Spring module). Examples use `./gradlew` (use `gradlew.bat` on cmd; from PowerShell use `./gradlew`).
- Run a single test class: `./gradlew test --tests "com.accountly.<FQCN>"`.
- Tests use H2 with `ddl-auto: create-drop` and **Flyway disabled** (`backend/src/test/resources/application-test.yml`). Therefore **entity changes drive the test schema**; the Flyway migration is for Postgres production and must be kept in sync by hand.
- Kill Bill is **disabled in the test profile** (`integration.killbill.enabled=false`), so the `KillBillAccountClient` bean is absent in `@SpringBootTest`. Kill-Bill-gated happy paths are covered by **unit tests with a mocked client**; integration (MockMvc) tests cover auth, ownership, validation, and the "billing disabled" gate.
- Existing test auth pattern (reuse it): `@MockBean JwtDecoder` stubbed so `Authorization: Bearer owner` → subject `jwt-owner-alice` and `Bearer subscriber` → subject `jwt-subscriber-bob` (see `backend/src/test/kotlin/com/accountly/AccountlyApiIntegrationTest.kt` and `testsupport/TestJwt.kt`).
- Conventional Commits; run `./gradlew ktlintFormat` before each commit if ktlint is wired (it is enforced in the parent project).

## File structure (created / modified)

**Created**
- `backend/src/main/resources/db/migration/V5__merchant_external_ref.sql` — add `app_users.external_ref`.
- `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookSigner.kt` — pure HMAC-SHA256 signer.
- `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookProperties.kt` — outbound webhook config.
- `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookSender.kt` — HTTP send seam (interface + WebClient impl).
- `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookDispatcher.kt` — builds + signs + sends the event.
- `backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriberService.kt` — ensure subscriber by external ref.
- `backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriptionService.kt` — subscribe / change / cancel (relaxed guard).
- `backend/src/main/kotlin/com/accountly/api/MerchantBillingController.kt` — the four merchant endpoints.
- `backend/src/main/kotlin/com/accountly/api/dto/MerchantBillingDtos.kt` — request/response DTOs.
- Test files mirroring each unit above (see tasks).
- `killbill/catalog.xml` + `killbill/README.md` — Kill Bill catalog and upload/verify guide (repo root, ops artifact).

**Modified**
- `backend/src/main/kotlin/com/accountly/persistence/AppUserEntity.kt` — add `externalRef`.
- `backend/src/main/kotlin/com/accountly/persistence/AppUserRepository.kt` — add `findByExternalRef`.
- `backend/src/main/kotlin/com/accountly/integration/killbill/KillBillAccountClient.kt` — add `changePlan`, `cancelSubscription`.
- `backend/src/main/kotlin/com/accountly/commerce/KillBillInboundService.kt` — dispatch outbound webhook on state change.
- `backend/src/main/kotlin/com/accountly/AccountlyApplication.kt` — register `MerchantWebhookProperties`.

---

## Task 1: `external_ref` subscriber identity

Adds a stable external reference to `app_users` so the merchant can provision subscribers that are not tied to a Keycloak JWT subject.

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__merchant_external_ref.sql`
- Modify: `backend/src/main/kotlin/com/accountly/persistence/AppUserEntity.kt`
- Modify: `backend/src/main/kotlin/com/accountly/persistence/AppUserRepository.kt`
- Test: `backend/src/test/kotlin/com/accountly/persistence/AppUserExternalRefRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/accountly/persistence/AppUserExternalRefRepositoryTest.kt`:

```kotlin
package com.accountly.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppUserExternalRefRepositoryTest {

    @Autowired private lateinit var users: AppUserRepository

    @Test
    fun `finds user by external ref and returns null when absent`() {
        val saved = users.save(
            AppUserEntity(displayName = "Org One", externalRef = "trustweave:org:1"),
        )

        val found = users.findByExternalRef("trustweave:org:1")
        assertEquals(saved.id, found?.id)
        assertNull(users.findByExternalRef("trustweave:org:does-not-exist"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.accountly.persistence.AppUserExternalRefRepositoryTest"`
Expected: FAIL — compile error (`externalRef` and `findByExternalRef` do not exist yet).

- [ ] **Step 3: Add the entity field**

In `backend/src/main/kotlin/com/accountly/persistence/AppUserEntity.kt`, add the property to the constructor (after `killbillAccountId`):

```kotlin
    @Column(name = "killbill_account_id")
    var killbillAccountId: String? = null,
    @Column(name = "external_ref", unique = true)
    var externalRef: String? = null,
)
```

- [ ] **Step 4: Add the repository finder**

Replace the body of `backend/src/main/kotlin/com/accountly/persistence/AppUserRepository.kt`:

```kotlin
package com.accountly.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AppUserRepository : JpaRepository<AppUserEntity, UUID> {
    fun findByExternalRef(externalRef: String): AppUserEntity?
}
```

- [ ] **Step 5: Add the Flyway migration (Postgres prod)**

Create `backend/src/main/resources/db/migration/V5__merchant_external_ref.sql`:

```sql
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS external_ref TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_app_users_external_ref
    ON app_users (external_ref)
    WHERE external_ref IS NOT NULL;
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests "com.accountly.persistence.AppUserExternalRefRepositoryTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V5__merchant_external_ref.sql \
        backend/src/main/kotlin/com/accountly/persistence/AppUserEntity.kt \
        backend/src/main/kotlin/com/accountly/persistence/AppUserRepository.kt \
        backend/src/test/kotlin/com/accountly/persistence/AppUserExternalRefRepositoryTest.kt
git commit -m "feat(billing): add external_ref subscriber identity to app_users"
```

---

## Task 2: Merchant webhook HMAC signer

A pure, dependency-free signer so the outbound webhook signature is unit-testable against a known RFC vector.

**Files:**
- Create: `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookSigner.kt`
- Test: `backend/src/test/kotlin/com/accountly/commerce/webhook/MerchantWebhookSignerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/accountly/commerce/webhook/MerchantWebhookSignerTest.kt`:

```kotlin
package com.accountly.commerce.webhook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MerchantWebhookSignerTest {

    // RFC 4231 / well-known HMAC-SHA256 test vector.
    @Test
    fun `signs payload with hmac sha256 hex and sha256 prefix`() {
        val signature = MerchantWebhookSigner.sign(
            secret = "key",
            payload = "The quick brown fox jumps over the lazy dog",
        )
        assertEquals(
            "sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            signature,
        )
    }

    @Test
    fun `different secret produces different signature`() {
        val a = MerchantWebhookSigner.sign("s1", "body")
        val b = MerchantWebhookSigner.sign("s2", "body")
        assert(a != b)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.accountly.commerce.webhook.MerchantWebhookSignerTest"`
Expected: FAIL — `MerchantWebhookSigner` unresolved.

- [ ] **Step 3: Implement the signer**

Create `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookSigner.kt`:

```kotlin
package com.accountly.commerce.webhook

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HMAC-SHA256 over the raw payload, returned as `sha256=<lowercase-hex>`. */
object MerchantWebhookSigner {
    fun sign(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "sha256=$hex"
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.accountly.commerce.webhook.MerchantWebhookSignerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookSigner.kt \
        backend/src/test/kotlin/com/accountly/commerce/webhook/MerchantWebhookSignerTest.kt
git commit -m "feat(billing): add HMAC-SHA256 merchant webhook signer"
```

---

## Task 3: Merchant webhook dispatcher (config + sender seam)

Builds the JSON event, signs it, and sends it — but only when configured. HTTP is behind a `MerchantWebhookSender` interface so unit tests use a fake sender (no network, no MockWebServer dependency).

**Files:**
- Create: `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookProperties.kt`
- Create: `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookSender.kt`
- Create: `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookDispatcher.kt`
- Modify: `backend/src/main/kotlin/com/accountly/AccountlyApplication.kt`
- Test: `backend/src/test/kotlin/com/accountly/commerce/webhook/MerchantWebhookDispatcherTest.kt`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/accountly/commerce/webhook/MerchantWebhookDispatcherTest.kt`:

```kotlin
package com.accountly.commerce.webhook

import com.accountly.persistence.AppUserEntity
import com.accountly.persistence.ApplicationEntity
import com.accountly.persistence.PlanEntity
import com.accountly.persistence.SubscriptionEntity
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MerchantWebhookDispatcherTest {

    private class RecordingSender : MerchantWebhookSender {
        var url: String? = null
        var signature: String? = null
        var body: String? = null
        var calls = 0
        override fun send(url: String, signature: String, body: String) {
            this.url = url; this.signature = signature; this.body = body; this.calls++
        }
    }

    private fun subscription(): SubscriptionEntity {
        val owner = AppUserEntity(displayName = "Merchant")
        val subscriber = AppUserEntity(displayName = "Org One", externalRef = "trustweave:org:1")
        val app = ApplicationEntity(owner = owner, name = "TrustWeave SaaS")
        val plan = PlanEntity(application = app, name = "tw-pro", pricingModel = "FlatRate")
        return SubscriptionEntity(
            subscriber = subscriber,
            plan = plan,
            status = "Active",
            killbillSubscriptionId = "kb-sub-1",
            killbillBundleId = "kb-bundle-1",
        )
    }

    @Test
    fun `signs and sends when enabled`() {
        val sender = RecordingSender()
        val props = MerchantWebhookProperties(enabled = true, url = "https://tw.test/hook", secret = "shh")
        val dispatcher = MerchantWebhookDispatcher(props, sender, ObjectMapper())

        dispatcher.dispatchSubscriptionUpdated(subscription())

        assertEquals(1, sender.calls)
        assertEquals("https://tw.test/hook", sender.url)
        val body = sender.body!!
        assertTrue(body.contains("\"event\":\"subscription.updated\""))
        assertTrue(body.contains("\"killbillSubscriptionId\":\"kb-sub-1\""))
        assertTrue(body.contains("\"externalRef\":\"trustweave:org:1\""))
        assertEquals(MerchantWebhookSigner.sign("shh", body), sender.signature)
    }

    @Test
    fun `does nothing when disabled`() {
        val sender = RecordingSender()
        val props = MerchantWebhookProperties(enabled = false, url = "https://tw.test/hook", secret = "shh")
        val dispatcher = MerchantWebhookDispatcher(props, sender, ObjectMapper())

        dispatcher.dispatchSubscriptionUpdated(subscription())

        assertEquals(0, sender.calls)
    }

    @Test
    fun `swallows sender failures`() {
        val failing = object : MerchantWebhookSender {
            override fun send(url: String, signature: String, body: String) = error("boom")
        }
        val props = MerchantWebhookProperties(enabled = true, url = "https://tw.test/hook", secret = "shh")
        val dispatcher = MerchantWebhookDispatcher(props, failing, ObjectMapper())

        // Must not throw — webhook delivery is best-effort.
        dispatcher.dispatchSubscriptionUpdated(subscription())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.accountly.commerce.webhook.MerchantWebhookDispatcherTest"`
Expected: FAIL — `MerchantWebhookProperties` / `MerchantWebhookSender` / `MerchantWebhookDispatcher` unresolved.

- [ ] **Step 3: Add the properties**

Create `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookProperties.kt`:

```kotlin
package com.accountly.commerce.webhook

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "accountly.merchant-webhook")
data class MerchantWebhookProperties(
    var enabled: Boolean = false,
    var url: String = "",
    var secret: String = "",
)
```

- [ ] **Step 4: Add the sender seam + default WebClient impl**

Create `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookSender.kt`:

```kotlin
package com.accountly.commerce.webhook

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/** Seam so dispatch logic is unit-testable without real HTTP. */
interface MerchantWebhookSender {
    fun send(url: String, signature: String, body: String)
}

@Component
open class WebClientMerchantWebhookSender : MerchantWebhookSender {
    private val client = WebClient.create()

    override fun send(url: String, signature: String, body: String) {
        client
            .post()
            .uri(url)
            .header("X-Accountly-Signature", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .timeout(Duration.ofSeconds(10))
            .block()
    }
}
```

- [ ] **Step 5: Add the dispatcher**

Create `backend/src/main/kotlin/com/accountly/commerce/webhook/MerchantWebhookDispatcher.kt`:

```kotlin
package com.accountly.commerce.webhook

import com.accountly.persistence.SubscriptionEntity
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
open class MerchantWebhookDispatcher(
    private val properties: MerchantWebhookProperties,
    private val sender: MerchantWebhookSender,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Best-effort: never throws to the caller's transaction. */
    open fun dispatchSubscriptionUpdated(subscription: SubscriptionEntity) {
        if (!properties.enabled || properties.url.isBlank()) return
        val payload =
            linkedMapOf<String, Any?>(
                "event" to "subscription.updated",
                "subscriptionId" to subscription.id.toString(),
                "killbillSubscriptionId" to subscription.killbillSubscriptionId,
                "killbillBundleId" to subscription.killbillBundleId,
                "planId" to subscription.plan.id.toString(),
                "planName" to subscription.plan.name,
                "subscriberId" to subscription.subscriber.id.toString(),
                "externalRef" to subscription.subscriber.externalRef,
                "status" to subscription.status,
            )
        val body = mapper.writeValueAsString(payload)
        val signature = MerchantWebhookSigner.sign(properties.secret, body)
        try {
            sender.send(properties.url, signature, body)
        } catch (ex: RuntimeException) {
            log.warn("Merchant webhook delivery failed for subscription {}", subscription.id, ex)
        }
    }
}
```

- [ ] **Step 6: Register the properties bean**

In `backend/src/main/kotlin/com/accountly/AccountlyApplication.kt`, add the import and the class to `@EnableConfigurationProperties`:

```kotlin
import com.accountly.commerce.webhook.MerchantWebhookProperties
```

```kotlin
@EnableConfigurationProperties(
    AccountlyIdmProperties::class,
    AccountlyUsageProperties::class,
    KillBillProperties::class,
    MerchantWebhookProperties::class,
)
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew test --tests "com.accountly.commerce.webhook.MerchantWebhookDispatcherTest"`
Expected: PASS (3 tests).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/accountly/commerce/webhook/ \
        backend/src/main/kotlin/com/accountly/AccountlyApplication.kt \
        backend/src/test/kotlin/com/accountly/commerce/webhook/MerchantWebhookDispatcherTest.kt
git commit -m "feat(billing): add merchant webhook dispatcher with sender seam"
```

---

## Task 4: Subscriber provisioning (service + endpoint)

Owner-authenticated provisioning of a subscriber by external ref. Idempotent; links a Kill Bill account when KB is enabled (best-effort, mirroring `IdentityProvisioningService`).

**Files:**
- Create: `backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriberService.kt`
- Create: `backend/src/main/kotlin/com/accountly/api/dto/MerchantBillingDtos.kt`
- Create: `backend/src/main/kotlin/com/accountly/api/MerchantBillingController.kt`
- Test: `backend/src/test/kotlin/com/accountly/commerce/MerchantSubscriberServiceTest.kt`
- Test: `backend/src/test/kotlin/com/accountly/merchant/MerchantBillingIntegrationTest.kt`

- [ ] **Step 1: Write the failing service unit test**

Create `backend/src/test/kotlin/com/accountly/commerce/MerchantSubscriberServiceTest.kt`:

```kotlin
package com.accountly.commerce

import com.accountly.integration.killbill.KillBillAccountClient
import com.accountly.integration.killbill.KillBillProperties
import com.accountly.persistence.AppUserEntity
import com.accountly.persistence.AppUserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider

class MerchantSubscriberServiceTest {

    private val users = mock<AppUserRepository>()
    private val kbProvider = mock<ObjectProvider<KillBillAccountClient>>()

    private fun service(enabled: Boolean) =
        MerchantSubscriberService(users, kbProvider, KillBillProperties(enabled = enabled))

    @Test
    fun `creates subscriber when external ref is new and skips kill bill when disabled`() {
        whenever(users.findByExternalRef("trustweave:org:1")).thenReturn(null)
        whenever(users.save(any<AppUserEntity>())).thenAnswer { it.arguments[0] as AppUserEntity }

        val result = service(enabled = false)
            .ensureSubscriber("trustweave:org:1", displayName = "Org One", email = "ops@org1.test")

        assertEquals("trustweave:org:1", result.let { "trustweave:org:1" }) // sanity
        assertNull(result.killbillAccountId)
        verify(kbProvider, never()).getIfAvailable()
    }

    @Test
    fun `is idempotent and returns existing subscriber`() {
        val existing = AppUserEntity(displayName = "Org One", externalRef = "trustweave:org:1", killbillAccountId = "kb-acct-1")
        whenever(users.findByExternalRef("trustweave:org:1")).thenReturn(existing)

        val result = service(enabled = false).ensureSubscriber("trustweave:org:1", "Org One", null)

        assertEquals(existing.id, result.subscriberId)
        assertEquals("kb-acct-1", result.killbillAccountId)
        verify(users, never()).save(any())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.accountly.commerce.MerchantSubscriberServiceTest"`
Expected: FAIL — `MerchantSubscriberService` unresolved.

- [ ] **Step 3: Implement the service**

Create `backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriberService.kt`:

```kotlin
package com.accountly.commerce

import com.accountly.integration.killbill.KillBillAccountClient
import com.accountly.integration.killbill.KillBillProperties
import com.accountly.persistence.AppUserEntity
import com.accountly.persistence.AppUserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class ProvisionedSubscriber(
    val subscriberId: UUID,
    val killbillAccountId: String?,
)

@Service
open class MerchantSubscriberService(
    private val users: AppUserRepository,
    killBillClients: ObjectProvider<KillBillAccountClient>,
    private val killBillProperties: KillBillProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val killBillClients = killBillClients

    @Transactional
    open fun ensureSubscriber(
        externalRef: String,
        displayName: String?,
        email: String?,
    ): ProvisionedSubscriber {
        val ref = externalRef.trim()
        require(ref.isNotEmpty()) { "externalRef must not be blank." }

        val user =
            users.findByExternalRef(ref)
                ?: users.save(
                    AppUserEntity(
                        email = email?.trim()?.takeIf { it.isNotEmpty() },
                        displayName = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: ref,
                        externalRef = ref,
                    ),
                )

        if (killBillProperties.enabled && user.killbillAccountId.isNullOrBlank()) {
            killBillClients.getIfAvailable()?.let { kb ->
                try {
                    val kbId =
                        kb.ensureAccount(
                            externalKey = KillBillAccountClient.externalKeyForUser(user.id),
                            displayName = user.displayName,
                            email = user.email,
                        )
                    if (!kbId.isNullOrBlank()) {
                        user.killbillAccountId = kbId
                        users.save(user)
                    }
                } catch (ex: RuntimeException) {
                    log.warn("Kill Bill linkage failed for subscriber {}", user.id, ex)
                }
            }
        }
        return ProvisionedSubscriber(user.id, user.killbillAccountId)
    }
}
```

- [ ] **Step 4: Run the unit test to verify it passes**

Run: `./gradlew test --tests "com.accountly.commerce.MerchantSubscriberServiceTest"`
Expected: PASS.

- [ ] **Step 5: Add the DTOs**

Create `backend/src/main/kotlin/com/accountly/api/dto/MerchantBillingDtos.kt`:

```kotlin
package com.accountly.api.dto

import jakarta.validation.constraints.NotBlank

data class ProvisionSubscriberRequestBody(
    @field:NotBlank val externalRef: String,
    val displayName: String? = null,
    val email: String? = null,
)

data class ProvisionSubscriberResponse(
    val subscriberId: String,
    val killbillAccountId: String?,
)

data class MerchantSubscribeRequestBody(
    @field:NotBlank val planId: String,
)
```

- [ ] **Step 6: Add the controller (provisioning endpoint only for now)**

Create `backend/src/main/kotlin/com/accountly/api/MerchantBillingController.kt`:

```kotlin
package com.accountly.api

import com.accountly.api.dto.ProvisionSubscriberRequestBody
import com.accountly.api.dto.ProvisionSubscriberResponse
import com.accountly.commerce.MerchantSubscriberService
import com.accountly.identity.IdentityProvisioningService
import com.accountly.security.ApplicationAccess
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/subscribers")
@Validated
open class MerchantBillingController(
    private val identityProvisioningService: IdentityProvisioningService,
    private val applicationAccess: ApplicationAccess,
    private val merchantSubscriberService: MerchantSubscriberService,
) {

    @PostMapping
    fun provisionSubscriber(
        @AuthenticationPrincipal principal: OAuth2AuthenticatedPrincipal,
        @PathVariable applicationId: String,
        @Valid @RequestBody body: ProvisionSubscriberRequestBody,
    ): ResponseEntity<ProvisionSubscriberResponse> {
        val owner = identityProvisioningService.ensureUser(principal).userId
        val appId = OwnerApplicationUsageApi.parseUuid(applicationId, "applicationId")
        applicationAccess.requireOwnerApplicationOrNotFound(owner, appId)

        val provisioned =
            merchantSubscriberService.ensureSubscriber(body.externalRef, body.displayName, body.email)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ProvisionSubscriberResponse(
                subscriberId = provisioned.subscriberId.toString(),
                killbillAccountId = provisioned.killbillAccountId,
            ),
        )
    }
}
```

- [ ] **Step 7: Write the failing integration test**

Create `backend/src/test/kotlin/com/accountly/merchant/MerchantBillingIntegrationTest.kt`:

```kotlin
package com.accountly.merchant

import com.accountly.testsupport.testJwt
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MerchantBillingIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @MockBean private lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun stubJwtDecoder() {
        whenever(jwtDecoder.decode(any())).thenAnswer { inv ->
            when (inv.getArgument<String>(0)) {
                "subscriber" -> testJwt("jwt-subscriber-bob")
                else -> testJwt("jwt-owner-alice")
            }
        }
    }

    private fun authOwner() = "Bearer owner"
    private fun authSubscriber() = "Bearer subscriber"

    private fun createApplication(name: String): String =
        objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/applications")
                    .header("Authorization", authOwner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name"}"""),
            ).andExpect(status().isCreated).andReturn().response.contentAsString,
        ).get("id").asText()

    @Test
    fun `owner provisions subscriber idempotently`() {
        val appId = createApplication("TrustWeave SaaS")

        val firstJson = mockMvc.perform(
            post("/api/v1/applications/$appId/subscribers")
                .header("Authorization", authOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"externalRef":"trustweave:org:1","displayName":"Org One"}"""),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.subscriberId").exists())
            .andExpect(jsonPath("$.killbillAccountId").doesNotExist()) // KB disabled in test profile -> null
            .andReturn().response.contentAsString
        val firstId = objectMapper.readTree(firstJson).get("subscriberId").asText()

        mockMvc.perform(
            post("/api/v1/applications/$appId/subscribers")
                .header("Authorization", authOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"externalRef":"trustweave:org:1"}"""),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.subscriberId").value(firstId))
    }

    @Test
    fun `non owner cannot provision subscriber`() {
        val appId = createApplication("TrustWeave SaaS")
        mockMvc.perform(
            post("/api/v1/applications/$appId/subscribers")
                .header("Authorization", authSubscriber())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"externalRef":"trustweave:org:2"}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `provision rejects unknown application`() {
        val random = UUID.randomUUID().toString()
        mockMvc.perform(
            post("/api/v1/applications/$random/subscribers")
                .header("Authorization", authOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"externalRef":"trustweave:org:3"}"""),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `provision rejects blank external ref`() {
        val appId = createApplication("TrustWeave SaaS")
        mockMvc.perform(
            post("/api/v1/applications/$appId/subscribers")
                .header("Authorization", authOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"externalRef":""}"""),
        ).andExpect(status().isBadRequest)
    }
}
```

- [ ] **Step 8: Run the integration test to verify it passes**

Run: `./gradlew test --tests "com.accountly.merchant.MerchantBillingIntegrationTest"`
Expected: PASS (4 tests).

> Note: `jsonPath("$.killbillAccountId").doesNotExist()` holds because Jackson omits `null` fields by Accountly's default mapper config; if the project serializes nulls, change to `.value(org.hamcrest.Matchers.nullValue())`.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriberService.kt \
        backend/src/main/kotlin/com/accountly/api/dto/MerchantBillingDtos.kt \
        backend/src/main/kotlin/com/accountly/api/MerchantBillingController.kt \
        backend/src/test/kotlin/com/accountly/commerce/MerchantSubscriberServiceTest.kt \
        backend/src/test/kotlin/com/accountly/merchant/MerchantBillingIntegrationTest.kt
git commit -m "feat(billing): merchant subscriber provisioning by external ref"
```

---

## Task 5: Merchant-mediated subscribe (relaxed guard)

The merchant subscribes one of its provisioned subscribers to one of its plans — without the self-serve `subscriber == owner` demo guard. KB happy path is unit-tested; the endpoint integration test covers ownership and the billing-disabled gate.

**Files:**
- Create: `backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriptionService.kt`
- Modify: `backend/src/main/kotlin/com/accountly/api/MerchantBillingController.kt`
- Test: `backend/src/test/kotlin/com/accountly/commerce/MerchantSubscriptionServiceTest.kt`
- Test: `backend/src/test/kotlin/com/accountly/merchant/MerchantBillingIntegrationTest.kt` (add cases)

- [ ] **Step 1: Write the failing service unit test**

Create `backend/src/test/kotlin/com/accountly/commerce/MerchantSubscriptionServiceTest.kt`:

```kotlin
package com.accountly.commerce

import com.accountly.commerce.webhook.MerchantWebhookDispatcher
import com.accountly.integration.killbill.KillBillAccountClient
import com.accountly.integration.killbill.KillBillProperties
import com.accountly.integration.killbill.KillBillSubscriptionResult
import com.accountly.persistence.AppUserEntity
import com.accountly.persistence.ApplicationEntity
import com.accountly.persistence.PlanEntity
import com.accountly.persistence.PlanRepository
import com.accountly.persistence.SubscriptionEntity
import com.accountly.persistence.SubscriptionRepository
import com.accountly.security.ApplicationAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import java.util.Optional
import java.util.UUID

class MerchantSubscriptionServiceTest {

    private val applicationAccess = mock<ApplicationAccess>()
    private val users = mock<com.accountly.persistence.AppUserRepository>()
    private val plans = mock<PlanRepository>()
    private val subscriptions = mock<SubscriptionRepository>()
    private val kb = mock<KillBillAccountClient>()
    private val kbProvider = mock<ObjectProvider<KillBillAccountClient>>()
    private val dispatcher = mock<MerchantWebhookDispatcher>()

    private fun service() =
        MerchantSubscriptionService(
            applicationAccess, users, plans, subscriptions,
            kbProvider, KillBillProperties(enabled = true), dispatcher,
        )

    @Test
    fun `subscribes a subscriber distinct from the owner and dispatches webhook`() {
        val owner = AppUserEntity(displayName = "Merchant")
        val app = ApplicationEntity(id = UUID.randomUUID(), owner = owner, name = "TrustWeave SaaS")
        val subscriber = AppUserEntity(
            id = UUID.randomUUID(), displayName = "Org One",
            externalRef = "trustweave:org:1", killbillAccountId = "kb-acct-1",
        )
        val plan = PlanEntity(
            id = UUID.randomUUID(), application = app, name = "tw-pro",
            pricingModel = "FlatRate", killbillPlanName = "trustweave-pro-monthly",
            killbillProductName = "trustweave-pro", killbillPriceList = "DEFAULT",
        )

        whenever(applicationAccess.requireOwnerApplicationOrNotFound(owner.id, app.id)).thenReturn(app)
        whenever(kbProvider.getIfAvailable()).thenReturn(kb)
        whenever(users.findById(subscriber.id)).thenReturn(Optional.of(subscriber))
        whenever(plans.fetchById(plan.id)).thenReturn(plan)
        whenever(subscriptions.countBySubscriber_IdAndPlan_IdAndStatus(subscriber.id, plan.id, "Active")).thenReturn(0)
        whenever(kb.createSubscription(eq("kb-acct-1"), eq("trustweave-pro-monthly"), any(), any(), any()))
            .thenReturn(KillBillSubscriptionResult(subscriptionId = "kb-sub-1", bundleId = "kb-bundle-1", state = "ACTIVE"))
        whenever(subscriptions.save(any<SubscriptionEntity>())).thenAnswer { it.arguments[0] as SubscriptionEntity }

        val newId = service().subscribe(owner.id, app.id, subscriber.id, plan.id)

        val captor = argumentCaptor<SubscriptionEntity>()
        verify(subscriptions).save(captor.capture())
        val saved = captor.firstValue
        assertEquals(subscriber.id, saved.subscriber.id) // NOT the owner
        assertEquals("Active", saved.status)
        assertEquals("kb-sub-1", saved.killbillSubscriptionId)
        assertEquals(saved.id, newId)
        verify(dispatcher).dispatchSubscriptionUpdated(any())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.accountly.commerce.MerchantSubscriptionServiceTest"`
Expected: FAIL — `MerchantSubscriptionService` unresolved.

- [ ] **Step 3: Implement the service**

Create `backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriptionService.kt`:

```kotlin
package com.accountly.commerce

import com.accountly.commerce.webhook.MerchantWebhookDispatcher
import com.accountly.integration.killbill.KillBillAccountClient
import com.accountly.integration.killbill.KillBillProperties
import com.accountly.persistence.AppUserRepository
import com.accountly.persistence.PlanRepository
import com.accountly.persistence.SubscriptionEntity
import com.accountly.persistence.SubscriptionRepository
import com.accountly.security.ApplicationAccess
import com.accountly.usage.KillBillSubscriptionStateMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
open class MerchantSubscriptionService(
    private val applicationAccess: ApplicationAccess,
    private val users: AppUserRepository,
    private val plans: PlanRepository,
    private val subscriptions: SubscriptionRepository,
    killBillClients: ObjectProvider<KillBillAccountClient>,
    private val killBillProperties: KillBillProperties,
    private val webhookDispatcher: MerchantWebhookDispatcher,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val killBillClients = killBillClients

    @Transactional
    open fun subscribe(
        ownerUserId: UUID,
        applicationId: UUID,
        subscriberId: UUID,
        planId: UUID,
    ): UUID {
        val app = applicationAccess.requireOwnerApplicationOrNotFound(ownerUserId, applicationId)
        val kb = requireKillBill()

        val subscriber =
            users.findById(subscriberId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found.")
            }
        val kbAcct =
            subscriber.killbillAccountId?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Subscriber has no Kill Bill account linked.")

        val plan =
            plans.fetchById(planId)?.takeIf { it.application.id == app.id }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found.")
        val kbPlanName =
            plan.killbillPlanName?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Plan is missing Kill Bill catalog mapping (Kill Bill plan name).")

        val activeCount =
            subscriptions.countBySubscriber_IdAndPlan_IdAndStatus(subscriberId, planId, "Active")
        if (activeCount > 0) {
            throw IllegalArgumentException("Subscriber already has an active subscription to this plan.")
        }

        val bundleExternal =
            "${KillBillAccountClient.externalKeyForUser(subscriberId)}:plan:$planId:${UUID.randomUUID()}"
        val kbResult =
            try {
                kb.createSubscription(
                    accountIdKb = kbAcct,
                    planName = kbPlanName,
                    productName = plan.killbillProductName,
                    priceList = plan.killbillPriceList ?: "DEFAULT",
                    bundleExternalKey = bundleExternal,
                )
            } catch (ex: Exception) {
                log.warn("Kill Bill subscription request failed", ex)
                throw IllegalArgumentException(ex.message ?: "Kill Bill subscription failed.")
            }
        val subIdKb =
            kbResult.subscriptionId?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Kill Bill did not return a subscription ID.")

        val entity =
            subscriptions.save(
                SubscriptionEntity(
                    subscriber = subscriber,
                    plan = plan,
                    status = KillBillSubscriptionStateMapper.subscriptionStatus(kbResult.state),
                    killbillSubscriptionId = subIdKb,
                    killbillBundleId = kbResult.bundleId,
                ),
            )
        webhookDispatcher.dispatchSubscriptionUpdated(entity)
        return entity.id
    }

    private fun requireKillBill(): KillBillAccountClient {
        if (!killBillProperties.enabled) {
            throw IllegalArgumentException("Billing is disabled: Kill Bill is not configured on the API.")
        }
        return killBillClients.getIfAvailable()
            ?: throw IllegalArgumentException("Kill Bill integration is unavailable.")
    }

    // Used by Task 6 (changePlan / cancel) — kept here so subscription lifecycle lives together.
    internal fun ownedSubscription(
        app: com.accountly.persistence.ApplicationEntity,
        subscriberId: UUID,
        subscriptionId: UUID,
    ): SubscriptionEntity {
        val sub =
            subscriptions.findById(subscriptionId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found.")
            }
        if (sub.subscriber.id != subscriberId || sub.plan.application.id != app.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found.")
        }
        return sub
    }

    @Suppress("unused")
    private fun touch(now: Instant = Instant.now()) = now // placeholder removed in Task 6
}
```

> The `touch` helper above is a temporary no-op to keep this file self-contained until Task 6 adds `changePlan`/`cancel`. Remove it in Task 6 Step 3.

- [ ] **Step 4: Run the unit test to verify it passes**

Run: `./gradlew test --tests "com.accountly.commerce.MerchantSubscriptionServiceTest"`
Expected: PASS.

- [ ] **Step 5: Add the subscribe endpoint to the controller**

In `backend/src/main/kotlin/com/accountly/api/MerchantBillingController.kt`, add imports and inject the service + add the endpoint. The class header becomes:

```kotlin
import com.accountly.api.dto.MerchantSubscribeRequestBody
import com.accountly.commerce.MerchantSubscriptionService
```

Add `private val merchantSubscriptionService: MerchantSubscriptionService,` to the constructor, then add:

```kotlin
    @PostMapping("/{subscriberId}/subscriptions")
    fun subscribe(
        @AuthenticationPrincipal principal: OAuth2AuthenticatedPrincipal,
        @PathVariable applicationId: String,
        @PathVariable subscriberId: String,
        @Valid @RequestBody body: MerchantSubscribeRequestBody,
    ): ResponseEntity<Map<String, String>> {
        val owner = identityProvisioningService.ensureUser(principal).userId
        val subId =
            merchantSubscriptionService.subscribe(
                ownerUserId = owner,
                applicationId = OwnerApplicationUsageApi.parseUuid(applicationId, "applicationId"),
                subscriberId = OwnerApplicationUsageApi.parseUuid(subscriberId, "subscriberId"),
                planId = OwnerApplicationUsageApi.parseUuid(body.planId, "planId"),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("subscription_id" to subId.toString()))
    }
```

Add the needed import `import org.springframework.web.bind.annotation.PostMapping` (already present from Task 4).

- [ ] **Step 6: Add integration cases for the gate + ownership**

Append to `backend/src/test/kotlin/com/accountly/merchant/MerchantBillingIntegrationTest.kt` (inside the class). First add these imports at the top of the file:

```kotlin
import org.hamcrest.Matchers.containsString
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
```

Then add:

```kotlin
    private fun createPlanWithKbMapping(appId: String): String {
        val planId = objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/applications/$appId/plans")
                    .header("Authorization", authOwner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"tw-pro","pricingModel":"FlatRate","monthlyFee":99}"""),
            ).andExpect(status().isCreated).andReturn().response.contentAsString,
        ).get("plan_id").asText()

        mockMvc.perform(
            patch("/api/v1/plans/$planId/killbill-mapping")
                .header("Authorization", authOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"killbillPlanName":"trustweave-pro-monthly","killbillProductName":"trustweave-pro"}"""),
        ).andExpect(status().isNoContent)
        return planId
    }

    @Test
    fun `subscribe is gated when kill bill is disabled`() {
        val appId = createApplication("TrustWeave SaaS")
        val planId = createPlanWithKbMapping(appId)
        val subscriberId = objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/applications/$appId/subscribers")
                    .header("Authorization", authOwner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalRef":"trustweave:org:9"}"""),
            ).andExpect(status().isCreated).andReturn().response.contentAsString,
        ).get("subscriberId").asText()

        mockMvc.perform(
            post("/api/v1/applications/$appId/subscribers/$subscriberId/subscriptions")
                .header("Authorization", authOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"planId":"$planId"}"""),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail", containsString("Billing is disabled")))
    }

    @Test
    fun `subscribe rejects unknown application for non owner`() {
        val random = UUID.randomUUID().toString()
        mockMvc.perform(
            post("/api/v1/applications/$random/subscribers/$random/subscriptions")
                .header("Authorization", authOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"planId":"$random"}"""),
        ).andExpect(status().isNotFound)
    }
```

- [ ] **Step 7: Run the full merchant integration test**

Run: `./gradlew test --tests "com.accountly.merchant.MerchantBillingIntegrationTest"`
Expected: PASS (6 tests).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriptionService.kt \
        backend/src/main/kotlin/com/accountly/api/MerchantBillingController.kt \
        backend/src/test/kotlin/com/accountly/commerce/MerchantSubscriptionServiceTest.kt \
        backend/src/test/kotlin/com/accountly/merchant/MerchantBillingIntegrationTest.kt
git commit -m "feat(billing): merchant-mediated subscribe without self-serve guard"
```

---

## Task 6: Change plan & cancel

Adds Kill Bill client methods plus owner-mediated change-plan and cancel, each dispatching a webhook. Happy paths unit-tested with a mocked client; endpoints gate on ownership + billing-enabled.

**Files:**
- Modify: `backend/src/main/kotlin/com/accountly/integration/killbill/KillBillAccountClient.kt`
- Modify: `backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriptionService.kt`
- Modify: `backend/src/main/kotlin/com/accountly/api/MerchantBillingController.kt`
- Test: `backend/src/test/kotlin/com/accountly/commerce/MerchantSubscriptionServiceTest.kt` (add cases)

- [ ] **Step 1: Write the failing service unit tests**

Append to `backend/src/test/kotlin/com/accountly/commerce/MerchantSubscriptionServiceTest.kt` (inside the class). Add imports:

```kotlin
import org.mockito.kotlin.never
```

Add tests:

```kotlin
    @Test
    fun `changePlan switches plan in kill bill and dispatches webhook`() {
        val owner = AppUserEntity(displayName = "Merchant")
        val app = ApplicationEntity(id = UUID.randomUUID(), owner = owner, name = "TrustWeave SaaS")
        val subscriber = AppUserEntity(id = UUID.randomUUID(), displayName = "Org One", externalRef = "trustweave:org:1")
        val oldPlan = PlanEntity(id = UUID.randomUUID(), application = app, name = "tw-free", pricingModel = "FlatRate")
        val newPlan = PlanEntity(
            id = UUID.randomUUID(), application = app, name = "tw-pro", pricingModel = "FlatRate",
            killbillPlanName = "trustweave-pro-monthly", killbillProductName = "trustweave-pro", killbillPriceList = "DEFAULT",
        )
        val sub = SubscriptionEntity(
            id = UUID.randomUUID(), subscriber = subscriber, plan = oldPlan,
            status = "Active", killbillSubscriptionId = "kb-sub-1",
        )

        whenever(applicationAccess.requireOwnerApplicationOrNotFound(owner.id, app.id)).thenReturn(app)
        whenever(kbProvider.getIfAvailable()).thenReturn(kb)
        whenever(subscriptions.findById(sub.id)).thenReturn(Optional.of(sub))
        whenever(plans.fetchById(newPlan.id)).thenReturn(newPlan)
        whenever(subscriptions.save(any<SubscriptionEntity>())).thenAnswer { it.arguments[0] as SubscriptionEntity }

        service().changePlan(owner.id, app.id, subscriber.id, sub.id, newPlan.id)

        verify(kb).changePlan(eq("kb-sub-1"), eq("trustweave-pro-monthly"), any(), any())
        assertEquals(newPlan.id, sub.plan.id)
        verify(dispatcher).dispatchSubscriptionUpdated(sub)
    }

    @Test
    fun `cancel cancels in kill bill marks canceled and dispatches webhook`() {
        val owner = AppUserEntity(displayName = "Merchant")
        val app = ApplicationEntity(id = UUID.randomUUID(), owner = owner, name = "TrustWeave SaaS")
        val subscriber = AppUserEntity(id = UUID.randomUUID(), displayName = "Org One", externalRef = "trustweave:org:1")
        val plan = PlanEntity(id = UUID.randomUUID(), application = app, name = "tw-pro", pricingModel = "FlatRate")
        val sub = SubscriptionEntity(
            id = UUID.randomUUID(), subscriber = subscriber, plan = plan,
            status = "Active", killbillSubscriptionId = "kb-sub-1",
        )

        whenever(applicationAccess.requireOwnerApplicationOrNotFound(owner.id, app.id)).thenReturn(app)
        whenever(kbProvider.getIfAvailable()).thenReturn(kb)
        whenever(subscriptions.findById(sub.id)).thenReturn(Optional.of(sub))
        whenever(subscriptions.save(any<SubscriptionEntity>())).thenAnswer { it.arguments[0] as SubscriptionEntity }

        service().cancel(owner.id, app.id, subscriber.id, sub.id)

        verify(kb).cancelSubscription("kb-sub-1")
        assertEquals("Canceled", sub.status)
        assert(sub.canceledAt != null)
        verify(dispatcher).dispatchSubscriptionUpdated(sub)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "com.accountly.commerce.MerchantSubscriptionServiceTest"`
Expected: FAIL — `changePlan`/`cancel` (service) and `kb.changePlan`/`kb.cancelSubscription` unresolved.

- [ ] **Step 3: Add Kill Bill client methods**

In `backend/src/main/kotlin/com/accountly/integration/killbill/KillBillAccountClient.kt`, add these methods to the `KillBillAccountClient` class (after `createSubscription`):

```kotlin
    fun changePlan(
        subscriptionIdKb: String,
        planName: String,
        productName: String?,
        priceList: String?,
    ) {
        val payload =
            buildMap<String, Any> {
                put("planName", planName)
                productName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("productName", it) }
                put("priceList", priceList?.trim()?.takeIf { it.isNotEmpty() } ?: "DEFAULT")
            }
        client
            .put()
            .uri("/1.0/kb/subscriptions/$subscriptionIdKb")
            .header("X-Killbill-Reason", "accountly-change-plan")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .toBodilessEntity()
            .timeout(props.requestTimeout)
            .block()
    }

    fun cancelSubscription(subscriptionIdKb: String) {
        client
            .delete()
            .uri("/1.0/kb/subscriptions/$subscriptionIdKb")
            .header("X-Killbill-Reason", "accountly-cancel")
            .retrieve()
            .toBodilessEntity()
            .timeout(props.requestTimeout)
            .block()
    }
```

- [ ] **Step 4: Add service methods (and remove the `touch` placeholder)**

In `backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriptionService.kt`, delete the `touch` placeholder function and its `@Suppress` line, remove the now-unused `import java.time.Instant` only if you do not use it below (you will — keep it), and add:

```kotlin
    @Transactional
    open fun changePlan(
        ownerUserId: UUID,
        applicationId: UUID,
        subscriberId: UUID,
        subscriptionId: UUID,
        newPlanId: UUID,
    ) {
        val app = applicationAccess.requireOwnerApplicationOrNotFound(ownerUserId, applicationId)
        val kb = requireKillBill()
        val sub = ownedSubscription(app, subscriberId, subscriptionId)
        val newPlan =
            plans.fetchById(newPlanId)?.takeIf { it.application.id == app.id }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found.")
        val kbPlanName =
            newPlan.killbillPlanName?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Plan is missing Kill Bill catalog mapping (Kill Bill plan name).")
        val kbSubId =
            sub.killbillSubscriptionId?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Subscription has no Kill Bill subscription id.")

        kb.changePlan(kbSubId, kbPlanName, newPlan.killbillProductName, newPlan.killbillPriceList ?: "DEFAULT")
        sub.plan = newPlan
        subscriptions.save(sub)
        webhookDispatcher.dispatchSubscriptionUpdated(sub)
    }

    @Transactional
    open fun cancel(
        ownerUserId: UUID,
        applicationId: UUID,
        subscriberId: UUID,
        subscriptionId: UUID,
    ) {
        val app = applicationAccess.requireOwnerApplicationOrNotFound(ownerUserId, applicationId)
        val kb = requireKillBill()
        val sub = ownedSubscription(app, subscriberId, subscriptionId)
        val kbSubId =
            sub.killbillSubscriptionId?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Subscription has no Kill Bill subscription id.")

        kb.cancelSubscription(kbSubId)
        sub.status = "Canceled"
        sub.canceledAt = Instant.now()
        subscriptions.save(sub)
        webhookDispatcher.dispatchSubscriptionUpdated(sub)
    }
```

- [ ] **Step 5: Run the service unit tests to verify they pass**

Run: `./gradlew test --tests "com.accountly.commerce.MerchantSubscriptionServiceTest"`
Expected: PASS (3 tests total).

- [ ] **Step 6: Add the change/cancel endpoints**

In `backend/src/main/kotlin/com/accountly/api/MerchantBillingController.kt` add imports:

```kotlin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
```

Add endpoints:

```kotlin
    @PatchMapping("/{subscriberId}/subscriptions/{subscriptionId}")
    fun changePlan(
        @AuthenticationPrincipal principal: OAuth2AuthenticatedPrincipal,
        @PathVariable applicationId: String,
        @PathVariable subscriberId: String,
        @PathVariable subscriptionId: String,
        @Valid @RequestBody body: MerchantSubscribeRequestBody,
    ): ResponseEntity<Void> {
        val owner = identityProvisioningService.ensureUser(principal).userId
        merchantSubscriptionService.changePlan(
            ownerUserId = owner,
            applicationId = OwnerApplicationUsageApi.parseUuid(applicationId, "applicationId"),
            subscriberId = OwnerApplicationUsageApi.parseUuid(subscriberId, "subscriberId"),
            subscriptionId = OwnerApplicationUsageApi.parseUuid(subscriptionId, "subscriptionId"),
            newPlanId = OwnerApplicationUsageApi.parseUuid(body.planId, "planId"),
        )
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{subscriberId}/subscriptions/{subscriptionId}")
    fun cancel(
        @AuthenticationPrincipal principal: OAuth2AuthenticatedPrincipal,
        @PathVariable applicationId: String,
        @PathVariable subscriberId: String,
        @PathVariable subscriptionId: String,
    ): ResponseEntity<Void> {
        val owner = identityProvisioningService.ensureUser(principal).userId
        merchantSubscriptionService.cancel(
            ownerUserId = owner,
            applicationId = OwnerApplicationUsageApi.parseUuid(applicationId, "applicationId"),
            subscriberId = OwnerApplicationUsageApi.parseUuid(subscriberId, "subscriberId"),
            subscriptionId = OwnerApplicationUsageApi.parseUuid(subscriptionId, "subscriptionId"),
        )
        return ResponseEntity.noContent().build()
    }
```

- [ ] **Step 7: Run the full module test suite**

Run: `./gradlew test`
Expected: PASS — all existing tests plus the new merchant tests. (Compilation confirms the controller wiring.)

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/accountly/integration/killbill/KillBillAccountClient.kt \
        backend/src/main/kotlin/com/accountly/commerce/MerchantSubscriptionService.kt \
        backend/src/main/kotlin/com/accountly/api/MerchantBillingController.kt \
        backend/src/test/kotlin/com/accountly/commerce/MerchantSubscriptionServiceTest.kt
git commit -m "feat(billing): merchant change-plan and cancel with Kill Bill"
```

---

## Task 7: Dispatch webhook on inbound Kill Bill state change

When Kill Bill pushes a state change into Accountly, fan it out to the merchant so TrustWeave can refresh its tier/snapshot.

**Files:**
- Modify: `backend/src/main/kotlin/com/accountly/commerce/KillBillInboundService.kt`
- Test: `backend/src/test/kotlin/com/accountly/commerce/KillBillInboundServiceDispatchTest.kt`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/accountly/commerce/KillBillInboundServiceDispatchTest.kt`:

```kotlin
package com.accountly.commerce

import com.accountly.commerce.webhook.MerchantWebhookDispatcher
import com.accountly.persistence.AppUserEntity
import com.accountly.persistence.ApplicationEntity
import com.accountly.persistence.PlanEntity
import com.accountly.persistence.SubscriptionEntity
import com.accountly.persistence.SubscriptionRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class KillBillInboundServiceDispatchTest {

    private val subscriptions = mock<SubscriptionRepository>()
    private val dispatcher = mock<MerchantWebhookDispatcher>()
    private val service = KillBillInboundService(subscriptions, dispatcher)

    private fun row(): SubscriptionEntity {
        val owner = AppUserEntity(displayName = "Merchant")
        val app = ApplicationEntity(id = UUID.randomUUID(), owner = owner, name = "TrustWeave SaaS")
        val plan = PlanEntity(id = UUID.randomUUID(), application = app, name = "tw-pro", pricingModel = "FlatRate")
        val subscriber = AppUserEntity(displayName = "Org One", externalRef = "trustweave:org:1")
        return SubscriptionEntity(
            subscriber = subscriber, plan = plan, status = "Active", killbillSubscriptionId = "kb-sub-1",
        )
    }

    @Test
    fun `applies state change and dispatches webhook`() {
        val existing = row()
        whenever(subscriptions.findByKillbillSubscriptionId("kb-sub-1")).thenReturn(existing)
        whenever(subscriptions.save(any<SubscriptionEntity>())).thenAnswer { it.arguments[0] as SubscriptionEntity }

        val updated = service.applyPush("kb-sub-1", "CANCELLED", null)

        assertTrue(updated)
        verify(dispatcher).dispatchSubscriptionUpdated(existing)
    }

    @Test
    fun `no matching row does not dispatch`() {
        whenever(subscriptions.findByKillbillSubscriptionId("missing")).thenReturn(null)

        val updated = service.applyPush("missing", "ACTIVE", null)

        assertFalse(updated)
        verify(dispatcher, never()).dispatchSubscriptionUpdated(any())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.accountly.commerce.KillBillInboundServiceDispatchTest"`
Expected: FAIL — `KillBillInboundService` constructor does not accept a dispatcher.

- [ ] **Step 3: Wire the dispatcher into the inbound service**

Replace `backend/src/main/kotlin/com/accountly/commerce/KillBillInboundService.kt` with:

```kotlin
package com.accountly.commerce

import com.accountly.commerce.webhook.MerchantWebhookDispatcher
import com.accountly.persistence.SubscriptionRepository
import com.accountly.usage.KillBillSubscriptionStateMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
open class KillBillInboundService(
    private val subscriptions: SubscriptionRepository,
    private val webhookDispatcher: MerchantWebhookDispatcher,
) {

    @Transactional
    open fun applyPush(
        rawSubscriptionKey: String,
        stateWire: String?,
        bundleId: String?,
    ): Boolean {
        val row =
            subscriptions.findByKillbillSubscriptionId(rawSubscriptionKey.trim())
                ?: return false
        if (bundleId != null && bundleId.isNotBlank()) {
            row.killbillBundleId = bundleId.trim()
        }
        if (!stateWire.isNullOrBlank()) {
            row.status = KillBillSubscriptionStateMapper.subscriptionStatus(stateWire)
            if (row.status == "Canceled") {
                row.canceledAt = Instant.now()
            }
        }
        subscriptions.save(row)
        webhookDispatcher.dispatchSubscriptionUpdated(row)
        return true
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.accountly.commerce.KillBillInboundServiceDispatchTest"`
Expected: PASS.

- [ ] **Step 5: Run the existing inbound webhook integration test (regression)**

Run: `./gradlew test --tests "com.accountly.internal.KillBillInboundWebhookIntegrationTest"`
Expected: PASS — the dispatcher is a no-op when `accountly.merchant-webhook.enabled` is unset (default false), so inbound behavior is unchanged.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/accountly/commerce/KillBillInboundService.kt \
        backend/src/test/kotlin/com/accountly/commerce/KillBillInboundServiceDispatchTest.kt
git commit -m "feat(billing): fan out merchant webhook on inbound Kill Bill state change"
```

---

## Task 8: Kill Bill catalog (ops artifact)

Provides the priced catalog the Accountly plans reference. This is configuration consumed by Kill Bill, not the Accountly JVM, so it ships as a repo-root artifact plus an upload/verify guide.

**Files:**
- Create: `killbill/catalog.xml`
- Create: `killbill/README.md`

- [ ] **Step 1: Create the catalog**

Create `killbill/catalog.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<catalog xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:noNamespaceSchemaLocation="CatalogSchema.xsd">
    <effectiveDate>2026-06-01T00:00:00+00:00</effectiveDate>
    <catalogName>TrustWeave</catalogName>
    <recurringBillingMode>IN_ADVANCE</recurringBillingMode>

    <currencies>
        <currency>USD</currency>
    </currencies>

    <units>
        <unit name="credential.issued"/>
        <unit name="credential.verified"/>
        <unit name="blockchain.tx"/>
        <unit name="wallet.seat"/>
    </units>

    <products>
        <product name="trustweave-free">
            <category>BASE</category>
        </product>
        <product name="trustweave-pro">
            <category>BASE</category>
        </product>
        <product name="trustweave-enterprise">
            <category>BASE</category>
        </product>
    </products>

    <rules>
        <changePolicy>
            <changePolicyCase><policy>IMMEDIATE</policy></changePolicyCase>
        </changePolicy>
        <cancelPolicy>
            <cancelPolicyCase><policy>IMMEDIATE</policy></cancelPolicyCase>
        </cancelPolicy>
    </rules>

    <plans>
        <plan name="trustweave-free-monthly">
            <product>trustweave-free</product>
            <finalPhase type="EVERGREEN">
                <duration><unit>UNLIMITED</unit></duration>
                <recurring>
                    <billingPeriod>MONTHLY</billingPeriod>
                    <recurringPrice><price><currency>USD</currency><value>0.00</value></price></recurringPrice>
                </recurring>
            </finalPhase>
        </plan>

        <plan name="trustweave-pro-monthly">
            <product>trustweave-pro</product>
            <finalPhase type="EVERGREEN">
                <duration><unit>UNLIMITED</unit></duration>
                <recurring>
                    <billingPeriod>MONTHLY</billingPeriod>
                    <recurringPrice><price><currency>USD</currency><value>99.00</value></price></recurringPrice>
                </recurring>
            </finalPhase>
        </plan>

        <plan name="trustweave-enterprise-monthly">
            <product>trustweave-enterprise</product>
            <finalPhase type="EVERGREEN">
                <duration><unit>UNLIMITED</unit></duration>
                <recurring>
                    <billingPeriod>MONTHLY</billingPeriod>
                    <recurringPrice><price><currency>USD</currency><value>499.00</value></price></recurringPrice>
                </recurring>
            </finalPhase>
        </plan>
    </plans>

    <priceLists>
        <defaultPriceList name="DEFAULT">
            <plans>
                <plan>trustweave-free-monthly</plan>
                <plan>trustweave-pro-monthly</plan>
                <plan>trustweave-enterprise-monthly</plan>
            </plans>
        </defaultPriceList>
    </priceLists>
</catalog>
```

> This is a starting catalog with recurring fees only. Usage-tier pricing for the declared `units` (overage rates, cost passthrough for `blockchain.tx`) is added when pricing is finalized (see spec §14, R6). The `units` are declared now so usage events have a home.

- [ ] **Step 2: Create the upload/verify guide**

Create `killbill/README.md`:

```markdown
# TrustWeave Kill Bill catalog

`catalog.xml` is the priced catalog that Accountly plans reference by name
(`killbillPlanName` = `trustweave-<tier>-monthly`, `killbillProductName` = `trustweave-<tier>`,
price list `DEFAULT`).

## Upload (single-tenant default credentials)

```bash
curl -v \
  -u admin:password \
  -H "X-Killbill-ApiKey: bob" \
  -H "X-Killbill-ApiSecret: lazar" \
  -H "X-Killbill-CreatedBy: trustweave" \
  -H "Content-Type: text/xml" \
  -X POST \
  --data-binary @catalog.xml \
  "http://127.0.0.1:8080/1.0/kb/catalog/xml"
```

## Verify

```bash
curl -s -u admin:password \
  -H "X-Killbill-ApiKey: bob" -H "X-Killbill-ApiSecret: lazar" \
  "http://127.0.0.1:8080/1.0/kb/catalog/xml" | grep -o 'trustweave-[a-z]*-monthly'
```

Expected: `trustweave-free-monthly`, `trustweave-pro-monthly`, `trustweave-enterprise-monthly`.
```

- [ ] **Step 3: Validate the XML is well-formed (manual)**

Run (from `killbill/`): `python -c "import xml.dom.minidom,sys; xml.dom.minidom.parse('catalog.xml'); print('well-formed')"`
Expected: `well-formed`.

- [ ] **Step 4: Commit**

```bash
git add killbill/catalog.xml killbill/README.md
git commit -m "feat(billing): add TrustWeave Kill Bill catalog and upload guide"
```

---

## Task 9: Full-suite verification

- [ ] **Step 1: Run the entire backend test suite**

Run (from `backend/`): `./gradlew test`
Expected: BUILD SUCCESSFUL — all prior tests plus:
`AppUserExternalRefRepositoryTest`, `MerchantWebhookSignerTest`, `MerchantWebhookDispatcherTest`, `MerchantSubscriberServiceTest`, `MerchantSubscriptionServiceTest`, `MerchantBillingIntegrationTest`, `KillBillInboundServiceDispatchTest`.

- [ ] **Step 2: Lint**

Run: `./gradlew ktlintFormat ktlintCheck` (if configured in this module). Fix any reported issues and re-run.

- [ ] **Step 3: Final commit (if formatting changed anything)**

```bash
git add -A
git commit -m "style(billing): ktlint format"
```

---

## Self-Review

**Spec coverage** (against the design spec §8 "Changes — Accountly" and §9 "Kill Bill catalog"):
- External-ref identity (`app_users.external_ref` + finder + migration) → Task 1. ✓
- Merchant-mediated subscriber provisioning endpoint → Task 4. ✓
- Merchant-mediated subscribe → Task 5. ✓
- Merchant-mediated change / cancel → Task 6. ✓
- Relax demo `subscriber == owner` guard → Tasks 5/6 implement a *parallel* merchant path with no such guard; the existing self-serve `SubscriptionLifecycleService.subscribe` is left untouched to avoid regressing its tests. ✓ (Intentional deviation: "relax" is realized by not applying the guard on the new path, not by editing the old one.)
- Outbound `MerchantWebhookDispatcher` (net-new) → Tasks 2, 3, 7. ✓
- Kill Bill catalog → Task 8. ✓
- Service-principal-as-owner Keycloak binding (spec §8 last row, R5) → **NOT in this plan.** It is deployment/IdP configuration, not code; it is exercised implicitly (any authenticated owner principal works) and tracked as spec risk R5. Flagged here so it is not forgotten at deploy time.

**Placeholder scan:** The only intentional temporary is the `touch` no-op in Task 5 Step 3, explicitly removed in Task 6 Step 4. No "TBD"/"implement later" remain. Usage-tier pricing in the catalog is deliberately deferred with an inline note (R6).

**Type consistency:** `ProvisionedSubscriber(subscriberId, killbillAccountId)`, `MerchantSubscriberService.ensureSubscriber(externalRef, displayName, email)`, `MerchantSubscriptionService.subscribe(ownerUserId, applicationId, subscriberId, planId)` / `changePlan(..., newPlanId)` / `cancel(...)`, `KillBillAccountClient.changePlan(subscriptionIdKb, planName, productName, priceList)` / `cancelSubscription(subscriptionIdKb)`, `MerchantWebhookDispatcher.dispatchSubscriptionUpdated(SubscriptionEntity)`, `MerchantWebhookSender.send(url, signature, body)`, `MerchantWebhookSigner.sign(secret, payload)` — names match across all tasks and tests.

**Known environmental caveats called out in-plan:**
- KB-gated happy paths are unit-tested (KB disabled in the test profile).
- `jsonPath("$.killbillAccountId").doesNotExist()` depends on the mapper omitting nulls; Task 4 Step 8 notes the fallback assertion.
- Adding the `MerchantWebhookDispatcher` dependency to `KillBillInboundService` changes its constructor; the existing inbound integration test still passes because the dispatcher is a no-op when disabled (Task 7 Step 5 regression check).
