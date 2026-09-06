# Blockchain examples

Write and read an anchored document digest with three isolated in-memory clients and assert that payloads and references round-trip unchanged. Synthetic identities and data are used throughout. These source files compile as part of the `distribution:examples` module.

## runBlockchainAnchoring

Read [BlockchainAnchoringExample.kt](BlockchainAnchoringExample.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runBlockchainAnchoring
```

Use JDK 21 and the repository Gradle wrapper (`.\gradlew.bat` on Windows). Run `:distribution:examples:checkDocumentationExamples` for all runnable entry points and `:distribution:examples:test` for the module tests.

Read the source for the exact operations and assertions. In-memory anchor clients and registries do not validate hosted networks, institutional recognition or production custody. No real credentials should be used.
