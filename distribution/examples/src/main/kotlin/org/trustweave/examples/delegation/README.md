# Delegation examples

Local SDK demonstrations with synthetic identities and data. These source files compile as part of the `distribution:examples` module.

## runDelegationChain

Read [DelegationChainExample.kt](DelegationChainExample.kt) and run from the repository root:

```sh
./gradlew :distribution:examples:runDelegationChain
```

Use JDK 21 and the repository Gradle wrapper (`.\gradlew.bat` on Windows). Run `:distribution:examples:checkDocumentationExamples` for all runnable entry points and `:distribution:examples:test` for the module tests.

Demonstrates a local CEO-to-director-to-manager capability chain and rejects an undelegated employee. Local DID-document updates do not establish portable mutable did:key semantics or external organizational authority.
