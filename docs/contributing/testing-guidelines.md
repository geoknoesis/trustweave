---
title: Testing Guidelines
nav_exclude: true
nav_order: 60
---

# Testing Guidelines

Run tests against the exact working tree you intend to ship. Keep local doubles,
real component integrations, interoperability checks and deployment qualification
separate in the results. A successful build with zero discovered tests is not
validation; a caught exception followed by a normal return is not a recorded skip.

## Prerequisites and commands

Use the checked-in Gradle wrapper and JDK 21. Python checks use Python 3.11 or newer;
the JUnit checker tests also compile Java fixtures using the JDK. Run from the SDK
repository root. PowerShell users replace `./gradlew` with `./gradlew.bat` and quote
Gradle properties containing dots, for example `'-Pkotlin.compiler.execution.strategy=in-process'`.

| Purpose | Command | Environment |
| --- | --- | --- |
| Fixture examples | `./gradlew :testkit:test --tests '*DocumentationExampleTest'` | Local JVM |
| Shared host regression suite | `./gradlew :observability:test :observability:koverXmlReport` | Local HTTP, OTLP and H2; no Docker |
| All implemented documentation examples | `./gradlew :distribution:examples:checkDocumentationExamples :distribution:examples:test` | Local doubles and loopback services |
| Full SDK tests and compilation | `./gradlew build` | Docker and module-specific integration prerequisites |
| Merged coverage | `./gradlew koverXmlReport koverHtmlReport` | Same prerequisites as the full suite |
| Lint and API compatibility | `./gradlew ktlintCheck checkKotlinAbi` | JDK 21 |
| Validation-tool regressions | `python -m unittest discover -s scripts -p 'test_check_*.py'` | Python and JDK |
| Documentation drift and links | `python scripts/check-documentation.py --report build/reports/documentation.json` | Python, Git |

The [integration guide](testing/integration-testing.md) explains prerequisite failures,
provider qualification and cleanup. The [VI guide](../operations/vi-cross-stack.md)
records the pinned independent implementation and unsupported profiles.

## Test isolation

This complete example is compiled and executed by `:testkit:test`. It checks registry
isolation, independently generated issuer identifiers and cleanup. The fixture uses
local doubles; this test does not establish interoperability of a real DID provider.

<!-- example-source: testkit/src/test/kotlin/org/trustweave/testkit/DocumentationExampleTest.kt -->
```kotlin
package org.trustweave.testkit

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentationExampleTest {
    @Test
    fun `fixtures isolate registries and close releases registrations`(): Unit =
        runBlocking {
            val first = TrustWeaveTestFixture.builder().withInMemoryBlockchainClient("eip155:1337").build()
            first.use {
                TrustWeaveTestFixture.builder().build().use { second ->
                    assertNotNull(first.getBlockchainClient("eip155:1337"))
                    assertNull(second.getBlockchainClient("eip155:1337"))
                    val issuer = first.createIssuerDid()
                    val other = second.createIssuerDid()
                    assertTrue(issuer.id.value.startsWith("did:key:"))
                    assertNotEquals(issuer.id, other.id)
                    assertEquals(1, issuer.verificationMethod.size)
                }
            }
            assertNull(first.getDidRegistry().get("key"))
            assertNull(first.getBlockchainRegistry().get("eip155:1337"))
        }
}
```

## Assertions and coroutine tests

Use `kotlin.test` or JUnit assertions. JVM `assert(...)` can be disabled and is unsuitable
for test expectations. Check semantic results, error categories, persisted state and
cleanup; test counts and line coverage do not establish those properties.

Give expression-body coroutine tests an explicit `Unit` result, as above. An
`assertFailsWith` call returns the exception, so an inferred test return type can make
JUnit ignore the method. The compiled-class gate detects non-void, private and static
direct JUnit test methods. It reads actual JVM descriptors rather than guessing from
Kotlin source. It does not validate custom composed annotations or dynamic test contents.

Preserve cancellation and fatal errors. Assert exception type and identity where the
API promises it. Coroutine debug stack recovery can copy standard exception classes and
retain the original as the cause; account for that behavior without disabling diagnostics.
Use deterministic barriers to establish concurrent ordering, bounded timeouts to prevent
hangs and cleanup in `use` or `finally`. Avoid sleeps as proof of ordering.

## Regression and requirement evidence

[The versioned test contract](../../config/testing-contract.json) names critical discovery,
host behavior and documentation tests. After a successful build, run:

```text
python scripts/check-junit-contract.py --report build/reports/junit-contract.json
python scripts/check-test-evidence.py --report build/reports/test-evidence.json
python scripts/check-coverage-policy.py
```

On Windows the SDK centralizes Gradle output under
`%LOCALAPPDATA%/TrustWeave/gradle-build/trustweave`; pass that directory as
`--build-root` to the first two commands, and its `reports/kover/report.xml` to the
coverage checker. `observability/build/reports` contains additional explicit exercise
artifacts, separate from Gradle's centralized XML output.

The result gate requires each named test to appear exactly once and pass without a skip.
It rejects missing suites, zero tests, inconsistent XML counters and failing suites.
It does not prove freshness by itself: generate results from a successful current-tree
build before consuming them. CI runs it after Gradle, preserving the build and XML artifacts.
Do not combine old XML from unrelated runs and call it a new full-suite pass.

## Coverage policy

[Coverage floors](../../config/coverage-policy.json) apply to measured Kover LINE and
BRANCH counters. Missing packages, missing counters, zero-denominator evidence,
duplicate counters, empty policies and invalid/non-finite percentages fail the check.
Module-specific local measurement uses the corresponding scoped policy.

Raise a floor only after meaningful tests pass and the resulting report supports it.
Do not remove a scope or lower a floor to get a green build. Coverage is structural
evidence: unsupported inputs, authorization, state transitions, retry and recovery need
explicit assertions even when existing tests already execute those lines.

## Documentation contract

A `example-source` marker binds the immediately following Kotlin block to a shipped
`.kt` file in this repository. The checker rejects missing/ignored/external sources,
wrong fence languages and drift. The [required example inventory](../../config/documentation-contract.json)
also rejects a removed or duplicated source marker. Keep executable examples in normal source/test sets
and register their execution in CI. Update the source first, run it, then copy the exact
source into the documentation.

Other snippets are examples or fragments, not implicitly certified compilable programs.
The checker inventories them; it does not compile every Markdown block or verify every
external link. The [host example](../operations/host/example.md) demonstrates a second
complete compiled test. Generated [capability documentation](../api-reference/assessed-capabilities.md)
must also pass `python scripts/generate-capability-docs.py --check`.

## Release acceptance

Testing/documentation reaches a complete assessment only when the agreed supported
surface has fresh test and coverage evidence, every documented supported profile has
positive and adversarial interoperability vectors, examples execute, prerequisite failures
are visible, and the exact release candidate passes hosted CI. Local host coverage alone
cannot justify a perfect repository-wide score. Track remaining work in the
[testing acceptance checklist](testing/acceptance.md).

## References

JUnit documents the [test method return/visibility contract](https://docs.junit.org/5.11.1/user-guide/index.html#writing-tests-classes-and-methods).
Kotlin documents [coroutine stacktrace recovery and exception copying](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/debugging.md#stacktrace-recovery).
These explain the validation rules; the regression evidence comes from this repository's executed tests.
