---
title: Production Deployment Guide
nav_order: 12
parent: Getting Started
redirect_from:
  - /getting-started/production-deployment/

---

# Production Deployment Guide

This guide covers best practices for deploying TrustWeave in production environments.

## Overview

Production deployments require careful consideration of:
- **Security**: Key management, access control, encryption
- **Performance**: Caching, connection pooling, resource management
- **Reliability**: Error handling, monitoring, health checks
- **Scalability**: Horizontal scaling, stateless design
- **Observability**: Logging, metrics, tracing

## Configuration Best Practices

### 1. Use Production-Grade KMS

Never use `inMemory` KMS in production. Use a production-grade key management service:

```kotlin
import org.trustweave.trust.dsl.credential.KmsProviders.AWS
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519

val trustWeave = TrustWeave.build {
    keys {
        // ✅ Production: select a provider registered via SPI (AWS KMS, Azure Key Vault, etc.)
        // Provider-specific settings (region, key alias, vault URL) are sourced from environment
        // or system properties picked up by the provider plugin.
        provider(AWS)
        algorithm(ED25519)
    }
    // ... rest of configuration
}
```

**Key Considerations:**
- Use hardware security modules (HSM) for high-security applications
- Implement key rotation policies
- Use separate keys for different environments (dev, staging, prod)
- Enable key versioning and audit logging

### 2. Configure Persistent Storage

Use persistent storage for wallets and trust registries:

```kotlin
val trustWeave = TrustWeave.build {
    keys { ... }
    did { ... }
    trust {
        // ✅ Production: Use a database-backed trust registry provider (registered via SPI/factory)
        provider("database")
    }
}

// Wallets should use a persistent wallet provider (factory registered via WalletFactory)
val wallet = trustWeave.wallet {
    holder(holderDid)
    provider("database")
}.getOrThrow()
```

**Storage Options:**
- **Database**: PostgreSQL, MySQL for structured data
- **Object Storage**: S3, Azure Blob for large credentials
- **File System**: Only for single-instance deployments

### 3. Configure Blockchain Anchors

Use production blockchain networks:

```kotlin
import org.trustweave.trust.dsl.credential.AnchorProviders.ALGORAND
import org.trustweave.trust.dsl.credential.AnchorProviders.POLYGON

val trustWeave = TrustWeave.build {
    // ... other configuration
    anchor {
        // ✅ Production: Use mainnet or production testnets. Provider-specific options
        // (API key, RPC URL, signing key) are supplied via options { ... } or via the
        // plugin's environment configuration.
        chain("algorand:mainnet") {
            provider(ALGORAND)
            options {
                "apiKey" to System.getenv("ALGORAND_API_KEY")
                "network" to "mainnet"
            }
        }
        chain("polygon:mainnet") {
            provider(POLYGON)
            options {
                "rpcUrl" to "https://polygon-rpc.com"
                "privateKey" to System.getenv("POLYGON_PRIVATE_KEY")
            }
        }
    }
}
```

**Considerations:**
- Use mainnet for production data
- Implement transaction retry logic
- Monitor gas fees and transaction costs
- Set appropriate timeouts

### 4. Enable Trust Registry

Always enable trust registry in production:

```kotlin
val trustWeave = TrustWeave.build {
    // ... other configuration
    trust {
        // Select a registered trust registry provider via SPI / TrustRegistryFactory
        provider("database")
    }
}

// Verify credentials with trust checking (registry from the same TrustWeave.build { trust { ... } })
val trustRegistry = requireNotNull(trustWeave.configuration.trustRegistry)
val verification = trustWeave.verify {
    credential(credential)
    requireTrust(trustRegistry)
}
```

## Error Handling

### Production Error Handling Pattern

Always handle errors explicitly in production:

```kotlin
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.TrustWeave
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(MyService::class.java)

suspend fun issuePersonCredential(
    trustWeave: TrustWeave,
    issuerDid: Did,
    holderDid: String,
    claims: Map<String, Any>
): Result<VerifiableCredential> {
    return when (
        val issued = trustWeave.issue {
            credential {
                type(CredentialType.VerifiableCredential, CredentialType.Person)
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    claims.forEach { (key, value) -> key to value }
                }
            }
            signedBy(issuerDid, "key-1")
        }
    ) {
        is IssuanceResult.Success -> Result.success(issued.credential)
        is IssuanceResult.Failure -> {
            logger.error("Issuance failed: ${issued.allErrors.joinToString()}")
            Result.failure(
                TrustWeaveException.InvalidState(
                    message = issued.allErrors.joinToString("; "),
                    context = emptyMap(),
                    cause = null
                )
            )
        }
    }
}
```

