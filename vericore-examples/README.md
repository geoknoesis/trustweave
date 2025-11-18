# VeriCore Examples

This module contains runnable examples demonstrating various use cases for VeriCore.

## Available Examples

### Earth Observation
- **File**: `com.geoknoesis.vericore.examples.eo.EarthObservationExample`
- **Description**: Demonstrates EO data integrity workflow with artifacts, linksets, VCs, and blockchain anchoring
- **Run**: `./gradlew :vericore-examples:runEarthObservation`

### Academic Credentials
- **File**: `com.geoknoesis.vericore.examples.academic.AcademicCredentialsExample`
- **Description**: Shows how universities issue degree credentials and students manage them in wallets
- **Run**: `./gradlew :vericore-examples:runAcademicCredentials`

### Professional Identity
- **File**: `com.geoknoesis.vericore.examples.professional.ProfessionalIdentityExample`
- **Description**: Demonstrates professional credential wallet with education, work experience, and certifications
- **Run**: `./gradlew :vericore-examples:runProfessionalIdentity`

### Indy Integration
- **File**: `com.geoknoesis.vericore.examples.indy.IndyIntegrationExample`
- **Description**: Complete end-to-end scenario using Hyperledger Indy for blockchain anchoring. Demonstrates DID creation, credential issuance, verification, wallet storage, and anchoring to Indy blockchain (BCovrin Testnet)
- **Run**: `./gradlew :vericore-examples:runIndyIntegration`

## Running Examples

Each example can be run using Gradle:

```bash
# Run Earth Observation example
./gradlew :vericore-examples:runEarthObservation

# Run Academic Credentials example
./gradlew :vericore-examples:runAcademicCredentials

# Run Professional Identity example
./gradlew :vericore-examples:runProfessionalIdentity

# Run Indy Integration example
./gradlew :vericore-examples:runIndyIntegration
```

## Building Examples

To compile all examples:

```bash
./gradlew :vericore-examples:compileKotlin
```

## Source Code Location

All example source code is located in:
```
vericore-examples/src/main/kotlin/io/geoknoesis/vericore/examples/
```

Each example is organized in its own package:
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

## Documentation

For detailed explanations of each scenario, see the documentation in `docs/getting-started/`:
- [Earth Observation Scenario](../../docs/getting-started/earth-observation-scenario.md)
- [Academic Credentials Scenario](../../docs/getting-started/academic-credentials-scenario.md)
- [Professional Identity Scenario](../../docs/getting-started/professional-identity-scenario.md)
- And more...

