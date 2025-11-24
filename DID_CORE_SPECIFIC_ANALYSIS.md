# What's Specific About `:did:core` That Triggers the Circular Dependency?

## Comparison: `:did:core` vs `:common`

### Similarities
- ✅ Both use same plugins: `kotlin("jvm")` and `kotlin("plugin.serialization")`
- ✅ Both have no `internal` declarations (0 internal classes/functions)
- ✅ Both have similar build configurations
- ✅ Both are Kotlin-only modules (no Java sources)

### Differences

#### 1. Project Path Structure
- `:common` - Root level project (`include("common")`)
- `:did:core` - Nested project (`include("did:core")`)

#### 2. Dependencies
- `:common` - No project dependencies (only external libraries)
- `:did:core` - Depends on `:common` (`implementation(project(":common"))`)

#### 3. Artifact Naming
- `:common` - Gets `archivesName = "common"` (from root build.gradle.kts)
- `:did:core` - Gets `archivesName = "did-core"` (from root build.gradle.kts)
- `:credentials:core` - **EXCLUDED** from automatic artifact naming (special handling)

#### 4. Module Structure
- `:common` - Simple structure (fewer subdirectories)
- `:did:core` - More complex structure (registrar, resolver, registration, etc.)
- `:did:core` - Has resources (JSON files in `src/main/resources/did-methods/`)

#### 5. Transitive Dependencies
- `:common` - No transitive project dependencies
- `:did:core` - Has transitive dependency on `:common`

## Hypothesis: Why `:did:core` Triggers the Issue

### Theory 1: Nested Project Path
The nested path (`:did:core` vs `:common`) might cause the Kotlin plugin to handle artifact resolution differently, triggering the friend module behavior.

### Theory 2: Transitive Dependency Chain
When `credentials:core` depends on `:did:core`, and `:did:core` depends on `:common`:
- `credentials:core` → `:did:core` → `:common`
- The Kotlin plugin might need to resolve the transitive dependency chain
- This could trigger the `archivesTaskOutputAsFriendModule` behavior

### Theory 3: Artifact Naming
The automatic artifact naming (`did-core` vs `common`) might interact with the friend module feature in a way that triggers the circular dependency.

### Theory 4: Resources in `:did:core`
The presence of resources (JSON files) in `:did:core` might cause the Kotlin plugin to need the JAR artifact during compilation, triggering the friend module behavior.

## Test Results Summary

- ✅ **No dependencies**: No circular dependency
- ✅ **Only `:common`**: No circular dependency
- ❌ **Only `:did:core`**: Circular dependency appears
- ❌ **Both dependencies**: Circular dependency appears

This suggests it's **specifically about `:did:core`**, not about:
- The number of dependencies
- The dependency configuration type
- General project dependency handling

## Most Likely Cause

**Theory 2: Transitive Dependency Chain** seems most plausible:

1. `credentials:core` depends on `:did:core`
2. `:did:core` depends on `:common`
3. When compiling `credentials:core`, the Kotlin plugin needs to:
   - Access `:did:core`'s JAR
   - Resolve transitive dependencies (`:common`)
   - This triggers the friend module behavior
   - Creates `compileKotlin → jar` dependency
   - Circular dependency occurs

The fact that `:common` alone doesn't trigger it, but `:did:core` (which depends on `:common`) does, supports this theory.

## Next Steps to Verify - Investigation Results

### 1. ✅ Check if `:did:core` has any special Kotlin compiler options
**Result**: 
- Both `:did:core` and `:common` use identical plugins: `kotlin("jvm")` and `kotlin("plugin.serialization")`
- No special Kotlin compiler options in either module
- No `kotlin {}` block with custom configuration
- **Conclusion**: No special compiler options - this is NOT the cause

### 2. ✅ Check if the resources in `:did:core` trigger something
**Result**: 
- `:did:core` has **7 resource files** in `src/main/resources/did-methods/`
- `:common` has **0 resource files** (no resources directory)
- **Conclusion**: Resources might be a factor, but unlikely to be the root cause (resources are processed separately from compilation)

### 3. ⚠️ Test with a mock `:did:core` that has no dependencies
**Status**: Not tested yet - would require creating a test module
**Note**: This would help isolate if the transitive dependency on `:common` is the trigger

### 4. ⚠️ Test with a mock `:did:core` that has no resources
**Status**: Not tested yet - would require creating a test module
**Note**: This would help isolate if resources are a factor

### 5. ✅ Check if the nested project path (`:did:core`) vs root path (`:common`) matters
**Result**: 
- `:common` - Root level project (`include("common")`)
- `:did:core` - Nested project (`include("did:core")`)
- **Critical Finding**: `:credentials:core` is **EXCLUDED** from automatic artifact naming in `build.gradle.kts`:
  ```kotlin
  if (!plugins.hasPlugin("java-platform") && project.path != ":credentials:core") {
      // Sets archivesName for all projects EXCEPT :credentials:core
  }
  ```
- `:did:core` gets `archivesName = "did-core"` (automatic naming)
- `:common` gets `archivesName = "common"` (automatic naming)
- `:credentials:core` does NOT get automatic naming (excluded)
- **Conclusion**: The nested path structure might matter, but the artifact naming exclusion for `:credentials:core` is more significant

## Key Discovery: Artifact Naming Exclusion

**`:credentials:core` is explicitly excluded from automatic artifact naming!**

This suggests:
1. The circular dependency issue was known when this exclusion was added
2. The exclusion might be a workaround attempt
3. The interaction between `:did:core`'s automatic naming and `:credentials:core`'s exclusion might trigger the issue

## Most Likely Root Cause (Updated)

**Theory: Artifact Naming + Transitive Dependency Interaction**

1. `:did:core` gets automatic artifact naming (`archivesName = "did-core"`)
2. `:credentials:core` is excluded from automatic naming (no `archivesName` set)
3. When `credentials:core` depends on `:did:core`:
   - Kotlin plugin needs to resolve `:did:core`'s artifact
   - The artifact naming difference might cause resolution issues
   - This triggers the `archivesTaskOutputAsFriendModule` behavior
   - Creates `compileKotlin → jar` dependency
   - Circular dependency occurs

**Test Result**: ✅ **TESTED** - Removed exclusion for `:credentials:core`
- ❌ **Circular dependency STILL APPEARS!**
- **Conclusion**: Artifact naming is NOT the root cause. The exclusion was likely a failed workaround attempt.

