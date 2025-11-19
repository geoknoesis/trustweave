# Installation

Add VeriCore to your Kotlin project using Gradle or Maven.

## Gradle (Kotlin DSL)

Add VeriCore dependencies to your `build.gradle.kts`. This brings in the core runtime, optional adapters, and the test kit you will use in the tutorials.

```kotlin
dependencies {
    // Core modules (required)
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT")
    
    // Test kit (for testing)
    testImplementation("com.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
    
    // Optional: Integration modules (using hierarchical group IDs)
    implementation("com.geoknoesis.vericore.kms:waltid:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore.did:godiddy:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore.chains:algorand:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore.chains:polygon:1.0.0-SNAPSHOT")
    
    // Kotlinx Serialization (required)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Coroutines (required)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

**Result:** Gradle resolves the full VeriCore BOM plus kotlinx libraries so you can compile the sample code in this guide.

## Gradle (Groovy)

Add VeriCore dependencies to your `build.gradle`. Use this variant if your project still uses the Groovy DSL.

```groovy
dependencies {
    // Core modules (required)
    implementation 'com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT'
    implementation 'com.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT'
    implementation 'com.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT'
    implementation 'com.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT'
    implementation 'com.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT'
    
    // Test kit (for testing)
    testImplementation 'com.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT'
    
    // Optional: Integration modules (using hierarchical group IDs)
    implementation 'com.geoknoesis.vericore.kms:waltid:1.0.0-SNAPSHOT'
    implementation 'com.geoknoesis.vericore.did:godiddy:1.0.0-SNAPSHOT'
    implementation 'com.geoknoesis.vericore.chains:algorand:1.0.0-SNAPSHOT'
    implementation 'com.geoknoesis.vericore.chains:polygon:1.0.0-SNAPSHOT'
}
```

**Result:** The Groovy build picks up the same modules; no additional configuration is required besides applying the Kotlin and serialization plugins.

## Maven

Add VeriCore dependencies to your `pom.xml`. The snippet lists the minimum set needed to follow the scenarios.

```xml
<dependencies>
    <!-- Core modules (required) -->
    <dependency>
        <groupId>com.geoknoesis.vericore</groupId>
        <artifactId>vericore-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.geoknoesis.vericore</groupId>
        <artifactId>vericore-json</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.geoknoesis.vericore</groupId>
        <artifactId>vericore-kms</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.geoknoesis.vericore</groupId>
        <artifactId>vericore-did</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.geoknoesis.vericore</groupId>
        <artifactId>vericore-anchor</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Test kit (for testing) -->
    <dependency>
        <groupId>com.geoknoesis.vericore</groupId>
        <artifactId>vericore-testkit</artifactId>
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
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT")
    testImplementation("com.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
}
```

**Result:** You get the same APIs as `vericore-all` but can opt into additional modules later.

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

- [Quick Start](quick-start.md) - Create your first VeriCore application
- [Your First Application](your-first-application.md) - Build a complete example

