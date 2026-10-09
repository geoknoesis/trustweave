# Security Policy

## Supported Versions

TrustWeave provides security updates for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 0.7.x   | :white_check_mark: |
| 0.6.x   | :x:                |
| < 0.6   | :x:                |

Only the latest minor release line receives security fixes while TrustWeave is pre-1.0; upgrade
from 0.6.x to 0.7.x to receive them. We recommend using the latest stable release to ensure you
receive security updates and bug fixes.

## Reporting a Vulnerability

We take the security of TrustWeave seriously. If you believe you have found a security vulnerability, please report it to us as described below.

### Please Do NOT:

- ❌ Open a public GitHub issue for security vulnerabilities
- ❌ Share the vulnerability publicly until it has been resolved
- ❌ Use the vulnerability for malicious purposes

### Please DO:

- ✅ Report the vulnerability privately using one of the methods below
- ✅ Provide detailed information about the vulnerability
- ✅ Allow us reasonable time to address the vulnerability before disclosure

### How to Report

**Email:** security@geoknoesis.com

**Preferred Format:** Include the following information in your report:

1. **Type of vulnerability** (e.g., authentication bypass, injection, etc.)
2. **Affected component** (module, class, or feature)
3. **Description** of the vulnerability
4. **Steps to reproduce** (detailed steps to exploit the vulnerability)
5. **Potential impact** (what could an attacker do?)
6. **Suggested fix** (if you have ideas)
7. **Proof of concept** (code, screenshots, or links if applicable)

### What to Expect

After you submit a security report:

1. **Acknowledgment**: You will receive an acknowledgment within **48 hours**
2. **Initial Assessment**: We will perform an initial assessment within **7 days**
3. **Updates**: We will provide periodic updates on the status of the vulnerability
4. **Resolution**: We will work to resolve the vulnerability as quickly as possible
5. **Disclosure**: After a fix is available, we will coordinate disclosure with you

### Response Timeline

- **48 hours**: Initial acknowledgment
- **7 days**: Initial assessment and severity classification
- **30 days**: Status update (if not yet resolved)
- **90 days**: Target resolution for critical vulnerabilities
- **Disclosure**: Coordinated after fix is available (typically 7-30 days after release)

*Note: Complex vulnerabilities may take longer to resolve. We will keep you informed of progress.*

## Scope

### In Scope

We welcome reports about security vulnerabilities in:

- ✅ TrustWeave core libraries and modules
- ✅ Authentication and authorization mechanisms
- ✅ Cryptographic operations and key management
- ✅ DID creation, resolution, and verification
- ✅ Verifiable Credential issuance and verification
- ✅ Blockchain anchoring operations
- ✅ API endpoints and network communications
- ✅ Plugin system security
- ✅ Dependency vulnerabilities that affect TrustWeave

### Out of Scope

The following are generally considered out of scope:

- ❌ Denial of Service (DoS) attacks (rate limiting should be handled by your application)
- ❌ Social engineering attacks
- ❌ Physical security issues
- ❌ Issues in third-party dependencies that don't directly affect TrustWeave
- ❌ Issues requiring physical access to the device
- ❌ Issues in experimental or deprecated features
- ❌ Missing security headers (unless they lead to a direct vulnerability)
- ❌ Self-XSS or issues that require user interaction
- ❌ Issues in example code or documentation (unless exploitable in production)

*If you're unsure whether a vulnerability is in scope, please report it and we'll assess it.*

## Verifying a Release

Every tagged release is published by the `publish` job of `.github/workflows/release-evidence.yml`.
That job builds and signs the artifacts once into a local staging directory, checks that every staged jar is
byte-identical to the jar the evidence job tested, checksums and attests the directory, and only then, as its
last step, uploads that same directory to Maven Central. The attested files are therefore the uploaded
files, and nothing reaches Central if the checksums or attestations fail. The tagged commit must also be an
ancestor of `main`, the staged jar names must equal the evidence jar names (minus a documented allowlist) and the
evidence must come from the tagged commit, every non-sidecar file must carry an `.asc` signature (checked with
`gpg --verify` when the release public key is configured as a repository variable), the OSV baseline gate must
pass at the tag, and the attestation is re-verified immediately before the upload. The GitHub release is created
as a draft and published by a person after the Central deployment is released.
It produces:

