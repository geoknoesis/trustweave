---
title: TrustWeave Features
nav_exclude: true
redirect_from:
  - /features/README/
nav_order: 210
---

# TrustWeave Features

This directory documents the credential exchange and application-level features around TrustWeave's
core (DIDs, keys, credentials, wallets, anchoring).

## Documentation

- **[USAGE_GUIDE.md](USAGE_GUIDE.md)**: which features ship (with their Gradle modules and entry
  points) and which you implement in your application.
- **[Credential Exchange Protocols](credential-exchange-protocols/README.md)**: DIDComm v2, OIDC4VCI,
  OIDC4VP, SIOPv2 and CHAPI.
- **[Verifiable Intent](verifiable-intent.md)**.
- **[plugins.md](../plugins.md)**: every published plugin module and its Maven coordinates.

## What ships

- **Credential exchange:** DIDComm v2, OIDC4VCI (including credential-offer URLs for QR codes),
  OIDC4VP, SIOPv2 and CHAPI, each as a `credentials:plugins:*` module.
- **Revocation:** status-list plugins (`credentials:plugins:status-list:*`) and the
  `trustWeave.revoke { }` / `trustWeave.revocation { }` DSL.
- **Observability:** the `observability` module (OpenTelemetry library telemetry, and metrics, health
  and authentication for the bundled servers).

## What does not ship

Audit logging, notifications, credential versioning, backup/recovery, expiration management,
analytics, multi-party issuance workflows and credential rendering have **no TrustWeave API**. Earlier
versions of these pages showed `org.trustweave.audit`, `org.trustweave.metrics` and similar packages
that never existed; see [USAGE_GUIDE.md](USAGE_GUIDE.md#not-shipped-implement-in-your-application) for
how to build these on your own infrastructure.
