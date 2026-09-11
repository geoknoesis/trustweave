# Publishing a release

Until 11 September 2026 this repository could build a release but not publish one. No build file
declared a publishing repository, so Gradle never created a `PublishToMavenRepository` task and
`publishToMavenLocal` was the only thing that worked; the generated POM was also missing the `scm`
block that Maven Central validation requires. This runbook describes the path that now exists.

## What is wired up

| Piece | Where |
|---|---|
| Publishing repository (`central`) | `build.gradle.kts`, and `distribution/bom/build.gradle.kts` for the BOM |
| POM completeness (`scm`, `issueManagement`, licences, developers) | same two files |
| Mandatory signing on remote publication | `build.gradle.kts`, `PublishToMavenRepository.doFirst` |
| CycloneDX SBOM attached as a `cyclonedx` classifier | `build.gradle.kts` |
| Evidence gates, then publish | `.github/workflows/release-evidence.yml` |
| Static check that all of the above still exists | `scripts/check-publication.py`, run in CI |

`scripts/check-publication.py` also validates every generated POM under the build root and fails on
any missing required element, so a POM regression is caught before a tag is cut rather than by
Sonatype during staging.

## One-time setup

These are the parts no script can do for you.

1. **Verify the `org.trustweave` namespace** with Sonatype Central. This is a manual review with a
   DNS TXT record or a repository check, and it takes as long as it takes.
2. **Create a signing key** and record it as repository secrets:
   - `TRUSTWEAVE_SIGNING_KEY` — the ASCII-armoured private key
   - `TRUSTWEAVE_SIGNING_PASSWORD` — its passphrase
3. **Record the portal credentials** as `TRUSTWEAVE_PUBLISH_USERNAME` and
   `TRUSTWEAVE_PUBLISH_PASSWORD`. These are the portal's generated token pair, not an account
   password.
4. **Create a `maven-central` GitHub environment** with at least one required reviewer. The publish
   job targets it, so a tag alone never publishes — a person approves each release.
5. Optionally set the `TRUSTWEAVE_PUBLISH_URL` repository *variable* to a staging repository for
   rehearsals. Unset, it defaults to Sonatype Central.

## Release

1. Update `version` in `build.gradle.kts`. The publish job refuses a tag whose name does not match
   the declared version, so these cannot drift apart.
2. Land the release commit on `main` and confirm CI is green.
3. Tag it: `git tag v0.7.1 && git push origin v0.7.1`.
4. `release-evidence.yml` runs the evidence job: tests, lint, ABI, coverage policy, documentation
   execution, the VI cross-stack interoperability check against the pinned Python reference,
   reliability evidence, alert-rule validation, and provenance attestation.
5. The `publish` job waits for a reviewer on the `maven-central` environment. It then re-verifies
   the tag against the project version, regenerates and validates every POM, and publishes signed
   artifacts with their SBOMs.
6. Release the staged repository in the Sonatype portal.

## Rehearsing without publishing

```bash
# Everything except the upload, against a throwaway local repository.
./gradlew publishToMavenLocal
./gradlew generatePomFileForMavenPublication
python scripts/check-publication.py
```

`publishToMavenLocal` does not require signing, so it stays usable for day-to-day development. Only
`PublishToMavenRepository` enforces `TRUSTWEAVE_SIGNING_KEY` and the publish credentials, and it
fails with a message naming the missing variable rather than uploading unsigned artifacts.

## What is still not automated

- Namespace verification and the final "release" of the staged repository are manual portal steps.
- There is no automated rollback. A published version is immutable; a bad release is superseded by
  the next one.
