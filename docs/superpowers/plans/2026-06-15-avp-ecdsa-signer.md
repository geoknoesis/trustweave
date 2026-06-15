# AVP-Micro `ecdsa-jcs-2022` Signer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic `EcdsaJcs2022.sign` to the `avp-micro` plugin (byte-exact with the Python harness) plus a `seedKey` test helper, then use them to close the deferred negative-test gaps in the verifier and quote binding by re-signing modified genuine vectors.

**Architecture:** `sign` is the inverse of the existing `verify`: build the proof config, compute the same `hashData` (extracted into a shared helper), sign with deterministic ECDSA (Bouncy Castle RFC 6979 + canonical low-s, raw `r‖s`), and attach a `z`-base58 multibase `proofValue`. A test-only `TestSigning` helper reproduces the harness's `seed_key` derivation so tests can mint signed objects with the exact harness keys.

**Tech Stack:** Kotlin 2.3.21 (JVM), Bouncy Castle (`org.bouncycastle.crypto.signers.ECDSASigner` + `HMacDSAKCalculator`), JCA (`KeyFactory`/`AlgorithmParameters` EC), kotlinx-serialization-json, the existing `Jcs`/`EcdsaJcs2022`/`Vectors`, `org.trustweave.core.util.encodeBase58`, `DidMethodUtils`.

---

## Reference (from the Python harness)

- `avp_crypto.py::seed_key(label)` = `derive_private_key( (int.from_bytes(sha256("avp-micro-test:"+label),"big") % (n-1)) + 1 , SECP256R1)`.
- `sign_ecdsa_jcs_2022(doc, priv, created)`: proof config = `{type, cryptosuite, created, verificationMethod, proofPurpose}` + `@context` from the doc; sign `hashData = sha256(JCS(proofConfig)) ‖ sha256(JCS(doc_without_proof))` with deterministic ECDSA (RFC 6979) over SHA-256, canonical low-s, raw `r‖s`; `proofValue = "z" + base58btc(sig)`. The attached `proof` object carries the same fields **without** `@context`, plus `proofValue`.
- Harness seed labels (`generate.py`): issuer `issuer-acme-corp`, agent/payer `agent-buyer-01`, payee `service-tool-api`. The quote vector `01-payment-quote.json` is signed by `service-tool-api` with `created = "2026-03-25T21:30:00Z"`.

**KAT oracles:**
- `TestSigning.didKey("agent-buyer-01") == "did:key:zDnaew8NDU8VgvxWpWWxBeLWaVbGNEuXYyRFk2uLMjCdhxkSU"` (the payer).
- `TestSigning.didKey("service-tool-api") == "did:key:zDnaenNXPt8JM5YYhrjp23T2ZsgGjyEhVVSC7dhFjbQwdrxEN"` (the payee).
- Re-signing the quote (minus proof) with `service-tool-api` + `created=2026-03-25T21:30:00Z` reproduces its exact `proofValue` `z5MbMnuTG3AtHt1ScEfDaKp2ptWL6MZNm949zivVAe6Huji7iwScH1CX4pXd9LYwrKALsktKVUsT8xm7im25VhKmt`.

---

## File Structure

**`credentials/plugins/avp-micro`:**
- Modify `crypto/EcdsaJcs2022.kt` — extract private `hashData(unsecured, proofConfig)`; add public `sign(...)` + private `signRaw`/`toFixed32`.
- Create `src/test/.../crypto/TestSigning.kt` — test helper: `seedKey(label)`, `didKey(label)`, `verificationMethod(label)`, `sign(document, label, created)`.
- Create `src/test/.../crypto/EcdsaJcs2022SignTest.kt` — round-trip + sign-reproduction KAT.
- Create `src/test/.../crypto/TestSigningTest.kt` — the did:key KATs.
- Extend `src/test/.../verification/PaymentVerifierTest.kt` — the unlocked negatives.

---

## Task 1: `TestSigning` helper — harness seed-key reproduction

**Files:**
- Create: `credentials/plugins/avp-micro/src/test/kotlin/org/trustweave/credential/avpmicro/crypto/TestSigning.kt`
- Test: `credentials/plugins/avp-micro/src/test/kotlin/org/trustweave/credential/avpmicro/crypto/TestSigningTest.kt`

- [ ] **Step 1: Write the failing test**

`TestSigningTest.kt`:
```kotlin
package org.trustweave.credential.avpmicro.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class TestSigningTest {
    @Test
    fun `seed keys reproduce the harness did keys`() {
        assertEquals(
            "did:key:zDnaew8NDU8VgvxWpWWxBeLWaVbGNEuXYyRFk2uLMjCdhxkSU",
            TestSigning.didKey("agent-buyer-01"),
        )
        assertEquals(
            "did:key:zDnaenNXPt8JM5YYhrjp23T2ZsgGjyEhVVSC7dhFjbQwdrxEN",
            TestSigning.didKey("service-tool-api"),
        )
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*TestSigningTest"`
Expected: FAIL — `TestSigning` unresolved.

