# Project Setup

Set up your development environment for working with VeriCore.

## Prerequisites

- **Java 21+**: Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
- **Kotlin 2.2.0+**: Included with Gradle or download from [Kotlin releases](https://github.com/JetBrains/kotlin/releases)
- **Gradle 8.5+**: Download from [Gradle releases](https://gradle.org/releases/)
- **Node.js** (optional): Automatically downloaded by Gradle for `vericore-ganache` module tests. If you want to use a system installation, install from [nodejs.org](https://nodejs.org/)

## IDE Setup

### IntelliJ IDEA

1. Install IntelliJ IDEA (Community or Ultimate)
2. Install Kotlin plugin (usually included)
3. Configure JDK:
   - File → Project Structure → Project
   - Set SDK to Java 21
4. Import Gradle project:
   - File → Open → Select `build.gradle.kts`
   - Wait for Gradle sync

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

```bash
git clone https://github.com/your-org/vericore.git
cd vericore
```

### Build Project

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Build without tests
./gradlew build -x test
```

### Windows

```powershell
# Build all modules
.\gradlew.bat build

# Run tests
.\gradlew.bat test
```

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

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :vericore-core:test

# Run specific test class
./gradlew :vericore-core:test --tests "VeriCoreExceptionTest"
```

### Code Formatting

VeriCore uses Kotlin's standard formatting. Use IntelliJ IDEA's auto-format:

- **Mac**: `Cmd + Option + L`
- **Windows/Linux**: `Ctrl + Alt + L`

### Linting

```bash
# Run linting
./gradlew ktlintCheck

# Auto-fix linting issues
./gradlew ktlintFormat
```

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

## Common Issues

### Java Version Mismatch

**Error**: `Unsupported class file major version`

**Solution**: Ensure Java 21 is installed and configured:
```bash
java -version  # Should show 21.x.x
```

### Gradle Daemon Issues

**Error**: Build hangs or fails

**Solution**: Stop and restart Gradle daemon:
```bash
./gradlew --stop
./gradlew build
```

### Kotlin Version Conflicts

**Error**: `Kotlin version mismatch`

**Solution**: Update Kotlin version in `buildSrc/src/main/kotlin/Versions.kt`

## Next Steps

- [Quick Start](quick-start.md) - Create your first application
- [Your First Application](your-first-application.md) - Build a complete example
- [Contributing](../contributing/README.md) - Contribute to VeriCore

