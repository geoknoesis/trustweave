# Dependency Configuration Analysis - Is `implementation` the Issue?

## Question
Is the circular dependency issue related to using `implementation` for project dependencies in Gradle?

## Current Dependencies in `credentials:core`

```kotlin
dependencies {
    implementation(project(":common"))
    implementation(project(":did:core"))
    
    testImplementation(project(":testkit"))
    testImplementation(project(":kms:core"))
}
```

## Analysis

### 1. Type of Circular Dependency

The circular dependency we're seeing is a **TASK dependency cycle**, not a **PROJECT dependency cycle**:

```
compileKotlin → jar → classes → compileJava → compileKotlin
```

This is about **build tasks within the same module**, not about dependencies between modules.

### 2. Project Dependencies vs Task Dependencies

- **Project Dependencies** (`implementation`, `api`, etc.):
  - Define which modules/artifacts are needed at compile/runtime
  - Resolved by Gradle's dependency resolution
  - Don't directly create task dependencies

- **Task Dependencies**:
  - Define the order in which build tasks execute
  - Created by plugins (like Kotlin plugin)
  - The circular dependency is in task dependencies

### 3. Could `implementation` Still Be Related?

**Potentially, but indirectly:**

When `compileKotlin` needs to compile code that depends on other projects:
1. Kotlin plugin needs to access compiled classes/JARs of those projects
2. This might trigger the `archivesTaskOutputAsFriendModule` behavior
3. The plugin might need the current project's JAR to resolve friend modules
4. This could create the `compileKotlin → jar` dependency

However, this should be about **other projects' JARs**, not the current project's JAR.

### 4. Testing Different Dependency Configurations

We could test:
- **`api` instead of `implementation`**: Exposes dependencies transitively
- **`compileOnly`**: Only needed at compile time
- **Removing dependencies temporarily**: See if issue persists

But based on the KT-69330 description, the issue is about:
- Multiple compilations in the same module
- Generated artifact (JAR) depending on both compilations
- Not about project dependencies

### 5. Project Dependency Cycle Check

Let's verify there's no circular project dependency:
- `credentials:core` → `:common` → ? (does `:common` depend on `:credentials:core`?)
- `credentials:core` → `:did:core` → ? (does `:did:core` depend on `:credentials:core`?)

If there's a circular project dependency, that could cause issues, but it would be a different error.

## Critical Finding: Potential Circular Project Dependency!

The dependency output shows:
```
\--- project :did:core -> project :credentials:core (*)
```

This suggests there might be a **circular project dependency**:
- `credentials:core` depends on `:did:core` (via `implementation(project(":did:core"))`)
- `:did:core` might depend back on `:credentials:core` (transitively or directly)

However, checking `did/core/build.gradle.kts` shows:
- `:did:core` does NOT directly depend on `:credentials:core`
- But many `did:plugins/*` modules DO depend on `:credentials:core`

This could create a transitive circular dependency if:
- `credentials:core` → `:did:core` → `:did:plugins/*` → `:credentials:core`

But this would be a **different type of circular dependency** (project dependencies, not task dependencies).

## Conclusion

**The issue is likely NOT directly related to using `implementation`:**

1. The circular dependency we're seeing is a **task dependency** issue (`compileKotlin → jar → classes → compileJava → compileKotlin`)
2. This is different from a project dependency cycle
3. However, project dependencies might **trigger** the task dependency issue:
   - When `compileKotlin` needs to compile code that uses `:did:core`
   - The Kotlin plugin might need to access JAR artifacts
   - This could trigger the `archivesTaskOutputAsFriendModule` behavior
   - Which creates the `compileKotlin → jar` dependency

**Testing Recommendation:**
- Temporarily remove `implementation(project(":did:core"))` to see if the circular dependency persists
- If it goes away, the project dependency might be triggering the task dependency issue
- If it persists, it's purely a task dependency issue within the module

## Recommendation

The issue is most likely **NOT** related to using `implementation` for dependencies. The circular dependency is a task dependency issue within the `credentials:core` module itself, caused by the Kotlin plugin's `archivesTaskOutputAsFriendModule` feature.

The fix should be the `kotlin.build.archivesTaskOutputAsFriendModule=false` property, but it's not working (as we've documented).

