# TrustWeave Cloud - Pricing Strategy

> **Strategic Pricing for SSI/DID Infrastructure as a Service**

## Executive Summary

TrustWeave Cloud uses a **value-based tiered pricing model** designed to:
- **Maximize developer adoption** (generous free tier)
- **Convert growth-stage companies** (affordable Pro tier)
- **Capture enterprise value** (custom Enterprise tier)
- **Complement open-source offering** (not compete with it)

**Target Metrics:**
- Free-to-Paid conversion: 3-5%
- Pro-to-Enterprise conversion: 10-15%
- Annual LTV/CAC ratio: 3:1+
- Net Revenue Retention: 120%+

---

## Pricing Tiers Overview

### 📊 Four-Tier Model

```
FREE → STARTER → PRO → ENTERPRISE
 │        │       │         │
 │        │       │         └─> Custom Pricing
 │        │       └─> $149/month
 │        └─> $49/month  
 └─> $0 (forever)
```

---

## Tier 1: FREE (Developer)

### **Target Audience:**
- Individual developers
- Open-source projects
- Early-stage startups (pre-revenue)
- Students/researchers
- Proof-of-concept projects

### **Positioning:**
*"Build and test your SSI application for free"*

### **Pricing:**
```kotlin
object FreeTier {
    const val monthlyPrice = 0
    const val annualPrice = 0
    
    // Identity Operations
    const val didsPerMonth = 10
    const val didResolutionsPerMonth = 1000
    const val didMethodsSupported = listOf("key", "web")
    
    // Credential Operations
    const val credentialsIssuedPerMonth = 100
    const val credentialsVerifiedPerMonth = 1000
    const val credentialsStored = 500 // Total storage
    
    // Wallet Operations
    const val walletsCreated = 3
    const val queriesPerMonth = 10000
    
    // Blockchain Anchoring
    const val blockchainAnchoring = false
    const val anchoringNetworks = emptyList<String>()
    
    // Infrastructure
    const val apiCallsPerMonth = 10000
    const val storageGB = 0.5
    const val dataTransferGB = 1
    const val rateLimitPerSecond = 5
    
    // Support & SLA
    const val support = "Community (GitHub Discussions)"
    const val sla = "None"
    const val responseTime = "Best effort"
    
    // Features
    const val webhooks = false
    const val customDomains = false
    const val teamCollaboration = false
    const val apiVersions = listOf("v1") // Latest only
    const val auditLogs = false
    const val whiteLabeling = false
}
```

### **Strategic Purpose:**
- **Top of funnel**: Drive massive adoption
- **Education**: Learn TrustWeave, build prototypes
- **SEO**: Generate content, tutorials, blog posts
- **Conversion**: Upgrade when hitting limits

### **Limits Designed to Trigger Upgrade:**
- ✅ **100 credentials/month** → Good for learning, tight for production
- ✅ **No blockchain anchoring** → Enterprise feature, drives upgrade
- ✅ **3 wallets** → Forces upgrade for multi-tenant apps
- ✅ **5 req/sec** → Prevents abuse, forces upgrade for load testing

---

## Tier 2: STARTER ($49/month)

### **Target Audience:**
- Funded startups (seed stage)
- Small SaaS products
- Internal corporate tools
- Agencies building for clients
- Production MVPs

### **Positioning:**
*"Production-ready SSI for growing startups"*

### **Pricing:**
```kotlin
object StarterTier {
    const val monthlyPrice = 49 // USD
    const val annualPrice = 470 // 2 months free: 49 * 10
    const val annualDiscount = 0.20 // 20% off
    
    // Identity Operations
    const val didsPerMonth = 100
    const val didResolutionsPerMonth = 10000
    const val didMethodsSupported = listOf("key", "web", "ion")
    
    // Credential Operations
    const val credentialsIssuedPerMonth = 1000
    const val credentialsVerifiedPerMonth = 10000
    const val credentialsStored = 5000
    
    // Wallet Operations
    const val walletsCreated = 25
    const val queriesPerMonth = 100000
    
    // Blockchain Anchoring
    const val blockchainAnchoring = true
    const val anchoringNetworks = listOf("algorand:testnet", "polygon:mumbai")
    const val anchoringOperationsPerMonth = 100
    const val anchoringCostIncluded = true // Gas fees included up to limit
    
    // Infrastructure
    const val apiCallsPerMonth = 100000
    const val storageGB = 5
    const val dataTransferGB = 10
    const val rateLimitPerSecond = 25
    const val availability = 0.99 // 99% uptime
    
    // Support & SLA
    const val support = "Email Support"
    const val sla = "99% uptime"
    const val responseTime = "2 business days"
    
    // Features
    const val webhooks = true
    const val webhooksEndpoints = 5
    const val customDomains = false
    const val teamCollaboration = true
    const val teamMembers = 3
    const val apiVersions = listOf("v1", "v2") // Current + previous
    const val auditLogs = true // 30 days retention
    const val whiteLabeling = false
    const val environments = listOf("production") // 1 environment
}
```

