# Module Rename Guide

## Overview

To fix a circular dependency bug in the Kotlin Gradle Plugin, all modules ending with `:core` have been renamed to have unique suffixes. This document explains the changes and how to update your code.

## Renamed Modules

| Old Module Path | New Module Path | Old Folder | New Folder |
|----------------|-----------------|-----------|------------|
| `:did:core` | `:did:did-core` | `did/core` | `did/did-core` |
| `:credentials:core` | `:credentials:credential-core` | `credentials/core` | `credentials/credential-core` |
| `:kms:core` | `:kms:kms-core` | `kms/core` | `kms/kms-core` |
| `:anchors:core` | `:anchors:anchor-core` | `anchors/core` | `anchors/anchor-core` |
| `:wallet:core` | `:wallet:wallet-core` | `wallet/core` | `wallet/wallet-core` |

## Why This Change?

The Kotlin Gradle Plugin 2.2.21 has a bug where having multiple modules ending with `:core` triggers the `archivesTaskOutputAsFriendModule` behavior, causing a circular dependency error. The property `kotlin.build.archivesTaskOutputAsFriendModule=false` is ignored when this pattern is detected.

Renaming the modules to avoid the `:core` suffix pattern resolves this issue.

## Impact on Your Code

### Build Files (build.gradle.kts)

**Before:**
```kotlin
dependencies {
    implementation(project(":did:core"))
    implementation(project(":credentials:core"))
    implementation(project(":kms:core"))
    implementation(project(":anchors:core"))
    implementation(project(":wallet:core"))
}
```

**After:**
```kotlin
dependencies {
    implementation(project(":did:did-core"))
    implementation(project(":credentials:credential-core"))
    implementation(project(":kms:kms-core"))
    implementation(project(":anchors:anchor-core"))
    implementation(project(":wallet:wallet-core"))
}
```

### Settings File (settings.gradle.kts)

**Before:**
```kotlin
include("did:core")
include("credentials:core")
include("kms:core")
include("anchors:core")
include("wallet:core")
```

**After:**
```kotlin
include("did:did-core")
include("credentials:credential-core")
include("kms:kms-core")
include("anchors:anchor-core")
include("wallet:wallet-core")
```

### Source Code

**No changes needed!** Kotlin source code doesn't reference module paths directly. Only build configuration files need updates.

## Migration Steps

1. **Update `settings.gradle.kts`**: Change all `include()` statements to use new module paths
2. **Update all `build.gradle.kts` files**: Change all `project(":did:core")` references to `project(":did:did-core")` etc.
3. **Rename folders** (if cloning fresh): The folder structure has been updated in the repository
4. **Test your build**: Run `./gradlew build` to verify everything works

## Verification

After updating, verify the circular dependency is resolved:

```bash
./gradlew :credentials:credential-core:dependencies --configuration compileClasspath
```

You should see:
```
Project ':credentials:credential-core'
\--- project :did:did-core
```

**No `(*)` annotation** means no circular dependency!

## Related Documentation

- [Module Rename Summary](../MODULE_RENAME_SUMMARY.md) - Detailed technical explanation
- [Core Modules](modules/core-modules.md) - Module overview and dependencies

