# Development Guide

This guide covers building, testing, and contributing to TrustWeave.

## Building

The project uses Gradle Wrapper for consistent builds:

**Unix/Linux/macOS:**
```bash
./gradlew build
```

**Windows:**
```powershell
.\gradlew.bat build
```

**Build without tests:**
```bash
./gradlew build -x test
```

## Testing

```bash
./gradlew test
```

**Run specific test:**
```bash
./gradlew :TrustWeave-ganache:test --tests "*GanacheEoIntegrationTest*"
```

## Code Quality

The project includes code quality tools:

**Check code formatting:**
```bash
./gradlew ktlintCheck
```

**Auto-format code:**
```bash
./gradlew ktlintFormat
```

## Requirements

- **Java 21+**: Required for compilation and runtime
- **Kotlin 2.2.0+**: Included via Gradle plugin
- **Gradle 8.5+**: Automatically downloaded via Gradle Wrapper (no manual installation needed)
- **Docker** (optional): Required for `com.trustweave.chains:ganache` tests using TestContainers

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to contribute to TrustWeave.

## Security

Security vulnerabilities should be reported privately. See [SECURITY.md](SECURITY.md) for our security policy and reporting guidelines.

**Report security issues to:** security@geoknoesis.com

## Additional Resources

- [Getting Started Guide](GETTING_STARTED.md) - Examples and tutorials
- [API Guide](API_GUIDE.md) - API usage patterns
- [Architecture & Modules](ARCHITECTURE.md) - Module details
- [Available Plugins](PLUGINS.md) - Plugin documentation
- [Comprehensive Documentation](docs/) - Full documentation in the `docs/` directory