### **Value Proposition:**
- **10x capacity increase** over Free tier
- **Blockchain anchoring** on testnets (key differentiator)
- **Email support** (2-day response)
- **Team collaboration** (3 members)
- **Webhooks** for automation

### **Pricing Rationale:**
- **$49/month** = Sweet spot for startups
  - Comparable to: Auth0 Essentials ($35), Supabase Pro ($25), Clerk Pro ($25)
  - Lower than: AWS Cognito at scale (~$100+), Okta (~$150+)
- **Annual discount (20%)** = Improves cash flow, reduces churn

### **Expected Conversion:**
- **From Free:** When hitting 100 credentials/month or needing blockchain
- **From Pro:** Rare (designed as growth path)

---

## Tier 3: PRO ($149/month)

### **Target Audience:**
- Growth-stage startups (Series A+)
- Production SaaS applications
- Enterprise pilot projects
- Multi-tenant platforms
- High-traffic applications

### **Positioning:**
*"Scale your SSI infrastructure with confidence"*

### **Pricing:**
```kotlin
object ProTier {
    const val monthlyPrice = 149 // USD
    const val annualPrice = 1430 // 2 months free: 149 * 10 + (149 * 0.8 * 2)
    const val annualDiscount = 0.20 // 20% off
    
    // Identity Operations
    const val didsPerMonth = 1000
    const val didResolutionsPerMonth = 100000
    const val didMethodsSupported = listOf("key", "web", "ion", "ethr", "polygonid")
    
    // Credential Operations
    const val credentialsIssuedPerMonth = 10000
    const val credentialsVerifiedPerMonth = 100000
    const val credentialsStored = 50000
    
    // Wallet Operations
    const val walletsCreated = 250
    const val queriesPerMonth = 1000000
    
    // Blockchain Anchoring
    const val blockchainAnchoring = true
    const val anchoringNetworks = listOf(
        "algorand:mainnet", "algorand:testnet",
        "polygon:mainnet", "polygon:mumbai",
        "ethereum:mainnet", "ethereum:sepolia"
    )
    const val anchoringOperationsPerMonth = 1000
    const val anchoringCostIncluded = true // Up to $100/month gas fees
    
    // Infrastructure
    const val apiCallsPerMonth = 1000000
    const val storageGB = 50
    const val dataTransferGB = 100
    const val rateLimitPerSecond = 100
    const val availability = 0.995 // 99.5% uptime
    
    // Support & SLA
    const val support = "Priority Email + Chat Support"
    const val sla = "99.5% uptime"
    const val responseTime = "4 hours (business hours)"
    const val dedicatedSlackChannel = false
    
    // Features
    const val webhooks = true
    const val webhooksEndpoints = 25
    const val customDomains = true
    const val customDomainsCount = 3
    const val teamCollaboration = true
    const val teamMembers = 10
    const val apiVersions = listOf("v1", "v2", "v3") // All stable versions
    const val auditLogs = true // 90 days retention
    const val whiteLabeling = true
    const val environments = listOf("development", "staging", "production")
    const val advancedAnalytics = true
    const val customRetentionPolicies = true
    const val ipWhitelisting = true
}
```

### **Value Proposition:**
- **100x capacity** over Free tier
- **10x capacity** over Starter tier
- **Mainnet blockchain anchoring** (Algorand, Polygon, Ethereum)
- **Priority support** (4-hour response)
- **Multiple environments** (dev/staging/prod)
- **White-labeling** (brand as your own)
- **Advanced analytics** (usage dashboards)

### **Pricing Rationale:**
- **$149/month** = Standard SaaS scale pricing
  - Comparable to: Auth0 Professional ($240), Supabase Pro ($50-200), Clerk Pro ($75-200)
  - Positioned for companies with $50K-$500K ARR
- **3x price jump** from Starter = Justified by 10x capacity + enterprise features

### **Expected Conversion:**
- **From Starter:** When exceeding 1000 credentials/month or needing mainnet anchoring
- **From Enterprise:** Companies not needing custom SLAs

---

## Tier 4: ENTERPRISE (Custom Pricing)

### **Target Audience:**
- Large enterprises (1000+ employees)
- Government agencies
- Financial institutions
- Healthcare organizations
- Fortune 500 companies
- Enterprises with compliance requirements

### **Positioning:**
*"Enterprise-grade SSI with unlimited scale and dedicated support"*

