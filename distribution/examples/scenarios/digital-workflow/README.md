# Digital Workflow

A reviewer signs the approval of one workflow revision.

## Run

From the repository root, with JDK 21 installed:

```sh
./gradlew :distribution:examples:runDigitalWorkflow
```

On Windows use `.\gradlew.bat` in place of `./gradlew`.
Compile all scenarios with `./gradlew :distribution:examples:compileKotlin`.
Run and verify all examples with `./gradlew :distribution:examples:checkDocumentationExamples`.

## Read the code

[DigitalWorkflowExample.kt](src/main/kotlin/DigitalWorkflowExample.kt) defines the credential type, claims and an adversarial change.
The [shared runner](../../src/main/kotlin/org/trustweave/examples/scenarios/SignedScenario.kt)
creates an issuer and holder, explicitly defines a local JSON-LD vocabulary, signs the claims,
round-trips the credential through JSON, verifies it, and checks that changing `revision`
without re-signing fails verification. Each failed assertion exits the process with an error.
It closes the SDK in `finally`.

Expected output: `digital-workflow: original verified; JSON round-trip verified; tampering rejected`.
The issuer and holder DIDs are generated locally on each run. No credentials or keys persist.

## Scope

No workflow engine, access-control policy or external audit store is integrated. This demonstrates integrity and issuer-key possession with synthetic data,
not trust in an issuer. All services and context resolution are local; no accounts or secrets are needed.
