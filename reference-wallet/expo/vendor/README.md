# Temporary patched build dependencies

The Expo 57 toolchain requires braces 3.0.3 and node-forge 1.4.0. Those releases
have high-severity advisories with no fixed upstream release at the time of this
change. These private local packages contain actual source fixes; they are not
security exceptions or upstream version overrides claiming an unreleased fix.
The application's npm overrides route every installed consumer to these copies.

* **braces**: bounds parser nesting to 100 containers, including parentheses, and
  checks caller-supplied AST depth iteratively before compile, expand or stringify
  recurses. Inputs exceeding this limit throw a controlled `SyntaxError`, including
  unclosed patterns and cyclic ASTs. This intentionally limits extreme nesting;
  ordinary patterns retain upstream behavior. See
  [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) and
  [upstream issue 70](https://github.com/micromatch/braces/issues/70).
* **node-forge**: validates the nested DigestAlgorithm sequence length as well as
  the outer DigestInfo structure, rejecting unconsumed children during RSA
  signature verification. The fix follows
  [upstream PR 1152](https://github.com/digitalbazaar/forge/pull/1152), commit
  `ceba34402e329f0365134f23fe19898756527d65`, for
  [GHSA-86w9-cpqp-85rv](https://github.com/advisories/GHSA-86w9-cpqp-85rv).

Each directory retains the upstream license and README (as `README.upstream.txt`).
`upstream.json` records the official npm tarball URL, SHA-512 integrity, original
file SHA-256 hashes, test-source commit URL, original test hashes and reviewed
patched file hashes. The tarball integrity was verified before extraction.
`security.patch` shows the source and private package metadata changes, ignoring
line-ending differences. No prebuilt upstream distribution bundles are included.
`test-harness.patch` records the only test change: removal of an upstream
`describe.only` in Forge's jsbn tests. CI uses `--forbid-only` to prevent focused
tests from silently hiding failures.

Run `npm run test:security` for offline integrity checks, depth/AST attack cases,
installed transitive resolution checks and an Expo certificate/signing smoke test.
Run `npm run test:vendor` for the retained upstream suites, including Forge's
nested DigestAlgorithm and low-exponent signature-forgery regressions. Braces
tests come from commit `483836c3484f6bd0ff6db6e00d1dc35505efda15`.
CI also runs `npm audit --audit-level=high`; remaining moderate advisories are
still reported and no advisory is allowlisted.

When updating either copy, verify the new official tarball integrity, review the
source diff, refresh provenance hashes and rerun both suites plus Expo validation.
Remove each local package, direct file dependency and override once a released
upstream fix passes these regression tests through the normal dependency tree.
