# Review records

This directory keeps the **human-readable records** of dated reviews: Markdown and HTML reports, the
small score and validation JSON files they cite, and the helper scripts that rendered them
(`build_review.py`, `record_validation.py`, `render_report.py`).

## Raw evidence is a CI artifact, not committed

Raw generated output does not belong in git. That covers JUnit `TEST-*.xml` reports, JaCoCo
coverage (`jacoco*.xml`), Vitest `test-results*.json` and `coverage-final.json`, SBOMs, vulnerability
scans, zipped report bundles, OTLP captures, large screenshots and full validation manifests.
The review documents cite them by name; the files themselves were removed from the tree when
`docs/reviews` had grown to about 40 MB across 851 files.

To regenerate evidence for a new review, run the relevant job and download its artifact:

- `.github/workflows/release-evidence.yml` and `ci.yml` upload `validation-manifest.json`,
  `documentation.json`, test reports and SBOMs as workflow artifacts.
- `python3 scripts/record-validation-manifest.py --output build/reports/validation-manifest.json`
  builds a manifest locally.

Attach large artifacts to the GitHub release or the workflow run, and commit only a short summary
and the artifact's SHA-256 in the review text. `.gitignore` blocks the common raw-output patterns
under `docs/reviews/`.

## History

The removed files are still present in git history (the blobs were not rewritten). Use
`git log --diff-filter=D --name-only -- docs/reviews` to find them and
`git show <commit>^:<path>` to recover one. Shrinking the clone itself would need a history
rewrite (for example `git filter-repo`), which is a separate, deliberate maintainer decision.
