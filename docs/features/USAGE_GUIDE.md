# Features Usage Guide

This guide explains how to use VeriCore's feature plugins.

## Overview

All features are implemented as standalone plugins that can be instantiated and used independently. They follow VeriCore's plugin architecture and can be integrated into your application as needed.

## 1. Audit Logging

Track all operations with immutable audit logs.

```kotlin
import com.geoknoesis.vericore.audit.AuditLogger
import com.geoknoesis.vericore.audit.InMemoryAuditLogger
import com.geoknoesis.vericore.audit.AuditEvent

val auditLogger: AuditLogger = InMemoryAuditLogger()

// Log an event
auditLogger.logEvent(
    AuditEvent(
        id = UUID.randomUUID().toString(),
        timestamp = Instant.now(),
        actor = "did:key:alice",
        action = "ISSUE_CREDENTIAL",
        target = "credential-123",
        status = "SUCCESS",
        details = mapOf("credentialType" to "EducationCredential")
    )
)

// Query events
val events = auditLogger.getEvents(
    startTime = Instant.now().minusSeconds(3600),
    endTime = Instant.now(),
    action = "ISSUE_CREDENTIAL"
)
```

## 2. Metrics & Telemetry

Collect performance and usage metrics.

```kotlin
import com.geoknoesis.vericore.metrics.MetricsCollector
import com.geoknoesis.vericore.metrics.InMemoryMetricsCollector

val metrics: MetricsCollector = InMemoryMetricsCollector()

// Record metrics
metrics.increment("credentials.issued")
metrics.recordLatency("credential.issue", 150L) // milliseconds

// Get metrics
val counter = metrics.getMetric("credentials.issued")
val latency = metrics.getMetric("credential.issue")
println("Issued: ${counter?.value}, Avg latency: ${latency?.average}ms")
```

## 3. QR Code Generation

Generate QR codes for credential sharing.

```kotlin
import com.geoknoesis.vericore.qrcode.QrCodeGenerator
import com.geoknoesis.vericore.qrcode.ZxingQrCodeGenerator
import com.geoknoesis.vericore.qrcode.QrCodeFormat

val qrGenerator: QrCodeGenerator = ZxingQrCodeGenerator()

// Generate QR code data for credential
val qrData = qrGenerator.generateForCredential(
    credential = credential,
    format = QrCodeFormat.JSON
)

// Generate QR code image (PNG bytes)
val qrImage = qrGenerator.generateQrCode(qrData, 300, 300)

// Generate deep link
val deepLink = qrGenerator.generateDeepLink(
    baseUrl = "https://example.com/verify",
    credential = credential
)
```

## 4. Notifications

Send push notifications and webhooks for credential events.

```kotlin
import com.geoknoesis.vericore.notifications.NotificationService
import com.geoknoesis.vericore.notifications.InMemoryNotificationService

val notifications: NotificationService = InMemoryNotificationService()

// Send push notification
notifications.sendPushNotification(
    recipient = "did:key:alice",
    title = "New Credential",
    body = "You have received a new EducationCredential",
    data = mapOf("credentialId" to "cred-123")
)

// Send webhook
notifications.sendWebhook(
    url = "https://example.com/webhook",
    event = "CREDENTIAL_RECEIVED",
    payload = mapOf("credentialId" to "cred-123")
)
```

## 5. Credential Versioning

Track credential versions and rollback if needed.

```kotlin
import com.geoknoesis.vericore.versioning.CredentialVersioning
import com.geoknoesis.vericore.versioning.InMemoryCredentialVersioning

val versioning: CredentialVersioning = InMemoryCredentialVersioning()

// Save version
versioning.saveVersion(
    credentialId = "cred-123",
    version = 1,
    credential = credential,
    changeReason = "Initial issuance"
)

// Get version history
val history = versioning.getHistory("cred-123")

// Rollback to previous version
val rolledBack = versioning.rollback("cred-123", targetVersion = 1)
```

## 6. Backup & Recovery

Export and import credentials.