### **Pricing:**
```kotlin
object EnterpriseTier {
    const val startingPrice = 1500 // USD/month minimum
    const val typicalRange = 1500..10000 // USD/month
    const val annualContract = true // Minimum 1 year
    const val volumeDiscounts = true
    
    // Identity Operations
    const val didsPerMonth = Int.MAX_VALUE // Unlimited
    const val didResolutionsPerMonth = Int.MAX_VALUE
    const val didMethodsSupported = listOf("ALL") // All supported methods
    
    // Credential Operations
    const val credentialsIssuedPerMonth = Int.MAX_VALUE
    const val credentialsVerifiedPerMonth = Int.MAX_VALUE
    const val credentialsStored = Int.MAX_VALUE
    
    // Wallet Operations
    const val walletsCreated = Int.MAX_VALUE
    const val queriesPerMonth = Int.MAX_VALUE
    
    // Blockchain Anchoring
    const val blockchainAnchoring = true
    const val anchoringNetworks = listOf("ALL") // All chains + custom chains
    const val anchoringOperationsPerMonth = Int.MAX_VALUE
    const val anchoringCostNegotiated = true // Custom gas fee handling
    
    // Infrastructure
    const val apiCallsPerMonth = Int.MAX_VALUE
    const val storageGB = Int.MAX_VALUE // Or dedicated storage
    const val dataTransferGB = Int.MAX_VALUE
    const val rateLimitPerSecond = Int.MAX_VALUE // Or custom limits
    const val availability = 0.999 // 99.9% uptime SLA
    
    // Deployment Options
    const val cloudHosted = true
    const val onPremise = true // Self-hosted option
    const val hybridDeployment = true
    const val dedicatedInfrastructure = true // Isolated cluster
    const val privateCloud = true // AWS/Azure/GCP dedicated VPC
    
    // Support & SLA
    const val support = "Dedicated Support Engineer + 24/7 Coverage"
    const val sla = "99.9% uptime (contractual)"
    const val responseTime = "1 hour (24/7/365)"
    const val dedicatedSlackChannel = true
    const val quarterlBusinessReviews = true
    const val technicalAccountManager = true
    
    // Features
    const val webhooks = true
    const val webhooksEndpoints = Int.MAX_VALUE
    const val customDomains = true
    const val customDomainsCount = Int.MAX_VALUE
    const val teamCollaboration = true
    const val teamMembers = Int.MAX_VALUE // Unlimited
    const val apiVersions = listOf("ALL") // All versions + early access
    const val auditLogs = true // Unlimited retention or custom
    const val whiteLabeling = true
    const val environments = Int.MAX_VALUE // Unlimited environments
    const val advancedAnalytics = true
    const val customRetentionPolicies = true
    const val ipWhitelisting = true
    const val ssoIntegration = true // SAML, OIDC
    const val advancedSecurity = true // Penetration testing, SOC2
    
    // Enterprise Features
    const val customIntegrations = true // Build custom adapters
    const val priorityFeatureRequests = true
    const val earlyAccessProgram = true
    const val trainingServices = true
    const val professionalServices = true
    const val migrationAssistance = true
    const val complianceSupport = true // GDPR, SOC2, HIPAA, FedRAMP
    const val legalIndemnification = true
    const val upfrontCommitment = true // Minimum annual commitment
}
```

### **Pricing Structure:**

#### **Base Enterprise Tier:**
```
Minimum: $1,500/month ($18,000/year)

Includes:
- Unlimited API calls
- Unlimited DIDs/credentials
- All blockchain networks
- 99.9% SLA
- Dedicated support engineer
- On-premise deployment option
- SAML/SSO integration
```

#### **Volume-Based Add-Ons:**
```kotlin
object EnterpriseAddOns {
    // High-Volume Anchoring
    data class BlockchainAnchoringPackage(
        val operations: Int = 10000, // per month
        val price: Int = 500 // USD/month
    )
    
    // Dedicated Infrastructure
    data class DedicatedCluster(
        val cores: Int = 8,
        val ramGB: Int = 32,
        val storageGB: Int = 500,
        val price: Int = 1000 // USD/month
    )
    
    // Professional Services
    data class ProfessionalServices(
        val hoursPerMonth: Int = 20,
        val hourlyRate: Int = 250, // USD/hour
        val monthlyPackagePrice: Int = 4000 // 20% discount for package
    )
    
    // Training
    data class TrainingPackage(
        val developers: Int = 10,
        val days: Int = 2,
        val price: Int = 5000 // USD one-time
    )
    
    // Compliance Certification Assistance
    data class CompliancePackage(
        val certifications: List<String> = listOf("SOC2", "HIPAA"),
        val price: Int = 10000 // USD one-time
    )
}
```

### **Typical Enterprise Deal Examples:**

#### **Example 1: Mid-Size Enterprise ($3,500/month)**
```
Base Enterprise:              $1,500/month
Dedicated Cluster:            $1,000/month
High-Volume Anchoring:        $  500/month
20h/month Professional Svcs:  $  500/month (discounted)
                              ─────────────
Total:                        $3,500/month
Annual Contract:              $42,000/year
```

