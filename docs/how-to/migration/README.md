---
title: Migration Guides
parent: How-To Guides
has_children: true
redirect_from:
  - /migration/README/
nav_order: 220
---

# Migration Guides

This section provides migration guides for upgrading between TrustWeave versions and understanding deprecation policies.

## Version Compatibility Matrix

| TrustWeave Version | Kotlin | Java | Gradle | Status |
|------------------|--------|------|--------|--------|
| 0.7.0 | 2.4.21 | 21+ | 9.8.1 | Current |

## Deprecation Policy

TrustWeave follows semantic versioning (SemVer) principles:

- **Major versions** (X.0.0): Breaking changes allowed
- **Minor versions** (0.X.0): New features, backward compatible
- **Patch versions** (0.0.X): Bug fixes only

### Deprecation Timeline

- **Deprecation Announcement**: Deprecated APIs are marked with `@Deprecated` annotation and documented in CHANGELOG
- **Minimum Deprecation Period**: 6 months before removal
- **Removal**: Deprecated APIs are removed in the next major version

### How to Handle Deprecations

1. **Check CHANGELOG**: Review deprecation notices in [CHANGELOG.md](https://github.com/geoknoesis/trustweave/blob/main/CHANGELOG.md)
2. **Update Code**: Replace deprecated APIs with recommended alternatives
3. **Test Thoroughly**: Ensure your code works with new APIs
4. **Plan Migration**: Schedule migration before major version upgrade

## Migration Guides

### Migrating to 0.7.0

See [Migrating to 0.7.0](migrating-to-0.7.0.md) for upgrading from `0.6.x` to `0.7.0`:
- Toolchain upgrades (Kotlin 2.4.21, Gradle 9.8.1, Ktor 3.6.0, Coroutines 1.11.0)
- BOM (`org.trustweave:distribution-bom:0.7.0`), `distribution-all` bundling `kms-plugins-inmemory`, and removal of 12 pure stubs from publishing
- Sealed result APIs (`revokeResult`, `trustResult`, `getKeyIdResult`) and `KmsProviders.*` canonical SPI names
- Wallet storage schema/prefix upgrades (`DatabaseWallet` v2, `CloudWalletFactory`) and KMS/security hardening

### Migrating to Phase 4 API (Service Layer Removal)

See [Migrating to Phase 4 API](migrating-api-phase4.md) for migration from service layer (`dids`, `credentials`, `wallets`) to direct methods:
- Service layer removal
- Direct methods migration
- Error handling updates for sealed results
- Common migration issues

### Migrating to 1.0.0

See [Migrating to 1.0.0](migrating-to-1.0.0.md) for detailed migration instructions covering:
- Type-safe options migration
- Result-based API migration
- Error handling updates
- Plugin lifecycle changes

### Migrating to Typed Values API

See Migrating to Typed Values API for migration from String-based parameters to type-safe typed values:
- Replacing String parameters with `Did`, `KeyId`, etc.
- Updating DSL builders to use typed values
- Benefits of compile-time type safety
- Common migration issues and solutions

## Breaking Changes

Breaking changes are clearly marked in the CHANGELOG with migration instructions. Common breaking changes include:

- API signature changes
- Return type changes
- Exception type changes
- Configuration format changes

## Getting Help

If you encounter issues during migration:

1. Check the [FAQ](../../faq.md) for common questions
2. Review [Error Handling](../../api-reference/advanced/error-handling.md) for error patterns
3. Open an issue on GitHub with migration details
4. Contact support at [www.geoknoesis.com](https://www.geoknoesis.com)

