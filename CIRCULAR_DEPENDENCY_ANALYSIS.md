# Circular Dependency Analysis - Does Our Case Match KT-69330 Description?

## KT-69330 Description
> "In very rare cases, we've found that this new behavior can cause a circular dependency error. For example, if you have multiple compilations where one compilation can see all internal declarations of the other, and the generated artifact relies on the output of both compilation tasks, you could see an error like our case"

## Our Case Analysis

### 1. Multiple Compilations?
✅ **YES**
- `compileJava` task (even though no Java sources exist)
- `compileKotlin` task

**Note**: Even though `credentials:core` has no Java source files, Gradle still creates a `compileJava` task.

### 2. Can One Compilation See Internal Declarations of the Other?
✅ **POTENTIALLY YES**
- Kotlin and Java can see each other's internal declarations in the same module
- `compileJava` can see Kotlin internal declarations
- `compileKotlin` can see Java internal declarations (if any existed)

### 3. Generated Artifact Relies on Both Compilation Tasks?
✅ **YES**
- The `jar` task depends on the `classes` task
- The `classes` task depends on both:
  - `compileJava` (even if disabled/no sources)
  - `compileKotlin`

### 4. The Circular Dependency Chain
```
compileKotlin 
  → depends on jar (because of archivesTaskOutputAsFriendModule=true by default)
    → depends on classes
      → depends on compileJava
        → depends on compileKotlin (standard Gradle behavior)
          → CYCLE!
```

## Conclusion

**YES, our case matches the KT-69330 description!**

Our scenario:
1. ✅ Multiple compilations (`compileJava` + `compileKotlin`)
2. ✅ One can see internal declarations of the other (Kotlin/Java interop)
3. ✅ Generated artifact (`jar`) relies on both compilation tasks
4. ✅ Circular dependency occurs

## Why the Fix Doesn't Work

The property `kotlin.build.archivesTaskOutputAsFriendModule=false` should prevent `compileKotlin` from depending on `jar`, but:
- The property is read correctly
- The property is NOT respected by the plugin
- The dependency is still created

This suggests the fix in KT-69330 is incomplete or doesn't work for this specific scenario.

## Our Specific Case Details

### Module: `credentials:core`
- **Source files**: Only Kotlin (no Java)
- **Compilation tasks**: `compileJava` (empty) + `compileKotlin`
- **Dependencies**: 
  - `:common`
  - `:did:core`
- **Artifact**: JAR file depends on both compilation outputs

### The Problem
Even though:
- There are no Java source files
- `compileJava` is disabled
- The property is set to `false`

The circular dependency still occurs because:
1. Gradle still creates the `compileJava` task
2. `classes` task still depends on `compileJava`
3. `compileKotlin` still depends on `jar` (property ignored)
4. `compileJava` depends on `compileKotlin` (standard behavior)

## Recommendation

This confirms our case matches the KT-69330 description exactly. The fix should work but doesn't, which means:
1. The fix is incomplete
2. There's a bug in the fix implementation
3. Our specific configuration isn't covered by the fix

We should comment on KT-69330 that the fix doesn't work for this exact scenario.

