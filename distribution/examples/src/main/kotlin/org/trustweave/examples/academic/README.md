# Academic examples

Issue a degree credential, store it in a student wallet, organize it and verify it. The DSL variant also demonstrates a locally configured revocation status list. Synthetic identities and data are used throughout. These source files compile as part of the `distribution:examples` module.

## runAcademicCredentials

Read [AcademicCredentialsExample.kt](AcademicCredentialsExample.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runAcademicCredentials
```

## runAcademicCredentialsDsl

Read [AcademicCredentialsDslExample.kt](AcademicCredentialsDslExample.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runAcademicCredentialsDsl
```

Use JDK 21 and the repository Gradle wrapper (`.\gradlew.bat` on Windows). Run `:distribution:examples:checkDocumentationExamples` for all runnable entry points and `:distribution:examples:test` for the module tests.

Read the source for the exact operations and assertions. In-memory anchor clients and registries do not validate hosted networks, institutional recognition or production custody. No real credentials should be used.
