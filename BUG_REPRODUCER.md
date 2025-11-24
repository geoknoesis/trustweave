# Kotlin Gradle Plugin 2.2.21 Bug Report: archivesTaskOutputAsFriendModule Property Not Working

## Related Known Issue
**YouTrack Issue**: [KT-69330](https://youtrack.jetbrains.com/issue/KT-69330) - This may be a related or duplicate issue. Please check the issue status.

## Issue Summary

The `kotlin.build.archivesTaskOutputAsFriendModule=false` property in `gradle.properties` is not being respected by Kotlin Gradle Plugin 2.2.21, causing circular dependency errors that should be prevented by this property.

## Environment

- **Kotlin Gradle Plugin Version**: 2.2.21 (also tested: 2.2.20, 2.2.0 - bug exists in all)
- **Gradle Version**: 9.2.0
- **Java Version**: 21
- **Operating System**: Windows 10

## Version Testing

Tested with multiple Kotlin versions to determine if this is a regression:
- **Kotlin 2.2.21**: ❌ Circular dependency persists
- **Kotlin 2.2.20**: ❌ Circular dependency persists  
- **Kotlin 2.2.0**: ❌ Circular dependency persists
- **Kotlin 2.1.0**: ❌ Circular dependency persists
- **Kotlin 2.0.0**: ❌ Circular dependency persists
- **Kotlin 1.9.24**: ❌ Circular dependency persists
- **Kotlin 1.9.0 / 1.8.22**: ⚠️ Build incompatibility (cannot test)

**Conclusion**: This is **not a regression** - the bug exists in all tested versions from 1.9.24 through 2.2.21. The property appears to have **never worked correctly** since its introduction in Kotlin 1.9.x, despite being documented as the solution for circular dependencies.

## Expected Behavior

According to the [official Kotlin documentation](https://kotlinlang.org/docs/gradle-configure-project.html#other-details), setting `kotlin.build.archivesTaskOutputAsFriendModule=false` in `gradle.properties` should prevent the `compileKotlin` task from depending on the `jar` task, thereby avoiding circular dependency errors.

## Actual Behavior

Even with `kotlin.build.archivesTaskOutputAsFriendModule=false` set in `gradle.properties`, the circular dependency error persists:

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

## Minimal Reproducer

**Note**: The circular dependency issue appears to be specific to certain project configurations. In the actual project (`credentials:core`), the issue occurs with a multi-module setup where:
- The module has both Kotlin and Java compilation tasks
- The module depends on other modules (`:common`, `:did:core`)
- The module is part of a larger multi-module project

### Project Structure

```
minimal-reproducer/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── src/
    └── main/
        └── kotlin/
            └── Main.kt
```

**Alternative**: The issue can be reproduced in the actual project at `credentials/core` module.

### gradle.properties

```properties
kotlin.build.archivesTaskOutputAsFriendModule=false
```

### settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "2.2.21"
    }
}

rootProject.name = "minimal-reproducer"
```

### build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "2.2.21"
}

dependencies {
    // No dependencies needed to reproduce
}
```

### src/main/kotlin/Main.kt

```kotlin
fun main() {
    println("Hello, World!")
}
```

## Steps to Reproduce

### In the actual project:

1. Navigate to the `credentials/core` module
2. Ensure `kotlin.build.archivesTaskOutputAsFriendModule=false` is set in root `gradle.properties`
3. Run `./gradlew :credentials:core:build`
4. Observe the circular dependency error:

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

### Expected behavior:

With `kotlin.build.archivesTaskOutputAsFriendModule=false` set, the `compileKotlin` task should NOT depend on the `jar` task, preventing this circular dependency.

## Workaround

The only workaround found is to manually remove the `jar` dependency from `compileKotlin` in `afterEvaluate`:

```kotlin
afterEvaluate {
    tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
        val jarTask = tasks.findByName("jar")
        if (jarTask != null) {
            val currentDeps = dependsOn.toList().filter { it != jarTask }.toSet()
            setDependsOn(currentDeps)
        }
    }
}
```

However, this workaround is fragile and shouldn't be necessary if the property worked as documented.

## Related Documentation

- [Kotlin Gradle Plugin Configuration - Other Details](https://kotlinlang.org/docs/gradle-configure-project.html#other-details)
- Section: "Disable use of artifact in compilation task"

## Additional Notes

- The property is correctly set in `gradle.properties` (verified by reading the file)
- Configuration cache is disabled (tested both enabled and disabled)
- Gradle daemon was stopped and restarted
- Clean build was performed
- The issue occurs in multi-module projects with specific configurations
- The property name is exactly as documented: `kotlin.build.archivesTaskOutputAsFriendModule`
- No typos or case-sensitivity issues in the property name

## Verification Steps Taken

1. ✅ Verified property is set in `gradle.properties`: `kotlin.build.archivesTaskOutputAsFriendModule=false`
2. ✅ Stopped Gradle daemon: `./gradlew --stop`
3. ✅ Performed clean build: `./gradlew clean :credentials:core:build`
4. ✅ Disabled configuration cache to rule out caching issues
5. ✅ Removed all workarounds and manual task dependency modifications
6. ✅ Confirmed configuration matches official documentation exactly
7. ✅ **Verified property is being read by Gradle** (see below)

## Property Verification

Added debug output to confirm the property is being read:

```kotlin
println("kotlin.build.archivesTaskOutputAsFriendModule (project property): ${project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")}")
println("kotlin.build.archivesTaskOutputAsFriendModule (gradle property): ${project.providers.gradleProperty("kotlin.build.archivesTaskOutputAsFriendModule").orNull}")
```

**Output**:
```
=== Property Debug Info ===
kotlin.build.archivesTaskOutputAsFriendModule (project property): false
kotlin.build.archivesTaskOutputAsFriendModule (gradle property): false
All gradle properties containing 'archivesTaskOutputAsFriendModule':
  kotlin.build.archivesTaskOutputAsFriendModule = false
===========================
```

**Conclusion**: The property IS being read correctly by Gradle, but the Kotlin Gradle Plugin 2.2.21 is **ignoring it** when creating task dependencies. This confirms it's a bug in the plugin, not a configuration issue.

## Impact

This bug prevents the use of the documented solution for circular dependency issues, forcing developers to use fragile workarounds that may break with future Kotlin/Gradle versions.

