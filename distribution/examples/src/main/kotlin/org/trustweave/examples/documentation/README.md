# Documentation examples

Run the exact quick-start program embedded in the top-level guides: generate DIDs, register an explicit claim vocabulary, issue and verify one credential. Synthetic identities and data are used throughout. These source files compile as part of the `distribution:examples` module.

## runDocumentationQuickStart

Read [DocumentationQuickStart.kt](DocumentationQuickStart.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runDocumentationQuickStart
```

Use JDK 21 and the repository Gradle wrapper (`.\gradlew.bat` on Windows). Run `:distribution:examples:checkDocumentationExamples` for all runnable entry points and `:distribution:examples:test` for the module tests.

Read the source for the exact operations and assertions. In-memory anchor clients and registries do not validate hosted networks, institutional recognition or production custody. No real credentials should be used.
