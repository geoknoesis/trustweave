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
   - Optionally (recommended) set the repository **variables** `TRUSTWEAVE_SIGNING_PUBLIC_KEY` (the
     ASCII-armoured PUBLIC key) and `TRUSTWEAVE_SIGNING_FINGERPRINT` (its full fingerprint). When the public
     key is set, the publish job imports it and runs `gpg --verify` on every staged `.asc` before attesting;
     the fingerprint, when set, must also match the signing key. Without them the job still requires an
     `.asc` next to every non-sidecar file but only warns that the signatures were not cryptographically
     checked.
3. **Record the portal credentials** as `TRUSTWEAVE_PUBLISH_USERNAME` and
   `TRUSTWEAVE_PUBLISH_PASSWORD`. These are the portal's generated token pair, not an account
   password.
4. **Create a `maven-central` GitHub environment** with at least one required reviewer. The publish
   job targets it, so a tag alone never publishes — a person approves each release.
5. `TRUSTWEAVE_PUBLISH_URL` no longer affects tag releases: the workflow uploads to the Central Portal
   with `scripts/upload-to-central.py`. It still redirects Gradle's `central` repository for manual
   rehearsals.

## Release

1. Update `version` in `build.gradle.kts`. The publish job refuses a tag whose name does not match
   the declared version, so these cannot drift apart.
2. Land the release commit on `main` and confirm CI is green.
3. Tag it: `git tag v0.7.1 && git push origin v0.7.1`.
4. `release-evidence.yml` runs the evidence job: tests, lint, ABI, coverage policy, documentation
   execution, the VI cross-stack interoperability check against the pinned Python reference,
   reliability evidence, alert-rule validation, and provenance attestation.
   Before anything is built it also requires the tagged commit to be an ancestor of `origin/main`,
   and it records the SHA-256 of every jar in `validation-manifest.json`.
5. The `publish` job waits for a reviewer on the `maven-central` environment. It then re-verifies
   the tag against the project version, regenerates and validates every POM, builds the signed
   artifacts once into `build/release-staging`, fails unless every staged jar is byte-identical to
   the jar the evidence job validated (the staged jar names must equal the evidence jar names minus
   `config/release-unpublished-jars.json`, the manifest must be from the tagged commit, and the minimum jar
   count derives from the manifest), requires an `.asc` for every non-sidecar file, writes `SHA256SUMS`, attests
   it, re-verifies that attestation with `gh attestation verify` just before the upload, and only as its last
   step uploads that same directory to the Central Portal (`scripts/upload-to-central.py`, deployment
   type `USER_MANAGED`). A failure at any earlier step means nothing reached Central. The publish job also
   waits for the `osv-gate` job, which runs `scripts/check-osv-baseline.py` against a fresh scan at the tag.
6. Release the deployment in the Sonatype Central Portal.
7. Publish the **draft** GitHub release the `release-assets` job created for the tag, once the Portal shows
   the deployment as published. The job cannot check that itself (it would need a Central API call with the
   publish credentials, which only the upload step may see), so the release stays a draft until a person
   confirms it.

If `verify-staged-jars.py` reports a jar "validated by the evidence build but not staged", that jar is built by
`build` but never published: add it to `config/release-unpublished-jars.json` with a reason. The list starts
empty; the first tag run shows whether any entry is needed.

What only a real tag run proves: the Portal accepts the bundle layout (including Gradle's checksum
sidecars), the token-based `Authorization: Bearer` upload and that the answer is a deployment id, that the
rebuilt jars really are byte-identical in the CI environment, that the unpublished-jar allowlist is complete,
that Gradle signs every non-sidecar file (including the per-module CycloneDX SBOMs), that `gh attestation verify`
accepts the flags used, that the OSV scan job reproduces `osv.json` at the tag, and that the draft release flow
works with the installed `gh`. The script's
verification, bundling and request construction are unit-tested; the HTTP exchange is not.
`python scripts/upload-to-central.py --staging build/release-staging --name x --dry-run` rehearses
everything except the network call.

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