#### **Example 2: Large Enterprise ($8,000/month)**
```
Base Enterprise:              $1,500/month
Dedicated Cluster (2x):       $2,000/month
High-Volume Anchoring (5x):   $2,500/month
40h/month Professional Svcs:  $1,000/month (discounted)
SOC2 Compliance Support:      $1,000/month (amortized)
                              ─────────────
Total:                        $8,000/month
Annual Contract:              $96,000/year
```

### **Value Proposition:**
- **Unlimited everything** (no usage anxiety)
- **Contractual SLA** (99.9% uptime)
- **24/7 support** (1-hour response)
- **On-premise option** (critical for regulated industries)
- **Compliance assistance** (SOC2, HIPAA, FedRAMP)
- **Strategic partnership** (TAM, QBRs, roadmap influence)

### **Pricing Rationale:**
- **$1,500/month minimum** = Establishes enterprise positioning
  - Comparable to: Auth0 Enterprise (~$2K+), Okta Workforce Identity (~$2K+)
  - Lower than: Hyperledger Fabric/Indy hosting (~$5K+)
- **Annual contracts** = Predictable revenue, lower churn
- **Custom pricing** = Capture value from large deployments

---

## Usage-Based Overage Pricing

### **Philosophy:**
*"Never block customers - charge fairly for overage"*

### **Overage Rates (Applied Monthly):**

```kotlin
object OverageRates {
    // Identity Operations
    data class DidOverages(
        val didsCreated: Double = 0.10, // USD per DID
        val didResolutions: Double = 0.001 // USD per 100 resolutions
    )
    
    // Credential Operations
    data class CredentialOverages(
        val credentialsIssued: Double = 0.05, // USD per credential
        val credentialsVerified: Double = 0.005 // USD per 100 verifications
    )
    
    // Blockchain Anchoring
    data class AnchoringOverages(
        val anchoringOperation: Double = 1.00, // USD per operation (+ actual gas)
        val gasFeesPassthrough: Boolean = true // Pass actual gas costs
    )
    
    // Infrastructure
    data class InfrastructureOverages(
        val apiCalls: Double = 0.01, // USD per 1000 calls
        val storage: Double = 0.50, // USD per GB/month
        val dataTransfer: Double = 0.12 // USD per GB
    )
    
    // Example Calculations
    fun calculateOverage(tier: String, usage: Usage): Double {
        return when (tier) {
            "STARTER" -> {
                var overage = 0.0
                if (usage.credentialsIssued > 1000) {
                    overage += (usage.credentialsIssued - 1000) * 0.05
                }
                if (usage.apiCalls > 100000) {
                    overage += ((usage.apiCalls - 100000) / 1000.0) * 0.01
                }
                overage
            }
            "PRO" -> {
                // Similar calculation for Pro tier
                0.0 // placeholder
            }
            else -> 0.0
        }
    }
}

data class Usage(
    val credentialsIssued: Int,
    val credentialsVerified: Int,
    val apiCalls: Int,
    val storageGB: Double
)
```

### **Overage Communication Strategy:**

#### **Email Alerts:**
```
Subject: 🚨 TrustWeave: You've reached 80% of your credential limit

Hi [Customer],

You've issued 800 out of 1,000 credentials this month on your Starter plan.

What happens when you hit 1,000?
✅ Your service continues uninterrupted
✅ Overages charged at $0.05/credential
✅ Or upgrade to Pro for 10x capacity at $149/month

[View Usage Dashboard] [Upgrade to Pro]

Questions? Reply to this email.
```

#### **Dashboard Warning:**
```
⚠️ Usage Alert
You're at 900/1,000 credentials this month.

Options:
1. Continue with overage billing ($0.05/credential)
2. Upgrade to Pro (10,000 credentials/month) ← Recommended
3. Wait until next billing cycle resets
```

---

## Add-Ons & Upsells

### **Cross-Tier Add-Ons:**

```kotlin
object AddOns {
    // Additional Blockchain Networks
    data class CustomBlockchainIntegration(
        val setupFee: Int = 2500, // One-time
        val monthlyFee: Int = 200, // Ongoing
        val availableFor: List<String> = listOf("PRO", "ENTERPRISE")
    )
    
    // Extended Audit Log Retention
    data class ExtendedAuditLogs(
        val retentionDays: Int = 365, // 1 year
        val price: Int = 50, // USD/month
        val availableFor: List<String> = listOf("STARTER", "PRO")
    )
    
    // Advanced DID Methods
    data class PremiumDidMethods(
        val methods: List<String> = listOf("did:ion", "did:ethr", "did:polygonid"),
        val price: Int = 25, // USD/month
        val availableFor: List<String> = listOf("STARTER")
    )
    
    // Dedicated Support
    data class DedicatedSupport(
        val slackChannel: Boolean = true,
        val responseTime: String = "4 hours",
        val price: Int = 500, // USD/month
        val availableFor: List<String> = listOf("PRO")
    )
    
    // Additional Team Members
    data class ExtraTeamMembers(
        val membersPerPack: Int = 5,
        val price: Int = 15, // USD/month per pack
        val availableFor: List<String> = listOf("STARTER", "PRO")
    )
    
    // White Labeling
    data class WhiteLabelingAddOn(
        val customBranding: Boolean = true,
        val removeBranding: Boolean = true,
        val price: Int = 100, // USD/month
        val availableFor: List<String> = listOf("STARTER")
    )
}
```