- [ ] **Step 3: Implement `TestSigning.kt`**

```kotlin
package org.trustweave.credential.avpmicro.crypto

import org.bouncycastle.jce.ECNamedCurveTable
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.base.DidMethodUtils
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec

/** Test-only signing helpers reproducing the Python harness's deterministic seed keys. */
object TestSigning {
    private val curve = ECNamedCurveTable.getParameterSpec("secp256r1")

    private fun scalar(label: String): BigInteger {
        val h = MessageDigest.getInstance("SHA-256").digest("avp-micro-test:$label".toByteArray())
        return BigInteger(1, h).mod(curve.n.subtract(BigInteger.ONE)).add(BigInteger.ONE)
    }

    fun seedKey(label: String): ECPrivateKey {
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
        return KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(scalar(label), params)) as ECPrivateKey
    }

    /** Compressed SEC1 public point (33 bytes) for the label's key. */
    private fun compressedPoint(label: String): ByteArray =
        curve.g.multiply(scalar(label)).normalize().getEncoded(true)

    fun didKey(label: String): String {
        val multikey = DidMethodUtils.getMulticodecPrefix("P-256") + compressedPoint(label)
        return "did:key:z" + multikey.encodeBase58()
    }

    fun verificationMethod(label: String): String {
        val did = didKey(label)
        return "$did#${did.removePrefix("did:key:")}"
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*TestSigningTest"`
Expected: PASS. (Confirms `seed_key` + did:key derivation match the harness byte-for-byte.) If a did:key mismatches, do not fudge — report the computed value (it would indicate a scalar-derivation or point-encoding discrepancy).

- [ ] **Step 5: Commit**

```bash
git add credentials/plugins/avp-micro/src
git commit -m "test(avp-micro): TestSigning harness seed-key reproduction"
```

---

## Task 2: `EcdsaJcs2022.sign` (deterministic) + round-trip

**Files:**
- Modify: `credentials/plugins/avp-micro/src/main/kotlin/org/trustweave/credential/avpmicro/crypto/EcdsaJcs2022.kt`
- Modify: `credentials/plugins/avp-micro/src/test/kotlin/org/trustweave/credential/avpmicro/crypto/TestSigning.kt` (add a `sign` convenience)
- Test: `credentials/plugins/avp-micro/src/test/kotlin/org/trustweave/credential/avpmicro/crypto/EcdsaJcs2022SignTest.kt`

- [ ] **Step 1: Write the failing round-trip test**

`EcdsaJcs2022SignTest.kt`:
```kotlin
package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

class EcdsaJcs2022SignTest {
    @Test
    fun `sign then verify round-trips`() {
        val doc = JsonObject(
            mapOf(
                "@context" to JsonPrimitive("https://example.org/ctx"),
                "hello" to JsonPrimitive("world"),
            )
        )
        val signed = TestSigning.sign(doc, "service-tool-api", "2026-01-01T00:00:00Z")
        assertTrue(EcdsaJcs2022.verify(signed))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*EcdsaJcs2022SignTest"`
Expected: FAIL — `TestSigning.sign` / `EcdsaJcs2022.sign` unresolved.

- [ ] **Step 3: Add `sign` to `EcdsaJcs2022`**

In `EcdsaJcs2022.kt`:

(a) Add imports:
```kotlin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.jce.ECNamedCurveTable
import org.trustweave.core.util.encodeBase58
import java.security.interfaces.ECPrivateKey
```

(b) Extract the shared hash core and refactor `verifyData` to use it. Replace the existing `verifyData` body so it builds the proof config then delegates to a private `hashData`:
```kotlin
    /** The 64-byte signing input: sha256(JCS(proofConfig)) || sha256(JCS(unsecuredDoc)). */
    fun verifyData(document: JsonObject): ByteArray {
        val proof = document.getValue("proof").jsonObject
        val proofConfig = buildJsonObject {
            for ((k, v) in proof) if (k != "proofValue") put(k, v)
            document["@context"]?.let { put("@context", it) }
        }
        return hashData(JsonObject(document.filterKeys { it != "proof" }), proofConfig)
    }

    private fun hashData(unsecured: JsonObject, proofConfig: JsonObject): ByteArray =
        sha256(Jcs.canonicalize(proofConfig)) + sha256(Jcs.canonicalize(unsecured))
```

