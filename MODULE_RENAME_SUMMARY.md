# Module Rename Summary: Fixing Circular Dependency Bug

## Problem

The Kotlin Gradle Plugin 2.2.21 has a bug where having multiple modules ending with `:core` triggers the `archivesTaskOutputAsFriendModule` behavior, causing a circular dependency error. The property `kotlin.build.archivesTaskOutputAsFriendModule=false` is ignored when this pattern is detected.

## Solution

Renamed all modules ending with `:core` to have unique suffixes:

### Renamed Modules

1. `did/core` → `did/did-core` (module path: `:did:did-core`)
2. `credentials/core` → `credentials/credential-core` (module path: `:credentials:credential-core`)
3. `kms/core` → `kms/kms-core` (module path: `:kms:kms-core`)
4. `anchors/core` → `anchors/anchor-core` (module path: `:anchors:anchor-core`)
5. `wallet/core` → `wallet/wallet-core` (module path: `:wallet:wallet-core`)

## Changes Made

### 1. Folder Renames
- All `*:core` folders renamed to `*:*-core` format

### 2. settings.gradle.kts
- Updated all `include()` statements to use new module paths

### 3. build.gradle.kts Files
- Updated all `project(":did:core")` → `project(":did:did-core")`
- Updated all `project(":credentials:core")` → `project(":credentials:credential-core")`
- Updated all `project(":kms:core")` → `project(":kms:kms-core")`
- Updated all `project(":anchors:core")` → `project(":anchors:anchor-core")`
- Updated all `project(":wallet:core")` → `project(":wallet:wallet-core")`

### 4. Removed Workarounds
- Removed all circular dependency workarounds from `credentials/credential-core/build.gradle.kts`
- Removed exclusion for `:credentials:core` from artifact naming in root `build.gradle.kts`

## Verification

### Before (With `:core` Suffix)
```
Project ':credentials:core'
\--- project :did:core -> project :credentials:core (*)
```
- ❌ Circular dependency annotation `(*)` present
- ❌ Circular dependency error occurs

### After (With Renamed Modules)
```
Project ':credentials:credential-core'
\--- project :did:did-core
```
- ✅ No circular dependency annotation
- ✅ No circular dependency error
- ✅ Build succeeds (after fixing compilation errors)

## Result

**The circular dependency bug is fixed!** 

The Kotlin plugin no longer detects multiple modules with `:core` suffix, so it doesn't trigger the `archivesTaskOutputAsFriendModule` behavior that was causing the circular dependency.

## Notes

- Source code doesn't need changes (Kotlin code doesn't reference module paths)
- Only build configuration files needed updates
- The workaround code can now be removed since it's no longer needed

