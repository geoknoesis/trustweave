# TrustWeave Examples

This module contains runnable examples demonstrating various use cases for TrustWeave.

## Available Examples

### Quick Start
- **File**: `org.trustweave.examples.quickstart.QuickStartSample`
- **Description**: Minimal end-to-end credential issuance sample for newcomers
- **Run**: `./gradlew :distribution:examples:runQuickStartSample`

### DID Methods
- **did:key** — `org.trustweave.examples.did_key.KeyDidExample` — `./gradlew :distribution:examples:runKeyDid`
- **did:jwk** — `org.trustweave.examples.did_jwk.JwkDidExample` — `./gradlew :distribution:examples:runJwkDid`

### Earth Observation
- **File**: `org.trustweave.examples.eo.EarthObservationExample`
- **Description**: Demonstrates EO data integrity workflow with artifacts, linksets, VCs, and blockchain anchoring
- **Run**: `./gradlew :distribution:examples:runEarthObservation`

### Academic Credentials
- **File**: `org.trustweave.examples.academic.AcademicCredentialsExample`
- **Description**: Shows how universities issue degree credentials and students manage them in wallets
- **Run**: `./gradlew :distribution:examples:runAcademicCredentials`

### Professional Identity
- **File**: `org.trustweave.examples.professional.ProfessionalIdentityExample`
- **Description**: Demonstrates professional credential wallet with education, work experience, and certifications
- **Run**: `./gradlew :distribution:examples:runProfessionalIdentity`

### Indy Integration
- **File**: `org.trustweave.examples.indy.IndyIntegrationExample`
- **Description**: End-to-end scenario using Hyperledger Indy for blockchain anchoring. Demonstrates DID creation, credential issuance, verification, wallet storage, and anchoring to Indy (BCovrin Testnet)
- **Run**: `./gradlew :distribution:examples:runIndyIntegration`

### Blockchain Anchoring
- **File**: `org.trustweave.examples.blockchain.BlockchainAnchoringExample`
- **Description**: Anchors data to EVM chains (Ethereum, Base, Arbitrum) via the anchor plugins
- **Run**: `./gradlew :distribution:examples:runBlockchainAnchoring`

### Additional Scenarios

| Scenario | Class | Gradle task |
|---|---|---|
| Proof of Location | `org.trustweave.examples.location.ProofOfLocationExample` | `runProofOfLocation` |
| Spatial Web Authorization | `org.trustweave.examples.spatial.SpatialWebExample` | `runSpatialWeb` |
| Digital Workflow Provenance | `org.trustweave.examples.workflow.DigitalWorkflowExample` | `runDigitalWorkflow` |
| News Industry | `org.trustweave.examples.news.NewsIndustryExample` | `runNewsIndustry` |
| Data Catalog (DCAT) | `org.trustweave.examples.dcat.DataCatalogExample` | `runDataCatalog` |
| Healthcare Medical Records | `org.trustweave.examples.healthcare.HealthcareExample` | `runHealthcare` |
| Government Digital Identity | `org.trustweave.examples.government.GovernmentIdentityExample` | `runGovernment` |
| Supply Chain Traceability | `org.trustweave.examples.supplychain.SupplyChainExample` | `runSupplyChain` |
| Financial Services KYC | `org.trustweave.examples.financial.FinancialServicesExample` | `runFinancialServices` |
| IoT Device Identity | `org.trustweave.examples.iot.IoTDeviceExample` | `runIoT` |
| National Education Credentials | `org.trustweave.examples.national.NationalEducationExample` | `runNationalEducation` |
| Web of Trust | `org.trustweave.examples.trust.WebOfTrustExample` | _(see source; no dedicated task)_ |
| Delegation Chain | `org.trustweave.examples.delegation.DelegationChainExample` | _(see source; no dedicated task)_ |
| Comprehensive DSL | `org.trustweave.examples.comprehensive.ComprehensiveDslExample` | _(see source; no dedicated task)_ |

> **TODO**: A couple of source files (`trust/WebOfTrustExample.kt`, `delegation/DelegationChainExample.kt`, `comprehensive/ComprehensiveDslExample.kt`, `academic/AcademicCredentialsDslExample.kt`) do not yet have dedicated Gradle `JavaExec` tasks in `distribution/examples/build.gradle.kts`. Run them directly from your IDE or add a `tasks.register<JavaExec>` entry if you need a CLI invocation.

## Running Examples

Each example is registered as a Gradle `JavaExec` task in `distribution/examples/build.gradle.kts`:

```bash
# Examples
./gradlew :distribution:examples:runQuickStartSample
./gradlew :distribution:examples:runEarthObservation
./gradlew :distribution:examples:runAcademicCredentials
./gradlew :distribution:examples:runProfessionalIdentity
./gradlew :distribution:examples:runIndyIntegration
./gradlew :distribution:examples:runBlockchainAnchoring
./gradlew :distribution:examples:runKeyDid
./gradlew :distribution:examples:runJwkDid
```

## Building Examples

To compile all examples:

```bash
./gradlew :distribution:examples:compileKotlin
```

## Source Code Location

All example source code lives under:

```
distribution/examples/src/main/kotlin/org/trustweave/examples/
```

Each example is organized in its own package:
- `quickstart/` - Quick Start sample
- `did-key/` - did:key example (package `org.trustweave.examples.did_key`)
- `did-jwk/` - did:jwk example (package `org.trustweave.examples.did_jwk`)
- `eo/` - Earth Observation examples
- `academic/` - Academic Credentials examples
- `professional/` - Professional Identity examples
- `location/` - Proof of Location examples
- `spatial/` - Spatial Web Authorization examples
- `workflow/` - Digital Workflow Provenance examples
- `news/` - News Industry examples
- `dcat/` - Data Catalog DCAT examples
- `healthcare/` - Healthcare Medical Records examples
- `government/` - Government Digital Identity examples
- `supplychain/` - Supply Chain Traceability examples
- `financial/` - Financial Services KYC examples
- `iot/` - IoT Device Identity examples
- `national/` - National Education Credentials examples
- `indy/` - Indy Integration examples
- `blockchain/` - EVM blockchain anchoring examples
- `trust/` - Web of Trust examples
- `delegation/` - Delegation chain examples
- `comprehensive/` - Comprehensive DSL example

## Documentation

For detailed explanations of each scenario, see the documentation in `docs/getting-started/` and `docs/scenarios/`:
- [Earth Observation Scenario](../../docs/getting-started/earth-observation-scenario.md)
- [Academic Credentials Scenario](../../docs/getting-started/academic-credentials-scenario.md)
- [Professional Identity Scenario](../../docs/getting-started/professional-identity-scenario.md)
- And more under [`docs/scenarios/`](../../docs/scenarios/).
