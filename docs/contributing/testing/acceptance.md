# Testing and documentation acceptance

The review uses one combined **Testing and documentation** category. A 10/10 is a
complete engineering assessment of the agreed supported SDK surface, not a guarantee
that software has no defects. It requires evidence for every gate below. Do not award
10/10 for a filtered host run, a high line percentage, or a list of planned CI jobs.

| Gate | Acceptance evidence | Current status |
| --- | --- | --- |
| Discovery integrity | Compiled JUnit signature check and named critical-test XML checks pass after a current-tree build | Passed: 3831 compiled direct JUnit methods and 47 required named regressions checked; no invalid or missing required evidence |
| Complete supported-surface regression | Exact release candidate passes the full SDK build, supported targets, and required component integrations; every skip has an accepted reason | Passed for credential-free SDK candidate `32a73dfa6d8d`; 15 exact optional provider/example skips retained with reasons |
| Coverage and test quality | Fresh merged LINE/BRANCH counters meet reviewed floors; package-level gaps in security, persistence, parsing and recovery have meaningful adversarial tests | Fresh merged line/branch coverage and every current policy floor pass; deeper coverage remains work for 10/10 |
| Independent interoperability | A versioned requirement-to-vector matrix covers positive and adversarial cases in both directions for each supported profile, with explained stricter policies | Expanded: 26 Python vectors, three reverse profiles, 65 reference evaluations and 214 sequential PostgreSQL model transitions pass; exhaustive supported-profile/external executor agreement remains open |
| Reproducible examples | Maintained complete examples are copied from shipped source, compiled and executed; partial snippets are labelled as fragments | Nine exact source-copy contracts across seven files and 24 local entry points pass locally and in candidate CI; wider snippet conversion/review remains open |
| Accurate product documentation | API/version, capabilities, configuration, errors, migration, privacy and operational limits agree with code; maintained links resolve | Maintained-path, fence, API-pattern and source-copy checks pass; broad semantic review and external-link qualification remain open |
| Failure and recovery evidence | Cancellation, retries, saturation, corruption/migration and uncertain outcomes are tested at their real component boundary | Full candidate component tests and documented notification/recovery exercises pass; live provider, production load and PITR qualification remain separate |
| Release reproducibility | Required gates run on the exact committed SDK/release candidate in hosted CI and archive XML, source/artifact identity and coverage | Hosted release-evidence run passes for `32a73dfa6d8d` with retained JUnit/coverage/source identity and build attestation; publishing and deployed-artifact qualification remain open |

## Remaining qualification work

The [candidate qualification review](../../reviews/2026-09-10-testing-qualification/index.html)
raises this category to **9.5/10** with retained evidence and explicit limits.

1. Add meaningful tests for uncovered critical branches using the merged coverage report.
2. Complete independent conformance for every supported constraint/profile and malformed
   encoding, including external merchant and concurrent/stateful executor agreement.
3. Review the wider Kotlin snippet inventory beyond the nine protected source-copy contracts;
   convert complete examples and verify fragments in their declared context.
4. Qualify the optional external-service/HSM combinations in identified authorized
   environments. Approved SDK-gate skips are not provider qualification.
5. Qualify published artifacts and deployed operations, including load, recovery objectives,
   journal authentication and actual on-call delivery, before claiming those capabilities.

## Automated checks and limits

The [test contract](../../../config/testing-contract.json) maps a scoped set of critical
requirements to concrete methods. `check-test-evidence.py` rejects missing/failed/skipped
required tests. `check-junit-contract.py` examines direct JVM test annotations; custom
composed annotations, dynamic factories, JavaScript/native targets and framework lifecycle
rules need their own discovery checks. Neither script proves that an old build directory
matches the current source without a preceding successful build.

The [coverage policy](../../../config/coverage-policy.json) retains repository-wide floors;
the [host policy](../../../config/host-coverage-policy.json) adds measured local floors.
The documentation checker verifies maintained local paths, selected API patterns, fences
and exact source copies. It inventories other Kotlin blocks and explicitly does not certify
their compilation, external URLs or historical designs.

## Committed candidate evidence

`release-evidence.yml` runs the full SDK build, lint and ABI checks, merged coverage
policy, pinned interoperability reference suite, both-direction token checks, exact
conformance inventory, compiled JUnit discovery checks and required named regressions.
It also exercises the documented alert/notification paths and archives diagnostics
when a gate fails. A successful job writes `build/reports/validation-manifest.json`
with the exact Git commit/tree, source and JUnit/coverage hashes and test counters.
The manifest refuses dirty source and a mismatched `GITHUB_SHA`.

The [skip policy](../../../config/test-skip-policy.json) enumerates the exact optional
external-service/HSM/example cases allowed in the credential-free SDK gate. An
unlisted skipped test fails candidate qualification. Each retained skip carries its
reason; none is counted as executed provider coverage. Required named regressions
cannot use these exceptions. Live custody and deployed-service qualification still
require their own authorized environments.

For local diagnostics on Windows with the default external build layout, use:

```text
python scripts/record-validation-manifest.py --allow-dirty --build-root C:/Users/USER/AppData/Local/TrustWeave/gradle-build/trustweave --coverage-report C:/Users/USER/AppData/Local/TrustWeave/gradle-build/trustweave/_root/reports/kover/report.xml --output .gradle/local-validation.json
```

Replace `USER` with the actual account directory. `--allow-dirty` permits local changes and marks evidence from a dirty tree as
**not a release candidate**. Run it only after the preceding full
build and policy checks succeed. A manifest is an identity record, not independent
proof of execution freshness. Never run concurrent Gradle builds that share an output
directory; an isolated Windows worktree must use `-Ptrustweave.windowsInRepoBuild=true`.