### **Professional Services Menu:**

```kotlin
object ProfessionalServices {
    data class MigrationService(
        val description: String = "Migrate from existing SSI system to TrustWeave",
        val estimatedHours: IntRange = 40..80,
        val hourlyRate: Int = 250,
        val fixedPrice: Int = 15000 // Average
    )
    
    data class CustomIntegration(
        val description: String = "Build custom blockchain adapter or DID method",
        val estimatedHours: IntRange = 80..160,
        val hourlyRate: Int = 250,
        val fixedPrice: Int = 30000 // Average
    )
    
    data class TrainingWorkshop(
        val description: String = "2-day onsite training for up to 20 developers",
        val duration: Int = 2, // days
        val price: Int = 8000,
        val travel: String = "Additional if outside major cities"
    )
    
    data class ArchitectureReview(
        val description: String = "Review SSI architecture and provide recommendations",
        val duration: Int = 1, // day
        val price: Int = 3000
    )
}
```

---

## Self-Hosted vs SaaS Positioning

### **Strategic Decision Matrix:**

```kotlin
object SelfHostedVsSaaS {
    data class SaaSBenefits(
        val benefits: List<String> = listOf(
            "Zero infrastructure management",
            "Automatic updates & security patches",
            "Built-in monitoring & alerting",
            "Global CDN for low latency",
            "Instant scaling",
            "99.9% SLA",
            "24/7 support"
        ),
        val bestFor: List<String> = listOf(
            "Startups wanting to move fast",
            "Teams without DevOps expertise",
            "Multi-region deployments",
            "Variable workloads"
        )
    )
    
    data class SelfHostedBenefits(
        val benefits: List<String> = listOf(
            "Full data control & sovereignty",
            "Custom security policies",
            "Air-gapped environments",
            "Regulatory compliance (HIPAA, FedRAMP)",
            "Cost savings at very high scale",
            "No vendor lock-in"
        ),
        val bestFor: List<String> = listOf(
            "Enterprises with strict compliance requirements",
            "Government agencies",
            "Financial institutions",
            "Companies with existing Kubernetes infrastructure",
            "Very high volume (millions of transactions/month)"
        ),
        val requirements: List<String> = listOf(
            "Commercial License ($5,000-$50,000/year)",
            "DevOps team for deployment",
            "Infrastructure costs (AWS/Azure/GCP)",
            "Security & compliance management"
        )
    )
}
```

### **Self-Hosted Commercial License Pricing:**

```kotlin
object SelfHostedLicensing {
    data class BasicLicense(
        val price: Int = 5000, // USD/year
        val cores: Int = 8,
        val environments: Int = 2, // Production + Staging
        val support: String = "Email support (2 business days)",
        val updates: Boolean = true,
        val sourceCode: Boolean = false
    )
    
    data class ProfessionalLicense(
        val price: Int = 15000, // USD/year
        val cores: Int = 32,
        val environments: Int = Int.MAX_VALUE,
        val support: String = "Priority support (4 hours)",
        val updates: Boolean = true,
        val sourceCode: Boolean = false,
        val indemnification: Boolean = true
    )
    
    data class EnterpriseLicense(
        val price: Int = 50000, // USD/year (negotiable)
        val cores: Int = Int.MAX_VALUE,
        val environments: Int = Int.MAX_VALUE,
        val support: String = "24/7 dedicated support (1 hour SLA)",
        val updates: Boolean = true,
        val sourceCode: Boolean = true, // Access to source modifications
        val indemnification: Boolean = true,
        val customModifications: Boolean = true,
        val professionalServices: Boolean = true
    )
}
```

### **Conversion Messaging:**

#### **SaaS → Self-Hosted (Upsell):**
```
"Need more control? Deploy TrustWeave on-premise"

Perfect for:
✓ Regulatory compliance (HIPAA, FedRAMP)
✓ Air-gapped environments
✓ Custom security policies

Starting at $5,000/year + infrastructure costs

[Contact Sales]
```

#### **Self-Hosted → SaaS (Downsell/Retain):**
```
"Simplify your infrastructure with TrustWeave Cloud"

Let us handle:
✓ Deployments & updates
✓ Monitoring & scaling
✓ Security patches
✓ 99.9% uptime SLA

Starting at $49/month (no infrastructure costs!)

[Try TrustWeave Cloud]
```

---

## Pricing Psychology & Strategy

### **1. Anchor Pricing (Free Tier):**
```
FREE tier anchors value perception:
- $0 → $49 = "affordable"
- $49 → $149 = "reasonable for growth"
- $149 → $1,500 = "enterprise investment"

Without free tier:
- $49 feels expensive for trial
```

