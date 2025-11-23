# Version Catalog Usage Guide

All dependency versions are now centralized in `gradle/libs.versions.toml`.

## Using the Version Catalog

### In build.gradle.kts files:

**Dependencies:**
```kotlin
dependencies {
    // Single library
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    
    // Using bundles (groups of related dependencies)
    implementation(libs.bundles.kotlinx)
    implementation(libs.bundles.test)
    
    // Platform/BOM
    implementation(platform(libs.aws.sdk.bom))
}
```

**Plugins:**
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}
```

## Available Libraries

See `gradle/libs.versions.toml` for the complete list. Common ones:

- `libs.kotlinx.serialization.json` - Kotlinx serialization JSON
- `libs.kotlinx.coroutines.core` - Kotlin coroutines
- `libs.junit.jupiter` - JUnit 5
- `libs.okhttp` - OkHttp client
- `libs.web3j` - Web3j library
- `libs.postgresql` - PostgreSQL driver
- And many more...

## Bundles

Bundles group related dependencies:

- `libs.bundles.kotlinx` - Kotlinx serialization + coroutines
- `libs.bundles.test` - JUnit + Kotlin test
- `libs.bundles.ktor.client` - Ktor client dependencies
- `libs.bundles.database` - Database drivers
- `libs.bundles.bouncycastle` - BouncyCastle security libraries

## Updating Versions

To update a version, edit `gradle/libs.versions.toml` and change the version in the `[versions]` section. All modules using that dependency will automatically use the new version.

## Migration from Libs.kt

Old code using `Libs.xxx` can be migrated to use `libs.xxx` directly. The `Libs.kt` file is kept for backward compatibility but new code should use the version catalog.

