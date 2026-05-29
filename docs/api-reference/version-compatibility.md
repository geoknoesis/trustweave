---
title: Version Compatibility Matrix
nav_order: 300
parent: API Reference
keywords:
  - version
  - compatibility
  - kotlin
  - java
  - gradle
  - requirements
redirect_from:
  - /reference/version-compatibility/
  - /api-reference/reference/version-compatibility/
---

# Version Compatibility Matrix

This document provides version compatibility information for TrustWeave SDK and its dependencies.

## TrustWeave SDK Version

**Current Version:** `0.6.0`

> **Note:** TrustWeave follows semantic versioning (e.g., `0.6.0`, `0.7.0`, `1.0.0`, etc.).

## Runtime Requirements

### Java

| TrustWeave Version | Java Version | Status |
|-------------------|--------------|--------|
| 0.6.0             | 21+          | ✅ Required |
| Future            | 21+          | ✅ Required |

**Why Java 21?**
- Java 21 provides required language features (records, pattern matching, etc.)
- Long-term support (LTS) release
- Required for Kotlin 2.2.0+ compatibility

### Kotlin

| TrustWeave Version | Kotlin Version | Status |
|-------------------|----------------|--------|
| 0.6.0             | 2.3.21        | ✅ Built against |
| Future            | 2.3.x         | ✅ Tracking latest stable |

**Why Kotlin 2.3.x?**
- Required for DSL builders and type-safe configuration
- Modern coroutine features
- Improved type inference

### Build Tools

| Build Tool | Minimum Version | Status |
|------------|----------------|--------|
| Gradle     | 9.2+           | ✅ Required (wrapper pinned to 9.2.0) |
| Maven      | 3.8.0+         | ✅ Required |

## Dependency Compatibility

### Core Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| `kotlinx-serialization-json` | 1.11.0 | JSON serialization |
| `kotlinx-coroutines-core` | 1.10.2 | Coroutines support |
| `kotlinx-datetime` | 0.6.2 | Date/time handling |

### Optional Dependencies

| Dependency | Version | Purpose | Plugin |
|------------|---------|---------|--------|
| AWS SDK | 2.x | AWS KMS integration | `org.trustweave:kms-plugins-aws` |
| Azure SDK | Latest | Azure Key Vault integration | `org.trustweave:kms-plugins-azure` |
| Google Cloud SDK | Latest | Google Cloud KMS integration | `org.trustweave:kms-plugins-google` |

## Module Compatibility

All TrustWeave modules share the same version number for consistency:

```
org.trustweave:distribution-all:0.6.0
org.trustweave:trust:0.6.0
org.trustweave:did-did-core:0.6.0
org.trustweave:kms-kms-core:0.6.0
org.trustweave:anchors-anchor-core:0.6.0
org.trustweave:testkit:0.6.0
```

**Recommendation:** Use the same version for all TrustWeave modules to avoid compatibility issues.

## Platform Compatibility

### Operating Systems

| Platform | Status | Notes |
|----------|--------|-------|
| Linux    | ✅ Supported | Primary development platform |
| macOS    | ✅ Supported | Full support |
| Windows  | ✅ Supported | Full support |

### JVM Versions

| JVM | Status | Notes |
|-----|--------|-------|
| OpenJDK 21+ | ✅ Supported | Recommended |
| Oracle JDK 21+ | ✅ Supported | Full support |
| Eclipse Temurin 21+ | ✅ Supported | Recommended |
| Amazon Corretto 21+ | ✅ Supported | Full support |

## Breaking Changes

### Version History

| Version | Status | Breaking Changes |
|---------|--------|------------------|
| 0.6.0 | Current | None (initial version) |
| Future 1.0.0 | Planned | None expected |
| Future 1.1.0 | Planned | TBD |

**Note:** Breaking changes will be documented in [Migration Guides](../../how-to/migration/README.md).

## Compatibility Recommendations

### For Production Use

1. **Use stable releases** (avoid `-SNAPSHOT` versions in production)
2. **Pin dependency versions** in your build configuration
3. **Test upgrades** in staging before production
4. **Review migration guides** before upgrading major versions

### For Development

1. **Use latest snapshot** for testing new features
2. **Follow semantic versioning** when upgrading
3. **Check release notes** for changes
4. **Update dependencies** regularly

## Version Checking

### Gradle

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:0.6.0")
}

// Check version at runtime
val version = TrustWeave::class.java.getPackage()?.implementationVersion
```

### Maven

```xml
<dependency>
    <groupId>org.trustweave</groupId>
    <artifactId>distribution-all</artifactId>
    <version>0.6.0</version>
</dependency>
```

## Troubleshooting

### Version Conflicts

If you encounter version conflicts:

1. **Check dependency tree:**
   ```bash
   ./gradlew dependencies
   ```

2. **Resolve conflicts:**
   ```kotlin
   configurations.all {
       resolutionStrategy {
           force("org.trustweave:common:0.6.0")
       }
   }
   ```

3. **Use BOM** (when available):
   ```kotlin
   dependencies {
       implementation(platform("org.trustweave:distribution-bom:0.6.0"))
       implementation("org.trustweave:trust")
       implementation("org.trustweave:did-did-core")
   }
   ```

### Upgrade Path

When upgrading TrustWeave versions:

1. **Review release notes** for changes
2. **Check migration guides** for breaking changes
3. **Update dependencies** gradually
4. **Test thoroughly** before production deployment

## Related Documentation

- [Installation Guide](../getting-started/installation.md) - Setup instructions
- [Migration Guides](../../how-to/migration/README.md) - Upgrade instructions
- [Module Overview](../modules/core-modules.md) - Module dependencies

---

**Last Updated:** May 2026  
**TrustWeave Version:** 0.6.0
