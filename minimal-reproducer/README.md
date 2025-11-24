# Minimal Reproducer for KT-69330 Bug

This is a minimal project that reproduces the circular dependency bug where `kotlin.build.archivesTaskOutputAsFriendModule=false` doesn't work.

## Project Structure

```
minimal-reproducer/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── module-a/
│   └── build.gradle.kts
│   └── src/main/kotlin/
│       └── ModuleA.kt
└── module-b/
    └── build.gradle.kts
    └── src/main/kotlin/
        └── ModuleB.kt
```

## Reproduction Steps

1. Set `kotlin.build.archivesTaskOutputAsFriendModule=false` in `gradle.properties`
2. Run `./gradlew :credentials:core:build`
3. Observe the circular dependency error

**Note**: The bug is successfully reproduced using the exact same module names as the actual project (`credentials:core`, `did:core`, `common`).

## Expected Behavior

With `kotlin.build.archivesTaskOutputAsFriendModule=false`, the `compileKotlin` task should NOT depend on the `jar` task, preventing the circular dependency.

## Actual Behavior

Even with the property set to `false`, the circular dependency persists:
```
Circular dependency between the following tasks:
:module-a:classes
\--- :module-a:compileJava
     +--- :module-a:compileKotlin
     |    \--- :module-a:jar
     |         +--- :module-a:classes (*)
```

## Key Characteristics

This reproducer mimics the conditions that trigger the bug:
- Module A depends on Module B
- Module B depends on a third module (transitive dependency chain)
- Module B has resources (triggers additional processing)
- Module B has a nested project path

