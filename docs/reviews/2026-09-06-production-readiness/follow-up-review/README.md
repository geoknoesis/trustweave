# Follow-up review - 2026-09-06

Reviewed commit: e0a4464fc6cafa339b94121fd56f33812c6601e4. See index.html, scores.json and findings.json.

## Reproduce

In a disposable checkout, copy ReviewReadinessProbeTest.kt into credentials/plugins/status-list/database/src/test/kotlin/org/trustweave/revocation/database/ and run from the root:

```powershell
.\gradlew.bat :credentials:plugins:status-list:database:test --tests '*ReviewReadinessProbeTest' --max-workers=2 '-Ptrustweave.windowsInRepoBuild=true' --project-cache-dir .gradle/readiness-validation
```

Both tests assert correct behavior and fail on the reviewed commit. The JDBC wrapper synchronizes two SELECT results before either write, without changing data. Its barrier times out after ten seconds. Results are preserved in probe-results.xml: expansion expected 24, actual 72; concurrent revocations observed a=true, b=false.

These are archived review reproducers, not production changes. Integrate permanent regression tests with fixes. The temporary source was removed from the normal test tree after recording the evidence. The full-suite baseline in ../follow-up-validation.json was not regenerated in this review.
