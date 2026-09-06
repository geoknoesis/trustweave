# Did Key examples

Local SDK demonstrations with synthetic identities and data. These source files compile as part of the `distribution:examples` module.

## runKeyDid

Read [KeyDidExample.kt](KeyDidExample.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runKeyDid
```

Use JDK 21 and the repository Gradle wrapper (`.\gradlew.bat` on Windows). Run `:distribution:examples:checkDocumentationExamples` for all runnable entry points and `:distribution:examples:test` for the module tests.

Creates and resolves local DIDs with Ed25519, secp256k1 and P-256 keys. No hosted key custody or persistence is configured.
