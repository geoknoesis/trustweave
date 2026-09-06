# Indy examples

Exercise the Indy adapter in explicit in-memory test mode, including anchor write/read and service-provider discovery. It does not contact BCovrin or a live ledger. Synthetic identities and data are used throughout. These source files compile as part of the `distribution:examples` module.

## runIndyIntegration

Read [IndyIntegrationExample.kt](IndyIntegrationExample.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runIndyIntegration
```

Use JDK 21 and the repository Gradle wrapper (`.\gradlew.bat` on Windows). Run `:distribution:examples:checkDocumentationExamples` for all runnable entry points and `:distribution:examples:test` for the module tests.

Read the source for the exact operations and assertions. In-memory anchor clients and registries do not validate hosted networks, institutional recognition or production custody. No real credentials should be used.
