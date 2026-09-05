import { beforeEach, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import 'fake-indexeddb/auto'
import { bootstrap, store, createPresentation } from '../lib/wallet'
import { clearHolderKeys } from '../lib/key-store'
import { generateEd25519KeyPair, publicKeyToDidKey, signJws, b64uDecodeString } from '../lib/crypto'
import { issueSdJwtVc, createObjectDisclosure } from '../lib/sdjwt'
import { buildPlaintextDisclosure } from '../lib/claim-jwe'
import { sha256 } from '@noble/hashes/sha256'
import { b64uEncode } from '../lib/crypto'

beforeEach(async () => {
 const values = new Map<string,string>()
 vi.stubGlobal('crypto', webcrypto)
 vi.stubGlobal('navigator', {})
 vi.stubGlobal('window', {localStorage:{getItem:(k:string)=>values.get(k)??null,setItem:(k:string,v:string)=>values.set(k,v),removeItem:(k:string)=>values.delete(k)}})
 await clearHolderKeys()
})
it('PROBE: accepts an issuer credential with a corrupted signature', async () => {
 const h=(await bootstrap()).holder; const issuer=generateEd25519KeyPair();const did=publicKeyToDidKey(issuer.publicKey)
 const jwt=signJws({iss:did,sub:h.did,vc:{type:['VerifiableCredential','Employee'],credentialSubject:{id:h.did,name:'Forged'}}},issuer.privateKey,did)
 const parts=jwt.split('.');parts[2]='AAAA'
 expect(store(parts.join('.'),'vc+jwt').credential.preview.subtitle).toBe('Forged')
})
it('PROBE: multiple SD-JWTs bypass the empty disclosure selection',async()=>{
 const h=(await bootstrap()).holder;const issuer=generateEd25519KeyPair();const did=publicKeyToDidKey(issuer.publicKey)
 const make=(vct:string)=>issueSdJwtVc({issuerDid:did,issuerPrivateKey:issuer.privateKey,issuerKid:did,holderDid:h.did,alwaysVisible:{},selectivelyDisclosable:[{name:'secret',value:'private-'+vct}],vct,now:Math.floor(Date.now()/1000)})
 const a=make('TypeA'),b=make('TypeB'); const aa=store(a,'vc+sd-jwt'),bb=store(b,'vc+sd-jwt')
 const vp=await createPresentation([aa.credential.id,bb.credential.id],'verifier','nonce',[])
 const payload=JSON.parse(b64uDecodeString(vp.split('.')[1]));expect(payload.vp.verifiableCredential).toEqual([a,b])
})
it('PROBE: decrypted replacement disclosure no longer matches issuer digest',()=>{
 const original=createObjectDisclosure('photo',{alg:'ECDH-ES+A256GCM',ciphertext:'issuer-signed-ciphertext'})
 const replacement=buildPlaintextDisclosure('photo','plaintext-photo')
 expect(b64uEncode(sha256(new TextEncoder().encode(replacement)))).not.toBe(original.hash)
})
