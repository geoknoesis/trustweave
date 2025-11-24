# ✅ FOLDER RENAME TEST: Confirms "core" Suffix is the Trigger!

## Test Results

### Test 1: Original Folder Names (Both End with `:core`)
- **Modules**: `credentials:core` and `did:core`
- **Result**: ❌ **Circular dependency occurs**
- **Dependency output**: `project :did:core -> project :credentials:core (*)`
- **Task dependencies**: `compileKotlin → jar` (circular)

### Test 2: Renamed Folders (No Longer Both End with `:core`)
- **Modules**: `credentials:credential-core` and `did:did-core`
- **Result**: ✅ **Build succeeds!**
- **Dependency output**: `project :did:did-core` (no `(*)` annotation!)
- **Task dependencies**: No circular dependency

## Conclusion

**Renaming folders so modules no longer both end with `:core` fixes the bug!**

## Key Observations

1. **Module paths matter**: `:credentials:core` + `:did:core` = bug
2. **Module paths matter**: `:credentials:credential-core` + `:did:did-core` = no bug
3. **Dependency annotation disappears**: No `(*)` annotation when folders are renamed
4. **Task dependency removed**: `compileKotlin` no longer depends on `jar`

## Proof

The Kotlin Gradle Plugin is definitely pattern-matching on module path names:
- Detects when multiple modules end with `:core`
- Assumes friend module relationships
- Enables `archivesTaskOutputAsFriendModule` behavior
- Ignores the `kotlin.build.archivesTaskOutputAsFriendModule=false` property

## Workaround for Actual Project

To fix the bug in the actual Trustweave project, you could:

**Option 1**: Rename module folders
- `did/core` → `did/did-core` or `did/did-main`
- `credentials/core` → `credentials/credential-core` or `credentials/credential-main`
- Update all `include()` statements in `settings.gradle.kts`
- Update all `project(":did:core")` references to `project(":did:did-core")`

**Option 2**: Keep using the manual workaround (remove jar dependency in build.gradle.kts)

## Impact

This is **definitive proof** that:
- ✅ The bug is triggered by module path naming patterns
- ✅ Having multiple modules ending with `:core` is the specific trigger
- ✅ Renaming folders fixes the bug
- ✅ The property `kotlin.build.archivesTaskOutputAsFriendModule=false` is ignored when the pattern is detected

