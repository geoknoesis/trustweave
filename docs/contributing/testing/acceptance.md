# Testing and documentation acceptance

The review uses one combined **Testing and documentation** category. A 10/10 is a
complete engineering assessment of the agreed supported SDK surface, not a guarantee
that software has no defects. It requires evidence for every gate below. Do not award
10/10 for a filtered host run, a high line percentage, or a list of planned CI jobs.

| Gate | Acceptance evidence | Current status |
| --- | --- | --- |
| Discovery integrity | Compiled JUnit signature check and named critical-test XML checks pass after a current-tree build | Implemented; eight silently ignored methods corrected; local affected suites verified in the current review |
| Complete supported-surface regression | Exact release candidate passes the full SDK build, supported targets, and required component integrations; every skip has an accepted reason | Open: full current-tree suite not established; Docker now responds; a fresh full SDK run is in progress |
| Coverage and test quality | Fresh merged LINE/BRANCH counters meet reviewed floors; package-level gaps in security, persistence, parsing and recovery have meaningful adversarial tests | Partial: host has measured 97% line / 85% branch floors; broader current-tree merged coverage still required |
| Independent interoperability | A versioned requirement-to-vector matrix covers positive and adversarial cases in both directions for each supported profile, with explained stricter policies | Open: immediate and autonomous subset evidence exists; merchant and stateful independent agreement remain incomplete |
| Reproducible examples | Maintained complete examples are copied from shipped source, compiled and executed; partial snippets are labelled as fragments | Improved: fixture, host, verification, merchant authentication, wallet configuration and ledger migration guides now have executable source contracts; the wider snippet inventory still needs conversion/review |
| Accurate product documentation | API/version, capabilities, configuration, errors, migration, privacy and operational limits agree with code; maintained links resolve | Automated local checks implemented; broad semantic review and external-link qualification remain open |
| Failure and recovery evidence | Cancellation, retries, saturation, corruption/migration and uncertain outcomes are tested at their real component boundary | Strong scoped host and historical ledger evidence; full supported-provider matrix still requires qualification |
| Release reproducibility | Required gates run on the exact committed SDK/release candidate in hosted CI and archive XML, source/artifact identity and coverage | Open: changes are local and uncommitted; no hosted or published-artifact pass claimed |

## Next executable work

1. Restore an available Docker engine, then run the full `build`, `koverXmlReport` and
   `koverHtmlReport` tasks. Keep all existing integration requirements and coverage floors.
   Inspect and resolve failures rather than silently skipping them.
2. Expand the [cross-stack matrix](../../operations/vi-cross-stack.md) for Python-issued
   autonomous payment, selective disclosure, authenticated merchant checkout and stateful
   executor agreement. Preserve stricter fail-closed SDK rules and explicitly reject
   unsupported combinations. Do not treat the peer implementation as a security oracle.
3. Use the documentation inventory to convert complete copyable snippets into compiled
   source contracts. Prioritize authentication, credential verification, persistence,
   deployment configuration and migration. Review fragments in their declared context.
4. Use fresh merged branch reports to prioritize untested decisions across supported
   runtime modules. Record requirement IDs and semantic assertions for each added test;
   structural coverage alone is insufficient.
5. Execute the required workflows on the release candidate and verify retained outputs.
   Qualification involving external accounts or on-call recipients needs an identified,
   authorized target; local fixtures do not establish that evidence.

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
