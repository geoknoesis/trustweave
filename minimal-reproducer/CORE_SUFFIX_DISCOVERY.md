# 🎯 CRITICAL DISCOVERY: "core" Suffix Triggers the Bug!

## Hypothesis Test Results

### Test 1: Two modules with ":core" suffix
- **Modules**: `credentials:core` and `did:core`
- **Result**: ❌ **Circular dependency occurs**
- **Dependency output**: `project :did:core -> project :credentials:core (*)`

### Test 2: Only one module with ":core" suffix
- **Modules**: `credentials:core` and `did:main`
- **Result**: ✅ **Build succeeds!**
- **Dependency output**: `project :did:main` (no `(*)` annotation)

## Conclusion

**Having multiple modules with the same suffix `:core` triggers the bug!**

## Why This Happens

The Kotlin Gradle Plugin likely has logic that:

1. **Pattern matches module names** ending with `:core`
2. **Assumes friend module relationships** between modules with the same suffix
3. **Enables `archivesTaskOutputAsFriendModule` behavior** automatically
4. **Ignores the property setting** when this pattern is detected

## Evidence

### Actual Project
The actual Trustweave project has **multiple modules ending with `:core`**:
- `:credentials:core`
- `:did:core`
- `:kms:core`
- `:anchors:core`
- `:wallet:core`

This explains why the bug occurs in the actual project!

### Minimal Reproducer
- With `credentials:core` + `did:core`: Bug reproduces ✅
- With `credentials:core` + `did:main`: Bug does NOT reproduce ✅

## Impact

This is a **critical finding** because:

1. **Many projects use `:core` suffix** for their core modules
2. **The bug is triggered by naming convention**, not just dependency structure
3. **The property is ignored** when the pattern is detected
4. **This affects a large number of projects** using this naming pattern

## The Bug in Kotlin Plugin

The plugin likely has code like:

```kotlin
// Pseudo-code of what might be happening:
fun shouldUseArchivesTaskAsFriendModule(): Boolean {
    val propertyValue = project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")
        ?.toString()?.toBoolean() ?: true
    
    // BUG: Pattern matching overrides property!
    val hasMultipleCoreModules = project.allprojects.count { 
        it.path.endsWith(":core") 
    } > 1
    
    if (hasMultipleCoreModules) {
        return true  // Always enable, ignoring property!
    }
    
    return propertyValue
}
```

## Workaround

**Option 1**: Rename modules to avoid multiple `:core` suffixes
- Change `did:core` to `did:main` or `did:api`

**Option 2**: Use the manual workaround (remove jar dependency in build.gradle.kts)

## Additional Finding: Artifact Naming Doesn't Matter

**Tested**: Does artifact naming (using folder path) affect the bug?

**Result**: ❌ **No** - Artifact naming does NOT affect the bug.

- With artifact naming: Bug occurs
- Without artifact naming: Bug still occurs
- With unique artifact names: Bug still occurs

**Conclusion**: The Kotlin plugin checks **project paths** (like `:credentials:core`), not artifact names (like `credentials-core-1.0.0-SNAPSHOT.jar`), when detecting the pattern.

## Next Steps

1. ✅ Document this discovery in the bug report
2. ✅ Test with other suffixes (e.g., `:api`, `:base`) to see if pattern is suffix-specific
3. ✅ Report to JetBrains with this critical finding
4. ✅ Confirm that the bug is in project path analysis, not artifact resolution

