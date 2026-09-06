# SDK compatibility, coverage, and release evidence

Run these commands from the repository root with JDK 21:

```sh
./gradlew build checkKotlinAbi
./gradlew koverXmlReport koverHtmlReport
./gradlew cyclonedxBom
```

Every Kotlin JVM and multiplatform module participates in ABI validation and coverage.
Existing module coverage thresholds remain in effect. Merged coverage is a measurement,
not a claim that every module has adequate tests. Review the uncovered branches before
raising thresholds; never lower a threshold to make a failing change pass.

## API changes

The `api/` files describe the public binary surface. `checkKotlinAbi` rejects differences
from these references and also runs through Gradle `check`. For an intentional API change,
run `./gradlew updateKotlinAbi`, review the generated diff, and include migration guidance
for any incompatible change. Do not run the update task in CI: that would accept the
regression instead of detecting it. An ABI reference is not an assertion of API quality
or behavioral compatibility. Experimental APIs still need explicit stability guidance.

## Dependency inventory and signing

`cyclonedxDirectBom` produces a module dependency inventory. Maven publications attach
that module's JSON inventory with the `cyclonedx` classifier. The root `cyclonedxBom`
aggregates inventories for the repository; it describes the combined SDK, not an
individual artifact. SBOMs record dependencies and do not establish that they are safe.

Remote Maven publication requires `TRUSTWEAVE_SIGNING_KEY`, an ASCII-armored private
PGP key, and optionally `TRUSTWEAVE_SIGNING_PASSWORD` for a protected key. Configure
these through the release runner's secret store. Never place key material in Gradle
properties committed to Git. `publishToMavenLocal` supports unsigned local development.
Repository credentials and publication destinations remain release-owner configuration.

The Release evidence workflow builds and validates a tagged revision (or the selected
revision for a manual run), then produces GitHub build attestations and downloadable
JARs/inventories. It does not publish packages. A configured workflow is not evidence
of a successful release: retain the actual run and verify the downloaded artifact:

```sh
gh attestation verify path/to/artifact.jar -R geoknoesis/trustweave
```

Module reports are under the root `build/<module-path>/reports/` directory; root reports are under `build/reports/`. On Windows,
this repository normally redirects build outputs to
`%LOCALAPPDATA%/TrustWeave/gradle-build/trustweave/`; root reports use `_root`.

References: [Kotlin ABI validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html),
[Kover](https://github.com/Kotlin/kotlinx-kover),
[CycloneDX Gradle plugin](https://github.com/CycloneDX/cyclonedx-gradle-plugin),
[GitHub attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations).
