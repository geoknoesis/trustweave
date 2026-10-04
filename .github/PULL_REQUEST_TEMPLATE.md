## Summary

<!-- What does this change and why? Link issues (Fixes #123). Use a Conventional Commit title. -->

## Testing

<!-- Commands run and their result, e.g. ./gradlew :did:did-core:test. Note what is not covered. -->

## Security impact

<!-- Does this touch key handling, signature/credential verification, DID resolution, untrusted input,
     dependencies, workflows or permissions? Write "None" if not, otherwise describe the risk and mitigation.
     Report vulnerabilities privately, not here: see SECURITY.md. -->

## Checklist

- [ ] `./gradlew ktlintFormat ktlintCheck` is clean (no new baseline entries)
- [ ] Public API changed: ABI dump updated (`./gradlew updateKotlinAbi`, then `checkKotlinAbi`)
- [ ] `CHANGELOG.md` updated for user-visible changes
- [ ] Docs updated (`python scripts/check-documentation.py` passes)
- [ ] Tests added or updated; no new skipped tests
- [ ] Workflow/dependency changes: actions SHA-pinned, `permissions` and `timeout-minutes` set
