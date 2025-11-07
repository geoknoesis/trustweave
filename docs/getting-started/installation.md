# Installation

Add VeriCore to your Kotlin project using Gradle or Maven.

## Gradle (Kotlin DSL)

Add VeriCore dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core modules (required)
    implementation("io.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT")
    
    // Test kit (for testing)
    testImplementation("io.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
    
    // Optional: Integration modules
    implementation("io.geoknoesis.vericore:vericore-waltid:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-godiddy:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-algorand:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-polygon:1.0.0-SNAPSHOT")
    
    // Kotlinx Serialization (required)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Coroutines (required)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

## Gradle (Groovy)

Add VeriCore dependencies to your `build.gradle`:

```groovy
dependencies {
    // Core modules (required)
    implementation 'io.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT'
    implementation 'io.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT'
    implementation 'io.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT'
    implementation 'io.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT'
    implementation 'io.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT'
    
    // Test kit (for testing)
    testImplementation 'io.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT'
    
    // Optional: Integration modules
    implementation 'io.geoknoesis.vericore:vericore-waltid:1.0.0-SNAPSHOT'
    implementation 'io.geoknoesis.vericore:vericore-godiddy:1.0.0-SNAPSHOT'
    implementation 'io.geoknoesis.vericore:vericore-algorand:1.0.0-SNAPSHOT'
    implementation 'io.geoknoesis.vericore:vericore-polygon:1.0.0-SNAPSHOT'
}
```

## Maven

Add VeriCore dependencies to your `pom.xml`:

```xml
<dependencies>
    <!-- Core modules (required) -->
    <dependency>
        <groupId>io.geoknoesis.vericore</groupId>
        <artifactId>vericore-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>io.geoknoesis.vericore</groupId>
        <artifactId>vericore-json</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>io.geoknoesis.vericore</groupId>
        <artifactId>vericore-kms</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>io.geoknoesis.vericore</groupId>
        <artifactId>vericore-did</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>io.geoknoesis.vericore</groupId>
        <artifactId>vericore-anchor</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Test kit (for testing) -->
    <dependency>
        <groupId>io.geoknoesis.vericore</groupId>
        <artifactId>vericore-testkit</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Minimal Setup

For a minimal setup, you only need:

```kotlin
dependencies {
    implementation("io.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT")
    testImplementation("io.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
}
```

## Repository Configuration

If using SNAPSHOT versions, ensure your repository configuration includes:

```kotlin
repositories {
    mavenCentral()
    // Add snapshot repository if needed
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}
```

## Version Information

- **Current Version**: 1.0.0-SNAPSHOT
- **Kotlin Version**: 2.2.0+
- **Java Version**: 21+
- **Gradle Version**: 8.5+

## Next Steps

- [Quick Start](quick-start.md) - Create your first VeriCore application
- [Your First Application](your-first-application.md) - Build a complete example