### **2. Good-Better-Best:**
```
STARTER:  "Good" - Basic production needs
PRO:      "Better" - Recommended (highlighted)
ENTERPRISE: "Best" - Ultimate solution

Research shows 60% choose middle tier when highlighted.
```

### **3. Annual Discount (20%):**
```
Monthly: $149 × 12 = $1,788/year
Annual:  $1,430/year (save $358)

Benefits:
✓ Improves cash flow
✓ Reduces monthly churn
✓ Increases LTV
```

### **4. Overage = Safety Net:**
```
"We'll never turn off your service"

Psychological safety → Higher conversions
Better than hard limits → Reduces churn
```

### **5. Enterprise "Call Us" Pricing:**
```
No price = creates conversation
Forces sales qualification
Allows custom negotiation
```

---

## Competitive Positioning

### **Competitive Pricing Analysis:**

```kotlin
object CompetitorPricing {
    data class Competitor(
        val name: String,
        val freeTier: Boolean,
        val starterPrice: Int?, // USD/month
        val proPrice: Int?,
        val enterprisePrice: String
    )
    
    val competitors = listOf(
        Competitor("Auth0", true, 35, 240, "Custom (~$2K+)"),
        Competitor("Okta", false, null, null, "~$2K-$15K/month"),
        Competitor("Clerk", true, 25, 75, "Custom"),
        Competitor("Supabase", true, 25, 50, "Custom"),
        Competitor("AWS Cognito", true, null, null, "Pay-per-use (~$100-$1K)"),
        Competitor("walt.id", true, null, null, "Open-source + Enterprise support"),
        Competitor("Veramo", true, null, null, "Open-source only"),
        Competitor("Trinsic", true, 49, null, "Custom (~$500+)")
    )
}
```

### **TrustWeave Positioning:**

```
┌─────────────────────────────────────────────────┐
│  PRICE vs VALUE MATRIX                          │
│                                                 │
│  High Price                                     │
│      ↑                                          │
│      │         Okta 💰                          │
│      │         Auth0 💰                         │
│      │                                          │
│      │                  TrustWeave Pro ⭐        │
│      │         Trinsic                          │
│      │    TrustWeave Starter                      │
│      │    Clerk / Supabase                      │
│      │                                          │
│      │    AWS Cognito                           │
│      │                                          │
│      │    walt.id / Veramo (OSS)                │
│      └─────────────────────────────→            │
│   Low Value              High Value             │
│                                                 │
│  TrustWeave = High Value, Mid-Market Price        │
└─────────────────────────────────────────────────┘
```

### **Differentiation:**
- ✅ **Lower than Okta/Auth0** (enterprise identity leaders)
- ✅ **Comparable to Clerk/Supabase** (modern SaaS)
- ✅ **Higher than open-source alone** (justified by SaaS value)
- ✅ **Unique: Blockchain anchoring** (no competitors offer this)

---

## Discounting Strategy

### **When to Discount:**

```kotlin
object DiscountingRules {
    data class AnnualDiscount(
        val discount: Double = 0.20, // 20% off
        val alwaysAvailable: Boolean = true,
        val messaging: String = "Save 20% with annual billing"
    )
    
    data class StartupProgram(
        val eligibility: String = "Funded startups < $1M ARR",
        val discount: Double = 0.50, // 50% off for 1 year
        val duration: Int = 12, // months
        val requiresApplication: Boolean = true
    )
    
    data class NonProfitProgram(
        val eligibility: String = "Registered 501(c)(3) or equivalent",
        val discount: Double = 0.50, // 50% off
        val duration: String = "Ongoing",
        val requiresApplication: Boolean = true
    )
    
    data class EducationProgram(
        val eligibility: String = "Students, teachers, researchers",
        val discount: Double = 1.0, // 100% off (free Pro tier)
        val duration: String = "During education",
        val requiresVerification: Boolean = true
    )
    
    data class MigrationIncentive(
        val eligibility: String = "Migrating from competitor",
        val discount: Double = 0.30, // 30% off for 6 months
        val duration: Int = 6,
        val requires: String = "Proof of existing service"
    )
    
    // NEVER discount:
    val noDiscountRules = listOf(
        "Never discount in first sales call (shows desperation)",
        "Never permanent discounts (trains customer to expect)",
        "Never discount below 30% without exec approval",
        "Never discount Enterprise base price (custom pricing already)"
    )
}
```

### **Launch Promotions:**

```kotlin
object LaunchPromotions {
    data class EarlyAdopterProgram(
        val window: String = "First 100 customers",
        val benefit: String = "Lifetime 30% discount on any paid tier",
        val badge: String = "Early Adopter 🌟",
        val perks: List<String> = listOf(
            "Lifetime 30% discount",
            "Early access to new features",
            "Direct line to founders",
            "Influence product roadmap",
            "Featured in case studies"
        )
    )
    
    data class LaunchWeekSpecial(
        val window: String = "First week only",
        val benefit: String = "First 3 months free on Annual Pro plan",
        val messaging: String = "Launch Week Special: 3 months free + 20% off = 35% savings"
    )
}
```

