'use strict';

// Offline integrity check: changes to vendored code or tests require an explicit
// provenance manifest update alongside a review of the recorded upstream diff.
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

for (const name of ['braces', 'node-forge']) {
  const root = path.join(__dirname, name);
  const provenance = JSON.parse(fs.readFileSync(path.join(root, 'upstream.json'), 'utf8'));
  const expected = provenance.patchedSha256;
  const actual = fs.readdirSync(root, { recursive: true, withFileTypes: true })
    .filter(entry => entry.isFile())
    .map(entry => path.relative(root, path.join(entry.parentPath, entry.name)).split(path.sep).join('/'))
    .filter(file => file !== 'upstream.json').sort();
  assert.deepEqual(actual, Object.keys(expected).sort(), `${name}: unexpected or missing vendor file`);
  for (const file of actual) {
    const hash = createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
    assert.equal(hash, expected[file], `${name}/${file}: differs from reviewed patch`);
  }
  const manifest = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
  assert.equal(manifest.name, `@trustweave/${name}-patched`);
  assert.equal(manifest.private, true);
  assert.equal(manifest.trustweaveUpstream.integrity, provenance.integrity);
  assert.equal(manifest.trustweaveUpstream.version, provenance.version);
  console.log(`${name}: ${actual.length} vendored files match the reviewed provenance manifest`);
}
