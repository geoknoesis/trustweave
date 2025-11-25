---
title: Production Deployment Guide
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
val trustLayer = TrustLayer.build {
    keys {
        // ✅ Production: Use AWS KMS, Azure Key Vault, or HashiCorp Vault
        provider("awsKms") {
            region("us-east-1")
            keyAlias("trustweave-signing-key")
        }
        // Or
        provider("azureKeyVault") {
            vaultUrl("https://myvault.vault.azure.net")
            keyName("signing-key")
        }
        algorithm("Ed25519")
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
val trustLayer = TrustLayer.build {
    keys { ... }
    did { ... }
    trust {
        // ✅ Production: Use database-backed trust registry
        provider("database") {
            connectionString("jdbc:postgresql://db.example.com/trustweave")
            schema("trust_registry")
        }
    }
}

// Wallets should use persistent storage
val wallet = trustLayer.wallet {
    holder(holderDid)
    // Use database or S3 storage
    storageProvider("database")
    storagePath("wallets/${holderDid}")
}
```

**Storage Options:**
- **Database**: PostgreSQL, MySQL for structured data
- **Object Storage**: S3, Azure Blob for large credentials
- **File System**: Only for single-instance deployments

### 3. Configure Blockchain Anchors

Use production blockchain networks:

```kotlin
val trustLayer = TrustLayer.build {
    // ... other configuration
    anchor {
        // ✅ Production: Use mainnet or production testnets
        chain("algorand:mainnet") {
            provider("algorand") {
                apiKey(env("ALGORAND_API_KEY"))
                network("mainnet")
            }
        }
        // Or
        chain("polygon:mainnet") {
            provider("polygon") {
                rpcUrl("https://polygon-rpc.com")
                privateKey(env("POLYGON_PRIVATE_KEY"))
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
val trustLayer = TrustLayer.build {
    // ... other configuration
    trust {
        provider("database") {
            connectionString(env("TRUST_DB_URL"))
            // Enable trust checking
            enableTrustChecking(true)
        }
    }
}

// Verify credentials with trust checking
val verification = trustLayer.verify {
    credential(credential)
    checkTrust(true)  // ✅ Always check trust in production
}
```

## Error Handling

### Production Error Handling Pattern

Always handle errors explicitly in production:

```kotlin
import com.trustweave.core.TrustWeaveError
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(MyService::class.java)

suspend fun issueCredential(
    trustLayer: TrustLayer,
    issuerDid: String,
    holderDid: String,
    claims: Map<String, Any>
): Result<VerifiableCredential> {
    return try {
        val credential = trustLayer.issue {
            credential {
                type("VerifiableCredential", "PersonCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    claims.forEach { (key, value) ->
                        claim(key, value)
                    }
                }
            }
            by(issuerDid = issuerDid, keyId = "$issuerDid#key-1")
        }
        Result.success(credential)
    } catch (error: TrustWeaveError) {
        logger.error("Failed to issue credential", error)
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                logger.warn("DID method not registered: ${error.method}")
                Result.failure(error)
            }
            is TrustWeaveError.CredentialInvalid -> {
                logger.error("Credential validation failed: ${error.reason}")
                Result.failure(error)
            }
            else -> {
                logger.error("Unexpected error: ${error.message}", error)
                Result.failure(error)
            }
        }
    } catch (error: Exception) {
        logger.error("Unexpected exception", error)
        Result.failure(
            TrustWeaveError.Unknown(
                code = "UNEXPECTED_ERROR",
                message = error.message ?: "Unknown error",
                context = emptyMap(),
                cause = error
            )
        )
    }
}
```

### Error Recovery Strategies

Implement retry logic for transient errors:

```kotlin
import kotlinx.coroutines.delay
import kotlin.random.Random

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
            
            // Don't retry on validation errors
            if (error is TrustWeaveError.ValidationFailed ||
                error is TrustWeaveError.CredentialInvalid ||
                error is TrustWeaveError.InvalidDidFormat) {
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
    jdbcUrl = "jdbc:postgresql://db.example.com/trustweave"
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

// Issue multiple credentials concurrently
suspend fun issueMultipleCredentials(
    trustLayer: TrustLayer,
    requests: List<CredentialRequest>
): List<VerifiableCredential> {
    return requests.map { request ->
        async {
            trustLayer.issue {
                credential {
                    type("VerifiableCredential", request.type)
                    issuer(request.issuerDid)
                    subject {
                        id(request.holderDid)
                        request.claims.forEach { (key, value) ->
                            claim(key, value)
                        }
                    }
                }
                by(issuerDid = request.issuerDid, keyId = request.keyId)
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
    return Result.failure(TrustWeaveError.InvalidDidFormat(
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

class RateLimitedTrustLayer(
    private val delegate: TrustLayer,
    private val bucket: Bucket
) {
    suspend fun issue(block: IssuanceBuilder.() -> Unit): VerifiableCredential {
        if (!bucket.tryConsume(1)) {
            throw TrustWeaveError.InvalidOperation(
                code = "RATE_LIMIT_EXCEEDED",
                message = "Rate limit exceeded",
                context = emptyMap(),
                cause = null
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

private val logger = LoggerFactory.getLogger(MyService::class.java)

suspend fun issueCredential(...) {
    MDC.put("operation", "issueCredential")
    MDC.put("issuerDid", issuerDid)
    
    try {
        logger.info("Issuing credential", mapOf(
            "issuerDid" to issuerDid,
            "holderDid" to holderDid
        ))
        
        val credential = trustLayer.issue { ... }
        
        logger.info("Credential issued successfully", mapOf(
            "credentialId" to credential.id
        ))
        
        return credential
    } catch (error: TrustWeaveError) {
        logger.error("Failed to issue credential", mapOf(
            "error" to error.code,
            "message" to error.message
        ), error)
        throw error
    } finally {
        MDC.clear()
    }
}
```

### 2. Metrics

Collect metrics for key operations:

```kotlin
import io.micrometer.core.instrument.MeterRegistry

class MetricsTrustLayer(
    private val delegate: TrustLayer,
    private val registry: MeterRegistry
) {
    suspend fun issue(block: IssuanceBuilder.() -> Unit): VerifiableCredential {
        val timer = registry.timer("trustweave.issue.duration")
        return timer.recordCallable {
            try {
                delegate.issue(block).also {
                    registry.counter("trustweave.issue.success").increment()
                }
            } catch (error: TrustWeaveError) {
                registry.counter("trustweave.issue.error", "code", error.code).increment()
                throw error
            }
        }
    }
}
```

### 3. Health Checks

Implement health checks:

```kotlin
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator

class TrustLayerHealthIndicator(
    private val trustLayer: TrustLayer
) : HealthIndicator {
    
    override fun health(): Health {
        return try {
            runBlocking {
                // Test DID creation
                val testDid = trustLayer.createDid {
                    method("key")
                    algorithm("Ed25519")
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

- [ ] Use production-grade KMS (AWS KMS, Azure Key Vault, etc.)
- [ ] Configure persistent storage for wallets and trust registry
- [ ] Use production blockchain networks (mainnet)
- [ ] Enable trust registry and trust checking
- [ ] Implement comprehensive error handling
- [ ] Add retry logic for transient errors
- [ ] Configure connection pooling
- [ ] Implement caching for frequently accessed data
- [ ] Use environment variables for secrets
- [ ] Implement input validation
- [ ] Add rate limiting
- [ ] Configure structured logging
- [ ] Set up metrics collection
- [ ] Implement health checks
- [ ] Configure monitoring and alerting
- [ ] Set up backup and disaster recovery
- [ ] Document runbooks and procedures
- [ ] Perform load testing
- [ ] Review security configuration
- [ ] Test error scenarios

## Related Documentation

- [API Patterns](api-patterns.md) - Correct API usage patterns
- [Error Handling](../advanced/error-handling.md) - Detailed error handling guide
- [Performance](../advanced/performance.md) - Performance optimization guide
- [Security](../security/README.md) - Security best practices
- [Troubleshooting](troubleshooting.md) - Common issues and solutions

