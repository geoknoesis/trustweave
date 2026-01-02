# Rate Limiting in Credential API

## Overview

Rate limiting is **not implemented at the credential-api module level** by design. This is an intentional architectural decision to allow for flexible deployment scenarios.

## Design Rationale

The credential-api module is a **library** that provides core credential operations (issuance, verification, transformation). Rate limiting is typically a **cross-cutting concern** that should be handled at a higher layer in the application stack.

### Why Not at the Library Level?

1. **Deployment Flexibility**: Different deployment scenarios require different rate limiting strategies:
   - Single-tenant vs. multi-tenant applications
   - On-premise vs. cloud deployments
   - High-throughput vs. low-latency requirements

2. **Integration Flexibility**: Applications may use:
   - API gateways with built-in rate limiting
   - Load balancers with rate limiting capabilities
   - Application-level rate limiting libraries
   - Custom rate limiting logic

3. **Separation of Concerns**: Credential operations should focus on:
   - Cryptographic operations
   - Format conversion
   - Validation logic
   
   Rate limiting is a **non-functional requirement** that belongs in the application/infrastructure layer.

## Recommended Approach

Rate limiting should be implemented at one of these layers:

### 1. API Gateway / Load Balancer

Most cloud providers and API gateways provide built-in rate limiting:
- AWS API Gateway: Throttling and rate limits
- Azure API Management: Rate limiting policies
- NGINX: rate_limit module
- Kong: Rate Limiting plugin

**Benefits:**
- Applied before requests reach the application
- Can handle distributed rate limiting
- Minimal application code changes

### 2. Application Layer

Use dedicated rate limiting libraries in your application:
- **Bucket4j**: Token bucket algorithm for Java/Kotlin
- **Resilience4j**: Rate limiter component
- **Guava RateLimiter**: Simple rate limiting

**Example:**
```kotlin
import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy

// Configure rate limiter at application startup
val bucket = Bucket.builder()
    .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
    .build()

// Apply rate limiting in service layer
suspend fun issueCredential(request: IssuanceRequest): IssuanceResult {
    if (!bucket.tryConsume(1)) {
        return IssuanceResult.Failure.RateLimited(
            reason = "Rate limit exceeded. Please try again later."
        )
    }
    return credentialService.issue(request)
}
```

### 3. Middleware / Interceptor

Implement rate limiting as middleware/interceptor in your web framework:
- Spring Boot: `@RateLimiter` annotation
- Ktor: Rate limiting plugin
- Vert.x: Rate limiter handler

## Security Considerations

While rate limiting is not implemented at the library level, the credential-api module provides other security measures:

### Built-in Security Features

1. **Input Validation**: Size and length limits prevent DoS attacks
   - Maximum credential size: 1MB
   - Maximum presentation size: 5MB
   - Maximum claims per credential: 1000
   - Maximum credentials per presentation: 100
   - Maximum identifier lengths: 500-1000 characters

2. **Resource Limits**: Prevents resource exhaustion
   - Canonicalized document size limits
   - Status list check size limits
   - Comprehensive input validation

3. **Security Constants**: All limits are configurable via `SecurityConstants`
   - Well-documented rationale
   - Based on realistic use cases
   - Prevents DoS attacks

### Rate Limiting Recommendations

For production deployments, we recommend:

1. **Implement rate limiting** at the API gateway or application layer
2. **Set appropriate limits** based on:
   - Expected traffic patterns
   - Resource constraints
   - Business requirements
3. **Monitor and adjust** limits based on actual usage
4. **Use distributed rate limiting** for multi-instance deployments
5. **Consider different limits** for different operations:
   - Issuance: Lower limits (more resource-intensive)
   - Verification: Higher limits (less resource-intensive)
   - Transformation: Medium limits

## Summary

Rate limiting is **intentionally excluded** from the credential-api library to provide deployment flexibility. Applications should implement rate limiting at the appropriate layer (gateway, application, or middleware) based on their specific requirements and infrastructure.

The credential-api module provides **input validation and resource limits** as defense-in-depth measures, but rate limiting should be handled by the application infrastructure.



