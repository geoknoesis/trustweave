# Project Setup

Set up your development environment for working with VeriCore.

## Prerequisites

- **Java 21+**: Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
- **Kotlin 2.2.0+**: Included with Gradle or download from [Kotlin releases](https://github.com/JetBrains/kotlin/releases)
- **Gradle 8.5+**: Download from [Gradle releases](https://gradle.org/releases/)
- **Node.js** (optional): Automatically downloaded by Gradle for `vericore-ganache` module tests. If you want to use a system installation, install from [nodejs.org](https://nodejs.org/)

## IDE Setup

### IntelliJ IDEA

1. Install IntelliJ IDEA (Community or Ultimate).
2. Install Kotlin plugin (usually included).
3. Configure JDK (File → Project Structure → Project) and set SDK to Java 21 so Gradle and the IDE use the same toolchain.
4. Import the project (File → Open → `build.gradle.kts`) and wait for the initial Gradle sync to finish; this downloads dependencies and indexes the source.

### VS Code

1. Install VS Code
2. Install extensions:
   - Kotlin Language
   - Gradle for Java
3. Configure Java:
   - Set `JAVA_HOME` environment variable
   - Install Java Extension Pack

### Android Studio

1. Install Android Studio
2. Configure SDK:
   - Tools → SDK Manager
   - Install JDK 21
3. Create new project:
   - Select Kotlin/JVM
   - Set minimum SDK to 21

## Building from Source

### Clone Repository

Clone the codebase so you can build and run examples locally.

```bash
git clone https://github.com/your-org/vericore.git
cd vericore
```

**Result:** You now have the repository checked out on your machine and are positioned at the project root.

### Build Project

Use Gradle to compile all modules or run targeted workflows. The commands below rely on the Unix shell; Windows equivalents are shown in the next section.

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Build without tests
./gradlew build -x test
```

**Result:** `build/` directories appear under each module. Use `build -x test` when you need a fast compile without blocking on the full test suite.

### Windows

On Windows shells use the Gradle wrapper with `.bat`.

```powershell
# Build all modules
.\gradlew.bat build

# Run tests
.\gradlew.bat test
```

**Result:** Same as the Unix steps but without relying on Git Bash or WSL.

## Project Structure

```
vericore/
├── build.gradle.kts          # Root build file
├── settings.gradle.kts       # Project settings
├── buildSrc/                 # Build configuration
├── vericore-core/            # Core module
├── vericore-json/            # JSON utilities
├── vericore-kms/             # Key management
├── vericore-did/             # DID management
├── vericore-anchor/          # Blockchain anchoring
├── vericore-testkit/         # Test utilities
├── vericore-waltid/          # walt.id integration
├── vericore-godiddy/         # GoDiddy integration
├── vericore-algorand/        # Algorand adapter
└── vericore-polygon/         # Polygon adapter
```

## Development Workflow

### Running Tests

Gradle tasks let you execute tests for the whole workspace or specific modules/classes.

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :vericore-core:test

# Run specific test class
./gradlew :vericore-core:test --tests "VeriCoreExceptionTest"
```

**Result:** Successful runs keep CI parity with local development; failures point you at the module or class that needs attention.

### Code Formatting

VeriCore uses Kotlin's standard formatting. Use IntelliJ IDEA's auto-format:

- **Mac**: `Cmd + Option + L`
- **Windows/Linux**: `Ctrl + Alt + L`

### Linting

Use ktlint to keep style consistent across contributors.

```bash
# Run linting
./gradlew ktlintCheck

# Auto-fix linting issues
./gradlew ktlintFormat
```

**Result:** Lint violations surface before CI runs; the format task fixes common spacing/import issues automatically.

## Debugging

### IntelliJ IDEA

1. Set breakpoints in your code
2. Right-click test → Debug
3. Use debugger controls to step through code

### VS Code

1. Install Kotlin Debug Adapter
2. Create `.vscode/launch.json`:
```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "kotlin",
            "request": "launch",
            "name": "Debug Kotlin",
            "projectRoot": "${workspaceFolder}",
            "mainClass": "com.example.MainKt"
        }
    ]
}
```

**Result:** VS Code can launch and attach to Kotlin processes using the adapter, giving you breakpoints and variable inspection similar to IntelliJ.

## Common Issues

### Java Version Mismatch

**Error**: `Unsupported class file major version`

**Solution**: Ensure Java 21 is installed and configured:
```bash
java -version  # Should show 21.x.x
```

**Result:** The command prints the active JDK version; if it’s not 21+, update `JAVA_HOME` before retrying builds.

### Gradle Daemon Issues

**Error**: Build hangs or fails

**Solution**: Stop and restart Gradle daemon:
```bash
./gradlew --stop
./gradlew build
```

**Result:** Restarts the background daemon, clearing stale caches or hung workers.

### Kotlin Version Conflicts

**Error**: `Kotlin version mismatch`

**Solution**: Update Kotlin version in `buildSrc/src/main/kotlin/Versions.kt`

## Next Steps

- [Quick Start](quick-start.md) - Create your first application
- [Your First Application](your-first-application.md) - Build a complete example
- [Contributing](../contributing/README.md) - Contribute to VeriCore

