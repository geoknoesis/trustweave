# Artifact Naming Hypothesis

## Discovery

In `build.gradle.kts`, there's special handling that **excludes `:credentials:core` from automatic artifact naming**:

```kotlin
if (!plugins.hasPlugin("java-platform") && project.path != ":credentials:core") {
    val artifactName = project.path
        .removePrefix(":")
        .replace(":", "-")
    
    extensions.findByType<org.gradle.api.plugins.BasePluginExtension>()?.let {
        it.archivesName.set(artifactName)
    }
}
```

## Artifact Naming Status

- ✅ `:common` - Gets `archivesName = "common"`
- ✅ `:did:core` - Gets `archivesName = "did-core"`
- ❌ `:credentials:core` - **EXCLUDED** (no `archivesName` set, uses default)

## Hypothesis

The circular dependency might be triggered by the **interaction** between:
1. `:did:core` having automatic artifact naming (`did-core`)
2. `:credentials:core` being excluded from automatic naming (default name)
3. When `credentials:core` depends on `:did:core`, the Kotlin plugin needs to resolve artifacts
4. The artifact naming mismatch or resolution process triggers the friend module behavior

## Why This Could Be the Cause

1. **Artifact Resolution**: When resolving `:did:core`'s artifact, the plugin might need to check the current project's artifact
2. **Naming Mismatch**: The difference in how artifacts are named might cause resolution issues
3. **Friend Module Behavior**: This triggers the `archivesTaskOutputAsFriendModule` feature
4. **Circular Dependency**: Creates `compileKotlin → jar` dependency, causing the cycle

## Test Results

### Test 1: Remove exclusion for `:credentials:core` - give it automatic naming ✅
**Action**: Removed `&& project.path != ":credentials:core"` condition

**Result**: 
- ❌ **Circular dependency STILL APPEARS!**
- Artifact naming is NOT the root cause

**Conclusion**: The exclusion was likely added as a workaround attempt, but it doesn't actually fix the issue. The circular dependency persists regardless of artifact naming.

### Test 2: Also exclude `:did:core` from automatic naming
**Status**: Not tested yet

### Test 3: Check if other modules with automatic naming also trigger the issue
**Status**: Not tested yet

## Evidence Supporting This Theory

1. `:credentials:core` is the ONLY module explicitly excluded from automatic naming
2. The exclusion comment mentions "circular dependency issue"
3. `:common` (with automatic naming) doesn't trigger the issue
4. `:did:core` (with automatic naming) DOES trigger the issue
5. The difference might be the **combination** of:
   - `:credentials:core` being excluded
   - `:did:core` having automatic naming
   - The transitive dependency chain

