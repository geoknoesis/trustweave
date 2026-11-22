---
title: Installation
nav_order: 10
parent: Getting Started
keywords:
  - installation
  - setup
  - gradle
  - maven
  - dependencies
  - prerequisites
redirect_from:
  - /tutorials/getting-started/installation/
---

# Installation

Add TrustWeave to your Kotlin project using Gradle or Maven. These examples target
the current 0.7.0 source checkout. Artifacts reach Maven Central only when the `publish` job of
`.github/workflows/release-evidence.yml` completes for a `v*` tag, and this repository does not record that it
has. Until you have confirmed the coordinates resolve from your repository, build from source with
`./gradlew publishToMavenLocal` and add `mavenLocal()`. Local source validation is not proof that a matching
immutable release has been published.

## Prerequisites

Before installing TrustWeave, ensure you have:

- **Kotlin 2.4.21+** (included via Gradle plugin, no manual installation needed)
- **Java 21+** (required for compilation and runtime)
- **Gradle 9.8.1 via this repository's wrapper** (no separate Gradle installation needed)
- **Basic Kotlin knowledge** (coroutines, data classes, sealed classes)
- **Familiarity with W3C standards** (helpful but not required):
  - [Verifiable Credentials Data Model v1.1](https://www.w3.org/TR/vc-data-model/)
  - [Decentralized Identifiers (DIDs) v1.0](https://www.w3.org/TR/did-core/)

### Optional Prerequisites

- **Docker** (optional): Required for `org.trustweave:anchors-plugins-ganache` tests using TestContainers
- **IDE**: IntelliJ IDEA or VS Code with Kotlin support (recommended for better developer experience)

### Verify Your Setup

After installation, verify your setup by running a simple test:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.getOrThrow // Pair<Did, DidDocument> from DidCreationResult
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys {
            provider(IN_MEMORY)  // Auto-discovered via SPI
            algorithm(ED25519)
        }
        did {
            method(KEY) {  // Auto-discovered via SPI
                algorithm(ED25519)
            }
        }
        // KMS, DID methods, and CredentialService all auto-created!
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
    // Import the TrustWeave BOM to align module versions
    implementation(platform("org.trustweave:distribution-bom:0.7.0"))

    // Recommended for getting started: includes core modules, did:key, and kms-plugins-inmemory
    // so TrustWeave.quickStart() works out of the box without :testkit
    implementation("org.trustweave:distribution-all")

    // Test kit (for testing only)
    testImplementation("org.trustweave:testkit:0.7.0")

    // Optional: Integration modules (versions managed by the BOM)
    implementation("org.trustweave:kms-plugins-waltid")
    implementation("org.trustweave:did-plugins-godiddy")
    implementation("org.trustweave:anchors-plugins-algorand")
    implementation("org.trustweave:anchors-plugins-polygon")
}
```

> **Note:** `org.trustweave:distribution-all:0.7.0` is the all-in-one runtime bundle (core modules, `did:key`, and `kms-plugins-inmemory` for quickstart). `org.trustweave:distribution-bom:0.7.0` is the Bill of Materials (`java-platform`) that aligns versions across published TrustWeave modules. For production, prefer individual modules plus a production KMS plugin to minimize bundle size and avoid in-memory key custody. See [Module Architecture](../api-reference/modules/core-modules.md) for details.

**Result:** Gradle resolves the TrustWeave BOM, the all-in-one bundle (with built-in in-memory KMS for quickstart), and kotlinx dependencies so you can compile and run the sample code in this guide.

## Gradle (Groovy)

Add TrustWeave dependencies to your `build.gradle`. Use this variant if your project still uses the Groovy DSL.

```groovy
dependencies {
    // Import the TrustWeave BOM to align module versions
    implementation platform('org.trustweave:distribution-bom:0.7.0')

    // Recommended for getting started: includes core modules, did:key, and kms-plugins-inmemory
    implementation 'org.trustweave:distribution-all'

    // Test kit (for testing only)
    testImplementation 'org.trustweave:testkit:0.7.0'

    // Optional: Integration modules (versions managed by the BOM)
    implementation 'org.trustweave:kms-plugins-waltid'
    implementation 'org.trustweave:did-plugins-godiddy'
    implementation 'org.trustweave:anchors-plugins-algorand'
    implementation 'org.trustweave:anchors-plugins-polygon'
}
```

**Result:** The Groovy build picks up the same modules; no additional configuration is required besides applying the Kotlin and serialization plugins.

## Maven

Add TrustWeave dependencies to your `pom.xml`. Import `distribution-bom` in `<dependencyManagement>` to align versions, then add `distribution-all` (or individual modules).

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.trustweave</groupId>
            <artifactId>distribution-bom</artifactId>
            <version>0.7.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Recommended for getting started (includes core modules, did:key, and kms-plugins-inmemory) -->
    <dependency>
        <groupId>org.trustweave</groupId>
        <artifactId>distribution-all</artifactId>
    </dependency>

    <!-- Test kit (for testing only; excluded from BOM by design) -->
    <dependency>
        <groupId>org.trustweave</groupId>
        <artifactId>testkit</artifactId>
        <version>0.7.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Result:** Maven downloads the artifacts during the next `mvn compile`. If you rely on integrations, add the matching `<dependency>` entries just like the Gradle example.

## Production Setup (Individual Modules)

For production deployments, use the BOM with individual modules instead of `distribution-all` to minimize bundle size and pick a production KMS plugin:

```kotlin
dependencies {
    // Align all TrustWeave module versions
    implementation(platform("org.trustweave:distribution-bom:0.7.0"))

    // Core modules (required)
    implementation("org.trustweave:trust")
    implementation("org.trustweave:credentials-credential-api")
    implementation("org.trustweave:did-did-core")
    implementation("org.trustweave:kms-kms-core")
    implementation("org.trustweave:anchors-anchor-core")
    implementation("org.trustweave:common")

    // Add your production KMS and DID plugins (example: AWS KMS + did:web)
    implementation("org.trustweave:kms-plugins-aws")
    implementation("org.trustweave:did-plugins-web")

    // Test kit (for testing only)
    testImplementation("org.trustweave:testkit:0.7.0")
}
```

> **Note:** For getting started, use `distribution-all` instead. Only use individual modules if you need to minimize bundle size in production.

**Result:** You get the same APIs with a smaller bundle size. See [Module Architecture](../api-reference/modules/core-modules.md) for details on available modules.

## Repository Configuration

If you pull SNAPSHOT builds, add the Sonatype Central snapshot repository so Gradle/Maven can resolve them.

```kotlin
repositories {
    mavenCentral()
    // Add snapshot repository if needed
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
```

**Result:** Build tools know where to fetch snapshot artifacts; remove the block when you move to a stable release.

## Version Information

- **Current Version**: 0.7.0
- **Kotlin Version**: 2.4.21+
- **Java Version**: 21+
- **Gradle wrapper version**: 9.8.1

## Next Steps

- [Quick Start](quick-start.md) - Create your first TrustWeave application
- [Your First Application](your-first-application.md) - Build a complete example