---

## Revenue Projections

### **Conservative Projection (Year 1):**

```kotlin
object Year1Projections {
    data class Customers(
        val free: Int = 500,
        val starter: Int = 15, // 3% conversion
        val pro: Int = 5, // 1% conversion
        val enterprise: Int = 1
    )
    
    fun calculateMRR(): Int {
        return (
            Customers().starter * 49 +
            Customers().pro * 149 +
            Customers().enterprise * 3000 // Avg enterprise deal
        )
    }
    // MRR = $735 + $745 + $3,000 = $4,480
    // ARR = $53,760
    
    data class Overages(
        val estimatedMonthly: Int = 200 // From starter/pro overages
    )
    
    data class ProfessionalServices(
        val estimatedQuarterly: Int = 10000 // Custom integrations, training
    )
    
    fun totalAnnualRevenue(): Int {
        return calculateMRR() * 12 + 
               Overages().estimatedMonthly * 12 + 
               ProfessionalServices().estimatedQuarterly * 4
    }
    // Total = $53,760 + $2,400 + $40,000 = ~$96,160
}
```

### **Optimistic Projection (Year 1):**

```kotlin
object Year1OptimisticProjections {
    data class Customers(
        val free: Int = 1000,
        val starter: Int = 40, // 4% conversion (with good marketing)
        val pro: Int = 15, // 1.5% conversion
        val enterprise: Int = 3
    )
    
    fun calculateMRR(): Int {
        return (
            Customers().starter * 49 +
            Customers().pro * 149 +
            Customers().enterprise * 5000 // Higher avg deal
        )
    }
    // MRR = $1,960 + $2,235 + $15,000 = $19,195
    // ARR = $230,340
    
    fun totalAnnualRevenue(): Int {
        return calculateMRR() * 12 + 5000 * 12 + 80000
        // Overages + Services
    }
    // Total = $230,340 + $60,000 + $80,000 = ~$370,340
}
```

### **Year 3 Target (Scaled):**

```kotlin
object Year3Projections {
    data class Customers(
        val free: Int = 5000,
        val starter: Int = 200,
        val pro: Int = 75,
        val enterprise: Int = 15
    )
    
    fun calculateMRR(): Int {
        return (
            Customers().starter * 49 +
            Customers().pro * 149 +
            Customers().enterprise * 7000
        )
    }
    // MRR = $9,800 + $11,175 + $105,000 = $125,975
    // ARR = $1,511,700
    
    // With overages, services, self-hosted licenses:
    // Total ARR Target: $2,000,000
}
```

---

## Pricing Page Copy

### **Headline:**
```
# Pricing
## Start free. Scale as you grow. Go enterprise when ready.

All plans include W3C-compliant DIDs, Verifiable Credentials, 
and Wallet APIs. No credit card required for Free tier.
```

### **CTA Buttons:**
```kotlin
object CTAButtons {
    val free = "Start Free → No credit card"
    val starter = "Start 14-day trial → Cancel anytime"
    val pro = "Start 14-day trial → Cancel anytime"
    val enterprise = "Contact Sales → Custom demo"
}
```

### **FAQ Section:**

```markdown
## Frequently Asked Questions

### Can I change plans anytime?
Yes! Upgrade or downgrade anytime. Prorated credits applied immediately.

### What happens if I exceed my plan limits?
Your service continues without interruption. We charge fair overage rates 
and send alerts at 80% usage. Or upgrade to the next tier anytime.

### Do you offer discounts for startups or nonprofits?
Yes! We offer 50% off for funded startups (<$1M ARR) and registered 
nonprofits. [Apply here](https://geoknoesis.com/programs)

### Can I self-host instead of using the cloud?
Absolutely. TrustWeave is open source (AGPL v3.0). For commercial 
self-hosting, you need a commercial license starting at $5,000/year.
[Learn more](https://geoknoesis.com/self-hosted)

### What's included in Enterprise support?
24/7 dedicated support with 1-hour SLA, technical account manager, 
quarterly business reviews, custom SLA, and priority feature requests.

### How does blockchain anchoring work?
We anchor credential digests to blockchains like Algorand, Ethereum, 
and Polygon for tamper-proof verification. Gas fees included up to 
plan limits. [Technical details](https://docs.geoknoesis.com/anchoring)

### Can I try Pro or Enterprise features first?
Yes! Start a 14-day free trial of any paid plan. No credit card required.
```

---

## Implementation Checklist

### **Phase 1: MVP (Week 1-2)**
- [ ] Set up Stripe account
- [ ] Create products & prices in Stripe
- [ ] Implement basic API key generation
- [ ] Build usage tracking (simple counters)
- [ ] Create pricing page (static)

