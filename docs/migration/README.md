# Migration Guides

This section provides migration guides for upgrading between VeriCore versions and understanding deprecation policies.

## Version Compatibility Matrix

| VeriCore Version | Kotlin | Java | Gradle | Status |
|------------------|--------|------|--------|--------|
| 1.0.0-SNAPSHOT | 2.2.0+ | 21+ | 8.5+ | Current |

## Deprecation Policy

VeriCore follows semantic versioning (SemVer) principles:

- **Major versions** (X.0.0): Breaking changes allowed
- **Minor versions** (0.X.0): New features, backward compatible
- **Patch versions** (0.0.X): Bug fixes only

### Deprecation Timeline

- **Deprecation Announcement**: Deprecated APIs are marked with `@Deprecated` annotation and documented in CHANGELOG
- **Minimum Deprecation Period**: 6 months before removal
- **Removal**: Deprecated APIs are removed in the next major version

### How to Handle Deprecations

1. **Check CHANGELOG**: Review deprecation notices in [CHANGELOG.md](../../CHANGELOG.md)
2. **Update Code**: Replace deprecated APIs with recommended alternatives
3. **Test Thoroughly**: Ensure your code works with new APIs
4. **Plan Migration**: Schedule migration before major version upgrade

## Migration Guides

### Migrating to 1.0.0

See [Migrating to 1.0.0](migrating-to-1.0.0.md) for detailed migration instructions covering:
- Type-safe options migration
- Result-based API migration
- Error handling updates
- Plugin lifecycle changes

## Breaking Changes

Breaking changes are clearly marked in the CHANGELOG with migration instructions. Common breaking changes include:

- API signature changes
- Return type changes
- Exception type changes
- Configuration format changes

## Getting Help

If you encounter issues during migration:

1. Check the [FAQ](../faq.md) for common questions
2. Review [Error Handling](../advanced/error-handling.md) for error patterns
3. Open an issue on GitHub with migration details
4. Contact support at [www.geoknoesis.com](https://www.geoknoesis.com)

