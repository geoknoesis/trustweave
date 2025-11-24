# Reproduction Steps Using Actual Project

Since the minimal reproducer may not trigger the bug, here are guaranteed reproduction steps using the actual Trustweave project.

## Prerequisites

- Kotlin Gradle Plugin 2.2.21 (or any version 1.9.24+)
- Gradle 9.2.0 (or any version 8.0+)
- Java 21

## Steps

1. **Clone the Trustweave project** (or use existing project)

2. **Set the property in `gradle.properties`:**
   ```properties
   kotlin.build.archivesTaskOutputAsFriendModule=false
   ```

3. **Remove all workarounds from `credentials/core/build.gradle.kts`:**
   - Remove all `afterEvaluate` blocks
   - Remove all `whenTaskAdded` blocks
   - Remove all debug print statements
   - Keep only the basic configuration:
   ```kotlin
   plugins {
       kotlin("jvm")
       kotlin("plugin.serialization")
   }
   
   dependencies {
       implementation(project(":common"))
       implementation(project(":did:core"))
       
       testImplementation(project(":testkit"))
       testImplementation(project(":kms:core"))
   }
   ```

4. **Run the build:**
   ```bash
   ./gradlew :credentials:core:build
   ```

5. **Observe the circular dependency error:**
   ```
   Circular dependency between the following tasks:
   :credentials:core:classes
   \--- :credentials:core:compileJava
        +--- :credentials:core:compileKotlin
        |    \--- :credentials:core:jar
        |         +--- :credentials:core:classes (*)
        |         +--- :credentials:core:compileJava (*)
        |         \--- :credentials:core:compileKotlin (*)
        \--- :credentials:core:jar (*)
   ```

## Verification

1. **Verify property is set:**
   ```bash
   cat gradle.properties | grep archivesTaskOutputAsFriendModule
   ```
   Should show: `kotlin.build.archivesTaskOutputAsFriendModule=false`

2. **Verify property is read by Gradle:**
   Add to `credentials/core/build.gradle.kts`:
   ```kotlin
   println("kotlin.build.archivesTaskOutputAsFriendModule = ${project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")}")
   ```
   Run: `./gradlew :credentials:core:build`
   Output should show: `kotlin.build.archivesTaskOutputAsFriendModule = false`

3. **Confirm the bug:**
   - Property is set to `false` ✅
   - Property is read correctly ✅
   - Circular dependency still occurs ❌
   - **Conclusion**: The property is ignored by the Kotlin plugin

## Why This Reproduces the Bug

The actual project has the exact conditions that trigger the bug:
- ✅ Transitive dependency chain: `credentials:core` → `:did:core` → `:common`
- ✅ Complex module structure in `:did:core`
- ✅ Resources in `:did:core` (JSON configuration files)
- ✅ Nested project paths
- ✅ Dependency graph complexity

These conditions together trigger the `archivesTaskOutputAsFriendModule` behavior, which the documented fix doesn't prevent.