### Error Recovery Strategies

Implement retry logic for transient errors:

```kotlin
import kotlinx.coroutines.delay
import kotlin.random.Random
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.exception.DidException

suspend fun <T> retryOperation(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    operation: suspend () -> T
): T {
    var lastError: Exception? = null
    var delay = initialDelay.toDouble()

    repeat(maxRetries) { attempt ->
        try {
            return operation()
        } catch (error: Exception) {
            lastError = error

            // Don't retry on validation / client input errors
            if (error is TrustWeaveException.ValidationFailed ||
                error is DidException.InvalidDidFormat) {
                throw error
            }

            if (attempt < maxRetries - 1) {
                val jitter = Random.nextLong(0, (delay * 0.1).toLong())
                delay((delay + jitter).toLong())
                delay *= 2.0
            }
        }
    }

    throw lastError ?: Exception("Operation failed after $maxRetries retries")
}
```

## Performance Optimization

### 1. Connection Pooling

Use connection pooling for database operations:

```kotlin
// Configure connection pool
val dataSource = HikariDataSource().apply {
    jdbcUrl = "jdbc:postgresql://db.example.org.trustweave"
    maximumPoolSize = 20
    minimumIdle = 5
    connectionTimeout = 30000
    idleTimeout = 600000
    maxLifetime = 1800000
}
```

### 2. Caching

Implement caching for frequently accessed data:

```kotlin
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

class CachedDidResolver(
    private val delegate: DidResolver,
    private val cache: Cache<String, DidResolutionResult>
) : DidResolver by delegate {

    override suspend fun resolve(did: String): DidResolutionResult {
        return cache.get(did) {
            delegate.resolve(did)
        } ?: delegate.resolve(did)
    }
}

// Configure cache
val didCache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build<String, DidResolutionResult>()
```

### 3. Async Operations

Use coroutines for concurrent operations:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.TrustWeave

// Issue multiple credentials concurrently
// CredentialRequest.issuerDid is a String from your API layer; signedBy requires Did as the first argument.
suspend fun issueMultipleCredentials(
    trustWeave: TrustWeave,
    requests: List<CredentialRequest>
): List<VerifiableCredential> {
    return requests.map { request ->
        async {
            when (
                val r = trustWeave.issue {
                    credential {
                        type(CredentialType.VerifiableCredential, CredentialType.fromString(request.type))
                        issuer(request.issuerDid)
                        subject {
                            id(request.holderDid)
                            request.claims.forEach { (key, value) ->
                                key to value
                            }
                        }
                    }
                    signedBy(Did(request.issuerDid), request.keyId)
                }
            ) {
                is IssuanceResult.Success -> r.credential
                is IssuanceResult.Failure -> throw TrustWeaveException.InvalidState(
                    message = r.allErrors.joinToString("; "),
                    context = emptyMap(),
                    cause = null
                )
            }
        }
    }.awaitAll()
}
```

## Security Best Practices

### 1. Environment Variables

Never hardcode secrets. Use environment variables or secret management:

```kotlin
// ✅ Good: Use environment variables
val apiKey = System.getenv("ALGORAND_API_KEY")
    ?: throw IllegalStateException("ALGORAND_API_KEY not set")

// ✅ Better: Use secret management service
val apiKey = secretManager.getSecret("algorand-api-key")
```

### 2. Input Validation

Always validate inputs before operations:

```kotlin
fun validateDid(did: String): ValidationResult {
    if (!did.startsWith("did:")) {
        return ValidationResult.Invalid("DID must start with 'did:'")
    }
    val parts = did.split(":")
    if (parts.size < 3) {
        return ValidationResult.Invalid("Invalid DID format")
    }
    return ValidationResult.Valid
}

