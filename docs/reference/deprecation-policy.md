---
title: Deprecation Policy
nav_order: 2
parent: Reference
keywords:
  - deprecation
  - policy
  - breaking changes
  - migration
  - lifecycle
---

# Deprecation Policy

This document outlines TrustWeave's deprecation policy and lifecycle management.

## Overview

TrustWeave follows semantic versioning and maintains a clear deprecation policy to ensure smooth upgrades and minimize breaking changes.

## Deprecation Lifecycle

### Timeline

1. **Announcement** - Feature marked as `@Deprecated` in code and documentation
2. **Deprecation Period** - Feature remains functional for at least one major version
3. **Removal** - Feature removed in next major version (e.g., removed in 2.0.0 if deprecated in 1.x)

### Example Timeline

```
Version 1.0.0: Feature introduced
Version 1.5.0: Feature deprecated (@Deprecated annotation added)
Version 1.x:   Feature still works (deprecation warnings)
Version 2.0.0: Feature removed (breaking change)
```

## Deprecation Markers

### Code Markers

Deprecated APIs are marked with Kotlin's `@Deprecated` annotation:

```kotlin
@Deprecated(
    message = "Use trustWeave.verify() instead",
    replaceWith = ReplaceWith("trustWeave.verify { credential(credential) }"),
    level = DeprecationLevel.WARNING
)
fun verifyCredential(credential: VerifiableCredential): VerificationResult {
    // Deprecated implementation
}
```

### Documentation Markers

Deprecated features are clearly marked in documentation:

```markdown
> **Deprecated:** This API is deprecated and will be removed in version 2.0.0.
> Use [new API](link) instead.
```

## What Gets Deprecated

### APIs and Methods

- Public methods that are no longer recommended
- Configuration options that are superseded
- Interfaces that have better alternatives

### Classes and Types

- Entire classes that are replaced by better designs
- Type aliases that are no longer needed
- Enums that are replaced by sealed classes

### Modules

- Entire modules that are consolidated or replaced
- Plugin modules that are superseded by better implementations

## What Doesn't Get Deprecated

### Internal APIs

- Private or internal methods (can be changed without deprecation)
- Package-private classes (implementation details)

### Experimental Features

- Features marked as `@Experimental` (may change without deprecation)
- Features in preview/beta (subject to change)

### Bug Fixes

- Bug fixes that change behavior (no deprecation needed)
- Security fixes (may break compatibility without deprecation)

## Migration Support

### Deprecation Messages

All deprecated APIs include:
- **Clear message** explaining why it's deprecated
- **Replacement API** (when available)
- **Migration guide** (for complex changes)

### Migration Guides

Major deprecations include migration guides:
- Step-by-step migration instructions
- Code examples showing old vs. new API
- Common pitfalls and solutions

## Version Policy

### Semantic Versioning

TrustWeave follows [Semantic Versioning](https://semver.org/):

- **MAJOR** (1.0.0 → 2.0.0): Breaking changes, deprecations removed
- **MINOR** (1.0.0 → 1.1.0): New features, backward compatible
- **PATCH** (1.0.0 → 1.0.1): Bug fixes, backward compatible

### Breaking Changes

Breaking changes only occur in major versions:
- **Minor versions** (1.0.0 → 1.1.0): Additive only, no breaking changes
- **Patch versions** (1.0.0 → 1.0.1): Bug fixes only, no breaking changes
- **Major versions** (1.0.0 → 2.0.0): May include breaking changes

## Deprecation Process

### 1. Announcement

- Feature marked with `@Deprecated` annotation
- Documentation updated with deprecation notice
- Release notes mention deprecation
- Migration guide provided (if needed)

### 2. Deprecation Period

- Feature continues to work (may show warnings)
- Documentation clearly marks as deprecated
- Migration guides available
- Support provided during transition

### 3. Removal

- Feature removed in next major version
- Migration guide updated
- Release notes document removal
- Breaking changes documented

## Checking for Deprecations

### Compile-Time Warnings

Kotlin compiler shows deprecation warnings:

```kotlin
// Warning: 'verifyCredential()' is deprecated
val result = verifyCredential(credential)
```

### Runtime Warnings

Some deprecated APIs may log warnings at runtime:

```kotlin
// May log: "API X is deprecated, use Y instead"
deprecatedMethod()
```

### IDE Support

IDEs (IntelliJ IDEA, VS Code) show deprecation warnings:
- Strikethrough text for deprecated symbols
- Hover tooltips with deprecation messages
- Quick fixes to migrate to new APIs

## Current Deprecations

### Version 1.0.0-SNAPSHOT

**No deprecations** (initial version)

> **Note:** As TrustWeave evolves, deprecated APIs will be listed here with migration instructions.

## Reporting Issues

If you encounter issues with deprecated APIs:

1. **Check migration guides** for solutions
2. **Report bugs** in deprecated APIs (still supported during deprecation period)
3. **Request clarification** on migration paths
4. **Suggest improvements** to deprecation messages

## Best Practices

### For Users

1. **Monitor deprecation warnings** in your codebase
2. **Migrate proactively** before major version upgrades
3. **Review release notes** for deprecation announcements
4. **Test migrations** in staging before production

### For Maintainers

1. **Deprecate thoughtfully** (provide clear migration path)
2. **Document deprecations** clearly in code and docs
3. **Maintain deprecated APIs** during deprecation period
4. **Remove deprecated APIs** in major versions only

## Related Documentation

- [Version Compatibility Matrix](version-compatibility.md) - Version requirements
- [Migration Guides](../migration/README.md) - Upgrade instructions
- [API Reference](../api-reference/README.md) - Complete API documentation
- [Release Notes](../CHANGELOG.md) - Version history and changes

---

**Last Updated:** January 2025  
**Policy Version:** 1.0  
**TrustWeave Version:** 1.0.0-SNAPSHOT
