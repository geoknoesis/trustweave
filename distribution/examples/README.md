# TrustWeave examples

Start with the [scenario folder](scenarios/README.md): each small example has Kotlin source,
a purpose, commands, expected output and explicit limits in its README.
The larger SDK demonstrations remain under `src/main/kotlin`, with a README beside each.

## Build and verify

Use JDK 21 and the repository Gradle wrapper. From the repository root:

```sh
./gradlew :distribution:examples:compileKotlin
./gradlew :distribution:examples:checkDocumentationExamples :distribution:examples:test :distribution:examples:ktlintCheck
```

On Windows replace `./gradlew` with `.\gradlew.bat`. All 24 entry points are included in the
aggregate execution check. These local demonstrations need no hosted accounts or secrets.
The nine small domain scenarios assert issuance, JSON round-trip, valid signature and rejection
of a modified claim. Lifecycle and delegation demos also exercise negative outcomes.
Other examples have differing assertion coverage; successful execution is not an exhaustive audit.

## Examples catalog

| Task | Source and purpose |
| --- | --- |
| `runDocumentationQuickStart` | [DocumentationQuickStart](src/main/kotlin/org/trustweave/examples/documentation/README.md) |
| `runEarthObservation` | [EarthObservationExample](src/main/kotlin/org/trustweave/examples/eo/README.md) |
| `runAcademicCredentials` | [AcademicCredentialsExample](src/main/kotlin/org/trustweave/examples/academic/README.md) |
| `runProfessionalIdentity` | [ProfessionalIdentityExample](src/main/kotlin/org/trustweave/examples/professional/README.md) |
| `runProofOfLocation` | [ProofOfLocationExample](scenarios/proof-of-location/README.md) |
| `runSpatialWeb` | [SpatialWebExample](src/main/kotlin/org/trustweave/examples/spatial/README.md) |
| `runDigitalWorkflow` | [DigitalWorkflowExample](scenarios/digital-workflow/README.md) |
| `runNewsIndustry` | [NewsIndustryExample](scenarios/news-industry/README.md) |
| `runDataCatalog` | [DataCatalogExample](scenarios/data-catalog/README.md) |
| `runHealthcare` | [HealthcareExample](scenarios/healthcare/README.md) |
| `runGovernment` | [GovernmentIdentityExample](scenarios/government/README.md) |
| `runSupplyChain` | [SupplyChainExample](scenarios/supply-chain/README.md) |
| `runFinancialServices` | [FinancialServicesExample](scenarios/financial-services/README.md) |
| `runIoT` | [IoTDeviceExample](scenarios/iot-device/README.md) |
| `runNationalEducation` | [NationalEducationExample](src/main/kotlin/org/trustweave/examples/national/README.md) |
| `runQuickStartSample` | [QuickStartSample](src/main/kotlin/org/trustweave/examples/quickstart/README.md) |
| `runIndyIntegration` | [IndyIntegrationExample](src/main/kotlin/org/trustweave/examples/indy/README.md) |
| `runKeyDid` | [KeyDidExample](src/main/kotlin/org/trustweave/examples/did-key/README.md) |
| `runJwkDid` | [JwkDidExample](src/main/kotlin/org/trustweave/examples/did-jwk/README.md) |
| `runBlockchainAnchoring` | [BlockchainAnchoringExample](src/main/kotlin/org/trustweave/examples/blockchain/README.md) |
| `runCredentialLifecycle` | [ComprehensiveDslExample](src/main/kotlin/org/trustweave/examples/comprehensive/README.md) |
| `runDelegationChain` | [DelegationChainExample](src/main/kotlin/org/trustweave/examples/delegation/README.md) |
| `runAcademicCredentialsDsl` | [AcademicCredentialsDslExample](src/main/kotlin/org/trustweave/examples/academic/README.md) |
| `runWebOfTrust` | [WebOfTrustExample](src/main/kotlin/org/trustweave/examples/trust/README.md) |

## Relationship to the guides

The main quick-start code blocks are checked against their executable source. Longer scenario
guides contain explanatory fragments and broader architecture proposals; they are not all
standalone applications. Prefer the source and README linked above for commands you can run.

All credentials, authorities and domain claims are illustrative. A valid signature establishes
integrity and possession of an issuer key, not truth or trust in the issuer. Provider/network,
hardware custody, external schema standards and production operations require separate validation.
