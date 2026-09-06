# Scenario examples

Small, runnable examples with synthetic domain claims. Each directory contains its README and Kotlin source; Gradle compiles them as part of `distribution:examples`. No code is copied into documentation.

- [Proof Of Location](proof-of-location/README.md): An observer signs a location claim for a subject.
- [Digital Workflow](digital-workflow/README.md): A reviewer signs the approval of one workflow revision.
- [News Industry](news-industry/README.md): An editor signs an article identifier, revision and author attribution.
- [Data Catalog](data-catalog/README.md): A publisher signs dataset catalog metadata.
- [Healthcare](healthcare/README.md): A clinic signs a synthetic encounter record for a patient DID.
- [Government](government/README.md): An example municipal issuer signs a residence claim.
- [Supply Chain](supply-chain/README.md): A supplier signs a shipment batch and destination.
- [Financial Services](financial-services/README.md): An example reviewer signs a synthetic identity-review outcome.
- [Iot Device](iot-device/README.md): An operator signs a device registration and firmware revision.

Run all examples from the repository root:

```sh
./gradlew :distribution:examples:checkDocumentationExamples
```

Each small scenario asserts successful issuance, JSON round-trip, signature verification and rejection of a modified claim. Domain services, legal authority, hosted providers and physical devices are outside this local demonstration. See [the full examples catalog](../README.md) for the larger SDK workflows.