```kotlin
import com.geoknoesis.vericore.backup.CredentialBackup
import com.geoknoesis.vericore.backup.InMemoryCredentialBackup

val backup: CredentialBackup = InMemoryCredentialBackup()

// Export credentials
val exportData = backup.exportCredentials(
    wallet = wallet,
    credentialIds = listOf("cred-1", "cred-2")
)

// Import credentials
backup.importCredentials(
    wallet = wallet,
    backupData = exportData
)
```

## 7. Expiration Management

Monitor and manage expiring credentials.

```kotlin
import com.geoknoesis.vericore.expiration.ExpirationManager
import com.geoknoesis.vericore.expiration.InMemoryExpirationManager

val expiration: ExpirationManager = InMemoryExpirationManager()

// Monitor expirations
expiration.monitorExpirations(
    wallet = wallet,
    onExpiring = { credential ->
        println("Credential ${credential.id} expires soon!")
    },
    onExpired = { credential ->
        println("Credential ${credential.id} has expired!")
    }
)

// Renew credential
val renewed = expiration.renewCredential(
    credentialId = "cred-123",
    newExpirationDate = Instant.now().plusSeconds(86400 * 365)
)
```

## 8. Analytics & Reporting

Generate analytics reports.

```kotlin
import com.geoknoesis.vericore.analytics.AnalyticsService
import com.geoknoesis.vericore.analytics.InMemoryAnalyticsService
import com.geoknoesis.vericore.analytics.ReportPeriod

val analytics: AnalyticsService = InMemoryAnalyticsService()

// Record events
analytics.recordEvent("credential.issued", mapOf("type" to "EducationCredential"))
analytics.recordEvent("credential.verified", mapOf("issuer" to "did:key:university"))

// Get report
val report = analytics.getReport(ReportPeriod.DAILY)
println("Issued: ${report.issuanceCount}")
println("Verified: ${report.verificationCount}")
println("Top issuers: ${report.topIssuers}")
```

## 9. OIDC4VCI

OpenID Connect for Verifiable Credential Issuance.

```kotlin
import com.geoknoesis.vericore.oidc4vci.Oidc4VciService
import com.geoknoesis.vericore.oidc4vci.InMemoryOidc4VciService

val oidc4vci: Oidc4VciService = InMemoryOidc4VciService()

// Issue credential via OIDC4VCI
val credential = oidc4vci.issueCredential(
    issuerEndpoint = "https://issuer.example.com",
    credentialOffer = credentialOffer,
    accessToken = accessToken
)
```

## 10. DIDComm v2

DIDComm credential exchange protocol.

```kotlin
import com.geoknoesis.vericore.didcomm.DidCommService
import com.geoknoesis.vericore.didcomm.InMemoryDidCommService
import com.geoknoesis.vericore.didcomm.DidCommMessage

val didcomm: DidCommService = InMemoryDidCommService()

// Send credential offer
val offerMessage = DidCommMessage(
    id = UUID.randomUUID().toString(),
    type = DidCommMessageTypes.CREDENTIAL_OFFER,
    from = "did:key:issuer",
    to = listOf("did:key:holder"),
    body = buildJsonObject { /* offer data */ }
)
didcomm.sendMessage(offerMessage)

// Receive and process message
val received = didcomm.receiveMessage(messageJson)
```

## 11. CHAPI

Credential Handler API support.

```kotlin
import com.geoknoesis.vericore.chapi.ChapiService
import com.geoknoesis.vericore.chapi.InMemoryChapiService

val chapi: ChapiService = InMemoryChapiService()

// Handle credential request
val response = chapi.handleGetRequest(
    request = chapiRequest,
    wallet = wallet
)

// Handle credential storage
chapi.handleStoreRequest(
    request = storeRequest,
    wallet = wallet
)
```

## 12. Multi-Party Issuance

Collaborative credential issuance.

