# Plan 3 — TrustWeave Billing UI: Pricing Plans Widget (Accountly catalog-as-code → live UI)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface TrustWeave's tier catalog (declared into Accountly by Plan 2a's `CatalogReconciler`) as a live `PricingPlans` widget — a backend proxy endpoint that reads Accountly's plans, and a React component that renders tier cards. Editing a tier in Accountly updates the UI with no redeploy.

**Architecture:** A Kotlin `BillingProxyController.GET /api/billing/plans` resolves the Accountly applicationId (`CatalogReconciler.reconcile()`), lists plans (`AccountlyBillingClient.listPlans`), and maps them to a sanitized public DTO (display name, monthly fee, quota-derived feature bullets). A React `accountlyBillingApi.getPlans()` client calls it; a `PricingPlans` component renders tier cards. Backend tested with `@WebMvcTest`; frontend tested with Vitest + Testing Library + MSW.

**Tech Stack:** Backend — Kotlin/Spring Boot 3.5.7, `@WebMvcTest`, mockito-kotlin. Frontend — React 18, Vite, TypeScript, axios, Tailwind, Vitest + @testing-library/react + MSW.

---

## Working directory & conventions

- **Repo: `c:\Users\steph\work\trustweave-saas`**, branch `main` (Plan 2 is on main). Run backend Gradle from repo root; run frontend from `frontend/`.
- Backend filtered test: `./gradlew :server:test --tests "<FQCN>" -x jacocoTestCoverageVerification`.
- Frontend tests: `cd frontend && npx vitest run <path>` (the `test` script is `vitest run`).
- Backend patterns (present on main): `AccountlyBillingClient.listPlans(applicationId): List<PlanDto>` where `PlanDto(planId, applicationId, name, pricingModel, monthlyFee?, level, isDefault, killbill*, quotas: List<PlanQuotaDto(quotaId, eventType, hardLimit, period, expression?)>)`; `CatalogReconciler.reconcile(): String`. Controllers are plain `@RestController` classes; `@WebMvcTest(addFilters = false)` is the proven slice pattern (see `AccountlyWebhookControllerTest`).
- Frontend patterns: `import apiClient from './index'` (axios, baseURL `/api`); per-domain API objects (see `api/domains.ts`, `api/billing.ts`); MSW handlers in `src/test/mocks/handlers.ts` (global) or `server.use(...)` per test; setup uses `onUnhandledRequest: 'warn'` so ADD a handler for new endpoints; components are functional + Tailwind (see `components/cards/TrustedDomainCard.tsx`).

## File structure

**Created (backend)**
- `billing/accountly/PublicPlanDto.kt` — sanitized plan + feature bullets.
- `billing/accountly/PlanFeatureFormatter.kt` — quota → human feature string (pure, unit-tested).
- `controller/BillingProxyController.kt` — `GET /api/billing/plans`.

**Created (frontend)**
- `frontend/src/api/accountlyBilling.ts` — `accountlyBillingApi.getPlans()`.
- `frontend/src/components/PricingPlans.tsx` — tier cards.
- Tests for each (backend `@WebMvcTest`, frontend Vitest).

**Modified**
- `frontend/src/test/mocks/handlers.ts` — add a default `/api/billing/plans` handler.

---

