---
title: Version Compatibility Matrix
nav_order: 1
parent: Reference
keywords:
  - version
  - compatibility
  - kotlin
  - java
  - gradle
  - requirements
---

# Version Compatibility Matrix

This document provides version compatibility information for TrustWeave SDK and its dependencies.

## TrustWeave SDK Version

**Current Version:** `1.0.0-SNAPSHOT`

> **Note:** This is a snapshot version. Stable releases will follow semantic versioning (e.g., `1.0.0`, `1.1.0`, etc.).

## Runtime Requirements

### Java

| TrustWeave Version | Java Version | Status |
|-------------------|--------------|--------|
| 1.0.0-SNAPSHOT    | 21+          | ✅ Required |
| Future            | 21+          | ✅ Required |

**Why Java 21?**
- Java 21 provides required language features (records, pattern matching, etc.)
- Long-term support (LTS) release
- Required for Kotlin 2.2.0+ compatibility

### Kotlin

| TrustWeave Version | Kotlin Version | Status |
|-------------------|----------------|--------|
| 1.0.0-SNAPSHOT    | 2.2.21+       | ✅ Required |
| Future            | 2.2.0+        | ✅ Required |

**Why Kotlin 2.2.0+?**
- Required for DSL builders and type-safe configuration
- Modern coroutine features
- Improved type inference

### Build Tools

| Build Tool | Minimum Version | Status |
|------------|----------------|--------|
| Gradle     | 8.5+           | ✅ Required |
| Maven      | 3.8.0+         | ✅ Required |

## Dependency Compatibility

### Core Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| `kotlinx-serialization-json` | 1.6.0+ | JSON serialization |
| `kotlinx-coroutines-core` | 1.7.3+ | Coroutines support |
| `kotlinx-datetime` | 0.5.0+ | Date/time handling |

### Optional Dependencies

| Dependency | Version | Purpose | Plugin |
|------------|---------|---------|--------|
| AWS SDK | 2.x | AWS KMS integration | `org.trustweave.kms:aws` |
| Azure SDK | Latest | Azure Key Vault integration | `org.trustweave.kms:azure` |
| Google Cloud SDK | Latest | Google Cloud KMS integration | `org.trustweave.kms:google` |

## Module Compatibility

All TrustWeave modules share the same version number for consistency:

```
org.trustweave:distribution-all:1.0.0-SNAPSHOT
org.trustweave:trustweave-trust:1.0.0-SNAPSHOT
org.trustweave:trustweave-did:1.0.0-SNAPSHOT
org.trustweave:trustweave-kms:1.0.0-SNAPSHOT
org.trustweave:trustweave-anchor:1.0.0-SNAPSHOT
org.trustweave:testkit:1.0.0-SNAPSHOT
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
| 1.0.0-SNAPSHOT | Current | None (initial version) |
| Future 1.0.0 | Planned | None expected |
| Future 1.1.0 | Planned | TBD |

**Note:** Breaking changes will be documented in [Migration Guides](../migration/README.md).

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
    implementation("org.trustweave:distribution-all:1.0.0-SNAPSHOT")
}

// Check version at runtime
val version = TrustWeave::class.java.getPackage()?.implementationVersion
```

### Maven

```xml
<dependency>
    <groupId>org.trustweave</groupId>
    <artifactId>distribution-all</artifactId>
    <version>1.0.0-SNAPSHOT</version>
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
           force("org.trustweave:trustweave-common:1.0.0-SNAPSHOT")
       }
   }
   ```

3. **Use BOM** (when available):
   ```kotlin
   dependencies {
       implementation(platform("org.trustweave:trustweave-bom:1.0.0-SNAPSHOT"))
       implementation("org.trustweave:trustweave-trust")
       implementation("org.trustweave:trustweave-did")
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
- [Migration Guides](../migration/README.md) - Upgrade instructions
- [Module Overview](../modules/core-modules.md) - Module dependencies

---

**Last Updated:** January 2025  
**TrustWeave Version:** 1.0.0-SNAPSHOT
