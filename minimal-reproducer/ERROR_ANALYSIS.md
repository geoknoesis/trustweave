# Error Source Analysis: Circular Dependency in Minimal Reproducer

## Error Message

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

## Root Cause Analysis

### 1. The Circular Dependency Chain

The error shows a **task dependency cycle**:

```
classes
  └─> compileJava
       ├─> compileKotlin  ←─┐
       │    └─> jar         │
       │         ├─> classes│ (cycle back)
       │         ├─> compileJava (cycle back)
       │         └─> compileKotlin (cycle back)
       └─> jar (cycle back)
```

### 2. Why This Happens

#### Standard Gradle Task Dependencies (Normal Behavior)

1. **`classes` task** depends on:
   - `compileJava` (to compile Java sources)
   - `compileKotlin` (to compile Kotlin sources)
   - `processResources` (to process resources)

2. **`jar` task** depends on:
   - `classes` (to include compiled classes in the JAR)

3. **`compileJava` task** depends on:
   - `compileKotlin` (standard Gradle behavior - Java compilation needs Kotlin compiled first for interop)

#### The Problem: Kotlin Plugin Adds Extra Dependency

The Kotlin Gradle Plugin's `archivesTaskOutputAsFriendModule` feature adds:

4. **`compileKotlin` task** depends on:
   - **`jar` task** ← **THIS IS THE PROBLEM!**

This creates the cycle:
- `compileKotlin` → `jar` → `classes` → `compileJava` → `compileKotlin` (CYCLE!)

### 3. Why `kotlin.build.archivesTaskOutputAsFriendModule=false` Doesn't Work

#### Expected Behavior

When `kotlin.build.archivesTaskOutputAsFriendModule=false` is set:
- The Kotlin plugin should **NOT** add the `jar` dependency to `compileKotlin`
- This should break the cycle

#### Actual Behavior

Even with the property set to `false`:
- ✅ Property is **read correctly** by Gradle (verified via debug output)
- ❌ Property is **ignored** by the Kotlin plugin
- ❌ `compileKotlin` **still depends on `jar`**
- ❌ Circular dependency **still occurs**

### 4. What Triggers the `archivesTaskOutputAsFriendModule` Behavior

Based on our analysis, the Kotlin plugin enables this behavior when:

1. **Transitive dependency chain exists**: `credentials:core` → `did:core` → `common`
2. **Dependency graph annotation**: Gradle detects `project :did:core -> project :credentials:core (*)`
3. **Nested project paths**: `:credentials:core`, `:did:core`
4. **Resources in dependency**: `did:core` has resources (JSON files)
5. **Complex module structure**: Multiple subdirectories in `did:core`

The combination of these factors triggers the friend module behavior, which adds the `jar` dependency to `compileKotlin`.

### 5. The Bug in Kotlin Gradle Plugin

The bug is in the Kotlin Gradle Plugin's code that:

1. **Reads the property** (or should read it)
2. **Decides whether to add the `jar` dependency** to `compileKotlin`
3. **Ignores the property value** and adds the dependency anyway

**Likely locations in Kotlin plugin source code:**
- `KotlinJvmPlugin.kt` - Main plugin configuration
- `KotlinCompile.kt` - Task dependency setup
- Friend module configuration code
- Property reading utilities

**The bug is likely:**
- Property is read at the wrong time (after dependencies are already set)
- Property value is not passed to the code that creates dependencies
- Conditional check is missing or incorrect
- Default value override doesn't work

### 6. Why Module Names Matter

Using exact module names (`credentials:core`, `did:core`, `common`) reproduces the bug because:

1. **Dependency resolution**: Gradle's dependency resolution might use module names/paths in its internal logic
2. **Friend module detection**: The Kotlin plugin might use module names to detect friend module relationships
3. **Caching**: Gradle might cache dependency resolution based on module names
4. **Internal plugin logic**: The plugin might have hardcoded logic or patterns that match these specific names

### 7. Stack Trace Analysis

The error occurs during:
- **Phase**: Task execution plan determination
- **Location**: `DetermineExecutionPlanAction.onOrderingCycle()`
- **Cause**: Gradle detects the circular dependency when building the task execution graph

The stack trace shows:
```
org.gradle.api.CircularReferenceException: Circular dependency between the following tasks:
    at org.gradle.execution.plan.DetermineExecutionPlanAction.onOrderingCycle(...)
```

This happens **before** any tasks are executed - during the planning phase.

### 8. Verification

**Property is read correctly:**
```
=== Property Debug Info ===
kotlin.build.archivesTaskOutputAsFriendModule (project property): false
===========================
```

**But dependency still exists:**
```
:credentials:core:compileKotlin
    \--- :credentials:core:jar  ← Should NOT exist when property is false!
```

## Conclusion

The bug is in the Kotlin Gradle Plugin 2.2.21 where:

1. ✅ The `kotlin.build.archivesTaskOutputAsFriendModule` property is **read correctly**
2. ❌ The property value is **not used** when creating task dependencies
3. ❌ The `compileKotlin → jar` dependency is **always created** regardless of the property value
4. ❌ This causes a **circular dependency** that should be prevented by the property

**The fix should be** in the Kotlin plugin code to:
- Check the property value before adding the `jar` dependency
- Respect the `false` value and skip adding the dependency
- This would break the cycle and allow the build to succeed

