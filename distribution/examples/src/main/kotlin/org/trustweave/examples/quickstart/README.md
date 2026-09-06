# Quickstart examples

Demonstrate subject digests, DID creation, credential issuance and verification, a presentation and an in-memory anchor write. Synthetic identities and data are used throughout. These source files compile as part of the `distribution:examples` module.

## runQuickStartSample

Read [QuickStartSample.kt](QuickStartSample.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runQuickStartSample
```

Use JDK 21 and the repository Gradle wrapper (`.\gradlew.bat` on Windows). Run `:distribution:examples:checkDocumentationExamples` for all runnable entry points and `:distribution:examples:test` for the module tests.

Read the source for the exact operations and assertions. In-memory anchor clients and registries do not validate hosted networks, institutional recognition or production custody. No real credentials should be used.