```kotlin
import com.geoknoesis.vericore.multiparty.MultiPartyIssuance
import com.geoknoesis.vericore.multiparty.InMemoryMultiPartyIssuance
import com.geoknoesis.vericore.multiparty.ConsensusType

val multiParty: MultiPartyIssuance = InMemoryMultiPartyIssuance()

// Initiate issuance
val issuanceId = multiParty.initiateIssuance(
    credential = credential,
    participants = listOf("did:key:issuer1", "did:key:issuer2"),
    consensusType = ConsensusType.ALL
)

// Add signatures
multiParty.addSignature(issuanceId, "did:key:issuer1", signature1)
multiParty.addSignature(issuanceId, "did:key:issuer2", signature2)

// Finalize when consensus reached
val finalCredential = multiParty.finalizeIssuance(issuanceId)
```

## 13. Health Checks

System health monitoring.

```kotlin
import com.geoknoesis.vericore.health.HealthCheckService
import com.geoknoesis.vericore.health.InMemoryHealthCheckService

val health: HealthCheckService = InMemoryHealthCheckService()

// Run health checks
val healthStatus = health.runHealthChecks()
println("Status: ${healthStatus.status}") // HEALTHY, DEGRADED, UNHEALTHY
println("Components: ${healthStatus.components}")
```

## 14. Credential Rendering

Render credentials as HTML or PDF.

```kotlin
import com.geoknoesis.vericore.rendering.CredentialRenderer
import com.geoknoesis.vericore.rendering.InMemoryCredentialRenderer
import com.geoknoesis.vericore.rendering.RenderingFormat

val renderer: CredentialRenderer = InMemoryCredentialRenderer()

// Render as HTML
val html = renderer.renderHtml(credential)

// Render as PDF
val pdf = renderer.renderPdf(credential)

// Render presentation
val htmlPresentation = renderer.renderHtml(presentation)
```

## Integration Example

Here's how to integrate multiple features together:

```kotlin
import com.geoknoesis.vericore.*
import com.geoknoesis.vericore.audit.*
import com.geoknoesis.vericore.metrics.*
import com.geoknoesis.vericore.qrcode.*

val vericore = VeriCore.create()
val auditLogger = InMemoryAuditLogger()
val metrics = InMemoryMetricsCollector()
val qrGenerator = ZxingQrCodeGenerator()

// Issue credential with audit logging and metrics
suspend fun issueCredentialWithTracking(
    issuerDid: String,
    subjectDid: String
): VerifiableCredential {
    val startTime = System.currentTimeMillis()
    
    val credential = vericore.issueCredential(
        issuerDid = issuerDid,
        subjectDid = subjectDid
    ) { /* credential data */ }.getOrThrow()
    
    // Track metrics
    metrics.increment("credentials.issued")
    metrics.recordLatency("credential.issue", System.currentTimeMillis() - startTime)
    
    // Audit log
    auditLogger.logEvent(
        AuditEvent(
            id = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            actor = issuerDid,
            action = "ISSUE_CREDENTIAL",
            target = credential.id ?: "unknown",
            status = "SUCCESS"
        )
    )
    
    // Generate QR code
    val qrData = qrGenerator.generateForCredential(credential, QrCodeFormat.JSON)
    
    return credential
}
```

## Database-Backed Implementations

For production use, you'll want to create database-backed implementations. Each feature has an interface that you can implement:

- `AuditLogger` → `DatabaseAuditLogger`
- `MetricsCollector` → `DatabaseMetricsCollector`
- `CredentialVersioning` → `DatabaseCredentialVersioning`
- etc.

These can use your preferred database (PostgreSQL, MySQL, MongoDB, etc.) and follow the same interface contracts.

## Extending Features

All features are designed to be:
- **Pluggable**: Easy to swap implementations
- **Testable**: In-memory implementations for testing
- **Extensible**: Easy to add new functionality
- **Production-ready**: Can be extended with database-backed implementations

To extend features for production:
1. **Choose features**: Select which features you need for your use case
2. **Create implementations**: Build database-backed implementations as needed
3. **Integrate**: Add feature instances to your application
4. **Test**: Write tests for your integrations
5. **Monitor**: Use metrics and audit logs to monitor your system

