# Trust examples

Model trust anchors, credential-type trust decisions and DID capability delegation in a local trust registry. No external institution or trust list is consulted. Synthetic identities and data are used throughout. These source files compile as part of the `distribution:examples` module.

## runWebOfTrust

Read [WebOfTrustExample.kt](WebOfTrustExample.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runWebOfTrust
```

Use JDK 21 and the repository Gradle wrapper (`.\gradlew.bat` on Windows). Run `:distribution:examples:checkDocumentationExamples` for all runnable entry points and `:distribution:examples:test` for the module tests.

Read the source for the exact operations and assertions. In-memory anchor clients and registries do not validate hosted networks, institutional recognition or production custody. No real credentials should be used.
