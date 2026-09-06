# Comprehensive examples

Local SDK demonstrations with synthetic identities and data. These source files compile as part of the `distribution:examples` module.

## runCredentialLifecycle

Read [ComprehensiveDslExample.kt](ComprehensiveDslExample.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runCredentialLifecycle
```

Use JDK 21 and the repository Gradle wrapper (`.\gradlew.bat` on Windows). Run `:distribution:examples:checkDocumentationExamples` for all runnable entry points and `:distribution:examples:test` for the module tests.

This lifecycle issues a revocable training credential, stores and retrieves it from a local wallet, verifies it, revokes it, and asserts rejection. Status lists are local; publication and remote freshness checks are not covered.
