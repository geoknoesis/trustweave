'use strict';

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { createRequire } = require('node:module');
const braces = require('braces');

const nested = (open, close, depth) => open.repeat(depth) + 'x' + close.repeat(depth);
const depthError = { name: 'SyntaxError', message: /nesting exceeds supported depth/ };

test('all public brace APIs reject adversarial nesting before recursive traversal', () => {
  for (const pattern of [nested('{', '}', 4000), nested('(', ')', 4000),
    '{('.repeat(2000) + 'x' + ')}'.repeat(2000), '{'.repeat(4000)]) {
    for (const api of [braces, braces.parse, braces.compile, braces.expand, braces.stringify]) {
      assert.throws(() => api(pattern, { maxDepth: Infinity }), depthError);
    }
  }
});

test('the fixed depth limit preserves supported patterns and literal braces', () => {
  assert.doesNotThrow(() => braces.parse(nested('{', '}', 100)));
  assert.doesNotThrow(() => braces.parse(nested('(', ')', 100)));
  assert.throws(() => braces.parse(nested('{', '}', 101)), depthError);
  assert.throws(() => braces.parse(nested('(', ')', 101)), depthError);
  assert.deepEqual(braces.expand('a{b,c}{1..3}'), ['ab1', 'ab2', 'ab3', 'ac1', 'ac2', 'ac3']);
  for (const pattern of ['"' + '{'.repeat(4000) + '"', '[' + '{'.repeat(4000) + ']',
    '\\{'.repeat(4000)]) {
    assert.doesNotThrow(() => braces.parse(pattern));
  }
});

test('direct recursive walkers reject deep or cyclic caller-supplied ASTs', () => {
  let ast = { type: 'text', value: 'x' };
  for (let i = 0; i < 4000; i++) ast = { type: 'root', nodes: [ast] };
  const cyclic = { type: 'root', nodes: [] };
  cyclic.nodes.push(cyclic);
  for (const name of ['compile', 'expand', 'stringify']) {
    const walk = require(`./braces/lib/${name}`);
    assert.throws(() => walk(ast), depthError);
    assert.throws(() => walk(cyclic), depthError);
  }
});

test('installed consumers resolve to the patched packages', () => {
  for (const [consumer, dependency, identity] of [
    ['micromatch', 'braces', '@trustweave/braces-patched'],
    ['@expo/code-signing-certificates', 'node-forge', '@trustweave/node-forge-patched'],
  ]) {
    const consumerRequire = createRequire(require.resolve(consumer));
    assert.equal(consumerRequire(`${dependency}/package.json`).name, identity);
    assert.equal(consumerRequire.resolve(dependency), require.resolve(dependency));
  }
  const lock = require('../package-lock.json');
  for (const [path, entry] of Object.entries(lock.packages)) {
    if (/(^|\/)node_modules\/(braces|node-forge)$/.test(path)) {
      assert.equal(entry.link, true, `unpatched registry copy at ${path}`);
    }
  }
});

test('Expo code signing can generate, validate and sign with the patched Forge', () => {
  const signing = require('@expo/code-signing-certificates');
  const keyPair = signing.generateKeyPair();
  const now = Date.now();
  const certificate = signing.generateSelfSignedCodeSigningCertificate({
    keyPair, commonName: 'TrustWeave patch regression',
    validityNotBefore: new Date(now - 60000), validityNotAfter: new Date(now + 3600000),
  });
  signing.validateSelfSignedCertificate(certificate, keyPair);
  const message = Buffer.from('TrustWeave Expo signing regression');
  const signature = signing.signBufferRSASHA256AndVerify(keyPair.privateKey, certificate, message);
  const forge = require('node-forge');
  const digest = forge.md.sha256.create().update(message.toString()).digest().getBytes();
  assert.equal(certificate.publicKey.verify(digest, Buffer.from(signature, 'base64').toString('binary')), true);
});
