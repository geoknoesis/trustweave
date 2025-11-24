# Bug Report: kotlin.build.archivesTaskOutputAsFriendModule Property Not Working

## Issue Summary

The `kotlin.build.archivesTaskOutputAsFriendModule=false` property in `gradle.properties` is **not respected** by Kotlin Gradle Plugin 2.2.21, causing circular dependency errors that should be prevented by this property.

## Environment

- **Kotlin Gradle Plugin Version**: 2.2.21 (also tested: 2.2.20, 2.2.0, 2.1.0, 2.0.20, 1.9.24 - bug exists in all)
- **Gradle Version**: 9.2.0 (also tested: 8.0, 8.5, 8.10 - bug exists in all)
- **Java Version**: 21
- **Operating System**: Windows 10

## Expected Behavior

According to the [official Kotlin documentation](https://kotlinlang.org/docs/gradle-configure-project.html#other-details), setting `kotlin.build.archivesTaskOutputAsFriendModule=false` in `gradle.properties` should prevent the `compileKotlin` task from depending on the `jar` task, thereby avoiding circular dependency errors.

## Actual Behavior

Even with `kotlin.build.archivesTaskOutputAsFriendModule=false` set in `gradle.properties`, the circular dependency error persists:

```
Circular dependency between the following tasks:
:module-a:classes
\--- :module-a:compileJava
     +--- :module-a:compileKotlin
     |    \--- :module-a:jar
     |         +--- :module-a:classes (*)
     |         +--- :module-a:compileJava (*)
     |         \--- :module-a:compileKotlin (*)
     \--- :module-a:jar (*)
```

## Steps to Reproduce

### Using This Minimal Reproducer ✅

1. Clone this minimal reproducer project
2. Ensure `kotlin.build.archivesTaskOutputAsFriendModule=false` is set in `gradle.properties`
3. Run `./gradlew :credentials:core:build`
4. Observe the circular dependency error

**Note**: The bug is successfully reproduced using the exact same module names as the actual project (`credentials:core`, `did:core`, `common`).

### Alternative: Using the Actual Project

1. Clone the Trustweave project
2. Ensure `kotlin.build.archivesTaskOutputAsFriendModule=false` is set in `gradle.properties`
3. Remove all workarounds from `credentials/core/build.gradle.kts` (remove all `afterEvaluate` and `whenTaskAdded` blocks)
4. Run `./gradlew :credentials:core:build`
5. Observe the circular dependency error

The bug is guaranteed to reproduce in the actual project with the following conditions:
- `credentials:core` depends on `:did:core`
- `:did:core` depends on `:common` (transitive chain)
- `:did:core` has resources and complex structure

## Project Structure

This minimal reproducer mimics the conditions that trigger the bug:

- **Module A** (like `credentials:core`): Depends on Module B
- **Module B** (like `did:core`): 
  - Depends on Module C (transitive dependency chain)
  - Has resources (JSON file)
  - Uses Kotlin serialization
- **Module C** (like `common`): Simple module with no dependencies

The transitive dependency chain (`module-a → module-b → module-c`) combined with Module B's complexity (resources, serialization) triggers the bug.

## Property Verification

The property IS being read correctly by Gradle (verified via debug output), but the Kotlin plugin ignores it when creating task dependencies.

## Workaround

No working workaround has been found. All attempted solutions failed:
- Setting property in various locations (gradle.properties, system property, project property)
- Removing task dependencies manually
- Using different dependency configurations (api, compileOnly)
- Disabling compileJava task
- Modifying artifact naming

## Related Issues

- **KT-69330**: Marked as "resolved" but the fix doesn't work for this scenario
- The fix was supposedly introduced in Kotlin 2.0.20, but testing shows the bug persists even in that version

## Impact

This bug prevents builds for projects with:
- Transitive project dependency chains
- Modules with resources
- Complex module structures
- Nested project paths

The documented solution doesn't work, forcing developers to use fragile workarounds or restructure their projects.

