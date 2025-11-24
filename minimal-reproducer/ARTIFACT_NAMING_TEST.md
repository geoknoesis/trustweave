# Artifact Naming Test Results

## Hypothesis

**Question**: Does renaming generated artifacts using folder path affect the circular dependency bug?

## Test Setup

### Test 1: With Artifact Naming (Default Configuration)
- **Configuration**: Artifact names set based on project path (`credentials-core`, `did-core`, `common`)
- **Modules**: `credentials:core` and `did:core` (both have `:core` suffix)
- **Result**: ❌ **Circular dependency occurs**

### Test 2: Without Artifact Naming (Default Gradle Names)
- **Configuration**: Artifact naming disabled (uses default names like `core-1.0.0-SNAPSHOT.jar`)
- **Modules**: `credentials:core` and `did:core` (both have `:core` suffix)
- **Result**: ❌ **Circular dependency still occurs**

## Conclusion

**Artifact naming does NOT affect the bug.**

The circular dependency occurs regardless of:
- ✅ Whether artifacts are renamed based on folder paths
- ✅ Whether default Gradle artifact names are used
- ✅ The actual artifact file names

## Root Cause Confirmed

The bug is triggered by:
- ✅ **Module path names** (having multiple modules with `:core` suffix)
- ❌ **NOT** artifact file names
- ❌ **NOT** artifact naming configuration

## Evidence

Even with artifact naming disabled:
- Dependency output still shows: `project :did:core -> project :credentials:core (*)`
- Circular dependency still occurs
- The `(*)` annotation still appears

This confirms that the Kotlin plugin is using **module path names** (like `:credentials:core`, `:did:core`) to detect patterns, not artifact names.
