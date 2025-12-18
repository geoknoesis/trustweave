---
title: Development Setup
nav_exclude: true
---

# Development Setup

This guide explains how to set up your development environment for TrustWeave.

## Prerequisites

### Required Software

- **Java 21+** – Required for compilation and runtime
- **Kotlin 2.2.0+** – Included via Gradle plugin
- **Gradle 8.5+** – Automatically downloaded via Gradle Wrapper
- **Git** – For version control

### Optional Software

- **Docker** – Required for `chains/plugins/ganache` tests using TestContainers
- **IntelliJ IDEA** – Recommended IDE with excellent Kotlin support
- **Eclipse** – Alternative IDE with Kotlin plugin

## Cloning the Repository

```bash
git clone https://github.com/geoknoesis/trustweave.git
cd trustweave
```

## Building the Project

### Using Gradle Wrapper

**Unix/Linux/macOS:**
```bash
./gradlew build
```

**Windows:**
```powershell
.\gradlew.bat build
```

### Building Specific Modules

```bash
# Build specific module
./gradlew :trust:build

# Build all modules
./gradlew build
```

## Running Tests

### All Tests

```bash
./gradlew test
```

### Specific Module Tests

```bash
# Run tests for specific module
./gradlew :trust:test

# Run specific test class
./gradlew :trust:test --tests "TrustWeaveTest"
```

### EO Integration Tests

```bash
# Run EO integration tests
./gradlew :chains/plugins/algorand:test --tests "*EoIntegrationTest"
```

**Note:** Some integration tests require Docker for TestContainers.

## IDE Setup

### IntelliJ IDEA

1. Open IntelliJ IDEA
2. Select "Open or Import"
3. Navigate to the TrustWeave directory
4. Select the root `build.gradle.kts` file
5. Select "Open as Project"
6. Wait for Gradle sync to complete

### Eclipse

1. Install Kotlin plugin for Eclipse
2. Import project as Gradle project
3. Wait for Gradle build to complete

## Code Quality

### Formatting Code

```bash
# Check code formatting
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat
```

### Linting

The project uses ktlint for code formatting. Configure your IDE to format on save:

**IntelliJ IDEA:**
1. Install ktlint plugin
2. Enable "Reformat code on save"
3. Configure ktlint as formatter

## Development Workflow

### Creating a Feature Branch

```bash
git checkout -b feature/my-feature
```

### Running Tests Before Commit

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :trust:test
```

### Building Before Commit

```bash
./gradlew build
```

### Code Review Checklist

Before submitting a pull request:

- [ ] All tests pass (`./gradlew test`)
- [ ] Code is formatted (`./gradlew ktlintCheck`)
- [ ] Build succeeds (`./gradlew build`)
- [ ] Documentation updated (if applicable)
- [ ] Commit messages follow conventions

## Troubleshooting

### Gradle Sync Issues

If Gradle sync fails:

```bash
# Clean and rebuild
./gradlew clean build

# Invalidate caches (IntelliJ)
File > Invalidate Caches / Restart
```

### Test Failures

If tests fail:

```bash
# Run with more verbose output
./gradlew test --info

# Run specific test
./gradlew test --tests "SpecificTestClass.testMethod"
```

### Dependency Issues

If dependencies fail to resolve:

```bash
# Refresh dependencies
./gradlew --refresh-dependencies

# Clean build
./gradlew clean build
```

## Next Steps

- Review [Code Style](code-style.md) for coding conventions
- See [Testing Guidelines](testing-guidelines.md) for testing practices
- Check [Pull Request Process](pull-request-process.md) for contribution workflow
- Explore [Creating Plugins](creating-plugins.md) for plugin development

## References

- [Gradle Documentation](https://docs.gradle.org/)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [ktlint Documentation](https://ktlint.github.io/)

