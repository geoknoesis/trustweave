# IDE Setup Guide

This guide helps you set up your development environment for working with TrustWeave.

## IntelliJ IDEA

### Recommended Setup

1. **Install IntelliJ IDEA**
   - Download from [JetBrains website](https://www.jetbrains.com/idea/)
   - Community Edition is sufficient for TrustWeave development

2. **Install Kotlin Plugin**
   - Kotlin plugin is included by default in IntelliJ IDEA
   - Ensure Kotlin version matches project requirements (2.2.0+)

3. **Recommended Plugins**
   - **Markdown**: For viewing documentation (built-in)
   - **Git**: For version control (built-in)
   - **Gradle**: For build management (built-in)

### Code Style Configuration

TrustWeave uses ktlint for code formatting. Configure IntelliJ to match:

1. **Enable ktlint Formatting**
   ```bash
   ./gradlew ktlintFormat
   ```

2. **Import Code Style** (Optional)
   - File → Settings → Editor → Code Style → Kotlin
   - Import from `.editorconfig` if available

### Debugging Setup

1. **Set Breakpoints**
   - Click in the gutter next to line numbers
   - Red dot indicates active breakpoint

2. **Run with Debugger**
   - Right-click on `main()` function → Debug
   - Or use Debug button in toolbar

3. **Inspect Variables**
   - Hover over variables to see values
   - Use "Evaluate Expression" (Alt+F8) to evaluate expressions
   - Use "Variables" panel to inspect all variables

4. **Debugging Coroutines**
   - IntelliJ IDEA has built-in coroutine debugging support
   - Use "Coroutines" panel to inspect coroutine state

### Example Debug Configuration

Create a run configuration for debugging:

1. Run → Edit Configurations
2. Click "+" → Kotlin
3. Configure:
   - **Name**: TrustWeave Debug
   - **Main class**: Your main class
   - **Use classpath of module**: Select your module
4. Apply and OK

## VS Code

### Recommended Setup

1. **Install VS Code**
   - Download from [VS Code website](https://code.visualstudio.com/)

2. **Install Extensions**
   - **Kotlin Language**: Official Kotlin extension
   - **Markdown Preview Enhanced**: For viewing documentation
   - **Gradle for Java**: For Gradle support

### Code Style Configuration

1. **Install ktlint Extension** (Optional)
   - Search for "ktlint" in Extensions
   - Install and configure

2. **Format on Save**
   - File → Preferences → Settings
   - Search "format on save"
   - Enable for Kotlin files

### Debugging Setup

1. **Create Launch Configuration**
   Create `.vscode/launch.json`:
   ```json
   {
     "version": "0.2.0",
     "configurations": [
       {
         "type": "kotlin",
         "request": "launch",
         "name": "Debug TrustWeave",
         "mainClass": "com.example.MainKt",
         "projectRoot": "${workspaceFolder}",
         "modulePaths": ["${workspaceFolder}/build/classes/kotlin/main"]
       }
     ]
   }
   ```

2. **Set Breakpoints**
   - Click in the gutter next to line numbers
   - Red dot indicates active breakpoint

3. **Start Debugging**
   - Press F5 or use Debug panel
   - Select "Debug TrustWeave" configuration

## Common IDE Tasks

### Running Tests

**IntelliJ IDEA:**
- Right-click test class → Run
- Or use Gradle tool window → Tasks → test

**VS Code:**
- Use Gradle extension to run tests
- Or use terminal: `./gradlew test`

### Viewing Documentation

**IntelliJ IDEA:**
- Hover over symbols to see KDoc
- Use Quick Documentation (Ctrl+Q / Cmd+J)
- Navigate to source (Ctrl+B / Cmd+B)

**VS Code:**
- Hover over symbols to see documentation
- Use "Go to Definition" (F12)

### Code Navigation

**IntelliJ IDEA:**
- **Go to Declaration**: Ctrl+B / Cmd+B
- **Find Usages**: Alt+F7 / Cmd+Option+F7
- **Navigate to Symbol**: Ctrl+Alt+Shift+N / Cmd+Option+O

**VS Code:**
- **Go to Definition**: F12
- **Find References**: Shift+F12
- **Go to Symbol**: Ctrl+Shift+O / Cmd+Shift+O

## Troubleshooting

### Issue: Kotlin Version Mismatch

**Problem**: IDE shows Kotlin version different from project

**Solution**: 
- File → Project Structure → Project → Kotlin version
- Set to match `build.gradle.kts`

### Issue: Gradle Sync Fails

**Problem**: IDE can't sync Gradle project

**Solution**:
- File → Invalidate Caches / Restart
- Or: Gradle tool window → Reload Gradle Project

### Issue: Code Completion Not Working

**Problem**: IDE doesn't show autocomplete for TrustWeave APIs

**Solution**:
- File → Invalidate Caches / Restart
- Ensure dependencies are resolved: Gradle tool window → Refresh

### Issue: Debugger Not Stopping at Breakpoints

**Problem**: Breakpoints not hit during debugging

**Solution**:
- Ensure you're running in Debug mode (not Run mode)
- Check that code is compiled with debug symbols
- Verify breakpoint is in active code path

## Next Steps

- [Quick Start](quick-start.md) - Create your first TrustWeave application
- [Your First Application](your-first-application.md) - Build a complete example
- [API Reference](../api-reference/core-api.md) - Complete API documentation

