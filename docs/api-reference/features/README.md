---
title: TrustWeave Features
nav_exclude: true
---

# TrustWeave Features

This directory contains documentation for all TrustWeave features and plugins.

## Overview

TrustWeave provides a comprehensive set of features for building verifiable credential systems. All features follow TrustWeave's plugin architecture, making them easy to integrate and extend.

## Documentation

- **[USAGE_GUIDE.md](USAGE_GUIDE.md)**: Step-by-step guide on how to wire each feature into your application

> **TODO:** The previously linked `IMPLEMENTED_FEATURES.md` has not been written for the 0.6.0 baseline; treat USAGE_GUIDE.md as the source of truth and cross-reference the plugin tables in [plugins.md](../plugins.md) for shipped Gradle modules.

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
- **[Credential Exchange Protocols](credential-exchange-protocols/README.md)**: Protocol abstraction layer for credential exchange
  - **DIDComm V2**: Secure, private, decentralized messaging
  - **OIDC4VCI**: OpenID Connect for Verifiable Credential Issuance
  - **CHAPI**: Credential Handler API for browser-based wallet interactions

### Advanced Features
- **Multi-Party Issuance**: Collaborative credential issuance
- **Analytics & Reporting**: Analytics and trend analysis

## Quick Start

> **TODO:** The 0.6.0 baseline does not ship `org.trustweave.audit`, `org.trustweave.metrics`, etc. as Gradle modules. The patterns shown in [USAGE_GUIDE.md](USAGE_GUIDE.md) document the interfaces / in-memory implementations that integrators are expected to write today, alongside the actually-shipped credential exchange plugins (`credentials:plugins:oidc4vci`, `credentials:plugins:didcomm`, `credentials:plugins:chapi`, etc.) listed in [plugins.md](../plugins.md).

See [USAGE_GUIDE.md](USAGE_GUIDE.md) for the integration sketches; treat the in-memory classes (`InMemoryAuditLogger`, `InMemoryMetricsCollector`, …) as patterns to implement rather than ready-made imports.

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

1. Read [USAGE_GUIDE.md](USAGE_GUIDE.md) to see the integration sketches
2. Check [plugins.md](../plugins.md) for the credential format / exchange Gradle modules that actually ship in 0.6.0
3. Create database-backed implementations for any operational concerns (audit, metrics, notifications) your deployment needs
4. Integrate features into your application

