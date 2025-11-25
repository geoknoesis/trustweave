---
title: TrustWeave Cloud - Implementation Guide
---

# TrustWeave Cloud - Implementation Guide

> **Practical code and setup instructions for building TrustWeave Cloud SaaS**

## Table of Contents

1. [Tech Stack Overview](#tech-stack-overview)
2. [Stripe Setup](#stripe-setup)
3. [Ktor Backend API](#ktor-backend-api)
4. [Usage Tracking](#usage-tracking)
5. [Billing System](#billing-system)
6. [Landing Page](#landing-page)
7. [Dashboard UI](#dashboard-ui)
8. [Deployment](#deployment)

---

## Tech Stack Overview

### **Recommended Stack**

**Goal:** Summarise the opinionated stack Geoknoesis uses internally so you can mirror the SaaS reference implementation without second-guessing tooling choices.  
**Result:** A Kotlin-first backend, modern TypeScript frontend, and pay-as-you-go infrastructure that stays light for early-stage deployments but scales with usage.

```kotlin
object TechStack {
    // Backend
    const val backend = "Ktor 3.0"
    const val database = "PostgreSQL 16"
    const val orm = "Exposed (Kotlin SQL)"
    const val cache = "Redis"

    // Frontend
    const val frontend = "React 18 + TypeScript"
    const val styling = "Tailwind CSS + shadcn/ui"
    const val stateManagement = "Zustand"

    // Billing
    const val billing = "Stripe"
    const val analytics = "PostHog (or Mixpanel)"

    // Deployment
    const val hosting = "Fly.io (or Railway)"
    const val cdn = "Cloudflare"
    const val monitoring = "Sentry"
}
```

**Design significance:** Every component is battle-tested with TrustWeave. Kotlin end-to-end keeps skills transferable, and SaaS infrastructure choices minimise cold-start cost without locking you into proprietary tooling.

### **Why This Stack?**

- ✅ **Ktor**: Kotlin-native, easy to share domain models with the SDK.  
- ✅ **PostgreSQL**: Strong transactional semantics for subscription and audit data.  
- ✅ **Stripe**: SaaS-focused billing primitives, webhooks, dunning, and invoicing out of the box.  
- ✅ **Fly.io**: Global edge deployment in minutes; costs stay predictable during experimentation.  
- ✅ **React + Tailwind**: Rapid UI iteration with a rich ecosystem of components.

---

## Stripe Setup

### **1. Create Stripe Account**

**Goal:** Install the Stripe CLI and wire webhooks into your local Ktor server.  
**Result:** Webhook events appear in your development environment so you can exercise subscription state transitions without deploying infrastructure.

```bash
brew install stripe/stripe-cli/stripe
stripe login
stripe listen --forward-to localhost:8080/webhooks/stripe
```

**Design significance:** The CLI mirrors production webhook delivery semantics; aligning local dev with production reduces surprises when billing logic goes live.

### **2. Create Products & Prices**

**Goal:** Seed Stripe with opinionated Starter/Pro plans so the rest of the guide can reference real price IDs.  
**Prerequisites:** Export `STRIPE_SECRET_KEY` in your environment and add the Stripe Java SDK to your build script.

```kotlin
import com.stripe.Stripe
import com.stripe.model.Product
import com.stripe.model.Price
import com.stripe.param.ProductCreateParams
import com.stripe.param.PriceCreateParams

fun main() {
    Stripe.apiKey = System.getenv("STRIPE_SECRET_KEY")
    
    // Starter Plan
    val starterProduct = Product.create(
        ProductCreateParams.builder()
            .setName("TrustWeave Starter")
            .setDescription("Production-ready SSI for growing startups")
            .build()
    )
    
    val starterMonthly = Price.create(
        PriceCreateParams.builder()
            .setProduct(starterProduct.id)
            .setCurrency("usd")
            .setUnitAmount(4900L) // $49.00 in cents
            .setRecurring(
                PriceCreateParams.Recurring.builder()
                    .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                    .build()
            )
            .build()
    )
    
    val starterYearly = Price.create(
        PriceCreateParams.builder()
            .setProduct(starterProduct.id)
            .setCurrency("usd")
            .setUnitAmount(47000L) // $470.00 (20% discount)
            .setRecurring(
                PriceCreateParams.Recurring.builder()
                    .setInterval(PriceCreateParams.Recurring.Interval.YEAR)
                    .build()
            )
            .build()
    )
    
    println("Starter Monthly Price ID: ${starterMonthly.id}")
    println("Starter Yearly Price ID: ${starterYearly.id}")
    
    // Pro Plan
    val proProduct = Product.create(
        ProductCreateParams.builder()
            .setName("TrustWeave Pro")
            .setDescription("Scale your SSI infrastructure with confidence")
            .build()
    )
    
    val proMonthly = Price.create(
        PriceCreateParams.builder()
            .setProduct(proProduct.id)
            .setCurrency("usd")
            .setUnitAmount(14900L) // $149.00
            .setRecurring(
                PriceCreateParams.Recurring.builder()
                    .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                    .build()
            )
            .build()
    )
    
    val proYearly = Price.create(
        PriceCreateParams.builder()
            .setProduct(proProduct.id)
            .setCurrency("usd")
            .setUnitAmount(143000L) // $1,430 (20% discount)
            .setRecurring(
                PriceCreateParams.Recurring.builder()
                    .setInterval(PriceCreateParams.Recurring.Interval.YEAR)
                    .build()
            )
            .build()
    )
    
    println("Pro Monthly Price ID: ${proMonthly.id}")
    println("Pro Yearly Price ID: ${proYearly.id}")
}
```

**Result:** Stripe returns product and price identifiers you can persist in configuration (see the next section).  
**Design significance:** Driving provisioning through Kotlin scripts keeps the flow consistent with the rest of your stack and avoids brittle manual dashboard work.

### **3. Store Price IDs in Config**

**Goal:** Centralise Stripe credentials and price IDs so your application code can inject them via typed configuration.  
**Result:** The Ktor service reads a single configuration object and avoids scattering magic strings across handlers.

```kotlin
data class StripeConfig(
    val secretKey: String = System.getenv("STRIPE_SECRET_KEY"),
    val webhookSecret: String = System.getenv("STRIPE_WEBHOOK_SECRET"),
    
    val prices: StripePrices = StripePrices()
)

data class StripePrices(
    val starterMonthly: String = "price_XXXXX", // Replace with actual IDs
    val starterYearly: String = "price_XXXXX",
    val proMonthly: String = "price_XXXXX",
    val proYearly: String = "price_XXXXX"
)
```

**Design significance:** Using Kotlin data classes for configuration preserves type safety and makes it easy to supply overrides in tests or per-environment YAML/JSON parsers.

---

## Ktor Backend API

### **Project Structure:**

**Goal:** Establish a baseline folder layout so your team knows where to place configuration, services, and TrustWeave-facing routes.  
**Result:** A conventional Ktor project with clear separation between plugins, domain models, and usage/billing services—mirroring Geoknoesis’ production layout.

```
TrustWeave-cloud/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   ├── Application.kt
│   │   │   ├── config/
│   │   │   │   ├── DatabaseConfig.kt
│   │   │   │   └── StripeConfig.kt
│   │   │   ├── models/
│   │   │   │   ├── User.kt
│   │   │   │   ├── Organization.kt
│   │   │   │   └── Subscription.kt
│   │   │   ├── routes/
│   │   │   │   ├── AuthRoutes.kt
│   │   │   │   ├── DidRoutes.kt
│   │   │   │   ├── CredentialRoutes.kt
│   │   │   │   ├── BillingRoutes.kt
│   │   │   │   └── WebhookRoutes.kt
│   │   │   ├── services/
│   │   │   │   ├── VeriCoreService.kt
│   │   │   │   ├── UsageService.kt
│   │   │   │   └── BillingService.kt
│   │   │   └── plugins/
│   │   │       ├── Routing.kt
│   │   │       ├── Security.kt
│   │   │       └── Serialization.kt
│   │   └── resources/
│   │       └── application.conf
│   └── test/
└── docker-compose.yml
```

### **build.gradle.kts:**

**Goal:** Declare all backend dependencies—Ktor, TrustWeave, persistence, billing—and enable Kotlin serialization plugins.  
**Result:** A single Gradle module that compiles the SaaS backend and aligns dependency versions with the main TrustWeave distribution.

```kotlin
plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("io.ktor.plugin") version "3.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-auth-jwt-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-rate-limit-jvm")
    
    // TrustWeave
    implementation("com.trustweave:TrustWeave-all:1.0.0-SNAPSHOT")
    
    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.45.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.45.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.45.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.45.0")
    implementation("org.postgresql:postgresql:42.7.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    // Redis
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    
    // Stripe
    implementation("com.stripe:stripe-java:24.0.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Testing
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
```

**Design significance:** Centralising versions here keeps build reproducibility high; matching Kotlin/Ktor versions with TrustWeave avoids ABI drift when you embed the SDK.

### **Application.kt:**

**Goal:** Wire Ktor’s entry point so every plugin (security, serialization, routing, rate limiting) is configured before requests arrive.  
**Result:** A minimal `module` function that defers to dedicated plugin files—easier to test and override between environments.

```kotlin
package com.trustweave.cloud

import io.ktor.server.application.*
import io.ktor.server.netty.*
import com.trustweave.cloud.plugins.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureSecurity()
    configureSerialization()
    configureRouting()
    configureDatabase()
    configureRateLimiting()
}
```

**Design significance:** Treating configuration as plugins keeps the application modular; each concern can be toggled or replaced independently (for example, swapping authentication schemes in staging).

### **Database Models:**

**Goal:** Persist organisations, subscriptions, API keys, and metered usage with Exposed table definitions.  
**Result:** Relational tables that map directly onto billing and quota decisions while keeping audit timestamps for compliance.

```kotlin
// src/main/kotlin/models/Tables.kt
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 255)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

object Organizations : UUIDTable("organizations") {
    val name = varchar("name", 255)
    val ownerId = reference("owner_id", Users)
    val stripeCustomerId = varchar("stripe_customer_id", 255).nullable()
    val tier = varchar("tier", 50).default("FREE") // FREE, STARTER, PRO, ENTERPRISE
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

object Subscriptions : UUIDTable("subscriptions") {
    val organizationId = reference("organization_id", Organizations)
    val stripeSubscriptionId = varchar("stripe_subscription_id", 255)
    val stripePriceId = varchar("stripe_price_id", 255)
    val status = varchar("status", 50) // active, canceled, past_due, trialing
    val currentPeriodStart = datetime("current_period_start")
    val currentPeriodEnd = datetime("current_period_end")
    val cancelAtPeriodEnd = bool("cancel_at_period_end").default(false)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

object ApiKeys : UUIDTable("api_keys") {
    val organizationId = reference("organization_id", Organizations)
    val key = varchar("key", 255).uniqueIndex()
    val name = varchar("name", 255)
    val lastUsedAt = datetime("last_used_at").nullable()
    val createdAt = datetime("created_at")
    val revokedAt = datetime("revoked_at").nullable()
}

object UsageRecords : UUIDTable("usage_records") {
    val organizationId = reference("organization_id", Organizations)
    val periodStart = datetime("period_start")
    val periodEnd = datetime("period_end")
    
    // Counters
    val didsCreated = integer("dids_created").default(0)
    val didResolutions = integer("did_resolutions").default(0)
    val credentialsIssued = integer("credentials_issued").default(0)
    val credentialsVerified = integer("credentials_verified").default(0)
    val blockchainAnchorings = integer("blockchain_anchorings").default(0)
    val apiCalls = integer("api_calls").default(0)
    val storageBytes = long("storage_bytes").default(0)
    
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
```

**Design significance:** Capturing counters (issuance, verification, anchoring) and timestamps per organisation allows you to enforce tier limits and generate invoices without additional ETL.

---

## Usage Tracking

### **Usage Service:**

**Goal:** Aggregate near-real-time usage metrics in Redis before flushing them to PostgreSQL for billing and dashboards.  
**Result:** Lightweight counters that reset monthly and keep hot data in memory, reducing pressure on transactional storage.

```kotlin
// src/main/kotlin/services/UsageService.kt
package com.trustweave.cloud.services

import io.lettuce.core.RedisClient
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.YearMonth

class UsageService(
    private val redisClient: RedisClient
) {
    private val redis: RedisCommands<String, String> = redisClient.connect().sync()
    
    suspend fun incrementDidCreated(organizationId: String) {
        withContext(Dispatchers.IO) {
            val key = usageKey(organizationId, "dids_created")
            redis.incr(key)
            redis.expire(key, 2592000) // 30 days TTL
        }
    }
    
    suspend fun incrementCredentialIssued(organizationId: String) {
        withContext(Dispatchers.IO) {
            val key = usageKey(organizationId, "credentials_issued")
            redis.incr(key)
            redis.expire(key, 2592000)
        }
    }
    
    suspend fun incrementApiCall(organizationId: String) {
        withContext(Dispatchers.IO) {
            val key = usageKey(organizationId, "api_calls")
            redis.incr(key)
            redis.expire(key, 2592000)
        }
    }
    
    suspend fun getCurrentUsage(organizationId: String): UsageSnapshot {
        return withContext(Dispatchers.IO) {
            UsageSnapshot(
                didsCreated = redis.get(usageKey(organizationId, "dids_created"))?.toIntOrNull() ?: 0,
                didResolutions = redis.get(usageKey(organizationId, "did_resolutions"))?.toIntOrNull() ?: 0,
                credentialsIssued = redis.get(usageKey(organizationId, "credentials_issued"))?.toIntOrNull() ?: 0,
                credentialsVerified = redis.get(usageKey(organizationId, "credentials_verified"))?.toIntOrNull() ?: 0,
                blockchainAnchorings = redis.get(usageKey(organizationId, "blockchain_anchorings"))?.toIntOrNull() ?: 0,
                apiCalls = redis.get(usageKey(organizationId, "api_calls"))?.toIntOrNull() ?: 0
            )
        }
    }
    
    suspend fun checkLimit(organizationId: String, tier: String, metric: String): Boolean {
        val usage = getCurrentUsage(organizationId)
        val limit = getLimit(tier, metric)
        
        return when (metric) {
            "dids_created" -> usage.didsCreated < limit
            "credentials_issued" -> usage.credentialsIssued < limit
            "api_calls" -> usage.apiCalls < limit
            else -> true
        }
    }
    
    private fun usageKey(organizationId: String, metric: String): String {
        val yearMonth = YearMonth.now()
        return "usage:$organizationId:$yearMonth:$metric"
    }
    
    private fun getLimit(tier: String, metric: String): Int {
        return when (tier) {
            "FREE" -> when (metric) {
                "dids_created" -> 10
                "credentials_issued" -> 100
                "api_calls" -> 10000
                else -> Int.MAX_VALUE
            }
            "STARTER" -> when (metric) {
                "dids_created" -> 100
                "credentials_issued" -> 1000
                "api_calls" -> 100000
                else -> Int.MAX_VALUE
            }
            "PRO" -> when (metric) {
                "dids_created" -> 1000
                "credentials_issued" -> 10000
                "api_calls" -> 1000000
                else -> Int.MAX_VALUE
            }
            else -> Int.MAX_VALUE
        }
    }
}

data class UsageSnapshot(
    val didsCreated: Int,
    val didResolutions: Int,
    val credentialsIssued: Int,
    val credentialsVerified: Int,
    val blockchainAnchorings: Int,
    val apiCalls: Int
)
```

### **Rate Limiting Plugin:**

```kotlin
// src/main/kotlin/plugins/RateLimiting.kt
package com.trustweave.cloud.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimiting() {
    install(RateLimit) {
        register(RateLimitName("api-free")) {
            rateLimiter(limit = 5, refillPeriod = 1.seconds)
        }
        
        register(RateLimitName("api-starter")) {
            rateLimiter(limit = 25, refillPeriod = 1.seconds)
        }
        
        register(RateLimitName("api-pro")) {
            rateLimiter(limit = 100, refillPeriod = 1.seconds)
        }
    }
}
```

**Design significance:** By funnelling every metric through typed suspend functions you can enforce throttling, wrap access in traces, and swap Redis for another cache without touching callers.

---

## API Routes

### **DID Routes:**

```kotlin
// src/main/kotlin/routes/DidRoutes.kt
package com.trustweave.cloud.routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import com.trustweave.TrustWeave
import com.trustweave.cloud.services.UsageService
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.didCreationOptions
import kotlinx.serialization.Serializable

fun Route.didRoutes(
    TrustWeave: TrustWeave,
    usageService: UsageService
) {
    route("/v1/dids") {
        
        // Create DID
        post {
            val org = call.attributes[OrganizationKey]
            val request = call.receive<CreateDidRequest>()
            
            // Check limits
            if (!usageService.checkLimit(org.id, org.tier, "dids_created")) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(
                    error = "Limit exceeded",
                    message = "You've reached your DID creation limit. Upgrade your plan."
                ))
                return@post
            }
            
            // Convert incoming request options to typed DidCreationOptions
            val didOptions = request.options?.let { opts ->
                didCreationOptions {
                    opts["algorithm"]?.let { name ->
                        DidCreationOptions.KeyAlgorithm.fromName(name)?.let { algorithm = it }
                    }
                    opts.filterKeys { it != "algorithm" }
                        .forEach { (key, value) -> property(key, value) }
                }
            } ?: DidCreationOptions()

            // Create DID
            val didDocument = TrustWeave.createDid(
                method = request.method ?: "key",
                options = didOptions
            ).getOrThrow()
            
            // Track usage
            usageService.incrementDidCreated(org.id)
            
            call.respond(HttpStatusCode.Created, DidResponse(
                id = didDocument.id,
                document = didDocument,
                createdAt = System.currentTimeMillis()
            ))
        }
        
        // Resolve DID
        get("/{did}") {
            val org = call.attributes[OrganizationKey]
            val did = call.parameters["did"] ?: throw IllegalArgumentException("DID required")
            
            // Resolve
            val result = TrustWeave.resolveDid(did).getOrThrow()
            
            // Track usage
            usageService.incrementApiCall(org.id)
            
            if (result.document != null) {
                call.respond(HttpStatusCode.OK, DidResolutionResponse(
                    document = result.document,
                    metadata = result.metadata
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(
                    error = "Not found",
                    message = "DID not found: $did"
                ))
            }
        }
    }
}

@Serializable
data class CreateDidRequest(
    val method: String? = "key",
    val options: Map<String, String>? = null
)

@Serializable
data class DidResponse(
    val id: String,
    val document: com.trustweave.did.DidDocument,
    val createdAt: Long
)

@Serializable
data class DidResolutionResponse(
    val document: com.trustweave.did.DidDocument,
    val metadata: com.trustweave.did.ResolutionMetadata?
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)
```

### **Credential Routes:**

```kotlin
// src/main/kotlin/routes/CredentialRoutes.kt
package com.trustweave.cloud.routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import com.trustweave.TrustWeave
import com.trustweave.cloud.services.UsageService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

fun Route.credentialRoutes(
    TrustWeave: TrustWeave,
    usageService: UsageService
) {
    route("/v1/credentials") {
        
        // Issue Credential
        post {
            val org = call.attributes[OrganizationKey]
            val request = call.receive<IssueCredentialRequest>()
            
            // Check limits
            if (!usageService.checkLimit(org.id, org.tier, "credentials_issued")) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(
                    error = "Limit exceeded",
                    message = "You've reached your credential issuance limit. Upgrade your plan."
                ))
                return@post
            }
            
            // Issue credential
            val credential = TrustWeave.issueCredential(
                issuerDid = request.issuerDid,
                issuerKeyId = request.issuerKeyId,
                credentialSubject = request.credentialSubject,
                types = request.types ?: listOf("VerifiableCredential"),
                expirationDate = request.expirationDate
            ).getOrThrow()
            
            // Track usage
            usageService.incrementCredentialIssued(org.id)
            
            call.respond(HttpStatusCode.Created, CredentialResponse(
                credential = credential,
                issuedAt = System.currentTimeMillis()
            ))
        }
        
        // Verify Credential
        post("/verify") {
            val org = call.attributes[OrganizationKey]
            val request = call.receive<VerifyCredentialRequest>()
            
            // Verify
            val result = TrustWeave.verifyCredential(
                credential = request.credential,
                options = request.options ?: com.trustweave.credential.CredentialVerificationOptions()
            ).getOrThrow()
            
            // Track usage
            usageService.incrementApiCall(org.id)
            
            call.respond(HttpStatusCode.OK, VerificationResponse(
                valid = result.valid,
                errors = result.errors,
                warnings = result.warnings
            ))
        }
    }
}

@Serializable
data class IssueCredentialRequest(
    val issuerDid: String,
    val issuerKeyId: String,
    val credentialSubject: JsonElement,
    val types: List<String>? = null,
    val expirationDate: String? = null
)

@Serializable
data class CredentialResponse(
    val credential: com.trustweave.credential.models.VerifiableCredential,
    val issuedAt: Long
)

@Serializable
data class VerifyCredentialRequest(
    val credential: com.trustweave.credential.models.VerifiableCredential,
    val options: com.trustweave.credential.CredentialVerificationOptions? = null
)

@Serializable
data class VerificationResponse(
    val valid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
```

---

## Billing Integration

### **Stripe Checkout:**

```kotlin
// src/main/kotlin/routes/BillingRoutes.kt
package com.trustweave.cloud.routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import com.stripe.Stripe
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import kotlinx.serialization.Serializable

fun Route.billingRoutes(stripeSecretKey: String) {
    Stripe.apiKey = stripeSecretKey
    
    route("/v1/billing") {
        
        // Create checkout session
        post("/checkout") {
            val org = call.attributes[OrganizationKey]
            val request = call.receive<CreateCheckoutRequest>()
            
            val params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomerEmail(org.ownerEmail)
                .setClientReferenceId(org.id)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(request.priceId)
                        .setQuantity(1L)
                        .build()
                )
                .setSuccessUrl("${request.returnUrl}?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(request.returnUrl)
                .build()
            
            val session = Session.create(params)
            
            call.respond(HttpStatusCode.OK, CheckoutResponse(
                sessionId = session.id,
                url = session.url
            ))
        }
        
        // Get current subscription
        get("/subscription") {
            val org = call.attributes[OrganizationKey]
            
            // Query database for subscription
            val subscription = getSubscription(org.id)
            
            call.respond(HttpStatusCode.OK, SubscriptionResponse(
                tier = org.tier,
                status = subscription?.status,
                currentPeriodEnd = subscription?.currentPeriodEnd,
                cancelAtPeriodEnd = subscription?.cancelAtPeriodEnd ?: false
            ))
        }
        
        // Cancel subscription
        post("/subscription/cancel") {
            val org = call.attributes[OrganizationKey]
            
            // Cancel in Stripe
            val subscription = getSubscription(org.id)
            if (subscription != null) {
                val stripeSubscription = com.stripe.model.Subscription.retrieve(subscription.stripeSubscriptionId)
                stripeSubscription.cancel()
                
                // Update database
                updateSubscriptionCancellation(subscription.id, true)
            }
            
            call.respond(HttpStatusCode.OK, MessageResponse(
                message = "Subscription will be canceled at period end"
            ))
        }
    }
}

@Serializable
data class CreateCheckoutRequest(
    val priceId: String,
    val returnUrl: String
)

@Serializable
data class CheckoutResponse(
    val sessionId: String,
    val url: String
)

@Serializable
data class SubscriptionResponse(
    val tier: String,
    val status: String?,
    val currentPeriodEnd: Long?,
    val cancelAtPeriodEnd: Boolean
)

@Serializable
data class MessageResponse(
    val message: String
)
```

### **Webhook Handler:**

```kotlin
// src/main/kotlin/routes/WebhookRoutes.kt
package com.trustweave.cloud.routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook

fun Route.webhookRoutes(
    stripeSecretKey: String,
    webhookSecret: String
) {
    Stripe.apiKey = stripeSecretKey
    
    post("/webhooks/stripe") {
        val payload = call.receiveText()
        val sigHeader = call.request.header("Stripe-Signature") ?: ""
        
        try {
            val event = Webhook.constructEvent(payload, sigHeader, webhookSecret)
            
            when (event.type) {
                "checkout.session.completed" -> {
                    val session = event.dataObjectDeserializer.`object`.get() as Session
                    handleCheckoutComplete(session)
                }
                
                "customer.subscription.updated" -> {
                    val subscription = event.dataObjectDeserializer.`object`.get() as com.stripe.model.Subscription
                    handleSubscriptionUpdate(subscription)
                }
                
                "customer.subscription.deleted" -> {
                    val subscription = event.dataObjectDeserializer.`object`.get() as com.stripe.model.Subscription
                    handleSubscriptionDeleted(subscription)
                }
                
                "invoice.payment_succeeded" -> {
                    // Handle successful payment
                }
                
                "invoice.payment_failed" -> {
                    // Handle failed payment
                }
            }
            
            call.respond(HttpStatusCode.OK)
            
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Webhook error: ${e.message}")
        }
    }
}

private suspend fun handleCheckoutComplete(session: Session) {
    val orgId = session.clientReferenceId
    val subscriptionId = session.subscription
    
    // Update organization with Stripe customer and subscription
    updateOrganizationSubscription(orgId, session.customer, subscriptionId)
}

private suspend fun handleSubscriptionUpdate(subscription: com.stripe.model.Subscription) {
    // Update subscription status in database
    updateSubscriptionStatus(subscription.id, subscription.status)
}

private suspend fun handleSubscriptionDeleted(subscription: com.stripe.model.Subscription) {
    // Downgrade organization to FREE tier
    downgradeToFree(subscription.id)
}
```

---

## Landing Page Template

### **React + Tailwind Pricing Page:**

```typescript
// frontend/src/pages/Pricing.tsx
import React from 'react';
import { Check, X } from 'lucide-react';

const tiers = [
  {
    name: 'Free',
    price: '$0',
    description: 'Build and test your SSI application',
    features: [
      { name: '10 DIDs/month', included: true },
      { name: '100 Credentials/month', included: true },
      { name: '1K Verifications/month', included: true },
      { name: 'Community support', included: true },
      { name: 'Blockchain anchoring', included: false },
      { name: 'Custom domains', included: false },
      { name: 'Team collaboration', included: false },
    ],
    cta: 'Start Free',
    ctaLink: '/signup',
    popular: false,
  },
  {
    name: 'Starter',
    price: '$49',
    period: '/month',
    description: 'Production-ready for startups',
    features: [
      { name: '100 DIDs/month', included: true },
      { name: '1K Credentials/month', included: true },
      { name: '10K Verifications/month', included: true },
      { name: 'Email support (2 days)', included: true },
      { name: 'Blockchain anchoring', included: true },
      { name: 'Team collaboration (3 members)', included: true },
      { name: 'Custom domains', included: false },
    ],
    cta: 'Start 14-day trial',
    ctaLink: '/signup?plan=starter',
    popular: false,
  },
  {
    name: 'Pro',
    price: '$149',
    period: '/month',
    description: 'Scale with confidence',
    features: [
      { name: '1K DIDs/month', included: true },
      { name: '10K Credentials/month', included: true },
      { name: '100K Verifications/month', included: true },
      { name: 'Priority support (4 hours)', included: true },
      { name: 'Mainnet blockchain anchoring', included: true },
      { name: 'Team collaboration (10 members)', included: true },
      { name: 'Custom domains', included: true },
      { name: 'White-labeling', included: true },
    ],
    cta: 'Start 14-day trial',
    ctaLink: '/signup?plan=pro',
    popular: true,
  },
  {
    name: 'Enterprise',
    price: 'Custom',
    description: 'Unlimited scale and dedicated support',
    features: [
      { name: 'Unlimited everything', included: true },
      { name: '24/7 support (1 hour SLA)', included: true },
      { name: 'On-premise deployment', included: true },
      { name: 'Dedicated support engineer', included: true },
      { name: 'Custom integrations', included: true },
      { name: 'Compliance assistance', included: true },
      { name: 'SOC2, HIPAA, FedRAMP', included: true },
    ],
    cta: 'Contact Sales',
    ctaLink: '/contact-sales',
    popular: false,
  },
];

export function PricingPage() {
  return (
    <div className="bg-white py-24 sm:py-32">
      <div className="mx-auto max-w-7xl px-6 lg:px-8">
        <div className="mx-auto max-w-4xl text-center">
          <h2 className="text-base font-semibold leading-7 text-indigo-600">
            Pricing
          </h2>
          <p className="mt-2 text-4xl font-bold tracking-tight text-gray-900 sm:text-5xl">
            Start free. Scale as you grow. Go enterprise when ready.
          </p>
        </div>
        <p className="mx-auto mt-6 max-w-2xl text-center text-lg leading-8 text-gray-600">
          All plans include W3C-compliant DIDs, Verifiable Credentials, and Wallet APIs.
          No credit card required for Free tier.
        </p>
        
        <div className="isolate mx-auto mt-16 grid max-w-md grid-cols-1 gap-y-8 sm:mt-20 lg:mx-0 lg:max-w-none lg:grid-cols-4 gap-x-8">
          {tiers.map((tier) => (
            <div
              key={tier.name}
              className={`flex flex-col justify-between rounded-3xl p-8 ring-1 ${
                tier.popular
                  ? 'ring-2 ring-indigo-600 bg-indigo-50'
                  : 'ring-gray-200 bg-white'
              } xl:p-10`}
            >
              <div>
                {tier.popular && (
                  <div className="flex items-center justify-center mb-4">
                    <span className="rounded-full bg-indigo-600 px-4 py-1 text-xs font-semibold text-white">
                      Most Popular
                    </span>
                  </div>
                )}
                <div className="flex items-baseline gap-x-2">
                  <span className="text-4xl font-bold tracking-tight text-gray-900">
                    {tier.price}
                  </span>
                  {tier.period && (
                    <span className="text-base font-semibold leading-7 text-gray-600">
                      {tier.period}
                    </span>
                  )}
                </div>
                <h3 className="mt-6 text-lg font-semibold leading-8 text-gray-900">
                  {tier.name}
                </h3>
                <p className="mt-2 text-sm leading-6 text-gray-600">
                  {tier.description}
                </p>
                <ul role="list" className="mt-8 space-y-3 text-sm leading-6 text-gray-600">
                  {tier.features.map((feature) => (
                    <li key={feature.name} className="flex gap-x-3">
                      {feature.included ? (
                        <Check className="h-6 w-5 flex-none text-indigo-600" />
                      ) : (
                        <X className="h-6 w-5 flex-none text-gray-300" />
                      )}
                      {feature.name}
                    </li>
                  ))}
                </ul>
              </div>
              <a
                href={tier.ctaLink}
                className={`mt-8 block rounded-md px-3.5 py-2.5 text-center text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 ${
                  tier.popular
                    ? 'bg-indigo-600 text-white shadow-sm hover:bg-indigo-500 focus-visible:outline-indigo-600'
                    : 'bg-white text-indigo-600 ring-1 ring-inset ring-indigo-200 hover:ring-indigo-300'
                }`}
              >
                {tier.cta}
              </a>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
```

---

## Deployment

### **docker-compose.yml (Local Development):**

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: vericore_cloud
      POSTGRES_USER: TrustWeave
      POSTGRES_PASSWORD: vericore_dev
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
  
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
  
  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/vericore_cloud
      DATABASE_USER: TrustWeave
      DATABASE_PASSWORD: vericore_dev
      REDIS_URL: redis://redis:6379
      STRIPE_SECRET_KEY: ${STRIPE_SECRET_KEY}
      STRIPE_WEBHOOK_SECRET: ${STRIPE_WEBHOOK_SECRET}
    depends_on:
      - postgres
      - redis

volumes:
  postgres_data:
```

### **Fly.io Deployment:**

```toml
# fly.toml
app = "TrustWeave-cloud"
primary_region = "iad"

[build]
  builder = "paketobuildpacks/builder:base"

[env]
  PORT = "8080"

[[services]]
  internal_port = 8080
  protocol = "tcp"

  [[services.ports]]
    handlers = ["http"]
    port = 80
    force_https = true

  [[services.ports]]
    handlers = ["tls", "http"]
    port = 443

[http_service]
  internal_port = 8080
  force_https = true
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 1

[[vm]]
  size = "shared-cpu-1x"
  memory = "512mb"
```

### **Deploy Commands:**

```bash
# Install Fly CLI
brew install flyctl

# Login
flyctl auth login

# Create app
flyctl launch

# Create PostgreSQL
flyctl postgres create --name TrustWeave-cloud-db

# Attach database
flyctl postgres attach TrustWeave-cloud-db

# Create Redis
flyctl redis create --name TrustWeave-cloud-redis

# Set secrets
flyctl secrets set STRIPE_SECRET_KEY=sk_...
flyctl secrets set STRIPE_WEBHOOK_SECRET=whsec_...

# Deploy
flyctl deploy

# View logs
flyctl logs
```

---

## Next Steps

### **Week 1-2: Core Infrastructure**
- [ ] Set up Ktor project
- [ ] Configure PostgreSQL + Redis
- [ ] Implement authentication (JWT)
- [ ] Build API key management
- [ ] Create database migrations

### **Week 3-4: Usage & Billing**
- [ ] Implement usage tracking
- [ ] Build Stripe integration
- [ ] Create webhook handlers
- [ ] Add tier enforcement
- [ ] Build usage dashboard

### **Week 5-6: API Endpoints**
- [ ] DID creation/resolution
- [ ] Credential issuance
- [ ] Credential verification
- [ ] Wallet APIs
- [ ] Blockchain anchoring

### **Week 7-8: Frontend & Polish**
- [ ] Build landing page
- [ ] Create pricing page
- [ ] Build dashboard UI
- [ ] Add analytics (PostHog)
- [ ] Deploy to production

---

**Ready to build?** This gives you everything you need to launch TrustWeave Cloud in 8 weeks! 🚀

Let me know if you want me to:
- Generate the complete Ktor application code
- Build the React dashboard
- Create deployment scripts
- Write integration tests