### **Phase 2: Billing (Week 3-4)**
- [ ] Integrate Stripe Checkout
- [ ] Implement subscription management
- [ ] Build usage dashboards
- [ ] Create overage calculation logic
- [ ] Set up billing alerts (email)

### **Phase 3: Enforcement (Week 5-6)**
- [ ] Implement rate limiting per tier
- [ ] Add feature flags per tier
- [ ] Build upgrade prompts in dashboard
- [ ] Create downgrade flow
- [ ] Test all pricing scenarios

### **Phase 4: Polish (Week 7-8)**
- [ ] Add usage analytics
- [ ] Build admin portal
- [ ] Create sales CRM integration
- [ ] Set up revenue dashboards (ChartMogul/Baremetrics)
- [ ] Launch pricing A/B tests

---

## Success Metrics & KPIs

### **Key Metrics to Track:**

```kotlin
object PricingKPIs {
    data class ConversionMetrics(
        val freeToStarterConversion: Double = 0.03, // Target: 3-5%
        val starterToProConversion: Double = 0.10, // Target: 10-15%
        val proToEnterpriseConversion: Double = 0.10, // Target: 10-15%
        val trialToConversion: Double = 0.25 // Target: 25%+
    )
    
    data class RevenueMetrics(
        val mrr: Double, // Monthly Recurring Revenue
        val arr: Double, // Annual Recurring Revenue
        val averageRevenuePerUser: Double, // ARPU
        val customerLifetimeValue: Double, // LTV
        val customerAcquisitionCost: Double, // CAC
        val ltvCacRatio: Double // Target: 3:1+
    )
    
    data class RetentionMetrics(
        val monthlyChurn: Double = 0.05, // Target: <5%
        val netRevenueRetention: Double = 1.20, // Target: 120%+
        val expansionRevenue: Double // Upgrades - Downgrades
    )
    
    data class UsageMetrics(
        val averageAPICallsPerCustomer: Int,
        val averageCredentialsPerCustomer: Int,
        val featureAdoptionRates: Map<String, Double>,
        val overageFrequency: Double // % of customers with overages
    )
}
```

---

## Summary & Recommendations

### **Pricing Strategy TL;DR:**

| Tier | Price | Target | Key Value |
|------|-------|--------|-----------|
| **FREE** | $0 | Developers, OSS projects | Learn & prototype |
| **STARTER** | $49/mo | Funded startups, MVPs | Production-ready + blockchain |
| **PRO** | $149/mo | Growth companies | Scale + white-label |
| **ENTERPRISE** | $1,500+/mo | Large enterprises | Unlimited + dedicated support |

### **Strategic Recommendations:**

1. ✅ **Launch with all 4 tiers immediately**
   - Free drives adoption
   - Paid tiers prove business model
   
2. ✅ **Be generous with Free tier**
   - 100 credentials/month is enough to learn
   - Tight enough to force production upgrades
   
3. ✅ **Make Starter ($49) attractive**
   - Blockchain anchoring only in paid tiers
   - This is your main conversion target
   
4. ✅ **Highlight Pro ($149) as "Popular"**
   - Most companies will land here
   - Best margin tier
   
5. ✅ **Use overage pricing for safety**
   - Never block customers
   - Overages drive upgrade conversations
   
6. ✅ **Annual discount (20%) always available**
   - Improves cash flow
   - Reduces churn
   
7. ✅ **Enterprise pricing starts at $1,500**
   - Sets floor for enterprise deals
   - Custom pricing above that
   
8. ✅ **Self-hosted licenses separate**
   - Doesn't compete with SaaS
   - Captures regulated market

### **Next Steps:**

1. Set up Stripe with these exact tiers
2. Build minimal billing dashboard
3. Launch with pricing A/B tests:
   - Test $39 vs $49 for Starter
   - Test $129 vs $149 for Pro
4. Track conversions religiously
5. Iterate monthly based on data

---

## Appendix: Pricing Psychology Research

### **Key Insights Applied:**

1. **Charm Pricing ($49 vs $50)**
   - Research: 1% price difference = 20% conversion lift
   - Applied: $49 and $149 (not $50/$150)

2. **Anchoring Effect**
   - Free tier makes $49 feel cheap
   - $1,500 enterprise makes $149 feel reasonable

3. **Good-Better-Best (3 options)**
   - Research: 60% choose middle option when highlighted
   - Applied: Highlight PRO tier

4. **Annual Discount (20%)**
   - Research: Sweet spot is 15-20% for annual
   - Too little (10%) = not motivating
   - Too much (50%) = devalues product

5. **Unlimited =/= Infinite**
   - Enterprise "unlimited" has soft limits
   - Prevents abuse while removing anxiety

---

**Ready to implement?** Let me know if you want me to:
- Build the Stripe setup code
- Create the pricing page HTML/React
- Design the usage dashboard
- Write the billing logic

Let's make this happen! 🚀


