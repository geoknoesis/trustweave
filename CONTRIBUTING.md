# Contributing to TrustWeave

Thank you for your interest in contributing to TrustWeave! This document provides guidelines and information for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Code Style](#code-style)
- [Pull Request Process](#pull-request-process)
- [Creating Plugins](#creating-plugins)
- [Testing](#testing)
- [Documentation](#documentation)
- [Reporting Issues](#reporting-issues)
- [License](#license)

## Code of Conduct

TrustWeave is committed to providing a welcoming and inclusive environment for all contributors. By participating in this project, you agree to abide by our Code of Conduct (see [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)).

## Getting Started

### Prerequisites

- **Java 21+** - Required for compilation and runtime
- **Kotlin 2.2.0+** - Included via Gradle plugin
- **Gradle 8.5+** - Automatically downloaded via Gradle Wrapper
- **Git** - For version control
- **Docker** (optional) - Required for some integration tests using TestContainers

### Quick Start

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/trustweave.git
   cd trustweave
   ```
3. **Set up the upstream remote**:
   ```bash
   git remote add upstream https://github.com/geoknoesis/trustweave.git
   ```
4. **Build the project**:
   ```bash
   # Unix/Linux/macOS
   ./gradlew build
   
   # Windows
   .\gradlew.bat build
   ```
5. **Run tests** to verify your setup:
   ```bash
   ./gradlew test
   ```

For detailed setup instructions, see [Development Setup Guide](docs/contributing/development-setup.md).

## How to Contribute

There are many ways to contribute to TrustWeave:

### Reporting Bugs

Before reporting a bug, please:
1. Check existing issues to see if it's already reported
2. Try to reproduce the issue with the latest version
3. Gather relevant information (error messages, logs, steps to reproduce)

When creating a bug report, include:
- Clear description of the issue
- Steps to reproduce
- Expected vs actual behavior
- Environment details (Java version, OS, etc.)
- Relevant code snippets or logs

### Suggesting Enhancements

We welcome suggestions for new features and improvements! When suggesting an enhancement:
1. Check if it's already been suggested or discussed
2. Describe the use case clearly
3. Explain why it would be valuable
4. Consider implementation complexity

### Contributing Code

We welcome code contributions! Here's the typical workflow:

1. **Find an issue** or create one describing what you want to work on
2. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes** following our [code style guidelines](#code-style)
4. **Write tests** for your changes
5. **Run tests** to ensure everything passes:
   ```bash
   ./gradlew test
   ```
6. **Check code formatting**:
   ```bash
   ./gradlew ktlintCheck
   ```
7. **Commit your changes** using [Conventional Commits](https://www.conventionalcommits.org/):
   ```bash
   git commit -m "feat(module): add new feature"
   ```
8. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```
9. **Create a Pull Request** (see [Pull Request Process](#pull-request-process))

### Contributing Documentation

Documentation improvements are always welcome! This includes:
- Fixing typos or unclear explanations
- Adding examples or tutorials
- Improving API documentation
- Translating documentation

See [Documentation Guidelines](#documentation) for more details.

### Contributing Plugins

TrustWeave's plugin system allows you to add support for:
- New DID methods
- Additional blockchain networks
- Different key management services
- Custom proof types
- Wallet storage backends

See [Creating Plugins](#creating-plugins) for detailed instructions.

## Development Setup

### Building the Project

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :did:did-core:build

# Build without tests
./gradlew build -x test
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :did:did-core:test

# Run specific test class
./gradlew :did:did-core:test --tests "DidMethodTest"

# Run with verbose output
./gradlew test --info
```

### IDE Setup

**IntelliJ IDEA** (recommended):
1. Open IntelliJ IDEA
2. Select "Open or Import"
3. Navigate to the TrustWeave directory
4. Select the root `build.gradle.kts` file
5. Select "Open as Project"
6. Wait for Gradle sync to complete

For more details, see the [Development Setup Guide](docs/contributing/development-setup.md).

## Code Style

TrustWeave follows Kotlin coding conventions with automated formatting via ktlint.

### Formatting Code

```bash
# Check code formatting
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat
```

### Key Guidelines

- **Naming**: Use camelCase for functions/variables, PascalCase for classes
- **Documentation**: Document all public APIs with KDoc comments
- **Error Handling**: Use `Result<T>` for operations that can fail
- **Immutability**: Prefer immutable data classes
- **Null Safety**: Leverage Kotlin's null safety features

For detailed guidelines, see [Code Style Guide](docs/contributing/code-style.md).

### Pre-commit Checklist

Before committing, ensure:
- [ ] All tests pass (`./gradlew test`)
- [ ] Code is formatted (`./gradlew ktlintCheck`)
- [ ] Build succeeds (`./gradlew build`)
- [ ] Public APIs are documented with KDoc
- [ ] No hardcoded values (use constants)

## Pull Request Process

### Before Submitting

1. **Update your branch** with the latest changes from `main`:
   ```bash
   git checkout main
   git pull upstream main
   git checkout feature/your-feature-name
   git rebase main
   ```

2. **Ensure all checks pass**:
   - All tests pass
   - Code is properly formatted
   - Build succeeds
   - Documentation is updated

### Pull Request Title

Use descriptive titles following Conventional Commits format:

```
✅ Good: "feat(did): add support for did:web DID method"
✅ Good: "fix(anchor): fix transaction confirmation wait logic"
❌ Bad: "Updates"
❌ Bad: "WIP"
```

### Pull Request Description

Include:
- **Purpose** - What problem does this solve?
- **Changes** - What was changed?
- **Testing** - How was it tested?
- **Related Issues** - Link to related issues using `Fixes #123`

Example:
```markdown
## Purpose
Adds support for the did:web DID method.

## Changes
- Implemented WebDidMethod class
- Added WebDidMethodProvider for SPI discovery
- Added tests for did:web operations

## Testing
- All existing tests pass
- Added unit tests for WebDidMethod
- Added integration tests for did:web resolution

## Related Issues
Fixes #123
```

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation changes
- `style` - Code style changes (formatting)
- `refactor` - Code refactoring
- `test` - Test changes
- `chore` - Build/tool changes

**Example:**
```
feat(did): add did:web DID method support

Implemented WebDidMethod class with full W3C spec compliance.
Added SPI provider for auto-discovery.

Fixes #123
```

For complete details, see [Pull Request Process Guide](docs/contributing/pull-request-process.md).

## Creating Plugins

TrustWeave's plugin architecture allows you to extend functionality by implementing plugin interfaces:

- **DidMethod** - Custom DID methods
- **BlockchainAnchorClient** - New blockchain networks
- **KeyManagementService** - Different KMS backends
- **ProofGenerator** - Custom proof types
- **WalletFactory** - Custom wallet storage

Plugins can be registered manually or discovered automatically via SPI (Service Provider Interface).

For comprehensive instructions, see [Creating Plugins Guide](docs/contributing/creating-plugins.md).

## Testing

### Test Guidelines

- **Coverage**: Aim for high test coverage, especially for critical paths
- **Naming**: Use descriptive test names: `testCreateDidWithEd25519Algorithm()`
- **Organization**: Group related tests in test classes
- **Isolation**: Tests should be independent and not rely on execution order
- **Mocking**: Use in-memory implementations from `testkit` module when possible

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :did:did-core:test

# Run specific test
./gradlew :did:did-core:test --tests "DidMethodTest.testCreateDid"

# Run integration tests (requires Docker)
./gradlew :anchors:plugins:ganache:test --tests "*IntegrationTest"
```

### Test Templates

Test templates are available for common plugin types:
- `DidMethodTestTemplate.kt`
- `ChainPluginTestTemplate.kt`
- `KmsPluginTestTemplate.kt`

For detailed testing guidelines, see [Testing Guidelines](docs/contributing/testing-guidelines.md).

## Documentation

### Documentation Structure

- **Getting Started** - Quick starts, installation, tutorials
- **Core Concepts** - Conceptual explanations with code examples
- **API Reference** - Detailed API documentation (auto-generated from KDoc)
- **Tutorials** - End-to-end flows with prerequisites
- **Integration Guides** - Plugin-specific documentation

### Writing Style

- **Audience**: Experienced Kotlin developers who value clarity
- **Tone**: Conversational but professional
- **Formatting**: Use Markdown with ATX headings
- **Code Samples**: Every code block should be bracketed by prose explaining what it does
- **Terminology**: Refer to modules in inline code (e.g., `trustweave-did`)

### Documentation Verification

Before submitting documentation changes:
```bash
# Ensure Kotlin samples compile
./gradlew build

# Run quick-start examples if modified
./gradlew :distribution:examples:runQuickStartSample
```

For detailed guidelines, see the [Contributing to Documentation](docs/contributing/README.md).

## Reporting Issues

### Before Creating an Issue

1. Search existing issues to avoid duplicates
2. Check if it's already fixed in `main`
3. Gather relevant information

### Creating a Good Issue Report

Include:
- **Clear title** - Brief summary of the issue
- **Description** - Detailed explanation of the problem
- **Steps to reproduce** - Step-by-step instructions
- **Expected behavior** - What should happen
- **Actual behavior** - What actually happens
- **Environment** - Java version, OS, TrustWeave version
- **Error messages/logs** - If applicable
- **Code samples** - Minimal reproduction code if possible

### Issue Templates

We provide issue templates for:
- Bug reports
- Feature requests
- Plugin requests
- Documentation improvements
- Questions

Use the appropriate template when creating an issue.

## License

By contributing to TrustWeave, you agree that your contributions will be licensed under the same license as the project:

- **Community contributions** are licensed under the AGPL v3.0 license (see [LICENSE](LICENSE))
- **Commercial licensing** is available for proprietary use (see [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md))

## Getting Help

- **GitHub Discussions** - Ask questions and discuss ideas
- **GitHub Issues** - Report bugs and request features
- **Documentation** - Check the [docs](docs/) directory
- **Contributing Guides** - See [docs/contributing/](docs/contributing/) for detailed guides

## Recognition

Contributors are recognized in:
- Release notes
- Contributor list (if we add one)
- Documentation acknowledgments

Thank you for contributing to TrustWeave! 🙏

---

For detailed information on specific topics, see:
- [Development Setup](docs/contributing/development-setup.md)
- [Code Style](docs/contributing/code-style.md)
- [Pull Request Process](docs/contributing/pull-request-process.md)
- [Creating Plugins](docs/contributing/creating-plugins.md)
- [Testing Guidelines](docs/contributing/testing-guidelines.md)
- [Contributing to Documentation](docs/contributing/README.md)