- `SHA256SUMS`: SHA-256 of every published file (JARs, POMs, Gradle module metadata, per-module
  CycloneDX SBOMs, `.asc` signatures) and of the aggregate SBOM `trustweave-<version>-sbom.cdx.json`.
  Both are attached to the GitHub release for the tag.
- A SLSA build provenance attestation (`actions/attest-build-provenance`) for every file listed in
  `SHA256SUMS`, and one for `SHA256SUMS` itself, stored in GitHub's attestation store.

To verify an artifact you downloaded (`<version>` is the release without the `v`; requires the
[GitHub CLI](https://cli.github.com/)):

```bash
# 1. Fetch the checksum file from the release and check your download against it
gh release download "v<version>" --repo geoknoesis/trustweave --pattern SHA256SUMS
sha256sum --check --ignore-missing SHA256SUMS

# 2. Verify the provenance of the artifact itself (signer workflow, tag and commit)
gh attestation verify trustweave-core-<version>.jar --repo geoknoesis/trustweave \
  --signer-workflow geoknoesis/trustweave/.github/workflows/release-evidence.yml \
  --source-ref "refs/tags/v<version>"

# 3. Optionally verify the checksum file the same way (and the PGP .asc signature as usual)
gh attestation verify SHA256SUMS --repo geoknoesis/trustweave
```

Paths in `SHA256SUMS` follow the Maven repository layout (`org/trustweave/<artifact>/<version>/...`);
run the check from a directory that mirrors it, or verify single files with `gh attestation verify`.
A passing attestation proves the file was produced by that workflow at that tag; it does not replace
reviewing the SBOM for dependency risk.

## Dependency Scanning

Every pull request and every push to `main` runs [`.github/workflows/security.yml`](.github/workflows/security.yml):

- **Dependency review** fails a pull request that adds a dependency with a known *high* or
  *critical* advisory (Gradle dependency graph via GitHub dependency submission). **Maintainers must
  keep Settings -> Code security -> Dependency graph enabled** (it is) for this gate to work. The
  "Submit Gradle dependency graph" job is blocking: if it fails, review would have no snapshot to compare.
  Fork PRs and Dependabot PRs (read-only token) skip
  submission and review; review those bumps by hand (OSV and the build still run on them).
- **OSV-Scanner** scans the aggregate CycloneDX SBOM (`./gradlew cyclonedxBom`) and the contents of
  every resolved JAR, so libraries shaded inside a fat JAR are found too. Results are published to
  code scanning and to the workflow summary. The job **fails for any advisory that is not listed in
  [`config/osv/baseline.json`](config/osv/baseline.json)** (checked by `scripts/check-osv-baseline.py`).
  A baseline entry covers an advisory id (or alias) *in the listed packages at the listed versions only*: a
  baselined GHSA that shows up in a new package, or in a new version of a listed package that the scanner
  still reports as affected, fails again so it gets a fresh look (a bump to a fixed version just drops out
  of the report). The gate also fails (exit 2) when the report is missing,
  unparseable or empty while the baseline is not, and when the SBOM has fewer components than the floor
  (`--min-packages`, default 200, or half the baseline's recorded `package_count`), so a broken scan cannot
  pass silently. Baseline entries that no longer match anything fail the job (`--strict-stale`): remove them
  when a dependency is upgraded. The baseline is the existing backlog (90 distinct advisories on 2026-10-07, including
  the three didcomm entries below), so only new advisories gate a change. Remove entries as dependencies
  are upgraded; add one only after triage, with a reason. To regenerate it, run `./gradlew cyclonedxBom`,
  `python scripts/collect-sbom-jars.py build/reports/cyclonedx/bom.json --output build/reports/osv/jars`,
  `osv-scanner scan source --no-ignore --experimental-plugins=java/archive --format=json
  --output-file=build/reports/osv/osv.json -L=build/reports/cyclonedx/bom.json -r build/reports/osv/jars`
  and `python scripts/check-osv-baseline.py --report build/reports/osv/osv.json
  --sbom build/reports/cyclonedx/bom.json --update-baseline`. The update rewrites every entry with its
  packages and ecosystems (migrating legacy id-only entries, which match any package until then), records
  `package_count` from the SBOM and keeps an entry's triage only while the report adds no package version
  it did not record; new advisories and new versions come back as `needs-review` with a `TODO` reason, which
  the check rejects until a person triages them.

  **Triage.** Every baseline entry records `status`, `reason`, `reviewed` (a date) and, where required,
  `expires`. `status` is one of `affected` (a vulnerable version ships; waiting for a fix),
  `not-reachable` (present, but not on a production path, for example test-only), `false-positive`,
  `accepted-risk` (a person accepted it, with mitigations) or `needs-review` (honestly not yet analysed).
  `affected`, `accepted-risk` and `needs-review` must carry an `expires` date; every `expires` is at most
  366 days after `reviewed`. **An entry whose `expires` date has passed fails the job** (exit 1) until it
  is re-triaged with a new `reviewed`/`expires`, or the dependency is fixed and the entry deleted. An
  unknown status, a missing reason or date, or a `TODO` reason is a hard error (exit 2). The initial
  triage (2026-10-08) was derived from Gradle's resolved runtime and test classpaths, the OSV records and
  a source search; where the source of a version could not be found the entry says `needs-review`.
  Expiry dates are deliberately staggered (`needs-review` in November, `affected` across December) so one
  date cannot fail every entry at once; keep re-triaged dates spread out the same way. Packages are matched on
  `group:name` (a label with no group, as the JAR scan can report, matches on the artifact name alone).
  The scheduled run also opens or updates one GitHub issue ("OSV baseline: triage entries expired or expiring
  within 30 days", `scripts/osv-expiry-report.py`) while any entry expires within 30 days; that job alone holds
  `issues: write`. The baseline does not yet record `package_count` or `ecosystems`; the next
  `--update-baseline` from a real scan writes both.

Dependabot (`.github/dependabot.yml`) proposes version updates weekly from `gradle/libs.versions.toml`.

## Known Dependency Risks

### `org.didcommx:didcomm` 0.3.2 embeds outdated Nimbus JOSE+JWT and json-smart

**Affected module:** `credentials:plugins:didcomm` (optional plugin; nothing else depends on it).

`org.didcommx:didcomm` 0.3.2 (the latest release, August 2022) is a fat JAR that contains its own
copies of `com.nimbusds:nimbus-jose-jwt` **9.16-preview.1** and `net.minidev:json-smart`
**2.4.7**. They are not separate dependencies, so they do not appear in the Gradle dependency graph
or the SBOM, Dependabot cannot update them, and the catalog's Nimbus version (9.48) does not apply.
The module's build file excludes a standalone `nimbus-jose-jwt` from its classpath on purpose,
because two copies of the `com.nimbusds` packages cause split-package and `NoSuchMethodError` failures
at runtime.

Advisories in the embedded copies (as reported by OSV-Scanner):

| Embedded library | Advisory | Impact | Fixed in |
| ---------------- | -------- | ------ | -------- |
| nimbus-jose-jwt 9.16-preview.1 | [GHSA-gvpg-vgmx-xg6w](https://osv.dev/GHSA-gvpg-vgmx-xg6w) (CVE-2023-52428) | Denial of service through a large PBES2 iteration count (`p2c`) when decrypting a password-based JWE | 9.37.2 |
| nimbus-jose-jwt 9.16-preview.1 | [GHSA-xwmg-2g98-w7v9](https://osv.dev/GHSA-xwmg-2g98-w7v9) (CVE-2025-53864) | Denial of service (stack overflow) from deeply nested JSON in a parsed JOSE object | 9.37.4 |
| json-smart 2.4.7 | [GHSA-493p-pfq6-5258](https://osv.dev/GHSA-493p-pfq6-5258) (CVE-2023-1370) | Denial of service (stack exhaustion) from deeply nested JSON arrays/objects | 2.4.9 |

All three are denial-of-service issues triggered by untrusted input. DIDComm messages are untrusted
input by definition, so treat them as reachable when the plugin unpacks messages from external parties.

**If you use the DIDComm plugin:**

- Bound the size of an inbound DIDComm message before it reaches the plugin (for example at your
  HTTP endpoint), and reject messages that nest JSON more deeply than your protocol needs.
- Run message unpacking where a thread or request failing with a stack overflow is contained and
  retried, not where it takes down the host.
- Do not use password-based (PBES2) JWE with the plugin.
- Prefer leaving `credentials:plugins:didcomm` off the classpath when you do not need DIDComm.

**Status:** accepted risk with mitigation, tracked in
[docs/contributing/dependency-upgrade-plan.md](docs/contributing/dependency-upgrade-plan.md). The
resolution is to move to a maintained DIDComm implementation (or a didcommx release that depends on,
rather than embeds, a current Nimbus; none exists as of October 2026, 0.3.2 is still the newest on
Maven Central) — not to patch the shaded classes. The three advisories above are the only didcomm
entries in the OSV baseline, so any further advisory against the embedded copies fails the job. The OSV-Scanner job scans the
didcomm JAR's contents, so this entry is re-confirmed on every run and any new advisory against the
embedded copies shows up there.

## Security Best Practices

### For Users

- **Keep TrustWeave Updated**: Always use the latest stable version
- **Secure Key Management**: Use production-grade KMS (AWS KMS, Azure Key Vault, Google Cloud KMS, HashiCorp Vault)
- **Encrypt Credential Storage**: Never store credentials in plain text
- **Validate Credentials**: Always verify credentials before trusting them
- **Use TLS/HTTPS**: Encrypt all network communications
- **Implement Rate Limiting**: Protect against abuse in your applications
- **Follow Principle of Least Privilege**: Grant minimum required permissions
- **Regular Security Audits**: Review your implementation regularly
- **Secure Configuration**: Use secure defaults and review configuration options

### For Contributors

- **Review Security Implications**: Consider security when designing features
- **Secure Coding Practices**: Follow secure coding guidelines
- **Dependency Updates**: Keep dependencies up to date
- **Security Testing**: Include security considerations in tests
- **Documentation**: Document security-relevant features and limitations

For detailed security guidance, see [Security Documentation](docs/getting-started/production-integration-checklist.md).

## Security Updates

Security updates are released through:

1. **GitHub Releases**: Tagged releases with security fixes
2. **Maven Central**: Updated artifacts in Maven Central
3. **Security Advisories**: GitHub Security Advisories for tracked vulnerabilities
4. **Release Notes**: Detailed information in [CHANGELOG.md](CHANGELOG.md)

## Vulnerability Disclosure

After a vulnerability is fixed:

1. **Fix Release**: A new version is released with the security fix
2. **Security Advisory**: A GitHub Security Advisory is published (if applicable)
3. **Release Notes**: The vulnerability and fix are documented in release notes
4. **CVE Assignment**: CVEs are assigned for significant vulnerabilities
5. **Coordinated Disclosure**: We coordinate with reporters before public disclosure

## Hall of Fame

We recognize security researchers who responsibly disclose vulnerabilities. Contributors who report valid security issues will be:

- Listed in our security acknowledgments (if desired)
- Credited in security advisories
- Thanked for helping keep TrustWeave secure

## Responsible Disclosure

We ask that security researchers:

- Act in good faith and avoid accessing or modifying data that does not belong to you
- Respect user privacy and data protection
- Not disrupt production systems
- Not violate any laws or breach any agreements
- Allow reasonable time for fixes before disclosure

## Additional Resources

- [Security Documentation](docs/getting-started/production-integration-checklist.md) - Detailed security guidance
- [Contributing Guide](CONTRIBUTING.md) - General contribution guidelines
- [Code of Conduct](CODE_OF_CONDUCT.md) - Community standards
- [W3C Security Considerations](https://www.w3.org/TR/vc-data-model/#security-considerations) - W3C VC security guidance

## Contact

**Security Issues:** security@geoknoesis.com

**General Support:** https://www.geoknoesis.com

**GitHub Issues:** For non-security issues, please use [GitHub Issues](https://github.com/geoknoesis/trustweave/issues)

---

**Thank you for helping keep TrustWeave and its users safe!**




