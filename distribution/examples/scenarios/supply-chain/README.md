# Supply Chain

A supplier signs a shipment batch and destination.

## Run

From the repository root, with JDK 21 installed:

```sh
./gradlew :distribution:examples:runSupplyChain
```

On Windows use `.\gradlew.bat` in place of `./gradlew`.
Compile all scenarios with `./gradlew :distribution:examples:compileKotlin`.
Run and verify all examples with `./gradlew :distribution:examples:checkDocumentationExamples`.

## Read the code

[SupplyChainExample.kt](src/main/kotlin/SupplyChainExample.kt) defines the credential type, claims and an adversarial change.
The [shared runner](../../src/main/kotlin/org/trustweave/examples/scenarios/SignedScenario.kt)
creates an issuer and holder, explicitly defines a local JSON-LD vocabulary, signs the claims,
round-trips the credential through JSON, verifies it, and checks that changing `destination`
without re-signing fails verification. Each failed assertion exits the process with an error.
It closes the SDK in `finally`.

Expected output: `supply-chain: original verified; JSON round-trip verified; tampering rejected`.
The issuer and holder DIDs are generated locally on each run. No credentials or keys persist.

## Scope

No logistics provider or physical custody event is verified. This demonstrates integrity and issuer-key possession with synthetic data,
not trust in an issuer. All services and context resolution are local; no accounts or secrets are needed.
