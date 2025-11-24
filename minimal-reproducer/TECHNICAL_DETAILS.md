# Technical Details: Error Source Analysis

## Task Dependency Graph

### Normal Task Dependencies (Without Bug)

```
classes
  ├─> compileJava
  │     └─> compileKotlin
  ├─> compileKotlin
  └─> processResources

jar
  └─> classes

compileKotlin
  └─> (no jar dependency - this is what we want when property is false)
```

### Actual Task Dependencies (With Bug)

```
classes
  ├─> compileJava
  │     ├─> compileKotlin  ←─┐
  │     │    └─> jar         │
  │     │         ├─> classes│ (CYCLE!)
  │     │         ├─> compileJava (CYCLE!)
  │     │         └─> compileKotlin (CYCLE!)
  │     └─> jar (CYCLE!)
  ├─> compileKotlin
  └─> processResources

jar
  └─> classes

compileKotlin
  └─> jar  ← THIS SHOULD NOT EXIST when kotlin.build.archivesTaskOutputAsFriendModule=false
```

## Property Reading vs Usage

### When Property is Read

The property is read during the **configuration phase**:
- `project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")` returns `false`
- This happens early in the build lifecycle

### When Dependencies are Created

Task dependencies are created during the **task configuration phase**:
- The Kotlin plugin configures `compileKotlin` task
- It should check the property value
- It should conditionally add the `jar` dependency
- **BUG**: It doesn't check or ignores the property

## Why Module Names Trigger It

### Dependency Resolution Output

```
project :did:core -> project :credentials:core (*)
```

The `(*)` annotation indicates:
- Gradle detected `:credentials:core` in `:did:core`'s dependency tree
- This creates a transitive cycle detection
- The Kotlin plugin might use this information to enable friend module behavior

### Module Name Matching

The exact module names (`credentials:core`, `did:core`) might:
1. Match internal patterns in the Kotlin plugin
2. Trigger specific code paths in dependency resolution
3. Cause the plugin to assume friend module relationships
4. Override the property setting

## Build Lifecycle Phases

1. **Initialization**: Projects are discovered
2. **Configuration**: 
   - Properties are read ✅ (property is read here)
   - Tasks are created
   - Dependencies are configured ❌ (bug happens here - property ignored)
3. **Execution**: Tasks are executed (never reached due to cycle)

## The Fix Location

The fix should be in the Kotlin Gradle Plugin source code, likely in:

```kotlin
// Pseudo-code of what should happen:
fun configureCompileKotlinTask() {
    val archivesTaskAsFriendModule = project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")
        ?.toString()?.toBoolean() ?: true  // Default to true
    
    if (archivesTaskAsFriendModule) {
        compileKotlinTask.dependsOn(jarTask)  // Only add if property is true
    }
    // BUG: Currently always adds the dependency, ignoring the property
}
```

## Verification Commands

### Check Property Value
```bash
./gradlew :credentials:core:build -Pkotlin.build.archivesTaskOutputAsFriendModule=false
# Output shows: kotlin.build.archivesTaskOutputAsFriendModule (project property): false
```

### Check Task Dependencies
```bash
./gradlew :credentials:core:compileKotlin --dry-run
# Shows: compileKotlin depends on jar (SHOULD NOT when property is false)
```

### Check Dependency Graph
```bash
./gradlew :credentials:core:dependencies --configuration compileClasspath
# Shows: project :did:core -> project :credentials:core (*)
```

