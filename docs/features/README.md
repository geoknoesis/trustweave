---
title: TrustWeave Features
nav_order: 3
---

# TrustWeave Features

This directory contains documentation for all TrustWeave features and plugins.

## Overview

TrustWeave provides a comprehensive set of features for building verifiable credential systems. All features follow TrustWeave's plugin architecture, making them easy to integrate and extend.

## Documentation

- **[IMPLEMENTED_FEATURES.md](IMPLEMENTED_FEATURES.md)**: Detailed documentation of all features
- **[USAGE_GUIDE.md](USAGE_GUIDE.md)**: Step-by-step guide on how to use each feature

## Feature Categories

### Core Features
- **Audit Logging**: Immutable audit logs for all operations
- **Metrics & Telemetry**: Performance and usage tracking
- **Health Checks**: System diagnostics and monitoring

### Credential Management
- **Credential Versioning**: Version tracking and rollback
- **Backup & Recovery**: Export/import functionality
- **Expiration Management**: Monitoring and renewal workflows
- **Credential Rendering**: HTML/PDF rendering

### Communication & Exchange
- **QR Code Generation**: Credential sharing via QR codes
- **Notifications**: Push notifications and webhooks
- **[Credential Exchange Protocols](./credential-exchange-protocols/README.md)**: Protocol abstraction layer for credential exchange
  - **DIDComm V2**: Secure, private, decentralized messaging
  - **OIDC4VCI**: OpenID Connect for Verifiable Credential Issuance
  - **CHAPI**: Credential Handler API for browser-based wallet interactions

### Advanced Features
- **Multi-Party Issuance**: Collaborative credential issuance
- **Analytics & Reporting**: Analytics and trend analysis

## Quick Start

All features can be used independently:

```kotlin
// Example: Using Audit Logging
import com.trustweave.audit.*

val auditLogger = InMemoryAuditLogger()
auditLogger.logEvent(AuditEvent(...))

// Example: Using Metrics
import com.trustweave.metrics.*

val metrics = InMemoryMetricsCollector()
metrics.increment("credentials.issued")
```

See [USAGE_GUIDE.md](USAGE_GUIDE.md) for detailed examples of each feature.

## Architecture

All features follow TrustWeave's plugin architecture:

1. **Interface Definition**: Each feature defines a clear interface
2. **In-Memory Implementation**: For testing and development
3. **Pluggable**: Easy to swap implementations with database-backed or custom implementations

## Integration

Features can be integrated into your application in several ways:

1. **Standalone**: Use features independently
2. **With TrustWeave**: Integrate with the main TrustWeave facade
3. **Custom**: Create your own implementations

See [USAGE_GUIDE.md](USAGE_GUIDE.md) for integration examples.

## Next Steps

1. Review [IMPLEMENTED_FEATURES.md](IMPLEMENTED_FEATURES.md) to understand what's available
2. Read [USAGE_GUIDE.md](USAGE_GUIDE.md) to learn how to use features
3. Create database-backed implementations for production
4. Integrate features into your application

