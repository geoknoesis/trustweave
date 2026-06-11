import * as vc from '@digitalbazaar/vc';
import {Ed25519Signature2020} from '@digitalbazaar/ed25519-signature-2020';
import {Ed25519VerificationKey2020} from '@digitalbazaar/ed25519-verification-key-2020';
import {decode} from 'base58-universal';
import fs from 'fs';

// Key pair published in W3C vc-di-eddsa 1.0 test vectors (sec 3.1/3.4)
const publicKeyMultibase = 'z6MkrJVnaZkeFzdQyMZu1cgjg7k1pZZ6pvBQ7XJPt4swbTQ2';
const secretKeyMultibase = 'z3u2en7t5LR2WtQH5PfFqMqwVHBeXouLzo6haApm8XHqvjxq';
const secretMc = decode(secretKeyMultibase.slice(1));
console.log('secret multicodec prefix:', secretMc[0].toString(16), secretMc[1].toString(16), 'len', secretMc.length);
const seed = secretMc.slice(2);

const controller = `did:key:${publicKeyMultibase}`;
const keyPair = await Ed25519VerificationKey2020.generate({
  seed, controller, id: `${controller}#${publicKeyMultibase}`
});
console.log('derived publicKeyMultibase:', keyPair.publicKeyMultibase);
if (keyPair.publicKeyMultibase !== publicKeyMultibase) throw new Error('public key mismatch');

const contexts = {
  'https://www.w3.org/2018/credentials/v1': JSON.parse(fs.readFileSync('credentials-v1.jsonld', 'utf8')),
  'https://w3id.org/security/suites/ed25519-2020/v1': JSON.parse(fs.readFileSync('ed25519-2020-v1.jsonld', 'utf8')),
  'https://example.org/contexts/alumni/v1': JSON.parse(fs.readFileSync('alumni-context.json', 'utf8')),
};

const didDoc = {
  '@context': ['https://www.w3.org/ns/did/v1', 'https://w3id.org/security/suites/ed25519-2020/v1'],
  id: controller,
  verificationMethod: [{
    id: `${controller}#${publicKeyMultibase}`,
    type: 'Ed25519VerificationKey2020',
    controller,
    publicKeyMultibase
  }],
  assertionMethod: [`${controller}#${publicKeyMultibase}`],
  authentication: [`${controller}#${publicKeyMultibase}`]
};

const documentLoader = async url => {
  if (contexts[url]) return {contextUrl: null, document: contexts[url], documentUrl: url};
  if (url === controller) return {contextUrl: null, document: didDoc, documentUrl: url};
  if (url === `${controller}#${publicKeyMultibase}`) {
    return {contextUrl: null, document: didDoc.verificationMethod[0], documentUrl: url};
  }
  throw new Error('refused to load: ' + url);
};

const credential = {
  '@context': [
    'https://www.w3.org/2018/credentials/v1',
    'https://example.org/contexts/alumni/v1',
    'https://w3id.org/security/suites/ed25519-2020/v1'
  ],
  id: 'urn:uuid:b4a072a2-aff1-4c2a-a1e7-bd5b54b3062b',
  type: ['VerifiableCredential', 'AlumniCredential'],
  issuer: controller,
  issuanceDate: '2023-02-24T23:36:38Z',
  credentialSubject: {
    id: 'did:example:abcdefgh',
    alumniOf: 'The School of Examples'
  }
};

const suite = new Ed25519Signature2020({key: keyPair, date: '2023-02-24T23:36:38Z'});
const signed = await vc.issue({credential: structuredClone(credential), suite, documentLoader});
console.log(JSON.stringify(signed, null, 2));
fs.writeFileSync('signed-credential.json', JSON.stringify(signed, null, 2) + '\n');

// independent verification with the same JS stack
const result = await vc.verifyCredential({
  credential: signed,
  suite: new Ed25519Signature2020(),
  documentLoader
});
console.log('JS-stack verification:', result.verified, result.error ? result.error : '');

// record versions
const lock = JSON.parse(fs.readFileSync('package-lock.json', 'utf8'));
for (const [k, v] of Object.entries(lock.packages)) {
  if (k.includes('@digitalbazaar') || k.includes('jsonld') || k.includes('rdf-canonize')) console.log(k, v.version);
}