(c) Add the signer:
```kotlin
    /**
     * Produce a `ecdsa-jcs-2022` Data Integrity proof over [document] (any existing `proof` is
     * replaced). Deterministic RFC 6979 + canonical low-s; raw R‖S; multibase base58btc.
     */
    fun sign(
        document: JsonObject,
        privateKey: ECPrivateKey,
        verificationMethod: String,
        created: String,
        proofPurpose: String = "assertionMethod",
    ): JsonObject {
        val unsecured = JsonObject(document.filterKeys { it != "proof" })
        val proofConfig = buildJsonObject {
            put("type", "DataIntegrityProof")
            put("cryptosuite", "ecdsa-jcs-2022")
            put("created", created)
            put("verificationMethod", verificationMethod)
            put("proofPurpose", proofPurpose)
            unsecured["@context"]?.let { put("@context", it) }
        }
        val raw = signRaw(privateKey, hashData(unsecured, proofConfig))
        val proof = buildJsonObject {
            put("type", "DataIntegrityProof")
            put("cryptosuite", "ecdsa-jcs-2022")
            put("created", created)
            put("verificationMethod", verificationMethod)
            put("proofPurpose", proofPurpose)
            put("proofValue", "z" + raw.encodeBase58())
        }
        return JsonObject(unsecured.toMutableMap().apply { put("proof", proof) })
    }

    private fun signRaw(privateKey: ECPrivateKey, hashData: ByteArray): ByteArray {
        val curve = ECNamedCurveTable.getParameterSpec("secp256r1")
        val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(privateKey.s, domain))
        val e = sha256(hashData) // ECDSA(SHA-256) signs SHA-256 of the message, matching the spec
        val sig = signer.generateSignature(e)
        val r = sig[0]
        var s = sig[1]
        val halfN = curve.n.shiftRight(1)
        if (s > halfN) s = curve.n.subtract(s) // canonical low-s
        return toFixed32(r) + toFixed32(s)
    }

    private fun toFixed32(v: java.math.BigInteger): ByteArray {
        val b = v.toByteArray()
        return when {
            b.size == 32 -> b
            b.size == 33 && b[0].toInt() == 0 -> b.copyOfRange(1, 33) // strip sign byte
            b.size < 32 -> ByteArray(32 - b.size) + b                 // left-pad
            else -> b.copyOfRange(b.size - 32, b.size)
        }
    }
```

Add the `sign` convenience to `TestSigning.kt`:
```kotlin
    fun sign(document: kotlinx.serialization.json.JsonObject, label: String, created: String) =
        EcdsaJcs2022.sign(document, seedKey(label), verificationMethod(label), created)
```

