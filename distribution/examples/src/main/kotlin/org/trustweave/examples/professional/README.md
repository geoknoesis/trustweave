# Professional examples

Local SDK demonstrations with synthetic identities and data. These source files compile as part of the `distribution:examples` module.

## runProfessionalIdentity

Read [ProfessionalIdentityExample.kt](ProfessionalIdentityExample.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runProfessionalIdentity
```

Use JDK 21 and the repository Gradle wrapper (`.\gradlew.bat` on Windows). Run `:distribution:examples:checkDocumentationExamples` for all runnable entry points and `:distribution:examples:test` for the module tests.

Organizes synthetic education, employment and certification records and runs schema validation. Some fixture certificates are intentionally expired. The schemas are illustrative, not industry credential standards.
