# Installation

Add TrustWeave to your Kotlin project using Gradle or Maven.

## Prerequisites

Before installing TrustWeave, ensure you have:

- **Kotlin 2.2.0+** (included via Gradle plugin, no manual installation needed)
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
import com.trustweave.trust.TrustLayer
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustLayer = TrustLayer.build {
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }
        did {
            method("key") {
                algorithm("Ed25519")
            }
        }
    }
    val did = trustLayer.createDid {
        method("key")
        algorithm("Ed25519")
    }
    println("✅ TrustWeave is working! Created DID: $did")
}
```

If this runs without errors, your installation is successful!

## Gradle (Kotlin DSL)

Add TrustWeave dependencies to your `build.gradle.kts`. This brings in the core runtime, optional adapters, and the test kit you will use in the tutorials.

```kotlin
dependencies {
    // Core modules (required)
    implementation("com.trustweave:TrustWeave-core:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-json:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-kms:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-anchor:1.0.0-SNAPSHOT")
    
    // Test kit (for testing)
    testImplementation("com.trustweave:TrustWeave-testkit:1.0.0-SNAPSHOT")
    
    // Optional: Integration modules (using hierarchical group IDs)
    implementation("com.trustweave.kms:waltid:1.0.0-SNAPSHOT")
    implementation("com.trustweave.did:godiddy:1.0.0-SNAPSHOT")
    implementation("com.trustweave.chains:algorand:1.0.0-SNAPSHOT")
    implementation("com.trustweave.chains:polygon:1.0.0-SNAPSHOT")
    
    // Kotlinx Serialization (required)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Coroutines (required)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

**Result:** Gradle resolves the full TrustWeave BOM plus kotlinx libraries so you can compile the sample code in this guide.

## Gradle (Groovy)

Add TrustWeave dependencies to your `build.gradle`. Use this variant if your project still uses the Groovy DSL.

```groovy
dependencies {
    // Core modules (required)
    implementation 'com.trustweave:TrustWeave-core:1.0.0-SNAPSHOT'
    implementation 'com.trustweave:TrustWeave-json:1.0.0-SNAPSHOT'
    implementation 'com.trustweave:TrustWeave-kms:1.0.0-SNAPSHOT'
    implementation 'com.trustweave:TrustWeave-did:1.0.0-SNAPSHOT'
    implementation 'com.trustweave:TrustWeave-anchor:1.0.0-SNAPSHOT'
    
    // Test kit (for testing)
    testImplementation 'com.trustweave:TrustWeave-testkit:1.0.0-SNAPSHOT'
    
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
    <!-- Core modules (required) -->
    <dependency>
        <groupId>com.trustweave</groupId>
        <artifactId>TrustWeave-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.trustweave</groupId>
        <artifactId>TrustWeave-json</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.trustweave</groupId>
        <artifactId>TrustWeave-kms</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.trustweave</groupId>
        <artifactId>TrustWeave-did</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.trustweave</groupId>
        <artifactId>TrustWeave-anchor</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Test kit (for testing) -->
    <dependency>
        <groupId>com.trustweave</groupId>
        <artifactId>TrustWeave-testkit</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Result:** Maven downloads the artifacts during the next `mvn compile`. If you rely on integrations, add the matching `<dependency>` entries just like the Gradle example.

## Minimal Setup

For a minimal demo you can stick to a subset of modules. This keeps the footprint small when you only need DID creation, credential issuance, and anchoring.

```kotlin
dependencies {
    implementation("com.trustweave:TrustWeave-core:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-json:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-anchor:1.0.0-SNAPSHOT")
    testImplementation("com.trustweave:TrustWeave-testkit:1.0.0-SNAPSHOT")
}
```

**Result:** You get the same APIs as `TrustWeave-all` but can opt into additional modules later.

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
- **Kotlin Version**: 2.2.0+
- **Java Version**: 21+
- **Gradle Version**: 8.5+

## Next Steps

- [Quick Start](quick-start.md) - Create your first TrustWeave application
- [Your First Application](your-first-application.md) - Build a complete example