// Use before operations
val validation = validateDid(userInputDid)
if (!validation.isValid()) {
    return Result.failure(DidException.InvalidDidFormat(
        did = userInputDid,
        reason = validation.errorMessage()
    ))
}
```

### 3. Rate Limiting

Implement rate limiting to prevent abuse:

```kotlin
import io.github.bucket4j.Bucket
import io.github.bucket4j.Bucket4j

class RateLimitedTrustWeave(
    private val delegate: TrustWeave,
    private val bucket: Bucket
) {
    suspend fun issue(block: IssuanceBuilder.() -> Unit): IssuanceResult {
        if (!bucket.tryConsume(1)) {
            return IssuanceResult.Failure.InvalidRequest(
                field = "rateLimit",
                reason = "Rate limit exceeded"
            )
        }
        return delegate.issue(block)
    }
}

// Configure rate limiter
val bucket = Bucket4j.builder()
    .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
    .build()
```

## Monitoring and Observability

### 1. Logging

Use structured logging:

```kotlin
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.core.exception.TrustWeaveException

private val logger = LoggerFactory.getLogger(MyService::class.java)

suspend fun issueCredentialSample(...) {
    MDC.put("operation", "issue_credential")
    MDC.put("issuerDid", issuerDid.toString())

    logger.info("Issuing credential", mapOf(
        "issuerDid" to issuerDid.toString(),
        "holderDid" to holderDid
    ))

    when (val issued = trustWeave.issue { /* ... */ }) {
        is IssuanceResult.Success -> {
            logger.info("Credential issued successfully", mapOf(
                "credentialId" to issued.credential.id
            ))
            MDC.clear()
            return issued.credential
        }
        is IssuanceResult.Failure -> {
            val error = TrustWeaveException.InvalidState(
                message = issued.allErrors.joinToString("; "),
                context = emptyMap(),
                cause = null
            )
            logger.error("Failed to issue credential", mapOf(
                "errors" to issued.allErrors
            ), error)
            MDC.clear()
            throw error
        }
    }
}
```

### 2. Metrics

Collect metrics for key operations:

```kotlin
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.IssuanceBuilder

class MetricsTrustWeave(
    private val delegate: TrustWeave,
    private val registry: MeterRegistry
) {
    suspend fun issue(block: IssuanceBuilder.() -> Unit): IssuanceResult {
        val sample = Timer.start(registry)
        val result = delegate.issue(block)
        sample.stop(registry.timer("trustweave.issue.duration"))
        when (result) {
            is IssuanceResult.Success ->
                registry.counter("trustweave.issue.success").increment()
            is IssuanceResult.Failure ->
                registry.counter("trustweave.issue.error", "code", "issuance_failure").increment()
        }
        return result
    }
}
```

### 3. Health Checks

Implement health checks:

```kotlin
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519

class TrustWeaveHealthIndicator(
    private val trustWeave: TrustWeave
) : HealthIndicator {

    override fun health(): Health {
        return try {
            runBlocking {
                // Test DID creation
                val testDid = trustWeave.createDid {
                    method(KEY)
                    algorithm(ED25519)
                }

                Health.up()
                    .withDetail("status", "operational")
                    .withDetail("testDid", testDid)
                    .build()
            }
        } catch (error: Exception) {
            Health.down()
                .withDetail("status", "unavailable")
                .withDetail("error", error.message)
                .withException(error)
                .build()
        }
    }
}
```

## Deployment Checklist

Before deploying to production:

- Use production-grade KMS (AWS KMS, Azure Key Vault, etc.)
- Configure persistent storage for wallets and trust registry
- Use production blockchain networks (mainnet)
- Enable trust registry and trust checking
- Implement comprehensive error handling
- Add retry logic for transient errors
- Configure connection pooling
- Implement caching for frequently accessed data
- Use environment variables for secrets
- Implement input validation
- Add rate limiting
- Configure structured logging
- Set up metrics collection
- Implement health checks
- Configure monitoring and alerting
- Set up backup and disaster recovery
- Document runbooks and procedures
- Perform load testing
- Review security configuration
- Test error scenarios

## Related Documentation

- API Patterns](api-patterns.md) - Correct API usage patterns
- Error Handling](../advanced/error-handling.md) - Detailed error handling guide
- Performance](../advanced/performance.md) - Performance optimization guide
- Security](../security/README.md) - Security best practices
- Troubleshooting](troubleshooting.md) - Common issues and solutions