- [ ] **Step 4: Run it to verify it passes** (and that `verifyData` refactor didn't regress the KAT)

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*EcdsaJcs2022SignTest" --tests "*EcdsaJcs2022DataTest"`
Expected: PASS (round-trip green; the existing hashData KAT still green after the refactor).

- [ ] **Step 5: Commit**

```bash
git add credentials/plugins/avp-micro/src
git commit -m "feat(avp-micro): deterministic ecdsa-jcs-2022 sign"
```

---

## Task 3: Sign-reproduction KAT (byte-exact cross-stack)

**Files:**
- Test: `credentials/plugins/avp-micro/src/test/kotlin/org/trustweave/credential/avpmicro/crypto/EcdsaJcs2022SignTest.kt` (add a test)

- [ ] **Step 1: Add the reproduction test** (inside the existing `EcdsaJcs2022SignTest` class; add imports `org.trustweave.credential.avpmicro.Vectors`, `kotlinx.serialization.json.jsonObject`, `kotlinx.serialization.json.jsonPrimitive`, `kotlin.test.assertEquals`)

```kotlin
    @Test
    fun `reproduces the harness quote signature byte-for-byte`() {
        val unsigned = JsonObject(Vectors.paymentQuote.filterKeys { it != "proof" })
        val signed = TestSigning.sign(unsigned, "service-tool-api", "2026-03-25T21:30:00Z")
        val expected = Vectors.paymentQuote.getValue("proof").jsonObject.getValue("proofValue").jsonPrimitive.content
        val actual = signed.getValue("proof").jsonObject.getValue("proofValue").jsonPrimitive.content
        assertEquals(expected, actual)
    }
```

- [ ] **Step 2: Run it**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*EcdsaJcs2022SignTest"`
Expected: PASS — reproduces `z5MbMnuTG3AtHt1ScEfDaKp2ptWL6MZNm949zivVAe6Huji7iwScH1CX4pXd9LYwrKALsktKVUsT8xm7im25VhKmt`.

This is the strong cross-stack sign-parity gate. If it does NOT match but the Task 2 round-trip passes, the signer is internally correct but not byte-identical to Python's RFC 6979 — report the actual vs expected `proofValue` and do NOT weaken the assertion; the negative tests in Task 4 only need round-trip-valid signing, so they can proceed while this discrepancy is investigated separately.

- [ ] **Step 3: Commit**

```bash
git add credentials/plugins/avp-micro/src
git commit -m "test(avp-micro): sign-reproduction KAT vs the harness quote signature"
```

---

## Task 4: Unlock the deferred negative tests

**Files:**
- Test: `credentials/plugins/avp-micro/src/test/kotlin/org/trustweave/credential/avpmicro/verification/PaymentVerifierTest.kt`

These re-sign *modified* genuine vectors with the correct harness key, so the proof stays valid while one field diverges — exercising the negatives that previously needed a signer.

- [ ] **Step 1: Add the tests** (inside the existing `PaymentVerifierTest` class; add imports `org.trustweave.credential.avpmicro.crypto.TestSigning`, `kotlinx.serialization.json.jsonObject`)

```kotlin
    // Re-sign a genuine object after mutating one top-level field, using the harness key.
    private fun resign(doc: JsonObject, label: String, key: String, value: String): JsonObject {
        val created = doc.getValue("proof").jsonObject.getValue("created").jsonPrimitive.content
        val mutated = JsonObject(doc.toMutableMap().apply { put(key, JsonPrimitive(value)) })
        return TestSigning.sign(mutated, label, created)
    }

    @Test
    fun `authorization amount over cap fails with AMOUNT_EXCEEDED`() {
        // 0.06 > the credential's 0.05 perTransaction cap; re-signed so the proof is valid.
        val authz = resign(Vectors.paymentAuthorization, "agent-buyer-01", "amount", "0.06")
        val r = AvpMicro.verifyPayment(authz, now)
        assertEquals(VerificationFailure.AMOUNT_EXCEEDED, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `payer not the credential subject fails with SUBJECT_MISMATCH`() {
        val authz = resign(Vectors.paymentAuthorization, "agent-buyer-01", "payer", "did:key:zNotTheSubject")
        val r = AvpMicro.verifyPayment(authz, now)
        assertEquals(VerificationFailure.SUBJECT_MISMATCH, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `authorization amount differs from quote fails with AMOUNT_MISMATCH`() {
        // Re-signed authz with amount 0.002 (<= cap, so passes the cap check) but quote says 0.001.
        val authz = resign(Vectors.paymentAuthorization, "agent-buyer-01", "amount", "0.002")
        val r = AvpMicro.verifyPayment(authz, now, quote = Vectors.paymentQuote)
        assertEquals(VerificationFailure.AMOUNT_MISMATCH, (r as PaymentVerificationResult.Invalid).reason)
    }

    @Test
    fun `quote with mismatched digest fails with QUOTE_MISMATCH`() {
        // A genuinely-signed quote whose content (amount) differs -> its digest won't match authz.quoteDigest.
        val quote = resign(Vectors.paymentQuote, "service-tool-api", "amount", "0.002")
        val r = AvpMicro.verifyPayment(Vectors.paymentAuthorization, now, quote = quote)
        assertEquals(VerificationFailure.QUOTE_MISMATCH, (r as PaymentVerificationResult.Invalid).reason)
    }
```

- [ ] **Step 2: Run it**

Run: `.\gradlew :credentials:plugins:avp-micro:test --tests "*PaymentVerifierTest"`
Expected: PASS. Note on ordering: `AMOUNT_MISMATCH` requires the cap check to pass first — `0.002 <= 0.05` — then the quote-binding amount check fires; `QUOTE_MISMATCH` fires at the digest step because the re-signed quote's content changed.

- [ ] **Step 3: Run the whole module**

Run: `.\gradlew :credentials:plugins:avp-micro:test`
Expected: all green.

- [ ] **Step 4: Commit**

```bash
git add credentials/plugins/avp-micro/src
git commit -m "test(avp-micro): negative verifier + quote-binding cases via re-signing"
```

---

## Done criteria

- `.\gradlew :credentials:plugins:avp-micro:test` green.
- `TestSigning.didKey(label)` reproduces the harness payer/payee did:keys (KAT).
- `EcdsaJcs2022.sign` round-trips through `verify`, and reproduces the harness quote `proofValue` byte-for-byte (sign-repro KAT) — or, if RFC 6979 byte-parity proves finicky, round-trip passes and the discrepancy is reported (not hidden).
- The previously-deferred negatives (`AMOUNT_EXCEEDED`, `SUBJECT_MISMATCH`, `AMOUNT_MISMATCH`, `QUOTE_MISMATCH`) are now covered by tests that re-sign modified genuine vectors.

## Deferred (unchanged)

- Issuer trust (allowlist / registry) — product #2.
- Payer-binding hardening (`quote.payer == authz.payer`) — would diverge from `sim.py`; tracked in the authorization-service design.
