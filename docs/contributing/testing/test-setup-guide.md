---
title: Test Setup Guide
parent: Testing
grand_parent: Contributing to TrustWeave
---

# Test Setup Guide

This guide explains how to set up the test environment for TrustWeave development.

## Prerequisites

- **Java 21+**: Required for compilation and runtime
- **Docker**: Required for TestContainers (integration tests)
- **Gradle 8.5+**: Automatically downloaded via Gradle Wrapper

## Quick Setup

### 1. Install Docker

Ensure Docker is installed and running:

```bash
docker --version
docker ps
```

### 2. Run Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :did:plugins:key:test

# Run only unit tests (skip integration tests)
./gradlew test -PskipIntegrationTests=true

# Run only integration tests
./gradlew test --tests "*IntegrationTest"
```

## Test Environment Variables

Configure test behavior using environment variables:

```bash
# Use real services instead of mocks (requires credentials)
export VERICORE_TEST_USE_REAL_SERVICES=true

# Set test timeout (seconds)
export VERICORE_TEST_TIMEOUT_SECONDS=60

# Skip integration tests
export VERICORE_SKIP_INTEGRATION_TESTS=true

# Set log level
export VERICORE_TEST_LOG_LEVEL=DEBUG
```

## TestContainers Setup

Integration tests use TestContainers for external services:

- **LocalStack**: AWS services (KMS, S3)
- **HashiCorp Vault**: Vault KMS testing
- **Ganache**: Ethereum local node
- **PostgreSQL**: Database wallet tests

TestContainers automatically downloads and manages containers.

## Test Coverage

Coverage tooling is **not configured** in the current repository. The root
`build.gradle.kts` does not apply Kover or JaCoCo, so the `koverReport`,
`koverHtmlReport`, `koverVerify`, and `jacocoTestReport` tasks are absent —
invoking them fails with `Task '<name>' not found in root project`.

If you need coverage in your fork, apply Kover (or JaCoCo) yourself and re-run
tests:

```kotlin
// Root build.gradle.kts
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.7.6"
}
```
```bash
./gradlew test koverHtmlReport
# Open build/reports/kover/html/index.html
```

Wiring coverage into the upstream build is tracked as a separate workstream — file
a build-system issue if you want it enabled by default.

## Troubleshooting

### Docker Issues

If TestContainers fails to start:

```bash
# Check Docker is running
docker ps

# Restart Docker daemon
# Linux/Mac: sudo systemctl restart docker
# Windows: Restart Docker Desktop
```

### Port Conflicts

If ports are already in use:

```bash
# Check what's using the port
# Linux/Mac: lsof -i :4566
# Windows: netstat -ano | findstr :4566

# Kill the process or change TestContainer configuration
```

### Memory Issues

Increase JVM memory for tests:

```bash
export GRADLE_OPTS="-Xmx4g -Xms1g"
./gradlew test
```

## Next Steps

- Writing Tests](writing-tests.md) - Guide for writing new tests
- Test Patterns](test-patterns.md) - Common test patterns
- Integration Testing](integration-testing.md) - Integration test best practices

