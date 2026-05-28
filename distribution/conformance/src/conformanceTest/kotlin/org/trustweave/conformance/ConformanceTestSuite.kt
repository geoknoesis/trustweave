package org.trustweave.conformance

/**
 * Phase 1 Conformance Test Suite — aggregator marker.
 *
 * All conformance tests are tagged with @Tag("conformance") and collected by the
 * `conformanceTest` Gradle task (see `distribution/conformance/build.gradle.kts`).
 *
 * Suites covered by this module:
 *  - [VcDataModel20ConformanceTest]    W3C VC Data Model 2.0 (@Tag("vc-data-model-2.0"))
 *  - [DidCore11ConformanceTest]        W3C DID Core 1.1     (@Tag("did-core-1.1"))
 *  - [PresentationExchangeConformanceTest] DIF PEx v2       (@Tag("presentation-exchange"))
 *
 * To run only a specific suite subset:
 *   ./gradlew :distribution:conformance:conformanceTest
 *        --tests "org.trustweave.conformance.VcDataModel20ConformanceTest"
 *
 * Phase 1 exit criteria (see docs/architecture/conformance-testing-plan.md §6):
 *   Every P0 self-run suite green on `main` for ten consecutive nightly runs.
 */
object ConformanceTestSuite
