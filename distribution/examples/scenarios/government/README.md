# Government

An example municipal issuer signs a residence claim.

## Run

From the repository root, with JDK 21 installed:

```sh
./gradlew :distribution:examples:runGovernment
```

On Windows use `.\gradlew.bat` in place of `./gradlew`.
Compile all scenarios with `./gradlew :distribution:examples:compileKotlin`.
Run and verify all examples with `./gradlew :distribution:examples:checkDocumentationExamples`.

## Read the code

[GovernmentIdentityExample.kt](src/main/kotlin/GovernmentIdentityExample.kt) defines the credential type, claims and an adversarial change.
The [shared runner](../../src/main/kotlin/org/trustweave/examples/scenarios/SignedScenario.kt)
creates an issuer and holder, explicitly defines a local JSON-LD vocabulary, signs the claims,
round-trips the credential through JSON, verifies it, and checks that changing `municipality`
without re-signing fails verification. Each failed assertion exits the process with an error.
It closes the SDK in `finally`.

Expected output: `government: original verified; JSON round-trip verified; tampering rejected`.
The issuer and holder DIDs are generated locally on each run. No credentials or keys persist.

## Scope

The generated issuer has no government authority and this credential is not an identity document. This demonstrates integrity and issuer-key possession with synthetic data,
not trust in an issuer. All services and context resolution are local; no accounts or secrets are needed.
