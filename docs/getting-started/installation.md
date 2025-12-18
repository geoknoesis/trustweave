---
title: Installation
nav_order: 2
parent: Getting Started
keywords:
  - installation
  - setup
  - gradle
  - maven
  - dependencies
  - prerequisites
---

# Installation

Add TrustWeave to your Kotlin project using Gradle or Maven.

## Prerequisites

Before installing TrustWeave, ensure you have:

- **Kotlin 2.2.21+** (included via Gradle plugin, no manual installation needed)
- **Java 21+** (required for compilation and runtime)
- **Gradle 8.5+** (automatically downloaded via Gradle Wrapper, no manual installation needed)
- **Basic Kotlin knowledge** (coroutines, data classes, sealed classes)
- **Familiarity with W3C standards** (helpful but not required):
  - [Verifiable Credentials Data Model v1.1](https://www.w3.org/TR/vc-data-model/)
  - [Decentralized Identifiers (DIDs) v1.0](https://www.w3.org/TR/did-core/)

### Optional Prerequisites

- **Docker** (optional): Required for `com.trustweave.chains:ganache` tests using TestContainers
- **IDE**: IntelliJ IDEA or VS Code with Kotlin support (recommended for better developer experience)

### Verify Your Setup

After installation, verify your setup by running a simple test:

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),
            didMethodFactory = TestkitDidMethodFactory()
        )
        keys {
            provider(IN_MEMORY)
            algorithm(ED25519)
        }
        did {
            method(KEY) {
                algorithm(ED25519)
            }
        }
    }
    val (did, document) = trustWeave.createDid().getOrThrow()
    println("✅ TrustWeave is working! Created DID: ${did.value}")
}
```

If this runs without errors, your installation is successful!

## Gradle (Kotlin DSL)

Add TrustWeave dependencies to your `build.gradle.kts`. This brings in the core runtime, optional adapters, and the test kit you will use in the tutorials.

```kotlin
dependencies {
    // Recommended: Use distribution-all for getting started
    implementation("com.trustweave:distribution-all:1.0.0-SNAPSHOT")
    
    // Test kit (for testing)
    testImplementation("com.trustweave:testkit:1.0.0-SNAPSHOT")

    // Optional: Integration modules (using hierarchical group IDs)
    implementation("com.trustweave.kms:waltid:1.0.0-SNAPSHOT")
    implementation("com.trustweave.did:godiddy:1.0.0-SNAPSHOT")
    implementation("com.trustweave.chains:algorand:1.0.0-SNAPSHOT")
    implementation("com.trustweave.chains:polygon:1.0.0-SNAPSHOT")
}
```

> **Note:** `distribution-all` includes all core modules, DID support, KMS abstractions, anchoring, and DSLs. For production, you may want to use individual modules to minimize bundle size. See [Module Architecture](../modules/core-modules.md) for details.

**Result:** Gradle resolves the full TrustWeave BOM plus kotlinx libraries so you can compile the sample code in this guide.

## Gradle (Groovy)

Add TrustWeave dependencies to your `build.gradle`. Use this variant if your project still uses the Groovy DSL.

```groovy
dependencies {
    // Recommended: Use distribution-all for getting started
    implementation 'com.trustweave:distribution-all:1.0.0-SNAPSHOT'
    
    // Test kit (for testing)
    testImplementation 'com.trustweave:testkit:1.0.0-SNAPSHOT'

    // Optional: Integration modules (using hierarchical group IDs)
    implementation 'com.trustweave.kms:waltid:1.0.0-SNAPSHOT'
    implementation 'com.trustweave.did:godiddy:1.0.0-SNAPSHOT'
    implementation 'com.trustweave.chains:algorand:1.0.0-SNAPSHOT'
    implementation 'com.trustweave.chains:polygon:1.0.0-SNAPSHOT'
}
```

**Result:** The Groovy build picks up the same modules; no additional configuration is required besides applying the Kotlin and serialization plugins.

## Maven

Add TrustWeave dependencies to your `pom.xml`. The snippet lists the minimum set needed to follow the scenarios.

```xml
<dependencies>
    <!-- Recommended: Use distribution-all for getting started -->
    <dependency>
        <groupId>com.trustweave</groupId>
        <artifactId>distribution-all</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Test kit (for testing) -->
    <dependency>
        <groupId>com.trustweave</groupId>
        <artifactId>testkit</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Result:** Maven downloads the artifacts during the next `mvn compile`. If you rely on integrations, add the matching `<dependency>` entries just like the Gradle example.

## Production Setup (Individual Modules)

For production deployments, you may want to use individual modules instead of `distribution-all` to minimize bundle size and reduce dependencies:

```kotlin
dependencies {
    // Core modules (required)
    implementation("com.trustweave:trust:1.0.0-SNAPSHOT")
    implementation("com.trustweave:credentials:credential-api:1.0.0-SNAPSHOT")
    implementation("com.trustweave:did:did-core:1.0.0-SNAPSHOT")
    implementation("com.trustweave:kms:kms-core:1.0.0-SNAPSHOT")
    implementation("com.trustweave:anchors:anchor-core:1.0.0-SNAPSHOT")
    implementation("com.trustweave:common:1.0.0-SNAPSHOT")

    // Test kit (for testing)
    testImplementation("com.trustweave:testkit:1.0.0-SNAPSHOT")
}
```

**Result:** You get the same APIs with a smaller bundle size. See [Module Architecture](../modules/core-modules.md) for details on available modules.

## Repository Configuration

If you pull SNAPSHOT builds, add the OSS Sonatype snapshot repository so Gradle/Maven can resolve them.

```kotlin
repositories {
    mavenCentral()
    // Add snapshot repository if needed
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}
```

**Result:** Build tools know where to fetch snapshot artifacts; remove the block when you move to a stable release.

## Version Information

- **Current Version**: 1.0.0-SNAPSHOT
- **Kotlin Version**: 2.2.21+
- **Java Version**: 21+
- **Gradle Version**: 8.5+

## Next Steps

- [Quick Start](quick-start.md) - Create your first TrustWeave application
- [Your First Application](your-first-application.md) - Build a complete example