## Task 1: Plan feature formatter (pure)

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/PlanFeatureFormatter.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/PlanFeatureFormatterTest.kt`

Turns a quota (`eventType`, `hardLimit`, `period`) into a human bullet, e.g. `credential.issued` / 500 / Month → `"500 credentials / month"`.

- [ ] **Step 1: Write the failing test.** Create `PlanFeatureFormatterTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlanFeatureFormatterTest {

    @Test
    fun `formats known event types with thousands separators and period`() {
        assertEquals("500 credentials / month", PlanFeatureFormatter.feature("credential.issued", 500, "Month"))
        assertEquals("50,000 credentials / month", PlanFeatureFormatter.feature("credential.issued", 50_000, "Month"))
        assertEquals("2,000 verifications / month", PlanFeatureFormatter.feature("credential.verified", 2_000, "Month"))
    }

    @Test
    fun `falls back to the raw event type for unknown events`() {
        assertEquals("10 blockchain.tx / month", PlanFeatureFormatter.feature("blockchain.tx", 10, "Month"))
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.PlanFeatureFormatterTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Implement.** `PlanFeatureFormatter.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

import java.text.NumberFormat
import java.util.Locale

/** Renders a plan quota as a human-readable feature bullet for the pricing UI. */
object PlanFeatureFormatter {

    private val LABELS = mapOf(
        "credential.issued" to "credentials",
        "credential.verified" to "verifications",
        "wallet.seat" to "wallet seats",
    )

    fun feature(eventType: String, hardLimit: Long, period: String): String {
        val count = NumberFormat.getIntegerInstance(Locale.US).format(hardLimit)
        val noun = LABELS[eventType] ?: eventType
        val per = period.lowercase()
        return "$count $noun / $per"
    }
}
```

- [ ] **Step 4: Run → PASS (2 tests).** Same command as Step 2.

- [ ] **Step 5: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/PlanFeatureFormatter.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/PlanFeatureFormatterTest.kt
git commit -m "feat(billing): plan-quota feature formatter for pricing UI"
```

---

## Task 2: BillingProxyController GET /api/billing/plans

**Files:**
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/PublicPlanDto.kt`
- Create: `server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/controller/BillingProxyController.kt`
- Test: `server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/controller/BillingProxyControllerTest.kt`

- [ ] **Step 1: Write the failing test.** Create `BillingProxyControllerTest.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.controller

import com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyBillingClient
import com.geoknoesis.trustweave.saas.server.billing.accountly.CatalogReconciler
import com.geoknoesis.trustweave.saas.server.billing.accountly.PlanDto
import com.geoknoesis.trustweave.saas.server.billing.accountly.PlanQuotaDto
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [BillingProxyController::class])
@AutoConfigureMockMvc(addFilters = false)
class BillingProxyControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockBean private lateinit var client: AccountlyBillingClient
    @MockBean private lateinit var reconciler: CatalogReconciler

    @Test
    fun `GET plans returns sorted tier cards with features`() {
        whenever(reconciler.reconcile()).thenReturn("app-1")
        whenever(client.listPlans("app-1")).thenReturn(
            listOf(
                PlanDto(planId = "p2", applicationId = "app-1", name = "tw-pro", pricingModel = "FlatRate",
                    monthlyFee = "99", level = 2, isDefault = false,
                    quotas = listOf(PlanQuotaDto("q", "credential.issued", 50_000, "Month", null))),
                PlanDto(planId = "p1", applicationId = "app-1", name = "tw-free", pricingModel = "FlatRate",
                    monthlyFee = "0", level = 1, isDefault = true,
                    quotas = listOf(PlanQuotaDto("q", "credential.issued", 500, "Month", null))),
            ),
        )

        mockMvc.perform(get("/api/billing/plans"))
            .andExpect(status().isOk)
            // sorted by level ascending: tw-free first
            .andExpect(jsonPath("$[0].name").value("tw-free"))
            .andExpect(jsonPath("$[0].displayName").value("Free"))
            .andExpect(jsonPath("$[0].monthlyFee").value("0"))
            .andExpect(jsonPath("$[0].isDefault").value(true))
            .andExpect(jsonPath("$[0].features[0]").value("500 credentials / month"))
            .andExpect(jsonPath("$[1].name").value("tw-pro"))
            .andExpect(jsonPath("$[1].displayName").value("Pro"))
            .andExpect(jsonPath("$[1].features[0]").value("50,000 credentials / month"))
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.controller.BillingProxyControllerTest" -x jacocoTestCoverageVerification`

- [ ] **Step 3: Create the DTO.** `PublicPlanDto.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.billing.accountly

data class PublicPlanDto(
    val name: String,
    val displayName: String,
    val monthlyFee: String,
    val level: Int,
    val isDefault: Boolean,
    val features: List<String>,
)
```

- [ ] **Step 4: Create the controller.** `BillingProxyController.kt`:

```kotlin
package com.geoknoesis.trustweave.saas.server.controller

import com.geoknoesis.trustweave.saas.server.billing.accountly.AccountlyBillingClient
import com.geoknoesis.trustweave.saas.server.billing.accountly.CatalogReconciler
import com.geoknoesis.trustweave.saas.server.billing.accountly.PlanFeatureFormatter
import com.geoknoesis.trustweave.saas.server.billing.accountly.PublicPlanDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/billing")
class BillingProxyController(
    private val reconciler: CatalogReconciler,
    private val client: AccountlyBillingClient,
) {

    /** Live tier catalog for the pricing UI, sourced from Accountly. */
    @GetMapping("/plans")
    fun plans(): List<PublicPlanDto> {
        val applicationId = reconciler.reconcile()
        return client.listPlans(applicationId)
            .sortedBy { it.level }
            .map { plan ->
                PublicPlanDto(
                    name = plan.name,
                    displayName = displayName(plan.name),
                    monthlyFee = plan.monthlyFee ?: "0",
                    level = plan.level,
                    isDefault = plan.isDefault,
                    features = plan.quotas
                        .sortedBy { it.eventType }
                        .map { PlanFeatureFormatter.feature(it.eventType, it.hardLimit, it.period) },
                )
            }
    }

    private fun displayName(planName: String): String =
        when (planName) {
            "tw-free" -> "Free"
            "tw-pro" -> "Pro"
            "tw-enterprise" -> "Enterprise"
            else -> planName.removePrefix("tw-").replaceFirstChar { it.uppercase() }
        }
}
```

- [ ] **Step 5: Run → PASS (1 test).** Same command as Step 2.

- [ ] **Step 6: Commit.**
```bash
git add server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/PublicPlanDto.kt server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/controller/BillingProxyController.kt server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/controller/BillingProxyControllerTest.kt
git commit -m "feat(billing): BillingProxyController GET /api/billing/plans"
```

> Note: `/api/billing/plans` sits under `/api/**` which the existing security config authenticates. That is fine for an in-app pricing/upgrade view. To make it truly public (marketing page), a follow-up would add `permitAll` for this exact path in `KeycloakSecurityConfig` — out of scope here.

---

## Task 3: Frontend API client `accountlyBillingApi.getPlans`

**Files:**
- Create: `frontend/src/api/accountlyBilling.ts`
- Modify: `frontend/src/test/mocks/handlers.ts`
- Test: `frontend/src/api/__tests__/accountlyBilling.test.ts`

- [ ] **Step 1: Add a default MSW handler.** In `frontend/src/test/mocks/handlers.ts`, add to the `handlers` array (keep existing entries):

```ts
  http.get('/api/billing/plans', () => {
    return HttpResponse.json([
      { name: 'tw-free', displayName: 'Free', monthlyFee: '0', level: 1, isDefault: true, features: ['500 credentials / month'] },
      { name: 'tw-pro', displayName: 'Pro', monthlyFee: '99', level: 2, isDefault: false, features: ['50,000 credentials / month'] },
    ])
  }),
```

- [ ] **Step 2: Write the failing test.** Create `frontend/src/api/__tests__/accountlyBilling.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { accountlyBillingApi } from '../accountlyBilling'

describe('accountlyBillingApi', () => {
  it('fetches the public plan list', async () => {
    const plans = await accountlyBillingApi.getPlans()
    expect(Array.isArray(plans)).toBe(true)
    expect(plans[0].displayName).toBe('Free')
    expect(plans[1].displayName).toBe('Pro')
    expect(plans[0].features[0]).toBe('500 credentials / month')
  })
})
```

- [ ] **Step 3: Run → FAIL.** `cd frontend && npx vitest run src/api/__tests__/accountlyBilling.test.ts`

- [ ] **Step 4: Implement.** Create `frontend/src/api/accountlyBilling.ts`:

```ts
import apiClient from './index';

export interface PublicPlan {
  name: string;
  displayName: string;
  monthlyFee: string;
  level: number;
  isDefault: boolean;
  features: string[];
}

export const accountlyBillingApi = {
  getPlans: async (): Promise<PublicPlan[]> => {
    const response = await apiClient.get('/billing/plans');
    return response.data as PublicPlan[];
  },
};
```

- [ ] **Step 5: Run → PASS (1 test).** Same command as Step 3.

- [ ] **Step 6: Commit.**
```bash
git add frontend/src/api/accountlyBilling.ts frontend/src/test/mocks/handlers.ts frontend/src/api/__tests__/accountlyBilling.test.ts
git commit -m "feat(billing-ui): accountlyBilling API client (getPlans)"
```

---

## Task 4: PricingPlans React component

**Files:**
- Create: `frontend/src/components/PricingPlans.tsx`
- Test: `frontend/src/components/__tests__/PricingPlans.test.tsx`

- [ ] **Step 1: Write the failing test.** Create `frontend/src/components/__tests__/PricingPlans.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { PricingPlans } from '../PricingPlans'

describe('PricingPlans', () => {
  it('renders a card per tier with price and features from the API', async () => {
    render(<PricingPlans />)

    // loading state first
    expect(screen.getByText(/loading plans/i)).toBeInTheDocument()

    // then the tiers from the MSW default handler
    await waitFor(() => expect(screen.getByText('Free')).toBeInTheDocument())
    expect(screen.getByText('Pro')).toBeInTheDocument()
    expect(screen.getByText('$0')).toBeInTheDocument()
    expect(screen.getByText('$99')).toBeInTheDocument()
    expect(screen.getByText('500 credentials / month')).toBeInTheDocument()
    expect(screen.getByText(/current plan/i)).toBeInTheDocument() // isDefault badge on Free
  })
})
```

- [ ] **Step 2: Run → FAIL.** `cd frontend && npx vitest run src/components/__tests__/PricingPlans.test.tsx`

- [ ] **Step 3: Implement.** Create `frontend/src/components/PricingPlans.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { accountlyBillingApi, type PublicPlan } from '../api/accountlyBilling';

export const PricingPlans = () => {
  const [plans, setPlans] = useState<PublicPlan[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    accountlyBillingApi
      .getPlans()
      .then((p) => { if (active) setPlans(p); })
      .catch(() => { if (active) setError('Could not load plans.'); });
    return () => { active = false; };
  }, []);

  if (error) return <div className="text-red-600">{error}</div>;
  if (!plans) return <div className="text-gray-500">Loading plans…</div>;

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
      {plans.map((plan) => (
        <div
          key={plan.name}
          className={`p-6 rounded-lg border-2 bg-white flex flex-col ${
            plan.isDefault ? 'border-blue-500 shadow-lg' : 'border-gray-200'
          }`}
        >
          <h3 className="text-xl font-semibold mb-1">{plan.displayName}</h3>
          <div className="text-3xl font-bold mb-1">${plan.monthlyFee}</div>
          <div className="text-sm text-gray-500 mb-4">per month</div>
          <ul className="text-sm text-gray-700 space-y-2 flex-1">
            {plan.features.map((f) => (
              <li key={f} className="flex items-start">
                <span className="text-blue-500 mr-2">✓</span>
                {f}
              </li>
            ))}
          </ul>
          {plan.isDefault && (
            <div className="mt-4 text-blue-600 font-medium text-sm">Current plan</div>
          )}
        </div>
      ))}
    </div>
  );
};
```

- [ ] **Step 4: Run → PASS (1 test).** Same command as Step 2.

- [ ] **Step 5: Commit.**
```bash
git add frontend/src/components/PricingPlans.tsx frontend/src/components/__tests__/PricingPlans.test.tsx
git commit -m "feat(billing-ui): PricingPlans tier-card component"
```

---

## Task 5: Verification

- [ ] **Step 1: Backend billing tests.** `cd /c/Users/steph/work/trustweave-saas && ./gradlew :server:test --tests "com.geoknoesis.trustweave.saas.server.billing.accountly.PlanFeatureFormatterTest" --tests "com.geoknoesis.trustweave.saas.server.controller.BillingProxyControllerTest" -x jacocoTestCoverageVerification` → BUILD SUCCESSFUL (3 tests).
- [ ] **Step 2: Frontend tests.** `cd /c/Users/steph/work/trustweave-saas/frontend && npx vitest run src/api/__tests__/accountlyBilling.test.ts src/components/__tests__/PricingPlans.test.tsx` → 2 files pass.
- [ ] **Step 3: Backend compiles.** `./gradlew :server:compileKotlin` → BUILD SUCCESSFUL.

---

## Self-Review

**Spec coverage** (design spec §4.2 plan widgets, public `PricingPlans`):
- Backend proxy serving Accountly's live plan list → Task 2. ✓
- Quota → feature-bullet formatting → Task 1. ✓
- Frontend API client → Task 3. ✓
- `PricingPlans` tier-card component → Task 4. ✓
- **Deferred (documented):** in-app `CurrentPlanCard` + `UsageMeter` + change-plan action — they need org-scoped subscription/usage endpoints and the Accountly entitlement envelope (currently a stub `{}`), so they wait on real entitlement computation. The change-plan action also calls `OrganizationBillingService.changeTier` (already built) — a thin follow-on once the current-plan view exists. Making `/api/billing/plans` truly public (marketing page) needs a `KeycloakSecurityConfig` permit rule — out of scope.

**Placeholder scan:** none — every step has concrete code.

**Type consistency:** `PublicPlanDto(name, displayName, monthlyFee, level, isDefault, features)` (Task 2) ↔ frontend `PublicPlan` interface (Task 3) ↔ MSW handler shape (Task 3) ↔ component fields (Task 4) ↔ controller test assertions (Task 2). `PlanFeatureFormatter.feature(eventType, hardLimit, period)` (Task 1) used by the controller (Task 2). `accountlyBillingApi.getPlans(): Promise<PublicPlan[]>` (Task 3) used by the component (Task 4).

**Known caveats to verify during execution:**
- `@WebMvcTest(addFilters = false)` is the proven slice pattern (matches `AccountlyWebhookControllerTest`); it avoids the pre-existing Flyway-in-test breakage.
- The frontend MSW setup warns (not errors) on unhandled requests; Task 3 adds the default `/api/billing/plans` handler so both the api test and the component test resolve it.
- The component test asserts `$0`/`$99` — the component renders `${plan.monthlyFee}` (string from the API), so `monthlyFee: '0'` → `$0`. Keep monthlyFee a string end-to-end.

---

## After Plan 3
The pricing widget closes the catalog-as-code → live-UI loop. Remaining for the integration: in-app `CurrentPlanCard`/`UsageMeter` + change-plan (needs real Accountly entitlement envelopes); `EntitlementGate` enforcement + credential-issuance wiring; full Stripe deletion (after the Flyway-in-test fix).
